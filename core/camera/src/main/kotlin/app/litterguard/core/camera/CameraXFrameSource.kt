package app.litterguard.core.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.litterguard.core.domain.ports.AnalysisFrame
import app.litterguard.core.domain.ports.Clock
import app.litterguard.core.domain.ports.FrameSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** CameraX `ImageAnalysis` as a throttled stream of upright 1280×720 frames (FR-2.1). */
@Singleton
internal class CameraXFrameSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clock: Clock,
    ) : FrameSource {
        override fun frames(maxFps: Float): Flow<AnalysisFrame> =
            callbackFlow {
                val provider = ProcessCameraProvider.awaitInstance(context)
                val executor = Executors.newSingleThreadExecutor { Thread(it, "camera-analysis") }
                val analysis = buildAnalysis()
                analysis.setAnalyzer(executor, analyzer(this, FrameThrottle(maxFps), FramePool(POOL_SIZE)))
                val owner = withContext(Dispatchers.Main) { bind(provider, analysis) }
                val rotationListener = followDisplayRotation(analysis)

                awaitClose { release(provider, analysis, owner, executor, rotationListener) }
                // No queue: a frame that arrives while the collector is busy is dropped.
            }.buffer(Channel.RENDEZVOUS)

        private fun buildAnalysis(): ImageAnalysis =
            ImageAnalysis
                .Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                // CameraX delivers upright pixels, so nothing downstream has to think about sensor rotation.
                .setOutputImageRotationEnabled(true)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(displayRotation().takeIf(MountRotation::isLandscape) ?: Surface.ROTATION_90)
                .setResolutionSelector(
                    ResolutionSelector
                        .Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(TARGET_WIDTH, TARGET_HEIGHT),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        ).build(),
                ).build()

        private fun analyzer(
            scope: ProducerScope<AnalysisFrame>,
            throttle: FrameThrottle,
            pool: FramePool,
        ) = ImageAnalysis.Analyzer { proxy ->
            // `use` closes the ImageProxy on every path; a leaked one would stall the camera.
            proxy.use {
                val now = clock.monotonic()
                if (!throttle.accept(now.nanos)) return@use
                val buffers = pool.acquire(proxy.width, proxy.height)
                if (buffers == null) {
                    Timber.w("All frame buffers busy (%d in use); dropping a frame", pool.inUse)
                    return@use
                }
                copy(proxy, buffers)
                val frame = pool.frame(buffers, now)
                if (scope.trySend(frame).isFailure) frame.close()
            }
        }

        private fun copy(
            proxy: ImageProxy,
            buffers: FramePool.Buffers,
        ) {
            val plane = proxy.planes.first()
            RgbaFrames.convert(plane.buffer, plane.rowStride, proxy.width, proxy.height, buffers.argb, buffers.luma)
        }

        private fun bind(
            provider: ProcessCameraProvider,
            analysis: ImageAnalysis,
        ): SessionLifecycle {
            // Only this class uses the camera. A stream that is still closing must let go first, or CameraX
            // refuses the new one.
            provider.unbindAll()
            val owner = SessionLifecycle()
            owner.start()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            Timber.i("Camera bound")
            return owner
        }

        private fun followDisplayRotation(analysis: ImageAnalysis): DisplayManager.DisplayListener {
            val listener =
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit

                    override fun onDisplayRemoved(displayId: Int) = Unit

                    override fun onDisplayChanged(displayId: Int) {
                        val rotation = displayRotation()
                        if (displayId == Display.DEFAULT_DISPLAY && MountRotation.isLandscape(rotation)) {
                            analysis.targetRotation = rotation
                        }
                    }
                }
            displayManager().registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            return listener
        }

        private fun release(
            provider: ProcessCameraProvider,
            analysis: ImageAnalysis,
            owner: SessionLifecycle,
            executor: ExecutorService,
            rotationListener: DisplayManager.DisplayListener,
        ) {
            displayManager().unregisterDisplayListener(rotationListener)
            Handler(Looper.getMainLooper()).post {
                analysis.clearAnalyzer()
                provider.unbind(analysis)
                owner.destroy()
                executor.shutdown()
                Timber.i("Camera released")
            }
        }

        private fun displayManager() = context.getSystemService(DisplayManager::class.java)

        private fun displayRotation(): Int = displayManager().getDisplay(Display.DEFAULT_DISPLAY).rotation

        private companion object {
            const val TARGET_WIDTH = 1280
            const val TARGET_HEIGHT = 720

            /** One frame being processed, one in hand-over, one being filled. */
            const val POOL_SIZE = 3
        }
    }

/**
 * The app is landscape-only, but the lock screen and other apps (Telegram, say) turn the display upright while we
 * keep watching in the background. Following them would turn the picture under the operator's dump zone, so only the
 * two landscape rotations are ever followed.
 */
internal object MountRotation {
    fun isLandscape(rotation: Int): Boolean = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
}

/** Keeps the camera bound for exactly as long as the frame stream is collected. Main thread only. */
private class SessionLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
