# Copilot Instructions for URL Vinegar

## AI Preferences

- Keep the project **lean**: minimal dependencies, minimal code, minimal APK size.
- Prefer standard Java/Android APIs over third-party libraries.
- Make the **smallest possible changes** to fix an issue — no refactors unless specifically requested.
- Never add comments unless they match the style of existing comments or explain a genuinely non-obvious choice.
- Always run `./gradlew test` before proposing changes and fix only test failures related to the task.
- Validate that changes don't break existing behavior or introduce security vulnerabilities.
- Use scaffolding/ecosystem tools (Gradle, etc.) rather than manual file manipulation.

## Style Guide

Follow the project style guide (placeholder — update with your published gist or GitHub Pages URL):
https://gatopeich.github.io/style

Inline conventions:
- Java 8 source compatibility, no Kotlin.
- 4-space indentation; braces on the same line.
- No trailing whitespace; newline at end of file.
- Class names: `UpperCamelCase`; method/variable names: `lowerCamelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep methods short and focused; extract helpers only when reused.

## Versioning

- `versionCode` = UTC seconds elapsed since the repo was created (computed at build time).
- `versionName` = `{release-tag}.{versionCode}` (e.g. `v0.0.12345678`), or `{release-tag}.{versionCode}.PR{n}` for PR builds.
- Default release tag is `v0.0`; the CI reads it from the latest GitHub Release.
- In CI: `VERSION_CODE`, `RELEASE_TAG`, and `PR_NUMBER` env vars are set before Gradle runs.
- In local builds: Gradle falls back to computing seconds since the first git commit.

## What This Is

Android app that cleans URLs by removing tracking parameters (UTM, fbclid, gclid, etc.) and applying regex transforms (e.g. YouTube shortening). It registers as a browser/share handler, shows a processing dialog with real-time preview, and re-shares the cleaned URL. See `README.md` for full RFC 2119 requirements spec.

## Build & Test

Requires JDK 17. Gradle wrapper is included.

```sh
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build (minified, signed with debug key)
./gradlew test             # Unit tests (JUnit 4)
```

The CI workflow (`.github/workflows/build.yml`) runs `assembleRelease` then `test` on every push/PR to main/master.

Always run `./gradlew test` before submitting changes.

## Project Layout

```
app/src/main/java/com/gatopeich/urlvinegar/
  data/
    Transform.java          # Data model for URL transforms (name, regex, replacement, enabled)
    ConfigRepository.java   # SharedPreferences-based persistence with JSON serialization
  ui/
    ProcessingActivity.java # Main dialog: URL preview, transform toggles, query param checkboxes, share/copy
    ConfigActivity.java     # Settings: manage transforms (add/edit/delete/reorder)
  util/
    UrlProcessor.java       # Core logic: applies transforms and filters query params

app/src/test/java/com/gatopeich/urlvinegar/
  UrlProcessorTest.java     # Unit tests for URL processing logic

app/build.gradle            # App config: minSdk 21, targetSdk 34, dependencies
build.gradle                # Root: AGP 8.5.0
.github/workflows/build.yml # CI: JDK 17, build + test
.github/copilot-firewall.yml # Copilot agent allowed network domains
```

## Conventions

- Java 8 source compatibility, no Kotlin.
- Minimal dependencies — only AndroidX AppCompat, Material, ConstraintLayout, RecyclerView.
- Release APK must stay under 2 MB (R8 minification + resource shrinking enabled).
- Configuration stored in SharedPreferences as JSON.
- URL processing is pure Java with `java.util.regex` and `java.net.URI` — no third-party URL libraries.
- Invalid regexes must never crash the app; they are skipped and highlighted in red.
