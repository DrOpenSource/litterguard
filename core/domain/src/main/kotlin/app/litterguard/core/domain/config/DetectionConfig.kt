package app.litterguard.core.domain.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Every tunable of the detection pipeline, in one place (PRD §6.4). Nothing else in the code base may hard-code
 * these values. When a default changes here, update the PRD table in the same change.
 *
 * Fractions are of the ROI area unless stated otherwise; luma values are 8-bit (0–255).
 */
data class DetectionConfig(
    /** Frames per second fed to the pipeline. Lowered at runtime by the thermal policy (FR-8.4). */
    val analysisFps: Float = 3f,
    /** Per-pixel luma change that counts as motion, on the downscaled ROI crop. */
    val motionLumaThreshold: Int = 25,
    /** Share of ROI pixels that must change before the frame counts as "motion". */
    val motionMinFraction: Float = 0.005f,
    /** Minimum detector confidence for a person/vehicle/animal to be tracked. */
    val actorMinConfidence: Float = 0.45f,
    /** Consecutive in-ROI frames before an actor counts as "entered" (hysteresis). */
    val actorEnterFrames: Int = 2,
    /** Consecutive missing frames before an actor counts as "left" (hysteresis). */
    val actorExitFrames: Int = 3,
    /** Quiet time after the last actor leaves before comparing against the baseline. */
    val settleTime: Duration = 1.seconds,
    /** Smallest new-object blob, as a share of the **whole frame** area. */
    val objectMinFrameFraction: Float = 0.0015f,
    /** How long a new object must stay static before dumping is confirmed. */
    val dwellTime: Duration = 5.seconds,
    /** IoU a candidate blob must keep with its first appearance to count as static. */
    val objectStabilityIou: Float = 0.6f,
    /** If the baseline diff covers more of the ROI than this, assume a lighting change or shake and abort. */
    val illuminationAbortFraction: Float = 0.35f,
    /** No new siren for this long after an alert; detections in the window merge into the same incident. */
    val cooldown: Duration = 60.seconds,
    /** How often the empty-ROI baseline is refreshed while IDLE. */
    val baselineRefresh: Duration = 30.seconds,
    /** An actor static for longer than this is treated as parked (stays tracked, stops consuming captures). */
    val parkedActorTimeout: Duration = 180.seconds,
    /** Best analysis frames kept per vehicle track for evidence. */
    val evidenceFramesPerTrack: Int = 3,
    /** Speculative high-resolution captures allowed per vehicle track. */
    val speculativeCapturesPerTrack: Int = 2,
    /** Margin added around the ROI bounds for ROI-crop inference, as a share of the ROI's width/height. */
    val roiCropMargin: Float = 0.10f,
    /** Play the "area under surveillance" notice while an actor is present (FR-3.5). Off by owner decision D2. */
    val preWarningEnabled: Boolean = false,
) {
    init {
        require(analysisFps in MIN_FPS..MAX_FPS) { "analysisFps must be in $MIN_FPS..$MAX_FPS, was $analysisFps" }
        require(motionLumaThreshold in 1..MAX_LUMA) { "motionLumaThreshold must be in 1..$MAX_LUMA" }
        requireFraction(motionMinFraction, "motionMinFraction")
        requireFraction(actorMinConfidence, "actorMinConfidence")
        require(actorEnterFrames >= 1) { "actorEnterFrames must be >= 1" }
        require(actorExitFrames >= 1) { "actorExitFrames must be >= 1" }
        require(!settleTime.isNegative()) { "settleTime must not be negative" }
        requireFraction(objectMinFrameFraction, "objectMinFrameFraction")
        require(dwellTime.isPositive()) { "dwellTime must be positive" }
        requireFraction(objectStabilityIou, "objectStabilityIou")
        requireFraction(illuminationAbortFraction, "illuminationAbortFraction")
        require(!cooldown.isNegative()) { "cooldown must not be negative" }
        require(baselineRefresh.isPositive()) { "baselineRefresh must be positive" }
        require(parkedActorTimeout > dwellTime) { "parkedActorTimeout must exceed dwellTime" }
        require(evidenceFramesPerTrack >= 1) { "evidenceFramesPerTrack must be >= 1" }
        require(speculativeCapturesPerTrack >= 0) { "speculativeCapturesPerTrack must be >= 0" }
        require(roiCropMargin in 0f..1f) { "roiCropMargin must be in [0, 1]" }
    }

    /**
     * Worst-case time from "last actor left the ROI" to the siren starting, per PRD §6.5:
     * settle + dwell + one frame interval + audio start-up. J2 requires this to stay under 8 s.
     */
    val worstCaseTriggerLatency: Duration
        get() = settleTime + dwellTime + (1.0 / analysisFps).seconds + AUDIO_START_BUDGET

    companion object {
        const val MIN_FPS = 0.5f
        const val MAX_FPS = 5f
        const val MAX_LUMA = 255

        /** PRD §6.5 budget for the deterrent to start playing after confirmation. */
        val AUDIO_START_BUDGET: Duration = 0.5.seconds

        /** J2 acceptance limit for [worstCaseTriggerLatency]. */
        val MAX_TRIGGER_LATENCY: Duration = 8.seconds

        /** Operator-facing presets (FR-1.6). All presets keep [worstCaseTriggerLatency] under 8 s. */
        fun forSensitivity(sensitivity: Sensitivity): DetectionConfig =
            when (sensitivity) {
                Sensitivity.LOW -> {
                    DetectionConfig(
                        actorMinConfidence = 0.55f,
                        objectMinFrameFraction = 0.0025f,
                        dwellTime = 6.seconds,
                    )
                }

                Sensitivity.NORMAL -> {
                    DetectionConfig()
                }

                Sensitivity.HIGH -> {
                    DetectionConfig(
                        actorMinConfidence = 0.35f,
                        objectMinFrameFraction = 0.0010f,
                        dwellTime = 4.seconds,
                    )
                }
            }

        private fun requireFraction(
            value: Float,
            name: String,
        ) {
            require(value in 0f..1f) { "$name must be in [0, 1], was $value" }
        }
    }
}

/** Operator-facing sensitivity. LOW = fewer false alarms, may miss small objects; HIGH = the opposite. */
enum class Sensitivity { LOW, NORMAL, HIGH }
