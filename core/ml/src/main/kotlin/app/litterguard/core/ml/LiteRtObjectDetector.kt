package app.litterguard.core.ml

import android.annotation.SuppressLint
import android.content.Context
import app.litterguard.core.domain.ports.ObjectDetector
import app.litterguard.core.domain.ports.ObjectDetectorFactory
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.ImageBuffer
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val ASSET_FOLDER = "models"

/** One LiteRT environment per process (ADR-0004). */
@Singleton
internal class LiteRtEnvironment
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val value: Environment by lazy { Environment.create(context) }
    }

/** Loads the detector, trying the manifest's variants in order and logging the one actually used (FR-2.7). */
@Singleton
internal class LiteRtObjectDetectorFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val environment: LiteRtEnvironment,
    ) : ObjectDetectorFactory {
        override suspend fun open(): ObjectDetector = open { true }

        /** [use] limits which variants are tried, for the on-device comparison test. */
        suspend fun open(use: (ModelManifest.Variant) -> Boolean): LiteRtObjectDetector {
            val manifest = readManifest()
            // The model is created, run and closed on this one thread (ADR-0004).
            val dispatcher =
                Executors.newSingleThreadExecutor { Thread(it, "litert-detector") }.asCoroutineDispatcher()
            var lastFailure: Exception? = null
            for (variant in manifest.detector.variants.filter(use)) {
                try {
                    val session = withContext(dispatcher) { load(manifest.detector, variant) }
                    return LiteRtObjectDetector(manifest.detector, variant, session, dispatcher)
                } catch (e: LiteRtException) {
                    Timber.w(e, "Detector variant %s on %s failed to load", variant.id, variant.accelerator)
                    lastFailure = e
                }
            }
            dispatcher.close()
            throw IllegalStateException("No detector variant could be loaded", lastFailure)
        }

        private fun readManifest(): ModelManifest =
            ModelManifest.parse(
                context.assets
                    .open("$ASSET_FOLDER/models.json")
                    .bufferedReader()
                    .use { it.readText() },
            )

        // LiteRT marks disableAutoFallbackToCpu() as "tests only", but it is the only way to make a GPU request
        // fail loudly instead of quietly running on the CPU while we record "GPU" (ADR-0004). The version is
        // pinned, and DetectorGoldenTest exercises this path on the device.
        @SuppressLint("RestrictedApi")
        private fun load(
            model: ModelManifest.DetectorModel,
            variant: ModelManifest.Variant,
        ): LiteRtSession {
            val accelerator = Accelerator.valueOf(variant.accelerator)
            val options =
                CompiledModel.Options(accelerator).apply {
                    // A GPU request must not silently run on the CPU.
                    if (accelerator != Accelerator.CPU) disableAutoFallbackToCpu()
                    variant.threads?.let { cpuOptions = CompiledModel.CpuOptions(numThreads = it) }
                }
            val compiled =
                CompiledModel.create(context.assets, "$ASSET_FOLDER/${variant.file}", options, environment.value)
            var session: LiteRtSession? = null
            try {
                session = LiteRtSession(compiled, compiled.createInputBuffers(), compiled.createOutputBuffers())
                // The first run compiles kernels and shaders; do it now rather than on the first real frame.
                val inputSize = model.input.size
                val output = session.run(FloatArray(CHANNELS * inputSize * inputSize))
                check(output.size == model.output.channels * model.output.anchors) {
                    "Model output has ${output.size} values, models.json expects " +
                        "${model.output.channels}×${model.output.anchors}"
                }
                return session
            } finally {
                if (session == null) compiled.close()
            }
        }

        private companion object {
            const val CHANNELS = 3
        }
    }

/** The native objects behind a loaded model. Use only on the model's thread. */
internal class LiteRtSession(
    private val model: CompiledModel,
    private val inputs: List<TensorBuffer>,
    private val outputs: List<TensorBuffer>,
) : AutoCloseable {
    fun run(input: FloatArray): FloatArray {
        inputs.first().writeFloat(input)
        model.run(inputs, outputs)
        return outputs.first().readFloat()
    }

    override fun close() {
        // Buffers first, then the model.
        (inputs + outputs).forEach { it.close() }
        model.close()
    }
}

internal class LiteRtObjectDetector(
    private val manifest: ModelManifest.DetectorModel,
    variant: ModelManifest.Variant,
    private val session: LiteRtSession,
    private val dispatcher: ExecutorCoroutineDispatcher,
) : ObjectDetector {
    override val description: String =
        "${manifest.name} ${manifest.version} ${variant.id} on ${variant.accelerator}" +
            (variant.threads?.let { " ×$it" } ?: "")

    private val size = manifest.input.size
    private val input = FloatArray(CHANNELS * size * size)
    private val decoder =
        YoloDecoder(manifest.output.anchors, manifest.postprocess.confidenceFloor, manifest.postprocess.nmsIou)
    private var closed = false

    init {
        require(manifest.input.layout == "NCHW") { "Only NCHW models are supported, got ${manifest.input.layout}" }
        Timber.i("Detector ready: %s", description)
    }

    override suspend fun detect(
        image: ImageBuffer,
        region: BoundingBox,
    ): List<Detection> =
        withContext(dispatcher) {
            check(!closed) { "Detector is closed" }
            val letterbox = Letterbox(region, image.width, image.height, size)
            letterbox.writeNchw(image, input)
            decoder.decode(session.run(input), letterbox)
        }

    override fun close() {
        if (closed) return
        runBlocking(dispatcher) {
            closed = true
            session.close()
        }
        dispatcher.close()
    }

    private companion object {
        const val CHANNELS = 3
    }
}
