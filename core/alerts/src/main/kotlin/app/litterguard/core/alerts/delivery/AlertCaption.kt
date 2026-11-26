package app.litterguard.core.alerts.delivery

import app.litterguard.core.model.Incident
import app.litterguard.core.model.ObjectCategory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The text under the alert photos (FR-7.1): site, local time, who was seen, and the incident ID. */
internal class AlertCaption(
    private val zone: () -> ZoneId,
) {
    private val timeFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm:ss a", Locale.ENGLISH)

    fun of(incident: Incident): String {
        val seen =
            incident.actorCategories
                .sorted()
                .joinToString(" and ") { describe(it) }
                .ifEmpty { "not identified" }
        return listOf(
            "🚨 Illegal dumping detected",
            "Site: ${incident.siteName}",
            "Time: ${timeFormat.format(incident.confirmedAt.atZone(zone()))}",
            "Seen: $seen",
            "Number plate: not read yet, please check the photos",
            "Ref: ${incident.id.value.take(REFERENCE_LENGTH)}",
        ).joinToString("\n")
    }

    private fun describe(category: ObjectCategory): String =
        when (category) {
            ObjectCategory.PERSON -> "a person"
            ObjectCategory.VEHICLE -> "a vehicle"
            ObjectCategory.ANIMAL -> "an animal"
            ObjectCategory.OTHER -> "something else"
        }

    private companion object {
        const val REFERENCE_LENGTH = 8
    }
}
