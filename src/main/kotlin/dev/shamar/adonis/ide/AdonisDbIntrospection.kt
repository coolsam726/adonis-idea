package dev.shamar.adonis.ide

/**
 * Pure planner for Laravel-Idea-style DB introspection settings.
 *
 * The IDE shell can later open a JDBC connection using [jdbcUrl]; this object
 * never opens sockets — it only derives connection hints from `.env` maps.
 */
object AdonisDbIntrospection {
    data class Config(
        val enabled: Boolean,
        val driver: String,
        val host: String,
        val port: Int,
        val database: String,
        val user: String,
        val password: String,
        val jdbcUrl: String,
    )

    fun fromEnv(env: Map<String, String>): Config {
        val driver = (env["DB_CONNECTION"] ?: env["DB_DRIVER"] ?: "").lowercase().ifBlank {
            if (env.containsKey("DB_HOST") || env.containsKey("DB_DATABASE")) "pg" else ""
        }
        if (driver.isBlank() && env["DB_DATABASE"].isNullOrBlank()) {
            return Config(
                enabled = false,
                driver = "",
                host = "",
                port = 0,
                database = "",
                user = "",
                password = "",
                jdbcUrl = "",
            )
        }
        val host = env["DB_HOST"] ?: "127.0.0.1"
        val database = env["DB_DATABASE"] ?: env["DB_NAME"] ?: ""
        val user = env["DB_USER"] ?: env["DB_USERNAME"] ?: ""
        val password = env["DB_PASSWORD"] ?: ""
        val port = (env["DB_PORT"] ?: defaultPort(driver)).toIntOrNull() ?: defaultPort(driver).toInt()
        return Config(
            enabled = true,
            driver = driver.ifBlank { "pg" },
            host = host,
            port = port,
            database = database,
            user = user,
            password = password,
            jdbcUrl = buildJdbcUrl(driver.ifBlank { "pg" }, host, port, database),
        )
    }

    fun buildJdbcUrl(driver: String, host: String, port: Int, database: String): String =
        when (driver.lowercase()) {
            "mysql", "mysql2" -> "jdbc:mysql://$host:$port/$database"
            "sqlite", "better-sqlite3", "libsql" ->
                if (database.startsWith("/") || database.contains(":")) "jdbc:sqlite:$database"
                else "jdbc:sqlite:$database"
            "mssql", "sqlserver" ->
                "jdbc:sqlserver://$host:$port;databaseName=$database"
            "pg", "postgres", "postgresql" ->
                "jdbc:postgresql://$host:$port/$database"
            else -> "jdbc:postgresql://$host:$port/$database"
        }

    private fun defaultPort(driver: String): String = when (driver.lowercase()) {
        "mysql", "mysql2" -> "3306"
        "mssql", "sqlserver" -> "1433"
        "sqlite", "better-sqlite3", "libsql" -> "0"
        else -> "5432"
    }

    /** Merge migration columns with optional live DB column names (union). */
    fun mergeColumns(
        fromMigrations: Set<String>,
        fromDatabase: Set<String>,
    ): Set<String> = fromMigrations + fromDatabase
}
