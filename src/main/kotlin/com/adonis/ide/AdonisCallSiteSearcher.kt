package com.adonis.ide

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Scoped scan for Adonis call-site usages (mirrors LSP `_iter_reference_sources`).
 */
object AdonisCallSiteSearcher {
    data class Occurrence(
        val path: Path,
        val range: com.intellij.openapi.util.TextRange,
        val kind: SymbolKind,
        val name: String,
    )

    private val SCAN_DIRS = listOf(
        "app",
        "start",
        "routes",
        "resources/views",
        "config",
        "database",
        "tests",
    )

    private val SKIP_DIR_NAMES = setOf(
        ".git", ".idea", ".tox", ".venv", "__pycache__",
        "build", "dist", "htmlcov", "node_modules",
        "storage", "vendor", "website", "editors", "docs",
    )

    /** Kinds supported by Find Usages MVP. */
    val SUPPORTED = setOf(
        SymbolKind.ROUTE,
        SymbolKind.VIEW,
        SymbolKind.CONFIG,
        SymbolKind.COMPONENT,
        SymbolKind.ENV,
    )

    fun findUsages(appRoot: Path, kind: SymbolKind, name: String): List<Occurrence> {
        if (name.isBlank() || kind !in SUPPORTED) return emptyList()
        val out = mutableListOf<Occurrence>()
        val seen = mutableSetOf<Triple<String, Int, Int>>()
        for (file in iterSourceFiles(appRoot, kind)) {
            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                continue
            }
            val dotenv = file.name == ".env" || file.name.startsWith(".env.")
            for (occ in findInText(text, kind, name, dotenvFile = dotenv)) {
                val key = Triple(file.toString(), occ.range.startOffset, occ.range.endOffset)
                if (!seen.add(key)) continue
                out.add(occ.copy(path = file))
            }
        }
        return out
    }

    /** Pure text search — used by unit tests without a real app tree. */
    fun findInText(
        text: String,
        kind: SymbolKind,
        name: String,
        dotenvFile: Boolean = false,
    ): List<Occurrence> {
        val dummy = Path.of("in-memory")
        val out = mutableListOf<Occurrence>()
        val seen = mutableSetOf<Pair<Int, Int>>()
        for ((start, end) in candidateSpans(text, kind, name)) {
            val probe = text.substring(0, start) + name
            val site = CallSiteDetector.detect(probe, dotenvFile = dotenvFile) ?: continue
            if (!kindsCompatible(kind, site.kind)) continue
            if (site.prefix != name) continue
            val range = com.intellij.openapi.util.TextRange(start, end)
            val key = range.startOffset to range.endOffset
            if (!seen.add(key)) continue
            out.add(Occurrence(dummy, range, site.kind, name))
        }
        return out
    }

    private fun kindsCompatible(query: SymbolKind, found: SymbolKind): Boolean =
        found == query

    private data class Span(val start: Int, val end: Int)

    private fun candidateSpans(text: String, kind: SymbolKind, name: String): List<Span> {
        val out = mutableListOf<Span>()
        for (quote in charArrayOf('"', '\'')) {
            val needle = "$quote$name$quote"
            var from = 0
            while (true) {
                val idx = text.indexOf(needle, from)
                if (idx < 0) break
                out.add(Span(idx + 1, idx + 1 + name.length))
                from = idx + needle.length
            }
        }
        if (kind == SymbolKind.COMPONENT) {
            val tag = "<x-$name"
            var from = 0
            while (true) {
                val idx = text.indexOf(tag, from)
                if (idx < 0) break
                val start = idx + 3 // after <x-
                out.add(Span(start, start + name.length))
                from = idx + tag.length
            }
        }
        if (kind == SymbolKind.ENV) {
            val interp = "\${$name}"
            var from = 0
            while (true) {
                val idx = text.indexOf(interp, from)
                if (idx < 0) break
                out.add(Span(idx + 2, idx + 2 + name.length))
                from = idx + interp.length
            }
            val assign = Regex("""(?m)^\s*(?:export\s+)?(${Regex.escape(name)})\s*=""")
            for (m in assign.findAll(text)) {
                val g = m.groups[1] ?: continue
                out.add(Span(g.range.first, g.range.last + 1))
            }
        }
        return out
    }

    private fun iterSourceFiles(appRoot: Path, kind: SymbolKind): Sequence<Path> = sequence {
        val files = mutableListOf<Path>()
        val bases = SCAN_DIRS.map { appRoot.resolve(it) }.filter { it.isDirectory() }
            .ifEmpty { listOfNotNull(appRoot.takeIf { it.isDirectory() }) }

        for (base in bases) {
            Files.walkFileTree(
                base,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val n = dir.name
                        if (dir != base && (n in SKIP_DIR_NAMES || n.startsWith("."))) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val n = file.name
                        if (n.endsWith(".ts") || n.endsWith(".js") || n.endsWith(".edge") ||
                            n.endsWith(".tsx") || n.endsWith(".jsx") || n.endsWith(".vue")
                        ) {
                            files.add(file)
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }

        if (kind == SymbolKind.ENV && appRoot.isDirectory()) {
            Files.list(appRoot).use { stream ->
                for (child in stream) {
                    if (child.isRegularFile() &&
                        (child.name == ".env" || child.name.startsWith(".env."))
                    ) {
                        files.add(child)
                    }
                }
            }
        }

        yieldAll(files.map { it.toAbsolutePath().normalize() }.distinct())
    }
}
