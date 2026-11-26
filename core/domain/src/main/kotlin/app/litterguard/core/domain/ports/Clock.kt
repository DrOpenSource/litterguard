package app.litterguard.core.domain.ports

import app.litterguard.core.model.MonotonicTime
import java.time.Instant

/** Injected time source, so everything time-dependent is deterministic in tests. */
interface Clock {
    /** For measuring durations. Not comparable across reboots. */
    fun monotonic(): MonotonicTime

    /** For records and messages shown to people. */
    fun wallClock(): Instant
}
