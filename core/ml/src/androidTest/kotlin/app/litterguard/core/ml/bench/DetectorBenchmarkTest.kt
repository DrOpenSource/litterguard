package app.litterguard.core.ml.bench

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * On-device latency benchmark for candidate detector models (task P0.8).
 *
 * Uses the production LiteRT CompiledModel API (ADR-0004), not the legacy `benchmark_model` tool. Model files are
 * never committed (hard rule 8). Put them in `src/androidTest/assets/bench/` locally (`*.tflite` is git-ignored),
 * named `<anything>_<imgsz>.tflite` with a single float32 image input of `3 × imgsz × imgsz` values (any layout;
 * `ml/scripts/export_bench_models.py` produces NCHW). Without them the test is skipped. Run it with:
 *
 * ```
 * ./gradlew :core:ml:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=app.litterguard.core.ml.bench.DetectorBenchmarkTest
 * adb logcat -d -s LGBench
 * ```
 *
 * Each timed iteration covers write + run + read-back, because GPU `run` returns before the GPU finishes
 * (android-litert-runtime skill).
 */
@RunWith(AndroidJUnit4::class)
class DetectorBenchmarkTest {
    private data class Setup(
        val name: String,
        val accelerator: Accelerator,
        val threads: Int? = null,
    )

    private val setups =
        listOf(
            Setup("CPU-2T", Accelerator.CPU, threads = 2),
            Setup("CPU-4T", Accelerator.CPU, threads = 4),
            Setup("GPU", Accelerator.GPU),
            Setup("NPU", Accelerator.NPU),
        )

    @Test
    fun benchmarkBundledModels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val models =
            assets
                .list(ASSET_DIR)
                .orEmpty()
                .filter { it.endsWith(".tflite") }
                .sorted()
        assumeTrue("No models in androidTest/assets/$ASSET_DIR; skipping benchmark", models.isNotEmpty())

        val environment = Environment.create(instrumentation.targetContext)
        log("device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT} models=${models.size}")
        try {
            for (model in models) {
                for (setup in setups) {
                    log(runOne(environment, "$ASSET_DIR/$model", setup))
                }
            }
        } finally {
            environment.close()
        }
    }

    private fun runOne(
        environment: Environment,
        assetPath: String,
        setup: Setup,
    ): String {
        val label = "${assetPath.substringAfterLast('/')} ${setup.name}"
        val options =
            CompiledModel.Options(setup.accelerator).apply {
                // Strict: a GPU/NPU request must not silently run on the CPU (LEARNINGS [litert]).
                if (setup.accelerator != Accelerator.CPU) disableAutoFallbackToCpu()
                setup.threads?.let { cpuOptions = CompiledModel.CpuOptions(numThreads = it) }
            }
        val heapBefore = Debug.getNativeHeapAllocatedSize()
        val loadStart = SystemClock.elapsedRealtimeNanos()
        val model =
            try {
                CompiledModel.create(
                    InstrumentationRegistry.getInstrumentation().context.assets,
                    assetPath,
                    options,
                    environment,
                )
            } catch (e: LiteRtException) {
                return "$label FAILED create: ${e.message?.take(MAX_ERROR_CHARS)}"
            }
        var inputs: List<TensorBuffer> = emptyList()
        var outputs: List<TensorBuffer> = emptyList()
        return try {
            inputs = model.createInputBuffers()
            outputs = model.createOutputBuffers()
            val input = randomInput(imageSizeOf(assetPath))
            val feed: (TensorBuffer) -> Unit = { it.writeFloat(input) }
            val loadMs = (SystemClock.elapsedRealtimeNanos() - loadStart) / NANOS_PER_MS

            // First run includes GPU shader compilation, so time it separately and exclude it from the stats.
            val firstMs = timeOnce(model, inputs, outputs, feed)
            repeat(WARMUP_RUNS) { timeOnce(model, inputs, outputs, feed) }
            val samples = DoubleArray(TIMED_RUNS) { timeOnce(model, inputs, outputs, feed) }.sorted()
            val heapMb = (Debug.getNativeHeapAllocatedSize() - heapBefore) / BYTES_PER_MB
            val outSizes = outputs.map { it.readFloat().size }
            "$label OK outFloats=$outSizes load=${loadMs}ms " +
                "first=${"%.1f".format(firstMs)}ms p50=${"%.1f".format(samples.percentile(P50))}ms " +
                "p90=${"%.1f".format(samples.percentile(P90))}ms min=${"%.1f".format(samples.first())}ms " +
                "nativeHeap+=${"%.1f".format(heapMb)}MB"
        } catch (e: LiteRtException) {
            "$label FAILED run: ${e.message?.take(MAX_ERROR_CHARS)}"
        } finally {
            (inputs + outputs).forEach { it.close() }
            model.close()
        }
    }

    private fun imageSizeOf(assetPath: String): Int =
        assetPath.substringAfterLast('_').removeSuffix(".tflite").toIntOrNull()
            ?: error("Benchmark model name must end in _<imgsz>.tflite: $assetPath")

    private fun randomInput(imageSize: Int): FloatArray {
        val random = Random(SEED)
        return FloatArray(imageSize * imageSize * CHANNELS) { random.nextFloat() }
    }

    private fun timeOnce(
        model: CompiledModel,
        inputs: List<TensorBuffer>,
        outputs: List<TensorBuffer>,
        feed: (TensorBuffer) -> Unit,
    ): Double {
        val start = SystemClock.elapsedRealtimeNanos()
        feed(inputs[0])
        model.run(inputs, outputs)
        // Read-back is where the app waits for the GPU; float outputs are what the detector parser consumes.
        outputs.forEach { it.readFloat() }
        return (SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MS.toDouble()
    }

    private fun List<Double>.percentile(p: Double): Double = this[((size - 1) * p).toInt()]

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "LGBench"
        const val ASSET_DIR = "bench"
        const val WARMUP_RUNS = 5
        const val TIMED_RUNS = 40
        const val P50 = 0.5
        const val P90 = 0.9
        const val SEED = 42
        const val CHANNELS = 3
        const val NANOS_PER_MS = 1_000_000L
        const val BYTES_PER_MB = 1_048_576.0
        const val MAX_ERROR_CHARS = 300
    }
}
