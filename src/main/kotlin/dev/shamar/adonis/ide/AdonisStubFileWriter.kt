package dev.shamar.adonis.ide

import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates stub Edge files for code actions — pure filesystem helper.
 */
object AdonisStubFileWriter {
    /**
     * Write [content] to [path] if missing. Creates parent directories.
     * @return true when a new file was written.
     */
    fun writeIfAbsent(path: Path, content: String): Boolean {
        if (Files.exists(path)) return false
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return true
    }

    /** Overwrite [path] with [content] (creates parents). */
    fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    fun applyCreateAction(action: AdonisCodeActionPlanner.Action): Boolean {
        val createPath = action.createPath ?: return false
        val body = AdonisCodeActionPlanner.stubEdgeContent(action.name)
        return writeIfAbsent(Path.of(createPath), body)
    }
}
