package app.litterguard.core.ml

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parts of `models.json` the app needs at run time. */
@Serializable
internal data class ModelManifest(
    val detector: DetectorModel,
) {
    @Serializable
    data class DetectorModel(
        val name: String,
        val version: String,
        val input: Input,
        val output: Output,
        val postprocess: Postprocess,
        /** In order of preference: the first one that loads is used (ADR-0004). */
        val variants: List<Variant>,
    )

    @Serializable
    data class Input(
        val size: Int,
        val layout: String,
    )

    @Serializable
    data class Output(
        val channels: Int,
        val anchors: Int,
    )

    @Serializable
    data class Postprocess(
        val confidenceFloor: Float,
        val nmsIou: Float,
    )

    @Serializable
    data class Variant(
        val id: String,
        val file: String,
        val accelerator: String,
        val threads: Int? = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ModelManifest = json.decodeFromString(serializer(), text)
    }
}
