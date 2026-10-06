package dev.shamar.adonis.ide

/**
 * Catalog of `node ace make:*` generators exposed in the Adonis menu / New… dialogs.
 * Pure data — IntelliJ actions collect options and invoke Ace via [buildAceCommand].
 */
object AdonisMakeCatalog {
    data class Generator(
        val id: String,
        /** Menu label, e.g. "Controller". */
        val label: String,
        /** Full ace subcommand, e.g. `make:controller`. */
        val command: String,
        /** Dialog prompt when a name is required. Null → run with no extra args. */
        val namePrompt: String? = "Name",
        val description: String = "",
        /** When true, the New… action shows companion-file checkboxes. */
        val interactive: Boolean = false,
    ) {
        fun aceArgs(name: String?): String {
            val trimmed = name?.trim().orEmpty()
            return if (namePrompt == null || trimmed.isEmpty()) command
            else "$command $trimmed"
        }
    }

    /**
     * Companion flags for `node ace make:model` (AdonisJS Ace scaffolder).
     */
    data class ModelOptions(
        val all: Boolean = false,
        val migration: Boolean = false,
        val factory: Boolean = false,
        val controller: Boolean = false,
    )

    val ALL: List<Generator> = listOf(
        Generator("controller", "Controller", "make:controller", "Controller name (e.g. posts_controller)"),
        Generator(
            "model", "Model", "make:model", "Model name (e.g. user)",
            interactive = true,
        ),
        Generator("migration", "Migration", "make:migration", "Migration name (e.g. create_users_table)"),
        Generator("seeder", "Seeder", "make:seeder", "Seeder name (e.g. user_seeder)"),
        Generator("factory", "Factory", "make:factory", "Factory name (e.g. user)"),
        Generator("middleware", "Middleware", "make:middleware", "Middleware name (e.g. auth_middleware)"),
        Generator("validator", "Validator", "make:validator", "Validator name (e.g. create_user)"),
        Generator("exception", "Exception", "make:exception", "Exception name (e.g. unauthorized)"),
        Generator("listener", "Listener", "make:listener", "Listener name"),
        Generator("provider", "Provider", "make:provider", "Provider name"),
        Generator("command", "Command", "make:command", "Command name (e.g. greet)"),
        Generator("test", "Test", "make:test", "Test name (e.g. users/list)"),
        Generator("view", "View", "make:view", "View name (e.g. posts/index)"),
        Generator("event", "Event", "make:event", "Event name"),
        Generator("policy", "Policy", "make:policy", "Policy name"),
        Generator("service", "Service", "make:service", "Service name"),
        // Optional Shamar / Wire generators (soft — Ace may reject if package absent)
        Generator("wire", "Wire Component", "make:wire", "Wire component name (e.g. counter)"),
        Generator("panel", "Panel", "make:panel", "Panel id (e.g. admin)"),
        Generator(
            "widget",
            "Widget",
            "shamar:make-widget",
            "Widget name (e.g. ProductStats)",
            description = "Scaffold under app/widgets/{panel}. Defaults: --type=stats --panel=admin",
        ),
    )

    fun byId(id: String): Generator? = ALL.firstOrNull { it.id == id }

    fun menuLabel(generator: Generator): String = "New ${generator.label}…"

    /** Build `make:model user -m -f -c` matching the Ace scaffolder flags. */
    fun modelAceArgs(name: String, options: ModelOptions): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Model name required" }
        if (options.all) return "make:model $trimmed --migration --factory --controller"
        val flags = buildList {
            if (options.migration) add("--migration")
            if (options.factory) add("--factory")
            if (options.controller) add("--controller")
        }
        return if (flags.isEmpty()) "make:model $trimmed"
        else "make:model $trimmed ${flags.joinToString(" ")}"
    }
}
