package app.litterguard.core.domain.site

import app.litterguard.core.domain.config.Sensitivity
import app.litterguard.core.model.Polygon

/** What the operator configures for the one site this phone watches (PRD §8). Secrets are stored separately. */
data class SiteConfig(
    val siteName: String = DEFAULT_SITE_NAME,
    /** The dump zone, normalised to the upright analysis frame (FR-1.3). Null until the operator draws it. */
    val zone: Polygon? = null,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    /** Telegram group that receives alerts, detected automatically during setup (FR-1.5). */
    val alertChatId: String? = null,
    val alertChatTitle: String? = null,
) {
    companion object {
        const val DEFAULT_SITE_NAME = "My site"
    }
}
