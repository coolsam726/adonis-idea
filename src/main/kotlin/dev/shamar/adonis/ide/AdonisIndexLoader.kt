package dev.shamar.adonis.ide

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses bundled indexer JSON dumps and builds the `node indexer/index.mjs` command line.
 * Process spawning lives in [AdonisIndexProcess].
 */
object AdonisIndexLoader {
    fun parse(json: String): AdonisIndex {
        val root = JsonParser.parseString(json).asJsonObject
        val routes = mutableMapOf<String, AdonisIndex.RouteEntry>()
        root.getAsJsonObject("routes")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            routes[name] = AdonisIndex.RouteEntry(
                uri = stringOrEmpty(obj, "uri"),
                methods = obj.getAsJsonArray("methods")?.map { it.asString } ?: emptyList(),
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
            )
        }
        val tables = mutableMapOf<String, AdonisIndex.TableEntry>()
        root.getAsJsonObject("tables")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val cols = mutableMapOf<String, AdonisIndex.Located>()
            obj.getAsJsonObject("columns")?.entrySet()?.forEach { (colName, colVal) ->
                val col = colVal.asJsonObject
                cols[colName] = AdonisIndex.Located(
                    path = stringOrNull(col, "path"),
                    line = col.get("line")?.asInt ?: 0,
                )
            }
            tables[name] = AdonisIndex.TableEntry(
                columns = cols,
                detail = stringOrEmpty(obj, "detail"),
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
                model = stringOrNull(obj, "model"),
            )
        }
        val modelMetadata = mutableMapOf<String, AdonisIndex.ModelEntry>()
        val relations = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("model_metadata")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val rels = obj.getAsJsonArray("relations")?.map { it.asString } ?: emptyList()
            val casts = mutableMapOf<String, String>()
            obj.getAsJsonObject("casts")?.entrySet()?.forEach { (k, v) -> casts[k] = v.asString }
            val relationLines = mutableMapOf<String, Int>()
            obj.getAsJsonObject("relation_lines")?.entrySet()?.forEach { (k, v) ->
                relationLines[k] = v.asInt
            }
            modelMetadata[name] = AdonisIndex.ModelEntry(
                fillable = obj.getAsJsonArray("fillable")?.map { it.asString } ?: emptyList(),
                guarded = obj.getAsJsonArray("guarded")?.map { it.asString } ?: emptyList(),
                hidden = obj.getAsJsonArray("hidden")?.map { it.asString } ?: emptyList(),
                casts = casts,
                relations = rels,
                relationLines = relationLines,
                module = stringOrEmpty(obj, "module"),
                path = stringOrEmpty(obj, "path"),
                table = stringOrEmpty(obj, "table"),
                orm = stringOrEmpty(obj, "orm"),
            )
            relations[name] = rels
        }
        root.getAsJsonObject("relations")?.entrySet()?.forEach { (name, value) ->
            relations[name] = value.asJsonArray.map { it.asString }
        }

        val viewData = mutableMapOf<String, Map<String, AdonisIndex.ViewVarEntry>>()
        root.getAsJsonObject("view_data")?.entrySet()?.forEach { (view, value) ->
            val vars = mutableMapOf<String, AdonisIndex.ViewVarEntry>()
            value.asJsonObject.entrySet().forEach { (varName, varVal) ->
                vars[varName] = viewVarEntry(varVal.asJsonObject)
            }
            viewData[view] = vars
        }

        val viewHelpers = mutableMapOf<String, AdonisIndex.ViewVarEntry>()
        val helpersEl = root.get("view_helpers")
        when {
            helpersEl == null || helpersEl.isJsonNull -> Unit
            helpersEl.isJsonArray -> helpersEl.asJsonArray.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val obj = el.asJsonObject
                val name = obj.get("name")?.asString ?: return@forEach
                viewHelpers[name] = viewVarEntry(obj)
            }
            helpersEl.isJsonObject -> helpersEl.asJsonObject.entrySet().forEach { (name, value) ->
                if (value.isJsonObject) viewHelpers[name] = viewVarEntry(value.asJsonObject)
                else viewHelpers[name] = AdonisIndex.ViewVarEntry(kind = "helper")
            }
        }

        val viewShared = mutableMapOf<String, AdonisIndex.ViewVarEntry>()
        root.getAsJsonObject("view_shared")?.entrySet()?.forEach { (name, value) ->
            if (value.isJsonObject) {
                viewShared[name] = viewVarEntry(value.asJsonObject)
            } else {
                viewShared[name] = AdonisIndex.ViewVarEntry(kind = "shared")
            }
        }

        val controllerActions = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("controller_actions")?.entrySet()?.forEach { (name, value) ->
            controllerActions[name] = value.asJsonArray.map { it.asString }
        }

        val controllerLocations = mutableMapOf<String, AdonisIndex.Located>()
        root.getAsJsonObject("controller_locations")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            controllerLocations[name] = AdonisIndex.Located(
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
            )
        }

        val envKeys = mutableMapOf<String, AdonisIndex.EnvEntry>()
        root.getAsJsonObject("env_keys")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val usedBy = obj.getAsJsonArray("used_by")?.map { it.asString } ?: emptyList()
            envKeys[name] = AdonisIndex.EnvEntry(
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
                kind = stringOrEmpty(obj, "kind"),
                detail = stringOrEmpty(obj, "detail"),
                usedBy = usedBy,
            )
        }

        val envOptions = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("env_options")?.entrySet()?.forEach { (name, value) ->
            envOptions[name] = when {
                value.isJsonArray -> value.asJsonArray.map { it.asString }
                else -> emptyList()
            }
        }

        val configLocations = mutableMapOf<String, AdonisIndex.Located>()
        root.getAsJsonObject("config_locations")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            configLocations[name] = AdonisIndex.Located(
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
            )
        }

        val wireComponents = mutableMapOf<String, AdonisIndex.WireEntry>()
        root.getAsJsonObject("wire_components")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            wireComponents[name] = AdonisIndex.WireEntry(
                path = stringOrNull(obj, "path"),
                view = stringOrNull(obj, "view"),
                props = obj.getAsJsonArray("props")?.map { it.asString } ?: emptyList(),
                methods = obj.getAsJsonArray("methods")?.map { it.asString } ?: emptyList(),
            )
        }

        val frameworkObj = root.getAsJsonObject("framework")
        val framework = AdonisIndex.FrameworkEntry(
            adonis = frameworkObj?.get("adonis")?.asBoolean ?: true,
            shamar = frameworkObj?.get("shamar")?.asBoolean ?: false,
            wire = (frameworkObj?.get("wire")?.asBoolean ?: false) || wireComponents.isNotEmpty(),
            orm = frameworkObj?.get("orm")?.asString ?: "unknown",
        )

        val shamarEl = root.get("shamar")
        val shamar = parseShamar(
            if (shamarEl != null && shamarEl.isJsonObject) shamarEl.asJsonObject else null,
        )

        return AdonisIndex(
            basePath = stringOrNull(root, "base_path") ?: "",
            ok = root.get("ok")?.asBoolean ?: false,
            error = stringOrNull(root, "error"),
            framework = framework,
            views = stringMap(root, "views"),
            routes = routes,
            configKeys = stringList(root, "config_keys"),
            configFiles = stringMap(root, "config_files"),
            configLocations = configLocations,
            translationKeys = stringList(root, "translation_keys"),
            middlewareAliases = stringList(root, "middleware_aliases"),
            envKeys = envKeys,
            envOptions = envOptions,
            tables = tables,
            modelMetadata = modelMetadata,
            relations = relations,
            casts = stringList(root, "casts"),
            components = stringMap(root, "components"),
            gates = stringList(root, "gates"),
            disks = stringList(root, "disks"),
            queues = stringList(root, "queues"),
            caches = stringList(root, "caches"),
            mailers = stringList(root, "mailers"),
            inertiaPages = stringList(root, "inertia_pages"),
            aceCommands = stringList(root, "ace_commands"),
            validationRules = stringList(root, "validation_rules"),
            directives = stringList(root, "directives"),
            viewHelpers = viewHelpers,
            viewShared = viewShared,
            viewData = viewData,
            viteEntries = stringMap(root, "vite_entries"),
            controllerActions = controllerActions,
            controllerLocations = controllerLocations,
            wireComponents = wireComponents,
            shamar = shamar,
        )
    }

    /**
     * Always uses the bundled Node indexer — never requires Shamar packages
     * or `node ace list` / a framework-side `ide:index` command.
     */
    internal fun buildIndexCommand(root: Path, indexerScriptPath: Path): GeneralCommandLine {
        val node = AdonisNode.resolveBinary(root)
        return GeneralCommandLine(
            node,
            indexerScriptPath.toAbsolutePath().toString(),
            "--path",
            root.toAbsolutePath().toString(),
            "--json",
        )
    }

    private fun parseShamar(obj: JsonObject?): AdonisIndex.ShamarEntry {
        if (obj == null) return AdonisIndex.ShamarEntry()
        val resources = mutableMapOf<String, AdonisIndex.ShamarResourceEntry>()
        obj.getAsJsonObject("resources")?.entrySet()?.forEach { (name, value) ->
            val r = value.asJsonObject
            resources[name] = AdonisIndex.ShamarResourceEntry(
                className = stringOrEmpty(r, "class"),
                panel = stringOrEmpty(r, "panel"),
                slug = stringOrEmpty(r, "slug").ifBlank { name },
                label = stringOrEmpty(r, "label").ifBlank { name },
                navigationGroup = stringOrNull(r, "navigationGroup"),
                icon = stringOrNull(r, "icon"),
                model = stringOrNull(r, "model"),
                path = stringOrNull(r, "path"),
            )
        }
        val pages = mutableMapOf<String, AdonisIndex.ShamarPageEntry>()
        obj.getAsJsonObject("pages")?.entrySet()?.forEach { (name, value) ->
            val p = value.asJsonObject
            pages[name] = AdonisIndex.ShamarPageEntry(
                className = stringOrEmpty(p, "class"),
                panel = stringOrEmpty(p, "panel"),
                slug = stringOrEmpty(p, "slug").ifBlank { name },
                label = stringOrEmpty(p, "label").ifBlank { name },
                navigationGroup = stringOrNull(p, "navigationGroup"),
                icon = stringOrNull(p, "icon"),
                path = stringOrNull(p, "path"),
            )
        }
        val widgets = mutableMapOf<String, AdonisIndex.ShamarWidgetEntry>()
        obj.getAsJsonObject("widgets")?.entrySet()?.forEach { (name, value) ->
            val w = value.asJsonObject
            widgets[name] = AdonisIndex.ShamarWidgetEntry(
                className = stringOrEmpty(w, "class").ifBlank { name },
                panel = stringOrEmpty(w, "panel"),
                kind = stringOrNull(w, "kind"),
                path = stringOrNull(w, "path"),
            )
        }
        return AdonisIndex.ShamarEntry(
            panels = obj.getAsJsonArray("panels")?.map { it.asString } ?: emptyList(),
            resources = resources,
            pages = pages,
            widgets = widgets,
            navGroups = obj.getAsJsonArray("nav_groups")?.map { it.asString } ?: emptyList(),
            fieldTypes = obj.getAsJsonArray("field_types")?.map { it.asString } ?: emptyList(),
            columnTypes = obj.getAsJsonArray("column_types")?.map { it.asString } ?: emptyList(),
            widgetTypes = obj.getAsJsonArray("widget_types")?.map { it.asString } ?: emptyList(),
            icons = obj.getAsJsonArray("icons")?.map { it.asString } ?: emptyList(),
        )
    }

    private fun viewVarEntry(obj: JsonObject): AdonisIndex.ViewVarEntry =
        AdonisIndex.ViewVarEntry(
            path = stringOrNull(obj, "path"),
            line = obj.get("line")?.asInt ?: 0,
            kind = stringOrEmpty(obj, "kind").ifBlank { "data" },
        )

    private fun stringOrNull(obj: JsonObject, field: String): String? {
        val el = obj.get(field) ?: return null
        if (el.isJsonNull) return null
        return el.asString
    }

    private fun stringOrEmpty(obj: JsonObject, field: String): String =
        stringOrNull(obj, field) ?: ""

    private fun stringMap(root: JsonObject, field: String): Map<String, String> {
        val obj = root.getAsJsonObject(field) ?: return emptyMap()
        return obj.entrySet().associate { (k, v) ->
            k to if (v.isJsonPrimitive) v.asString else v.toString()
        }
    }

    private fun stringList(root: JsonObject, field: String): Set<String> {
        val el = root.get(field) ?: return emptySet()
        return when {
            el.isJsonArray -> el.asJsonArray.map { it.asString }.toSet()
            el.isJsonObject -> el.asJsonObject.keySet()
            else -> emptySet()
        }
    }
}

/** Resolves a Node binary for Ace / indexer process launches. */
object AdonisNode {
    fun resolveBinary(root: Path?): String {
        if (root != null) {
            val local = root.resolve("node_modules/.bin/node")
            if (Files.isRegularFile(local) && (Files.isExecutable(local) || isWindows())) {
                return local.toAbsolutePath().toString()
            }
            val localCmd = root.resolve("node_modules/.bin/node.cmd")
            if (Files.isRegularFile(localCmd)) {
                return localCmd.toAbsolutePath().toString()
            }
        }
        for (name in listOf("node", "nodejs")) {
            if (isOnPath(name)) return name
        }
        return "node"
    }

    private fun isOnPath(name: String): Boolean {
        val pathEnv = System.getenv("PATH") ?: return false
        return pathEnv.split(File.pathSeparator).any { dir ->
            if (dir.isBlank()) return@any false
            val base = Path.of(dir)
            val candidates = buildList {
                add(base.resolve(name))
                if (isWindows()) {
                    add(base.resolve("$name.exe"))
                    add(base.resolve("$name.cmd"))
                }
            }
            candidates.any { Files.isRegularFile(it) && (Files.isExecutable(it) || isWindows()) }
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
