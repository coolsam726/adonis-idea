package dev.shamar.adonis.ide

/**
 * Offline TypeScript stubs used when `node ace make:*` cannot run.
 */
object AdonisFileTemplates {
    data class Spec(
        val relativePath: String,
        val contents: String,
        val openAfterCreate: Boolean = true,
    )

    fun resolve(generatorId: String, name: String): Spec? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return when (generatorId) {
            "controller" -> controller(trimmed)
            "model" -> model(trimmed)
            "view" -> view(trimmed)
            "middleware" -> middleware(trimmed)
            "validator" -> validator(trimmed)
            "exception" -> exception(trimmed)
            "listener" -> listener(trimmed)
            "provider" -> provider(trimmed)
            "command" -> command(trimmed)
            "test" -> test(trimmed)
            "migration" -> migration(trimmed)
            "seeder" -> seeder(trimmed)
            "factory" -> factory(trimmed)
            "event" -> event(trimmed)
            "policy" -> policy(trimmed)
            "service" -> service(trimmed)
            "wire" -> wire(trimmed)
            "panel" -> panel(trimmed)
            else -> null
        }
    }

    fun snake(name: String): String {
        val base = name.substringAfterLast('.').substringAfterLast('/')
            .removeSuffix("Controller")
            .removeSuffix("Middleware")
            .removeSuffix("Seeder")
            .removeSuffix("Factory")
            .removeSuffix("Test")
            .removeSuffix("Command")
            .removeSuffix("Service")
            .removeSuffix("Policy")
            .removeSuffix("Exception")
        return base
            .replace(Regex("""([a-z0-9])([A-Z])"""), "$1_$2")
            .replace('.', '_')
            .replace('/', '_')
            .lowercase()
            .trim('_')
    }

    fun className(name: String): String {
        val base = name.substringAfterLast('.').substringAfterLast('/')
        if (base.any { it.isLowerCase() } && base.any { it.isUpperCase() }) return base
        return snake(base).split('_').joinToString("") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    fun controller(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Controller")) it else "${it}Controller" }
        val file = snake(cls)
        return Spec(
            relativePath = "app/controllers/${file}.ts",
            contents = """
                import type { HttpContext } from '@adonisjs/core/http'

                export default class $cls {
                  async index({ response }: HttpContext) {
                    return response.ok({ controller: '$cls' })
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun model(name: String): Spec {
        val cls = className(name)
        val file = snake(cls)
        return Spec(
            relativePath = "app/models/${file}.ts",
            contents = """
                import { BaseModel, column } from '@adonisjs/lucid/orm'
                import { DateTime } from 'luxon'

                export default class $cls extends BaseModel {
                  @column({ isPrimary: true })
                  declare id: number

                  @column.dateTime({ autoCreate: true })
                  declare createdAt: DateTime

                  @column.dateTime({ autoCreate: true, autoUpdate: true })
                  declare updatedAt: DateTime
                }
            """.trimIndent() + "\n",
        )
    }

    fun view(name: String): Spec {
        val path = name.trim().replace('.', '/')
        return Spec(
            relativePath = "resources/views/$path.edge",
            contents = """
                <p>$name</p>
            """.trimIndent() + "\n",
        )
    }

    fun middleware(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Middleware")) it else "${it}Middleware" }
        return Spec(
            relativePath = "app/middleware/${snake(cls)}.ts",
            contents = """
                import type { HttpContext } from '@adonisjs/core/http'
                import type { NextFn } from '@adonisjs/core/types/http'

                export default class $cls {
                  async handle(ctx: HttpContext, next: NextFn) {
                    await next()
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun validator(name: String): Spec {
        val file = snake(name)
        return Spec(
            relativePath = "app/validators/${file}.ts",
            contents = """
                import vine from '@vinejs/vine'

                export const ${className(name).replaceFirstChar { it.lowercase() }}Validator = vine.compile(
                  vine.object({
                    //
                  })
                )
            """.trimIndent() + "\n",
        )
    }

    fun exception(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Exception")) it else "${it}Exception" }
        return Spec(
            relativePath = "app/exceptions/${snake(cls)}.ts",
            contents = """
                import { Exception } from '@adonisjs/core/exceptions'

                export default class $cls extends Exception {
                  static status = 400
                }
            """.trimIndent() + "\n",
        )
    }

    fun listener(name: String): Spec {
        val cls = className(name)
        return Spec(
            relativePath = "app/listeners/${snake(cls)}.ts",
            contents = """
                export default class $cls {
                  async handle(event: unknown) {
                    //
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun provider(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Provider")) it else "${it}Provider" }
        return Spec(
            relativePath = "providers/${snake(cls)}.ts",
            contents = """
                import type { ApplicationService } from '@adonisjs/core/types'

                export default class $cls {
                  constructor(protected app: ApplicationService) {}

                  register() {}
                  async boot() {}
                }
            """.trimIndent() + "\n",
        )
    }

    fun command(name: String): Spec {
        val cls = className(name)
        return Spec(
            relativePath = "commands/${snake(cls)}.ts",
            contents = """
                import { BaseCommand } from '@adonisjs/core/ace'
                import type { CommandOptions } from '@adonisjs/core/types/ace'

                export default class $cls extends BaseCommand {
                  static commandName = '${snake(cls).replace('_', ':')}'
                  static description = ''
                  static options: CommandOptions = {}

                  async run() {
                    this.logger.info('$cls')
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun test(name: String): Spec {
        val file = snake(name)
        return Spec(
            relativePath = "tests/unit/${file}.spec.ts",
            contents = """
                import { test } from '@japa/runner'

                test.group('$name', () => {
                  test('example', async ({ assert }) => {
                    assert.isTrue(true)
                  })
                })
            """.trimIndent() + "\n",
        )
    }

    fun migration(name: String): Spec {
        val file = "${System.currentTimeMillis()}_${snake(name)}"
        return Spec(
            relativePath = "database/migrations/$file.ts",
            contents = """
                import { BaseSchema } from '@adonisjs/lucid/schema'

                export default class extends BaseSchema {
                  protected tableName = '${snake(name)}'

                  async up() {
                    this.schema.createTable(this.tableName, (table) => {
                      table.increments('id').notNullable()
                      table.timestamp('created_at').notNullable()
                      table.timestamp('updated_at').notNullable()
                    })
                  }

                  async down() {
                    this.schema.dropTable(this.tableName)
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun seeder(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Seeder")) it else "${it}Seeder" }
        return Spec(
            relativePath = "database/seeders/${snake(cls)}.ts",
            contents = """
                import { BaseSeeder } from '@adonisjs/lucid/seeders'

                export default class $cls extends BaseSeeder {
                  async run() {
                    //
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun factory(name: String): Spec {
        val cls = className(name)
        return Spec(
            relativePath = "database/factories/${snake(cls)}_factory.ts",
            contents = """
                import factory from '@adonisjs/lucid/factories'
                import $cls from '#models/${snake(cls)}'

                export const ${cls}Factory = factory
                  .define($cls, async ({ faker }) => {
                    return {}
                  })
                  .build()
            """.trimIndent() + "\n",
        )
    }

    fun event(name: String): Spec {
        val cls = className(name)
        return Spec(
            relativePath = "app/events/${snake(cls)}.ts",
            contents = """
                import { BaseEvent } from '@adonisjs/core/events'

                export default class $cls extends BaseEvent {
                  constructor() {
                    super()
                  }
                }
            """.trimIndent() + "\n",
        )
    }

    fun policy(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Policy")) it else "${it}Policy" }
        return Spec(
            relativePath = "app/policies/${snake(cls)}.ts",
            contents = """
                import { BasePolicy } from '@adonisjs/bouncer'

                export default class $cls extends BasePolicy {}
            """.trimIndent() + "\n",
        )
    }

    fun service(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Service")) it else "${it}Service" }
        return Spec(
            relativePath = "app/services/${snake(cls)}.ts",
            contents = """
                export default class $cls {}
            """.trimIndent() + "\n",
        )
    }

    fun wire(name: String): Spec {
        val path = name.trim().replace('.', '/').replace('-', '_')
        val cls = className(path.replace('/', '_'))
        return Spec(
            relativePath = "app/wire/$path.ts",
            contents = """
                /**
                 * Wire component `$name`.
                 * Prefer `node ace make:wire $name` when available.
                 * With Shamar installed, extend `Wire` from `@shamar/adonis`.
                 */
                export default class $cls {
                  // public state + methods
                }
            """.trimIndent() + "\n",
        )
    }

    fun panel(name: String): Spec {
        val id = snake(name)
        return Spec(
            relativePath = "app/panels/$id/panel.ts",
            contents = """
                /**
                 * Panel `$id` scaffold.
                 * Prefer `node ace make:panel $id` when @shamar/adonis is installed.
                 */
                export default class ${className(id)}Panel {
                  static id = '$id'
                  static path = '/$id'
                }
            """.trimIndent() + "\n",
        )
    }
}
