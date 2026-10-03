# Coverage

Adonis Idea enforces line coverage on product logic via
[Kover](https://github.com/Kotlin/kotlinx-kover):

```bash
./gradlew test koverHtmlReport koverVerify
# HTML: build/reports/kover/html/index.html
```

`./gradlew check` runs `koverVerify`.

## Policy

- New intelligence (call-site detection, rename plans, code-action planners,
  index parsing, generators, DB planner) lives in **unit-testable** objects.
- Exclusions in `build.gradle.kts` are limited to thin IntelliJ Platform shells
  and OS-heavy file walkers.
- v0.1.0 gate is **≥ 97%** line coverage; target **≥ 98%** as remaining Edge/ORM
  branches fill in (same spirit as almasix-idea).
