# Coverage

Adonis Idea enforces **100% line coverage** on the verified product-logic set via
[Kover](https://github.com/Kotlin/kotlinx-kover):

```bash
./gradlew test koverHtmlReport koverVerify
# HTML: build/reports/kover/html/index.html
# XML:  build/reports/kover/report.xml
```

`./gradlew check` runs `koverVerify` and fails the build under 100%.

## Policy

- New intelligence (call-site detection, rename plans, code-action planners,
  index parsing, generators, DB planner) lives in **unit-testable** objects with
  tests in `src/test/kotlin/dev/shamar/adonis/ide/`.
- Exclusions in `build.gradle.kts` are limited to thin IntelliJ Platform shells
  (listeners, run-config UI, highlighter providers, PSI wiring, file walkers).
- Do **not** park feature logic in excluded classes to dodge the gate.
- Exhaust suites: `AdonisCoverageExhaustTest`, `AdonisCoverageBoostTest`,
  `AdonisFullParityTest`, `AdonisHundredPercentTest`.

## Regression guards

Known production regressions are locked by:

1. **`EdgeRegressionGuardTest`** — fails with `REGRESSION(...)` messages for
   blank dark icons, EDT indexer policy, `@` autopopup wiring, and `@form` vs
   HTML `<form>`.
2. **`scripts/check-regression-guards.py`** — CI source/SVG contracts (well-formed
   icons, `rebuildAsync` / `Task.Backgroundable`, `stopHere`, starter-kit seeds).
   Run locally: `python3 scripts/check-regression-guards.py`.
