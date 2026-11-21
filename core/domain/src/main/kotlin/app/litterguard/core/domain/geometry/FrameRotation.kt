package app.litterguard.core.domain.geometry

/** Quarter-turn rotations a camera frame can need to become upright (CameraX `rotationDegrees`). */
enum class FrameRotation(
    val degrees: Int,
) {
    NONE(0),
    CW_90(90),
    CW_180(180),
    CW_270(270),
    ;

    companion object {
        private const val FULL_TURN = 360

        fun fromDegrees(degrees: Int): FrameRotation =
            entries.firstOrNull { it.degrees == ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN }
                ?: throw IllegalArgumentException("Rotation must be a multiple of 90°, was $degrees")
    }
}
