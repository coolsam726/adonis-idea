package dev.shamar.adonis.ide

/**
 * Directive names the lexer will color and completions will offer.
 *
 * Builtin names come from [EdgeTagRegistry]. File-based tag components
 * (`@form`, `@!button`) are accepted by [EdgeLexer] whenever they carry `(…)`.
 *
 * Adonis Edge closes blocks with `@end`. Completion still offers `for` / `loop`
 * / `foreach` as aliases that insert `each`.
 */
object EdgeDirectives {
    val NAMES: Set<String> = EdgeTagRegistry.BUILTIN

    val ALIASES: Map<String, String> = EdgeTagRegistry.ALIASES
}
