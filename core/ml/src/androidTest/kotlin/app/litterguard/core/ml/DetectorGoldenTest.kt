package app.litterguard.core.ml

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.ObjectCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Golden test for every shipped detector variant (BEST_PRACTICES §6): it must load with the shapes `models.json`
 * declares and find the obvious things in a reference photo.
 *
 * The photo is `bus.jpg` from the Ultralytics package, supplied locally in the git-ignored
 * `src/androidTest/assets/bench/` folder; without it the detection check is skipped.
 */
@RunWith(AndroidJUnit4::class)
class DetectorGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val factory = LiteRtObjectDetectorFactory(context, LiteRtEnvironment(context))

    @Test
    fun everyVariantLoadsWithTheDeclaredShapes() =
        runBlocking {
            for (variant in VARIANTS) {
                factory.open { it.id == variant }.use { Log.i(TAG, "loaded ${it.description}") }
            }
        }

    @Test
    fun everyVariantFindsThePeopleAndTheBus() =
        runBlocking {
            val photo = loadReferencePhoto()
            assumeTrue("No $PHOTO in androidTest assets; skipping", photo != null)
            for (variant in VARIANTS) {
                factory.open { it.id == variant }.use { detector ->
                    val started = SystemClock.elapsedRealtimeNanos()
                    val found = detector.detect(requireNotNull(photo), BoundingBox(0f, 0f, 1f, 1f))
                    val millis = (SystemClock.elapsedRealtimeNanos() - started) / NANOS_PER_MS
                    Log.i(TAG, "$variant took ${millis}ms: ${describe(found)}")

                    val confident = found.filter { it.confidence >= ACTOR_CONFIDENCE }
                    val people = confident.count { it.category == ObjectCategory.PERSON }
                    assertTrue("$variant found $people people: ${describe(found)}", people >= MIN_PEOPLE)
                    assertTrue(
                        "$variant missed the bus: ${describe(found)}",
                        confident.any { it.category == ObjectCategory.VEHICLE },
                    )
                }
            }
        }

    private fun describe(found: List<Detection>): String =
        found.joinToString { d ->
            "%s %.2f [%.2f %.2f %.2f %.2f]".format(
                d.label,
                d.confidence,
                d.box.left,
                d.box.top,
                d.box.right,
                d.box.bottom,
            )
        }

    private fun loadReferencePhoto(): ImageBuffer? {
        val bitmap =
            try {
                context.assets.open(PHOTO).use(BitmapFactory::decodeStream)
            } catch (e: IOException) {
                Log.i(TAG, "No reference photo: ${e.message}")
                return null
            }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ImageBuffer(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
    }

    private companion object {
        const val TAG = "LGGolden"
        const val PHOTO = "bench/bus.jpg"
        val VARIANTS = listOf("int8", "fp32")
        const val ACTOR_CONFIDENCE = 0.45f
        const val MIN_PEOPLE = 3
        const val NANOS_PER_MS = 1_000_000L
    }
}
