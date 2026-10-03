package dev.shamar.adonis.ide

import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Spawns the bundled Node indexer (`indexer/index.mjs`) — thin process shell.
 * Never depends on Shamar packages or a framework-side Ace `ide:index` command.
 */
object AdonisIndexProcess {
    private val LOG = logger<AdonisIndexProcess>()

    fun run(root: Path): AdonisIndex {
        val indexerScript = resolveIndexerScript()
            ?: return AdonisIndex.empty(
                error = "Bundled indexer/index.mjs not found in plugin resources",
            )
        val cmd = AdonisIndexLoader.buildIndexCommand(root, indexerScript)
        cmd.withWorkDirectory(root.toFile())
        val output = ExecUtil.execAndGetOutput(cmd, TimeUnit.SECONDS.toMillis(60).toInt())
        if (output.exitCode != 0 && output.stdout.isBlank()) {
            return AdonisIndex.empty(
                error = "indexer failed (exit ${output.exitCode}): ${output.stderr.take(500)}",
            )
        }
        val text = output.stdout.trim()
        if (text.isEmpty()) {
            return AdonisIndex.empty(error = "indexer produced no output: ${output.stderr.take(500)}")
        }
        // Some Node versions / wrappers print logs before JSON — take the last `{…}` blob.
        val json = extractJsonObject(text) ?: text
        return try {
            AdonisIndexLoader.parse(json)
        } catch (t: Throwable) {
            LOG.warn("Failed to parse indexer JSON for $root", t)
            AdonisIndex.empty(error = "Invalid indexer JSON: ${t.message ?: t}")
        }
    }

    /**
     * Locate `indexer/index.mjs` from the plugin classpath / install dir.
     * When the resource lives inside a JAR, copy it to a temp file for Node.
     */
    fun resolveIndexerScript(): Path? {
        val resourceName = "indexer/index.mjs"

        // 1) ClassLoader resource (plugin jar or exploded classpath).
        val url = AdonisIndexProcess::class.java.classLoader.getResource(resourceName)
        if (url != null) {
            when (url.protocol) {
                "file" -> {
                    val path = Path.of(URI.create(url.toString()))
                    if (Files.isRegularFile(path)) return path
                }
                "jar" -> {
                    extractResourceToTemp(resourceName)?.let { return it }
                }
                else -> {
                    extractResourceToTemp(resourceName)?.let { return it }
                }
            }
        }

        // 2) Exploded plugin install under PathManager.getPluginsPath().
        val plugins = Path.of(PathManager.getPluginsPath())
        val candidates = listOf(
            plugins.resolve("adonis-idea").resolve(resourceName),
            plugins.resolve("Adonis").resolve(resourceName),
            plugins.resolve("dev.shamar.adonis.ide").resolve(resourceName),
            // Pre-rename installs (plugin id was com.adonis.ide).
            plugins.resolve("com.adonis.ide").resolve(resourceName),
        )
        for (candidate in candidates) {
            if (Files.isRegularFile(candidate)) return candidate
        }

        // 3) Dev / sibling checkout: repo-root/indexer/index.mjs next to the running module.
        val cwd = Path.of("").toAbsolutePath()
        val dev = cwd.resolve(resourceName)
        if (Files.isRegularFile(dev)) return dev

        return null
    }

    private fun extractResourceToTemp(resourceName: String): Path? {
        val stream = AdonisIndexProcess::class.java.classLoader.getResourceAsStream(resourceName)
            ?: return null
        return try {
            val temp = Files.createTempFile("adonis-idea-indexer-", ".mjs")
            temp.toFile().deleteOnExit()
            stream.use { input ->
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
            }
            temp
        } catch (t: Throwable) {
            LOG.warn("Failed to extract $resourceName to temp", t)
            null
        }
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
