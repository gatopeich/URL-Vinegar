# Copilot Instructions for URL Vinegar

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
```

## Conventions

- Java 8 source compatibility, no Kotlin.
- Minimal dependencies — only AndroidX AppCompat, Material, ConstraintLayout, RecyclerView.
- Release APK must stay under 2 MB (R8 minification + resource shrinking enabled).
- Configuration stored in SharedPreferences as JSON.
- URL processing is pure Java with `java.util.regex` and `java.net.URI` — no third-party URL libraries.
- Invalid regexes must never crash the app; they are skipped and highlighted in red.
