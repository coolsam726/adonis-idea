package com.adonis.ide

/**
 * Directive names the lexer will color and completions will offer.
 *
 * Matching a known name — rather than "`@` followed by a word" — is what keeps
 * `hi@example.com` out of the highlighter. Dotted tag components
 * (`@layouts.app`) are accepted separately by [EdgeLexer].
 *
 * Adonis Edge closes blocks with `@end` and loops with `@each` (not Blade
 * `@for` / `@foreach`). Completion still offers `for` / `loop` / `foreach` as
 * aliases that insert `each`.
 */
object EdgeDirectives {
    val NAMES: Set<String> = setOf(
        // Conditionals / loops
        "if", "elseif", "else", "unless", "each",
        // Composition / slots / layouts
        "component", "slot", "include", "section", "layout", "page",
        // Assignments / evaluation
        "inject", "eval", "let", "assign",
        // Assets / stacks
        "vite", "stack", "pushTo", "svg",
        // Debug / errors
        "debugger", "newError",
        // Wire / Shamar
        "wire", "persist",
        // Generic Edge closer (and legacy end* aliases some templates still use)
        "end",
        "endif", "endunless", "endeach", "endcomponent", "endslot",
        "endsection", "endlayout", "endwire", "endpersist",
    )

    /**
     * Muscle-memory aliases → real Edge directive inserted on accept.
     * Typing `@loop` / `@for` still surfaces the Edge `@each` loop.
     */
    val ALIASES: Map<String, String> = mapOf(
        "for" to "each",
        "foreach" to "each",
        "loop" to "each",
        "endif" to "end",
        "endeach" to "end",
    )
}
