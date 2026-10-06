# AdonisJS plugin — feature parity matrix

Maps **Laravel IDEA** feature categories and **almasix-idea** surfaces onto
AdonisJS plugin. Status as of **0.4.0** (Shamar 1.0.10 catch-up).

Legend: **full** = shipped · **partial** = useful MVP · **planned** = not yet

## Core (any AdonisJS app — no Shamar required)

| Category | Laravel IDEA / almasix | Adonis Idea | Status |
|----------|------------------------|-------------|--------|
| Template language | Blade / Prism | Edge dual-PSI + highlighter | full |
| Template structure | `@if`/`@endif` | `@if`/`@each`/`@end` | full |
| Project index | `smith ide:index` | Bundled `indexer/index.mjs` | full |
| Routes | named routes | `route()` / `.as()` + GTD | full |
| Views | `view()` / `@include` | same + Edge components | full |
| Config | `config()` keys | `config/` + locations | full |
| Env | two-way + bulk | `.env` + options + bulk | full |
| Middleware | aliases | `start/kernel.ts` | full |
| Generators | Artisan / Smith make | Ace `make:*` + offline stubs | full |
| Run configs | Artisan / Smith | Ace (`node ace`) | full |
| Tool window | symbol browser | Adonis tool window | full |
| Find Usages / Rename | routes/views/config/env | same kinds | full |
| Hover / annotators | unknown symbols | same pattern | full |

## ORM / DB

| Category | Target | Status |
|----------|--------|--------|
| Lucid models + relations | columns / `preload` / stubs | full |
| Lucid column case | query: snake+camel; attr: camel | full |
| `@column({ columnName })` aliases | indexed with property | full |
| Migration column index | `database/migrations` | full |
| Mongoose schemas | detect + path completion (exact keys) | partial |
| Live DB via Database tool | Settings source + DataSource cache | full |
| `.env` → JDBC URL planner | create/match hints | partial (no auto-create DS yet) |

## Wire (≈ Livewire)

| Category | Status |
|----------|--------|
| `wire:*` attributes + modifiers | full |
| `@wire` / `<wire:>` / `@persist` | full |
| `$wire` props/methods from `app/wire` | full |
| GTD class ↔ view | full |
| `make:wire` | full (Ace when present; offline stub else) |

## Shamar (detection-gated; aligned with `@shamar/*` 1.0.10)

| Category | Status |
|----------|--------|
| Discover resources / pages / panels | full |
| Discover `app/widgets/**` + widget type catalog | full |
| Navigation groups / icons | full |
| Field / column type catalogs (incl. RelationTable) | full |
| Convention `shamar.{panel}.*` routes | full |
| `shamar::` / `wire::` views | full |
| Action `openIn` / `presentation` / page modes / Stat colors | full (string-enum sites) |
| Edge helpers `isDialogPageMode` / `dialogPresentation` | full (when Shamar detected) |
| `make:wire` / `make:panel` / `shamar:make-widget` | full (Ace when present; offline stub else) |
| Builder chain intelligence | partial (`make(` + enums; no-arg TS chains via package types) |
| Cherubim / REST soft completion | planned |

## Explicit non-goals

- Never require `@shamar/*` to install or index an Adonis app
- No LSP4IJ / framework Ace `ide:index` dependency
- Not a PHP Laravel IDEA fork
