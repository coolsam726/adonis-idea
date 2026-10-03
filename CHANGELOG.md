# Changelog

## 0.3.5

- WebStorm-only product compatibility (`JavaScript` depend + IntelliJ IDEA `incompatible-with`)
- README install path is JetBrains Marketplace–first; Marketplace screenshots (1280×800)

## 0.3.4

- Tool-window icon uses a monochrome mark so New UI shows the blue Adonis glyph when selected (not a blue blob over the brand logo)

## 0.3.3

- Marketplace display name renamed to **AdonisJS** (JetBrains rejects names containing “IDEA”)

## 0.3.2

- Fix `@` directive autopopup (schedule from `checkAutoPopup` + never skip via HTML confidence)
- Detection-gate `@wire` / `@persist` until Wire is present (`framework.wire`)
- Never wait on the Node indexer under a ReadAction (refs / highlighting)

## 0.3.1

- Fix blank Adonis tool-window / `*.edge` icons in dark UI (invalid dark SVG)
- Rebuild Index no longer blocks the EDT (background task + deferred cold index)
- Typing `@` auto-opens directive completion again; `@form` no longer steals HTML `<form>`
- Regression guards: `EdgeRegressionGuardTest` + CI `scripts/check-regression-guards.py`

## 0.3.0

- EdgeJS guide fidelity: lex/complete arbitrary `@form` / `@!button` tag components
- Object-literal prop sites: `route` → routes, `method` → HTTP verbs, starter-kit prop keys
- `@includeIf` / `When` / `Unless` 2nd-arg view paths; `router.on().render` → views
- Escape `@{{ … }}`, trailing `~` on directives, `@dump`, seeded `$slots` / `$context` / helpers
- Raw `{!! … !!}` echo coloring; Adonis brand icon on `*.edge` files (incl. dark variant)
- GTD / Find Usages for Edge tag names and `route:` prop values
- Adonis menu / tool-window icon sized to 16×16

## 0.2.0

- Plugin id renamed to `dev.shamar.adonis.ide` (Kotlin package / Gradle group aligned)
- Official AdonisJS brand mark for plugin + Edge file icons
- Precise Ctrl-click for `[controllers.Session, 'store']` → that controller’s method only
- `view.render('…')` / `view.renderSync('…')` view-path completion
- Edge structure: `@layouts.app`, `@page`, `@!component`, `@pushTo` + `@end`
- Edge theming: dotted tag-component lexer, Color Scheme → Edge, dark file icon
- Two-way env coordination: `.env*`, `start/env.ts` schema, `env.get` / `process.env` usages
- `@each` loop completion (aliases: `for` / `loop` / `foreach`)
- Typing `@` auto-lists all Edge directives; block directives expand to structured snippets (args + `@end`)
- README usage guide with for-the-badge stats and demo GIFs

## 0.1.0

- Initial Adonis Idea release for WebStorm
- Native Edge language (dual PSI, highlighter, `@end` structure checks)
- Bundled Node indexer — works on any AdonisJS app without `@shamar/*`
- Completions / GTD / usages / rename for routes, views, config, env, middleware
- Lucid/Mongoose model + migration indexing; query-chain helpers
- DB introspection planner from `.env`
- Wire attributes, `@wire`, `$wire`, component discovery
- Detection-gated Shamar resources / pages / panels / nav / field catalogs
- Ace run configurations and `make:*` New… menu + offline TypeScript stubs
