# Adonis Idea (WebStorm)

Native JetBrains plugin for [AdonisJS](https://adonisjs.com) applications —
Edge file type, deep completions, navigation, rename, intentions, Ace run
configurations, Lucid/Mongoose ORM intelligence, Wire attributes, and
**optional** [Shamar](https://github.com/coolsam726/shamar) support.

Architecture twin of [almasix-idea](https://github.com/almasix-dev/almasix-idea):
native Kotlin (no LSP4IJ), plugin-owned index, WebStorm-first.

| | |
| --- | --- |
| **Plugin id** | `com.adonis.ide` |
| **Releases** | [GitHub Releases](https://github.com/coolsam726/adonis-idea/releases) (`.zip`) |
| **Parity matrix** | [PARITY.md](PARITY.md) |

**Hard rule:** installing or using this plugin never requires any `@shamar/*`
package. Shamar completions activate only when those packages (or
`app/panels/`) are detected in the project.

---

## How it works

```text
Adonis app (adonisrc.ts)
        │
        ▼
  Project Node (node on PATH)
        │
        ▼
  Bundled indexer/index.mjs --path <app> --json
        │
        ▼
  Plugin-owned AdonisIndex cache
        │
        ├── Completions + unknown-symbol annotators
        ├── Go to Declaration / Find Usages / Safe Rename
        ├── Hover / Quick Doc + Edge structure annotator
        ├── Intentions / Ace make:* + Tool Window
        └── Shamar layer (detection-gated)
```

---

## Requirements

- **WebStorm** 2024.2+ (build against `platformVersion` in `gradle.properties`)
- An AdonisJS app with `adonisrc.ts` / `ace.js`
- Node.js on PATH (for the bundled indexer)

```bash
node indexer/index.mjs --path /path/to/app --json | head
```

---

## Install

1. **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/coolsam726/adonis-idea/releases),
   or build locally (`./gradlew buildPlugin`).
2. Restart when prompted.
3. Open an Adonis app. Confirm `*.edge` files open as language **Edge**.
4. Force refresh: **Adonis → Rebuild Index**.

---

## Features

### Edge (`*.edge`)

- Native file type with HTML highlighting under Edge overlays
- Dual PSI roots (Edge + HTML)
- `@directives`, `{{ }}`, `{{{ }}}`, `@!component`, `@wire` / `@persist`
- Structure diagnostics for unmatched `@if` / `@each` / `@end`

### Adonis core

- Routes, views, config keys, env (two-way + bulk prefix insert), middleware
- Controllers / Vite entries / Ace command names
- Ace run configuration type (`node ace …`)
- **New…** menu: `make:controller`, `make:model`, migrations, validators, …

### ORM / DB

- Lucid models + migration columns; query-chain `where` / `preload` completion
- Mongoose schema path awareness when detected
- DB introspection planner from `.env` (`DB_*` → JDBC URL hints)

### Wire

- `wire:*` attributes, `@wire('name')`, `<wire:name />`, `$wire` props/methods
- GTD to `app/wire/**` + `resources/views/wire/**`

### Shamar (optional)

When `@shamar/adonis` / `app/panels/` is present:

- Resources / pages / panels / navigation groups
- Field & column type catalogs (`TextInput`, `TextColumn`, …)
- Convention route names `shamar.{panel}.*`
- Edge namespaces `shamar::` / `wire::`

---

## Develop

```bash
./gradlew test koverVerify
./gradlew buildPlugin   # → build/distributions/*.zip
./gradlew runIde        # WebStorm sandbox
```

Coverage policy: see [COVERAGE.md](COVERAGE.md).

---

## License

MIT — see [LICENSE](LICENSE).
