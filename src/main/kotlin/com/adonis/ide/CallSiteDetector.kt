package com.adonis.ide

/**
 * Detect which Adonis / Edge / Wire / Shamar string surface the caret is inside.
 */
object CallSiteDetector {
    data class Site(
        val kind: SymbolKind,
        val prefix: String,
        /** Model / table hint for columns / relations; env key for ENV_VALUE; wire name for props. */
        val receiver: String? = null,
        /** True when completing a pipe-segment of a validation rule string. */
        val validationSegment: Boolean = false,
    )

    private val COLUMN_FNS =
        "whereNotBetween|whereBetween|whereNotNull|whereNotIn|whereNull|whereDate|" +
            "whereNot|whereIn|orWhere|where|" +
            "orderByDesc|orderBy|groupBy|having|" +
            "select|pluck|increment|decrement|" +
            "sum|avg|max|min|latest|oldest|only|except|" +
            "update|create|firstOrCreate|updateOrCreate|firstOrNew|" +
            // snake_case aliases still seen in some codebases
            "where_not_in|where_in|or_where|order_by|order_by_desc|group_by|" +
            "first_or_create|update_or_create|add_select|force_fill"

    private val RELATION_FNS =
        "preload|withCount|has|whereHas|orWhereHas|doesntHave|with|" +
            "load|loadMissing|with_|load_missing|where_has|or_where_has|doesnt_have"

    private val METHOD_CALL = Regex(
        """(?<recv>\b[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(?<fn>route|route_is|view|config|__|t|trans|env|can|authorize|middleware|disk|render|$RELATION_FNS|$COLUMN_FNS|table|vite|asset|url)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val GLOBAL_CALL = Regex(
        """(?<![.\w])(?<fn>route|route_is|view|config|__|t|trans|env|can|authorize|middleware|disk|render|vite|asset|url|ace)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /** ``redirect().route("…`` — call after ``).``. */
    private val CHAINED_CALL = Regex(
        """\)\.(?<fn>route|route_is|view|config|__|t|trans|env|can|authorize|middleware|disk|render|$RELATION_FNS|$COLUMN_FNS|table|vite|asset|url|as|use)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /** Adonis ``env.get('KEY'`` / ``Env.get("KEY"``. */
    private val ENV_GET = Regex(
        """\b(?:env|Env)\.get\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    /** Second-arg defaults: ``env.get('KEY', 'sy`` or legacy ``env('KEY', 'sy``. */
    private val ENV_DEFAULT = Regex(
        """\b(?:(?:env|Env)\.get|(?<![.\w])env)\s*\(\s*(['"])(?<key>[A-Za-z_][\w]*)\1\s*,\s*(?:(['"])(?<pre>[^'"]*)|(?<bare>[A-Za-z_][\w.]*)?)?\z""",
    )

    /** Legacy / helper ``env('KEY'`` (single-arg). */
    private val ENV_CALL = Regex(
        """(?<![.\w])env\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val DIRECTIVE_VIEW = Regex(
        """@!?(?<dir>include|includeIf|includeWhen|includeUnless|each|component|wire|layout|section|svg|vite|extends|lang|choice|can|cannot|canany|cannotany|route|signedRoute|asset)\s*\(\s*(?:\[\s*)?(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val AT_DIRECTIVE = Regex("""@!?(?<pre>[A-Za-z_][\w]*)?\z""")

    private val COMPONENT_TAG = Regex("""<x-(?<pre>[\w./-]*)\z""")

    private val WIRE_TAG = Regex("""<wire:(?<pre>[\w./-]*)\z""")

    private val WIRE_ATTR = Regex(
        """\bwire:(?:model|click|submit|change|input|blur|focus|keydown|keyup|load|init|poll|ignore|key|dirty|loading|target|offline)\s*=\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val DOLLAR_WIRE_CALL = Regex(
        "\\\$wire\\.(?<pre>[A-Za-z_][\\w]*)\\s*\\(\\s*\\z",
    )

    private val DOLLAR_WIRE_PROP = Regex(
        "\\\$wire\\.(?<pre>[A-Za-z_][\\w]*)?\\z",
    )

    private val ROUTE_AS = Regex(
        """\.as\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val MIDDLEWARE_ARRAY = Regex(
        """(?:middleware|\.use)\s*\(\s*\[\s*(?:(?:['"][^'"]*['"])\s*,\s*)*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val SHAMAR_FIELD = Regex(
        """\b(?<fn>[A-Z][A-Za-z0-9_]*Input|[A-Z][A-Za-z0-9_]*(?:Picker|Editor|Upload|Repeater|Select|Toggle|Checkbox|Radio|Slider|Rating|Hidden))\s*\.\s*make\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val SHAMAR_COLUMN = Regex(
        """\b(?<fn>[A-Z][A-Za-z0-9_]*Column)\s*\.\s*make\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val SHAMAR_NAV = Regex(
        """\bnavigationGroup\s*=\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val DOTENV_INTERPOLATION = Regex("""\$\{(?<pre>[A-Za-z_][\w]*)?\z""")

    private val DOTENV_VALUE = Regex(
        """^\s*(?:export\s+)?(?<key>[A-Za-z_][\w]*)\s*=\s*(?<pre>[^#]*)\z""",
    )

    private val DOTENV_KEY = Regex(
        """^\s*(?:export\s+)?(?<pre>[A-Za-z_][\w]*)?\z""",
    )

    private val VALIDATION = Regex(
        """(?<fn>validate|rules|vine)\s*\([^)]*?(?:['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /** ``casts = { "email": "dat…`` — cast *type* value. */
    private val CASTS_VALUE = Regex(
        """casts\s*=\s*\{[^}]*['"][^'"]+['"]\s*:\s*['"](?<pre>[^'"]*)\z""",
    )

    /** ``casts = { "ema…`` — column *key*. */
    private val CASTS_KEY = Regex(
        """casts\s*=\s*\{(?:[^}'"]*(?:['"][^'"]*['"]\s*:\s*['"][^'"]*['"]\s*,\s*)*)\s*['"](?<pre>[^'"]*)\z""",
    )

    /** ``fillable = ["ema…`` / ``guarded = ("pass…``. */
    private val MODEL_LIST = Regex(
        """\b(?<attr>fillable|guarded|hidden|appends)\s*=\s*(?:\[|\()\s*(?:(?:['"][^'"]*['"])\s*,\s*)*['"](?<pre>[^'"]*)\z""",
    )

    /**
     * Instance attribute: ``user.ema``, ``auth().user.ema``, ``auth().user().ema``.
     */
    private val ATTR = Regex(
        """(?:auth\s*\(\s*\)(?:\s*\.\s*(?:use|guard)\s*\([^)]*\))?\s*\.\s*user\s*(?:\(\s*\))?|request\s*\.\s*user\s*(?:\(\s*\))?|(?<recv>[A-Za-z_][\w]*))\.(?<pre>[A-Za-z_][\w]*)?\z""",
    )

    private val CONTROLLER_ACTION = Regex(
        """\[\s*[A-Za-z_][\w.]*\s*,\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val ACE = Regex(
        """(?:Ace\.call|ace\.call|(?<![.\w])ace|Artisan::call|call)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val MUTATOR_FNS =
        "create|update|fill|merge|force_fill|firstOrCreate|updateOrCreate|firstOrNew|" +
            "first_or_create|update_or_create|first_or_new|find_or_new"

    /** ``Author.create(email=`` / ``user.update(name=``. */
    private val KWARG_COLUMN = Regex(
        """(?<recv>\b[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(?<fn>$MUTATOR_FNS)\s*\(\s*(?:.*,\s*)?(?<pre>[A-Za-z_][\w]*)?\s*=?\s*\z""",
        RegexOption.IGNORE_CASE,
    )

    /** ``Author.create({ ema`` / ``user.update({ name``. */
    private val DICT_COLUMN = Regex(
        """(?<recv>\b[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(?<fn>$MUTATOR_FNS)\s*\(\s*\{(?:[^}'"]*(?:['"][^'"]*['"]\s*:\s*[^,}]+,?\s*)*)\s*['"](?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * ``Author.query().where("`` — chained after ``query()`` so the model class
     * is recovered when [CHAINED_CALL] alone has no receiver.
     */
    private val MODEL_QUERY_CHAIN = Regex(
        """(?<recv>\b[A-Z][A-Za-z0-9_]*)\.(?:query|factory)\s*\([^)]*\)(?:\s*\.\s*[A-Za-z_][\w]*\s*\([^)]*\))*\s*\.\s*(?<fn>$RELATION_FNS|$COLUMN_FNS)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val COLUMN_FN_SET = COLUMN_FNS.split('|').map { it.lowercase() }.toSet()
    private val RELATION_FN_SET = RELATION_FNS.split('|').map { it.lowercase() }.toSet()
    private val CALL_MATCHERS = listOf(METHOD_CALL, CHAINED_CALL, GLOBAL_CALL)

    private fun kindForCall(fn: String): SymbolKind? = when (fn.lowercase()) {
        "route", "route_is", "as" -> SymbolKind.ROUTE
        "view" -> SymbolKind.VIEW
        "config" -> SymbolKind.CONFIG
        "__", "t", "trans" -> SymbolKind.TRANSLATION
        "env" -> SymbolKind.ENV
        "can", "authorize" -> SymbolKind.GATE
        "middleware", "use" -> SymbolKind.MIDDLEWARE
        "disk" -> SymbolKind.DISK
        "render" -> SymbolKind.INERTIA
        "table" -> SymbolKind.TABLE
        "vite", "asset", "url" -> SymbolKind.VITE
        "ace" -> SymbolKind.ACE
        else -> when {
            fn.lowercase() in RELATION_FN_SET -> SymbolKind.RELATION
            fn.lowercase() in COLUMN_FN_SET -> SymbolKind.COLUMN
            else -> null
        }
    }

    /**
     * @param dotenvFile when true, also match bare ``KEY`` / ``KEY=value`` lines
     *   (only safe inside ``.env`` / ``.env.*`` files).
     */
    fun detect(beforeCaret: String, dotenvFile: Boolean = false): Site? {
        if (dotenvFile) {
            detectDotenvLine(beforeCaret.substringAfterLast('\n'))?.let { return it }
        }

        val text = beforeCaret.replace('\n', ' ')
        val tail = if (text.length > 320) text.takeLast(320) else text

        DOTENV_INTERPOLATION.find(tail)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        ENV_DEFAULT.find(tail)?.let { m ->
            val pre = m.groups["pre"]?.value ?: m.groups["bare"]?.value ?: ""
            return Site(SymbolKind.ENV_VALUE, pre, receiver = m.groups["key"]?.value)
        }
        ENV_GET.find(tail)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        ENV_CALL.find(tail)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        WIRE_TAG.find(tail)?.let {
            return Site(SymbolKind.WIRE, it.groups["pre"]?.value ?: "")
        }
        WIRE_ATTR.find(tail)?.let {
            return Site(SymbolKind.WIRE_PROP, it.groups["pre"]?.value ?: "")
        }
        DOLLAR_WIRE_CALL.find(tail)?.let {
            return Site(SymbolKind.WIRE_METHOD, it.groups["pre"]?.value ?: "")
        }
        DOLLAR_WIRE_PROP.find(tail)?.let {
            return Site(SymbolKind.WIRE_PROP, it.groups["pre"]?.value ?: "")
        }
        COMPONENT_TAG.find(tail)?.let {
            return Site(SymbolKind.COMPONENT, it.groups["pre"]?.value ?: "")
        }
        AT_DIRECTIVE.find(tail)?.let {
            if (!tail.contains("(") || tail.lastIndexOf('@') > tail.lastIndexOf('(')) {
                return Site(SymbolKind.DIRECTIVE, it.groups["pre"]?.value ?: "")
            }
        }
        DIRECTIVE_VIEW.find(tail)?.let { m ->
            val name = m.groups["dir"]?.value ?: ""
            val kind = when (name) {
                "include", "includeIf", "includeWhen", "includeUnless",
                "each", "extends", "layout", "section",
                -> SymbolKind.VIEW
                "component" -> SymbolKind.COMPONENT
                "wire" -> SymbolKind.WIRE
                "lang", "choice" -> SymbolKind.TRANSLATION
                "can", "cannot", "canany", "cannotany" -> SymbolKind.GATE
                "route", "signedRoute" -> SymbolKind.ROUTE
                "svg", "vite", "asset" -> SymbolKind.VITE
                else -> SymbolKind.VIEW
            }
            return Site(kind, m.groups["pre"]?.value ?: "")
        }
        SHAMAR_NAV.find(tail)?.let {
            return Site(SymbolKind.SHAMAR_NAV, it.groups["pre"]?.value ?: "")
        }
        SHAMAR_COLUMN.find(tail)?.let {
            return Site(SymbolKind.SHAMAR_COLUMN, it.groups["pre"]?.value ?: "")
        }
        SHAMAR_FIELD.find(tail)?.let {
            return Site(SymbolKind.SHAMAR_FIELD, it.groups["pre"]?.value ?: "")
        }
        ROUTE_AS.find(tail)?.let {
            return Site(SymbolKind.ROUTE, it.groups["pre"]?.value ?: "")
        }
        MIDDLEWARE_ARRAY.find(tail)?.let {
            return Site(SymbolKind.MIDDLEWARE, it.groups["pre"]?.value ?: "")
        }
        CASTS_VALUE.find(tail)?.let {
            return Site(SymbolKind.CAST, it.groups["pre"]?.value ?: "")
        }
        CASTS_KEY.find(tail)?.let {
            return Site(SymbolKind.MODEL_ATTR, it.groups["pre"]?.value ?: "", receiver = "casts")
        }
        MODEL_LIST.find(tail)?.let { m ->
            return Site(
                SymbolKind.MODEL_ATTR,
                m.groups["pre"]?.value ?: "",
                receiver = m.groups["attr"]?.value,
            )
        }
        CONTROLLER_ACTION.find(tail)?.let {
            return Site(SymbolKind.CONTROLLER_ACTION, it.groups["pre"]?.value ?: "")
        }
        ACE.find(tail)?.let {
            return Site(SymbolKind.ACE, it.groups["pre"]?.value ?: "")
        }
        VALIDATION.find(tail)?.let { m ->
            val pre = m.groups["pre"]?.value ?: ""
            val segment = pre.substringAfterLast("|", pre)
            val prefix = if (pre.contains("|")) segment else pre
            return Site(SymbolKind.VALIDATION, prefix, validationSegment = pre.contains("|"))
        }
        KWARG_COLUMN.find(tail)?.let { m ->
            return Site(
                SymbolKind.COLUMN,
                m.groups["pre"]?.value ?: "",
                receiver = m.groups["recv"]?.value,
            )
        }
        DICT_COLUMN.find(tail)?.let { m ->
            return Site(
                SymbolKind.COLUMN,
                m.groups["pre"]?.value ?: "",
                receiver = m.groups["recv"]?.value,
            )
        }
        MODEL_QUERY_CHAIN.find(tail)?.let { m ->
            val fn = m.named("fn")?.lowercase() ?: return@let
            val kind = kindForCall(fn) ?: return@let
            return Site(kind, m.named("pre") ?: "", receiver = m.named("recv"))
        }
        CALL_MATCHERS.forEach { regex ->
            regex.find(tail)?.let { m ->
                val fn = m.named("fn")?.lowercase() ?: return@let
                val pre = m.named("pre") ?: ""
                var recv = m.named("recv")
                val kind = kindForCall(fn) ?: return@let
                if (recv == null && (kind == SymbolKind.COLUMN || kind == SymbolKind.RELATION)) {
                    recv = AdonisModelResolver.inferChainHead(tail)
                }
                return Site(kind, pre, receiver = recv)
            }
        }
        ATTR.find(tail)?.let { m ->
            val pre = m.named("pre") ?: ""
            val recv = when {
                m.value.contains("auth") && m.value.contains("user") ->
                    AdonisModelResolver.AUTH_USER_SENTINEL
                m.value.contains("request") && m.value.contains("user") ->
                    AdonisModelResolver.AUTH_USER_SENTINEL
                else -> m.named("recv")
            }
            if (recv != null && recv !in setOf("route", "view", "config", "env", "wire")) {
                return Site(SymbolKind.ATTR, pre, receiver = recv)
            }
        }
        // `{{ title` / `{{{ body` / `{!! html`
        Regex("""(?:\{\{\{|\{\{|\{\!\!)\s*(?<pre>[A-Za-z_][\w.]*)?\z""").find(tail)?.let {
            return Site(SymbolKind.TEMPLATE_VAR, it.groups["pre"]?.value ?: "")
        }
        return null
    }

    private fun MatchResult.named(name: String): String? =
        try {
            groups[name]?.value
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun detectDotenvLine(line: String): Site? {
        if (line.trimStart().startsWith("#")) return null
        DOTENV_INTERPOLATION.find(line)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        DOTENV_VALUE.matchEntire(line)?.let { m ->
            val raw = m.groups["pre"]?.value ?: ""
            return Site(
                SymbolKind.ENV_VALUE,
                stripDotenvValuePrefix(raw),
                receiver = m.groups["key"]?.value,
            )
        }
        DOTENV_KEY.matchEntire(line)?.let { m ->
            return Site(SymbolKind.ENV, m.groups["pre"]?.value ?: "")
        }
        return null
    }

    private fun stripDotenvValuePrefix(raw: String): String {
        val text = raw.trimStart()
        if (text.isEmpty()) return ""
        if (text[0] == '"' || text[0] == '\'') {
            val q = text[0]
            if (text.length == 1) return ""
            return if (text.last() == q) text.substring(1, text.length - 1) else text.substring(1)
        }
        return text
    }
}
