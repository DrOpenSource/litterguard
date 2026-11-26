package app.litterguard.core.data

import app.litterguard.core.domain.geometry.RoiRules
import app.litterguard.core.domain.geometry.RoiValidation
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.Polygon
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Stores the dump zone as JSON `[[x,y],…]`, normalised to the analysis frame (FR-1.3). */
internal object ZoneCodec {
    private val serializer = ListSerializer(ListSerializer(Float.serializer()))

    fun encode(zone: Polygon): String = Json.encodeToString(serializer, zone.vertices.map { listOf(it.x, it.y) })

    /** Returns null for anything that isn't a usable zone: bad stored data must not crash the app. */
    fun decode(text: String): Polygon? {
        val pairs =
            try {
                Json.decodeFromString(serializer, text)
            } catch (expected: SerializationException) {
                // Unreadable stored data counts as "no zone".
                null
            }
        val usable = pairs != null && pairs.all { pair -> pair.size == 2 && pair.all { it.isFinite() } }
        if (!usable) return null
        val validation = RoiRules.validate(requireNotNull(pairs).map { NormalizedPoint(it[0], it[1]) })
        return (validation as? RoiValidation.Valid)?.roi
    }
}
