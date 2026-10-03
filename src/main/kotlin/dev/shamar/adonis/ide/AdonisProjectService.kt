package dev.shamar.adonis.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level Adonis app root + cached [AdonisIndex] from the bundled Node indexer.
 */
@Service(Service.Level.PROJECT)
class AdonisProjectService(private val project: Project) {
    private val indexRef = AtomicReference<AdonisIndex?>(null)
    private val rebuildScheduled = AtomicBoolean(false)
    private val LOG = logger<AdonisProjectService>()

    fun appRoot(): Path? {
        val base = try {
            project.guessProjectDir()?.toNioPath()
        } catch (_: UnsupportedOperationException) {
            // Light-test temp VFS has no NIO path.
            null
        } ?: return null
        return findAppRoot(base)
    }

    /** Cached index, or null when never built / invalidated. */
    fun cachedIndex(): AdonisIndex? = indexRef.get()

    /**
     * Returns the cached index when warm. On a cold cache:
     * - background thread / unit tests → builds synchronously
     * - EDT → schedules a background rebuild and returns a non-blocking placeholder
     *   (never runs the Node indexer on the EDT).
     */
    fun index(): AdonisIndex {
        indexRef.get()?.let { return it }
        if (mustNotBlockEdt()) {
            scheduleRebuild()
            return AdonisIndex.empty(error = "Indexing…")
        }
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
        rebuildScheduled.set(false)
        return built
    }

    /**
     * Run [rebuild] off the EDT, then invoke [onDone] on the EDT (if provided).
     */
    fun rebuildAsync(onDone: ((AdonisIndex) -> Unit)? = null) {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            val built = rebuild()
            if (onDone != null) {
                app.invokeLater { onDone(built) }
            }
        }
    }

    fun invalidate() {
        indexRef.set(null)
        rebuildScheduled.set(false)
    }

    private fun scheduleRebuild() {
        if (!rebuildScheduled.compareAndSet(false, true)) return
        rebuildAsync()
    }

    private fun mustNotBlockEdt(): Boolean {
        val app = ApplicationManager.getApplication() ?: return false
        if (app.isUnitTestMode) return false
        return app.isDispatchThread
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
