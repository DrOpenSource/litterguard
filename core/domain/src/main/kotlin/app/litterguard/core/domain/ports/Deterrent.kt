package app.litterguard.core.domain.ports

import app.litterguard.core.domain.decision.DeterrentClip

/** Where sound will come out. */
sealed interface AudioRoute {
    data object PhoneSpeaker : AudioRoute

    data class Bluetooth(
        val deviceName: String,
    ) : AudioRoute
}

interface Deterrent {
    fun currentRoute(): AudioRoute

    /**
     * Plays a bundled clip at full volume and returns when it has finished, restoring the previous volume
     * (FR-4.1, FR-4.2). Cancelling stops playback.
     */
    suspend fun play(clip: DeterrentClip)
}
