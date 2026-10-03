# Changelog

## 0.2.0

- Plugin id renamed to `dev.shamar.adonis.ide` (Kotlin package / Gradle group aligned)
- Official AdonisJS brand mark for plugin + Edge file icons
- Precise Ctrl-click for `[controllers.Session, 'store']` → that controller’s method only
- `view.render('…')` / `view.renderSync('…')` view-path completion
- Edge structure: `@layouts.app`, `@page`, `@!component`, `@pushTo` + `@end`
- Edge theming: dotted tag-component lexer, Color Scheme → Edge, dark file icon
- Two-way env coordination: `.env*`, `start/env.ts` schema, `env.get` / `process.env` usages
- `@each` loop completion (aliases: `for` / `loop` / `foreach`)
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
