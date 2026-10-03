package dev.shamar.adonis.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level Adonis app root + cached [AdonisIndex] from the bundled Node indexer.
 */
@Service(Service.Level.PROJECT)
class AdonisProjectService(private val project: Project) {
    private val indexRef = AtomicReference<AdonisIndex?>(null)
    private val LOG = logger<AdonisProjectService>()

    fun appRoot(): Path? {
        val base = project.guessProjectDir()?.toNioPath() ?: return null
        return findAppRoot(base)
    }

    fun index(): AdonisIndex {
        indexRef.get()?.let { return it }
        return rebuild()
    }

    fun rebuild(): AdonisIndex {
        val root = appRoot()
        val built = if (root == null) {
            AdonisIndex.empty(
                error = "No AdonisJS application found (missing adonisrc.ts / adonisrc.js / ace.js).",
            )
        } else {
            try {
                AdonisIndexProcess.run(root)
            } catch (t: Throwable) {
                LOG.warn("indexer failed for $root", t)
                AdonisIndex.empty(error = t.message ?: t.toString())
            }
        }
        indexRef.set(built)
        return built
    }

    fun invalidate() {
        indexRef.set(null)
    }

    companion object {
        fun getInstance(project: Project): AdonisProjectService =
            project.getService(AdonisProjectService::class.java)

        /** Walks up from [start] looking for an AdonisJS app root. */
        fun findAppRoot(start: Path): Path? {
            var current: Path? = start
            while (current != null) {
                if (isAdonisAppRoot(current)) return current
                current = current.parent
            }
            // Shallow search under common monorepo folders.
            for (dirName in listOf("apps", "examples", "packages")) {
                val dir = start.resolve(dirName)
                if (!Files.isDirectory(dir)) continue
                Files.list(dir).use { stream ->
                    for (child in stream.toList().sortedBy { it.fileName.toString() }) {
                        if (Files.isDirectory(child) && isAdonisAppRoot(child)) {
                            return child
                        }
                    }
                }
            }
            return null
        }

        /** @deprecated Use [findAppRoot]. */
        fun findBootstrapRoot(start: Path): Path? = findAppRoot(start)

        fun isAdonisAppRoot(dir: Path): Boolean =
            Files.isRegularFile(dir.resolve("adonisrc.ts")) ||
                Files.isRegularFile(dir.resolve("adonisrc.js")) ||
                Files.isRegularFile(dir.resolve("ace.js"))

        fun isIndexRelevant(file: VirtualFile): Boolean {
            val path = file.path.replace('\\', '/')
            return path.contains("/start/") ||
                path.contains("/config/") ||
                path.contains("/app/models/") ||
                path.contains("/app/controllers/") ||
                path.contains("/app/wire/") ||
                path.contains("/app/panels/") ||
                path.contains("/resources/views/") ||
                path.contains("/database/migrations/") ||
                file.name.startsWith(".env") ||
                file.name == "adonisrc.ts" ||
                file.name == "adonisrc.js" ||
                file.name == "ace.js" ||
                file.name == "package.json"
        }
    }
}
