package app.litterguard.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

/** Registers the `fetchModels` task for a module that packages the models listed in its `models.json`. */
fun Project.registerFetchModels(): TaskProvider<FetchModelsTask> {
    val repositoryRoot = rootProject.layout.projectDirectory
    val gradleHome = gradle.gradleUserHomeDir
    val overrideDir = providers.gradleProperty("litterguardModelsDir")
    return tasks.register<FetchModelsTask>("fetchModels") {
        manifest.set(layout.projectDirectory.file("models.json"))
        localDir.set(overrideDir.map { repositoryRoot.dir(it).asFile.absolutePath })
        cacheDir.set(gradleHome.resolve("caches/litterguard-models"))
        outputDir.set(layout.buildDirectory.dir("generated/models"))
    }
}

/**
 * Puts the models listed in `models.json` into the app's assets, verifying each against its sha256 (ADR-0004).
 * A mismatch, or a model that can't be obtained, fails the build.
 *
 * Sources, in order: `-PlitterguardModelsDir=<dir>`, the per-user cache, then the manifest URL.
 */
@CacheableTask
abstract class FetchModelsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    /** Absolute path of a folder holding model files, for models that aren't published yet. */
    @get:Input
    @get:Optional
    abstract val localDir: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val target = outputDir.get().asFile.resolve("models")
        target.deleteRecursively()
        target.mkdirs()
        val manifestFile = manifest.get().asFile
        manifestFile.copyTo(target.resolve("models.json"))

        for (variant in variants(manifestFile)) {
            val destination = target.resolve(variant.file)
            locate(variant).copyTo(destination)
            val actual = sha256(destination)
            if (actual != variant.sha256) {
                destination.delete()
                throw GradleException(
                    "sha256 mismatch for ${variant.file}: models.json says ${variant.sha256}, got $actual",
                )
            }
        }
    }

    private data class Variant(
        val file: String,
        val url: String,
        val sha256: String,
    )

    private fun variants(file: File): List<Variant> {
        val root = JsonSlurper().parse(file) as Map<*, *>
        return root.values.flatMap { model ->
            ((model as Map<*, *>)["variants"] as List<*>).map { entry ->
                val fields = entry as Map<*, *>
                Variant(fields["file"] as String, fields["url"] as String, fields["sha256"] as String)
            }
        }
    }

    private fun locate(variant: Variant): File {
        localDir.orNull?.let { dir ->
            val local = File(dir, variant.file)
            if (local.isFile) return local
        }
        val cached = cacheDir.get().asFile.resolve("${variant.sha256}/${variant.file}")
        if (cached.isFile && sha256(cached) == variant.sha256) return cached
        download(variant, cached)
        return cached
    }

    private fun download(
        variant: Variant,
        destination: File,
    ) {
        destination.parentFile.mkdirs()
        val partial = File(destination.path + ".part")
        try {
            logger.lifecycle("Downloading model ${variant.file}")
            URI(variant.url).toURL().openStream().use { input -> partial.outputStream().use(input::copyTo) }
        } catch (e: IOException) {
            partial.delete()
            throw GradleException(
                "Could not download ${variant.file} from ${variant.url}. " +
                    "For a local copy, pass -PlitterguardModelsDir=<folder>.",
                e,
            )
        }
        destination.delete()
        check(partial.renameTo(destination)) { "Could not move ${partial.name} into the model cache" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
