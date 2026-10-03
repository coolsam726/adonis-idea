package com.adonis.ide

/**
 * Directive names the lexer will color.
 *
 * Matching a known name — rather than "`@` followed by a word" — is what keeps
 * `hi@example.com` out of the highlighter. Adonis Edge closes blocks with `@end`
 * (not Blade-style `@endif`). Includes optional Shamar/Wire directives; the
 * lexer always knows them, completions may be gated by the index.
 */
object EdgeDirectives {
    val NAMES: Set<String> = setOf(
        // Conditionals / loops
        "if", "elseif", "else", "unless", "each",
        // Composition
        "component", "slot", "include", "section", "layout",
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
}
