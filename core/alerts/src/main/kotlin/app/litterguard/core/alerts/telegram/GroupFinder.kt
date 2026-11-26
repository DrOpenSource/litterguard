package app.litterguard.core.alerts.telegram

/**
 * Picks the alert group from recent bot updates (FR-1.5). Telegram forgets updates after 24 hours, so this relies
 * on the operator's action during setup.
 */
internal object GroupFinder {
    private val ACTIVE_STATUSES = setOf("member", "administrator")

    /**
     * A message carrying this setup's [nonce] wins, because only someone using our "add to group" link can send it.
     * Otherwise the newest group where the bot was just added or was sent `/start` is chosen. The operator then
     * sees the group's name and sends a test alert, which confirms the choice.
     */
    fun find(
        updates: List<TelegramUpdate>,
        botUsername: String,
        nonce: String?,
    ): TelegramChat? {
        val newestFirst = updates.sortedByDescending { it.updateId }
        val withNonce =
            newestFirst.firstNotNullOfOrNull { update ->
                update.message
                    ?.takeIf { it.chat.isGroup && nonce != null && it.text?.trim()?.endsWith(" $nonce") == true }
                    ?.chat
            }
        return withNonce ?: newestFirst.firstNotNullOfOrNull { candidate(it, botUsername) }
    }

    private fun candidate(
        update: TelegramUpdate,
        botUsername: String,
    ): TelegramChat? {
        val added = update.myChatMember?.takeIf { it.newMember.status in ACTIVE_STATUSES }?.chat
        val started =
            update.message
                ?.takeIf { isStartCommand(it.text, botUsername) }
                ?.chat
        return (added ?: started)?.takeIf { it.isGroup }
    }

    private fun isStartCommand(
        text: String?,
        botUsername: String,
    ): Boolean {
        val command =
            text
                ?.trim()
                ?.substringBefore(' ')
                ?: return false
        return command == "/start" || command.equals("/start@$botUsername", ignoreCase = true)
    }
}
