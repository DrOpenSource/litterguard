package app.litterguard.feature.monitor

import app.litterguard.core.domain.decision.Phase
import app.litterguard.core.domain.pipeline.PipelineSnapshot

/** The one-line answer to "what is it doing right now?". */
internal enum class Headline {
    LEARNING_SCENE,
    WATCHING,
    PERSON_IN_ZONE,
    ANIMAL_IN_ZONE,
    CHECKING,
    NEW_OBJECT,
    ALERT_RAISED,
}

internal fun headlineOf(snapshot: PipelineSnapshot): Headline =
    when (snapshot.phase) {
        Phase.Idle -> {
            when {
                snapshot.cooldown != null -> Headline.ALERT_RAISED
                !snapshot.hasBaseline -> Headline.LEARNING_SCENE
                else -> Headline.WATCHING
            }
        }

        is Phase.ActorPresent -> {
            Headline.PERSON_IN_ZONE
        }

        is Phase.AnimalVisit -> {
            Headline.ANIMAL_IN_ZONE
        }

        is Phase.Settling -> {
            Headline.CHECKING
        }

        is Phase.ObjectPending -> {
            Headline.NEW_OBJECT
        }
    }

/** How long the candidate object has been sitting there, in whole seconds, or null if there is none. */
internal fun pendingSeconds(snapshot: PipelineSnapshot): Int? {
    val pending = snapshot.phase as? Phase.ObjectPending ?: return null
    return (snapshot.timestamp - pending.since).inWholeSeconds.toInt()
}
