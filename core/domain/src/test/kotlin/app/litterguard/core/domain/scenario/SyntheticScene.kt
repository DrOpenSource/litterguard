package app.litterguard.core.domain.scenario

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.decision.DeterrentClip
import app.litterguard.core.domain.decision.Effect
import app.litterguard.core.domain.pipeline.DecisionPipeline
import app.litterguard.core.domain.pipeline.PipelineSnapshot
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.Polygon
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** The dump zone used by all synthetic scenes. */
val SCENE_ZONE =
    Polygon(
        listOf(
            NormalizedPoint(0.20f, 0.30f),
            NormalizedPoint(0.80f, 0.30f),
            NormalizedPoint(0.80f, 0.95f),
            NormalizedPoint(0.20f, 0.95f),
        ),
    )

private const val FRAME_WIDTH = 640
private const val FRAME_HEIGHT = 360
private const val TILE = 6

/** A textured, deterministic "ground", wider than the frame so that camera shake can be simulated. */
private val GROUND: Array<IntArray> =
    Random(seed = 7).let { random ->
        val tiles = Array(FRAME_HEIGHT / TILE + 4) { IntArray(FRAME_WIDTH / TILE + 4) { random.nextInt(90, 170) } }
        Array(FRAME_HEIGHT + 2 * TILE) { y -> IntArray(FRAME_WIDTH + 2 * TILE) { x -> tiles[y / TILE][x / TILE] } }
    }

/** A moment in a keyframed path: at [time] seconds, the thing's ground-contact point is at ([x], [y]). */
data class Key(
    val time: Double,
    val x: Float,
    val y: Float,
)

/**
 * Something drawn into the scene. If [category] is set, the fake detector also reports it, like the real model
 * reporting a person; dumped objects have none because the detector is not expected to recognise them.
 */
class Sprite(
    val category: ObjectCategory?,
    val width: Float,
    val height: Float,
    private val tone: Int,
    private val path: List<Key>,
) {
    fun boxAt(time: Double): BoundingBox? {
        if (time < path.first().time || time > path.last().time) return null
        val after = path.indexOfFirst { it.time >= time }
        val b = path[after]
        val a = if (after == 0) b else path[after - 1]
        val share = if (b.time == a.time) 0f else ((time - a.time) / (b.time - a.time)).toFloat()
        val x = a.x + (b.x - a.x) * share
        val y = a.y + (b.y - a.y) * share
        return BoundingBox(x - width / 2, y - height, x + width / 2, y)
    }

    fun draw(
        pixels: IntArray,
        box: BoundingBox,
    ) {
        val x0 = (box.left * FRAME_WIDTH).roundToInt().coerceIn(0, FRAME_WIDTH)
        val x1 = (box.right * FRAME_WIDTH).roundToInt().coerceIn(0, FRAME_WIDTH)
        val y0 = (box.top * FRAME_HEIGHT).roundToInt().coerceIn(0, FRAME_HEIGHT)
        val y1 = (box.bottom * FRAME_HEIGHT).roundToInt().coerceIn(0, FRAME_HEIGHT)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val stripe = if (((x - x0) / TILE + (y - y0) / TILE) % 2 == 0) 0 else STRIPE
                pixels[y * FRAME_WIDTH + x] = tone + stripe
            }
        }
    }

    private companion object {
        const val STRIPE = 25
    }
}

fun person(vararg path: Key) = Sprite(ObjectCategory.PERSON, width = 0.07f, height = 0.30f, tone = 30, path.toList())

fun scooter(vararg path: Key) = Sprite(ObjectCategory.VEHICLE, width = 0.16f, height = 0.20f, tone = 200, path.toList())

fun car(vararg path: Key) = Sprite(ObjectCategory.VEHICLE, width = 0.32f, height = 0.28f, tone = 40, path.toList())

fun dog(vararg path: Key) = Sprite(ObjectCategory.ANIMAL, width = 0.10f, height = 0.10f, tone = 25, path.toList())

fun cow(vararg path: Key) = Sprite(ObjectCategory.ANIMAL, width = 0.24f, height = 0.24f, tone = 215, path.toList())

/** A bag lying still at ([x], [y]) from [from] to [until] seconds. */
fun bag(
    x: Float,
    y: Float,
    from: Double,
    until: Double = Double.MAX_VALUE,
) = Sprite(category = null, width = 0.07f, height = 0.09f, tone = 20, listOf(Key(from, x, y), Key(until, x, y)))

/** A bag being carried: it follows its keyframes. */
fun carriedBag(vararg path: Key) = Sprite(category = null, width = 0.05f, height = 0.07f, tone = 20, path.toList())

class SyntheticScene(
    private val sprites: List<Sprite>,
    /** Global brightness change over time (clouds, auto-exposure). */
    private val brightness: (Double) -> Int = { 0 },
    /** Horizontal camera displacement in pixels over time (shake). */
    private val shake: (Double) -> Int = { 0 },
) {
    fun lumaAt(time: Double): LumaPlane {
        val dx = shake(time).coerceIn(-TILE, TILE) + TILE
        val pixels = IntArray(FRAME_WIDTH * FRAME_HEIGHT) { GROUND[it / FRAME_WIDTH][it % FRAME_WIDTH + dx] }
        sprites.forEach { sprite -> sprite.boxAt(time)?.let { sprite.draw(pixels, it) } }
        val light = brightness(time)
        val noise = Random((time * 1000).toLong())
        val data = ByteArray(pixels.size) { (pixels[it] + light + noise.nextInt(-3, 4)).coerceIn(0, 255).toByte() }
        return LumaPlane(FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, data)
    }

    fun detectionsAt(time: Double): List<Detection> =
        sprites.mapNotNull { sprite ->
            val category = sprite.category ?: return@mapNotNull null
            val box = sprite.boxAt(time) ?: return@mapNotNull null
            // A detector only reports things that are mostly inside the picture.
            if (box.intersectionArea(BoundingBox(0f, 0f, 1f, 1f)) < box.area / 2) return@mapNotNull null
            Detection(box.clampToUnit(), category, category.name.lowercase(), confidence = 0.8f)
        }
}

data class TimedEffect(
    val time: Double,
    val effect: Effect,
)

class SceneResult(
    val effects: List<TimedEffect>,
    val snapshots: List<PipelineSnapshot>,
    val detectorRuns: Int,
) {
    val incidents get() = effects.mapNotNull { (it.effect as? Effect.CommitIncident)?.draft }

    val sirens get() = effects.filter { it.effect == Effect.PlayDeterrent(DeterrentClip.WARNING) }

    val alerts get() = effects.filter { it.effect is Effect.EnqueueAlert }

    val merges get() = effects.filter { it.effect is Effect.MergeIntoIncident }

    fun incidentsOfKind(kind: IncidentKind) = incidents.filter { it.kind == kind }
}

/** Plays a scene through the real decision pipeline at [DetectionConfig.analysisFps]. */
suspend fun SyntheticScene.run(
    seconds: Double,
    config: DetectionConfig = DetectionConfig(),
): SceneResult {
    var nextId = 0
    val pipeline = DecisionPipeline(SCENE_ZONE, config) { IncidentId("incident-${++nextId}") }
    val effects = mutableListOf<TimedEffect>()
    val snapshots = mutableListOf<PipelineSnapshot>()
    var detectorRuns = 0
    val frames = (seconds * config.analysisFps).toInt()
    for (frame in 0..frames) {
        val time = frame / config.analysisFps.toDouble()
        val step =
            pipeline.process(lumaAt(time), MonotonicTime(0) + time.seconds) {
                detectorRuns++
                detectionsAt(time)
            }
        effects += step.effects.map { TimedEffect(time, it) }
        snapshots += step.snapshot
    }
    return SceneResult(effects, snapshots, detectorRuns)
}
