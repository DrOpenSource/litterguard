package app.litterguard.core.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.litterguard.core.domain.decision.DeterrentClip
import app.litterguard.core.domain.ports.AudioRoute
import app.litterguard.core.domain.ports.Deterrent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Plays the bundled clips on the alarm stream at full volume, then puts the volume back (FR-4.1, FR-4.2). */
@Singleton
internal class SirenPlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Deterrent {
        private val audio = context.getSystemService(AudioManager::class.java)

        // One clip at a time, so "the previous volume" is always the operator's real setting.
        private val oneAtATime = Mutex()

        override fun currentRoute(): AudioRoute {
            val bluetooth =
                audio
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            return if (bluetooth == null) {
                AudioRoute.PhoneSpeaker
            } else {
                AudioRoute.Bluetooth(bluetooth.productName.toString())
            }
        }

        override suspend fun play(clip: DeterrentClip) {
            oneAtATime.withLock { withContext(Dispatchers.Main) { playAtFullVolume(clip) } }
        }

        private suspend fun playAtFullVolume(clip: DeterrentClip) {
            val attributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            val focus =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attributes)
                    .build()
            val previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.requestAudioFocus(focus)
            val player = MediaPlayer()
            try {
                setVolume(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
                player.setAudioAttributes(attributes)
                context.resources.openRawResourceFd(resourceOf(clip)).use(player::setDataSource)
                player.prepare()
                awaitCompletion(player)
            } finally {
                player.release()
                setVolume(previousVolume)
                audio.abandonAudioFocusRequest(focus)
            }
        }

        private suspend fun awaitCompletion(player: MediaPlayer) =
            suspendCancellableCoroutine { continuation ->
                player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                player.setOnErrorListener { _, what, extra ->
                    Timber.e("Siren playback error %d/%d", what, extra)
                    if (continuation.isActive) continuation.resume(Unit)
                    true
                }
                player.start()
            }

        private fun setVolume(volume: Int) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            } catch (e: SecurityException) {
                // Some Do Not Disturb modes forbid volume changes; play at the current volume instead.
                Timber.w(e, "Not allowed to change the alarm volume")
            }
        }

        private fun resourceOf(clip: DeterrentClip): Int =
            when (clip) {
                DeterrentClip.WARNING -> R.raw.warning_en
                DeterrentClip.NOTICE -> R.raw.notice_en
                DeterrentClip.TEST_TONE -> R.raw.test_tone
            }
    }
