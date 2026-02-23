# URL Vinegar
C-lean and mean-ingful URL cleaner and shortener

## Features
- **One-tap sharing** - Prominent Share button for quick cleaned URL sharing
- **YouTube URL shortener** - Converts `youtube.com/watch?v=xxx` to `youtu.be/xxx` (preserves timestamps)
- **Tracking removal** - Removes UTM, fbclid, gclid, and other tracking parameters
- **Transform-based cleaning** - Regex transforms for flexible URL manipulation
- **Real-time preview** - See cleaned URL update as you toggle options
- **Lime/green theme** - Clean, recognizable design with spray bottle icon

# RFC 2119 Requirements

## Objective

- URL Vinegar MUST allow User to clean-up URLs of tracking info and any unwanted metadata.
- The app MUST be able to apply changes and re-share with a single click when defaults are okay.
- The app SHALL allow User to tweak options on a shared URL, interactively showing the result.
- The app SHALL enable tweaking settings on the fly.

## 2. URL Interception

### 2.1 Browser Registration
- The application MUST register as a browser handler for `http` and `https` schemes.
- The application MUST register to receive `ACTION_SEND` intents with `text/plain` MIME type.
- The application MUST be selectable from the system share sheet when sharing URLs.

### 2.2 URL Reception
- The application MUST accept URLs via `ACTION_VIEW` intents (browser selection).
- The application MUST accept URLs via `ACTION_SEND` intents (share action).
- The application MUST extract URLs from `Intent.EXTRA_TEXT` when received via share.

---

## 3. Processing Dialog

### 3.1 Dialog Display
- When receiving a URL, the application MUST display a processing dialog before sharing.
- The dialog MUST show a preview of the cleaned URL.
- The dialog MUST update the preview in real-time when the user modifies selections.
- The dialog MUST have a prominent Share button directly below the URL preview.
- Settings button MUST be in the top right corner.

### 3.2 Transform List
- The dialog MUST display only transforms that match the current URL.
- Each transform MUST show its name and pattern.
- Non-matching transforms MUST NOT be displayed.
- Matching transforms MUST have a checkbox to enable or disable them.
- Enabled transforms MUST be checked by default according to their stored enabled state.
- (+) button MUST be available to add new transforms.

### 3.3 Transform Ordering
- Transforms MUST be applied in the order displayed.
- The user MUST be able to reorder transforms using drag handles.
- Drag handles MUST be present on each transform item.
- When the user reorders transforms, the new order MUST be persisted to configuration.

### 3.4 Transform Toggling
- When the user disables a transform that is enabled in configuration, the application MUST prompt the user with options:
  - "This URL only" — MUST disable the transform for the current URL without modifying configuration.
  - "Disable in config" — MUST update the configuration to disable this transform by default.
  - "Delete from config" — MUST remove the transform from configuration entirely.
- When the user enables a previously disabled transform, the application SHALL apply it without prompting.
- Disabled transforms MUST be shown with strikethrough text.

### 3.5 Adding Transforms
- The dialog MUST provide a (+) button to add a new transform.
- When adding a transform, the application MUST prompt for:
  - Name (REQUIRED)
  - Regex pattern (REQUIRED)
  - Replacement string (OPTIONAL, defaults to empty string)
- The add dialog MUST show a live preview of the transform applied to the current URL.
- After adding a transform, the application MUST ask whether to save it to configuration.
- If the user chooses "This time only", the transform applies only to current URL.

### 3.6 Query Parameter List
- The dialog MUST display all query parameters present in the URL (after transform application).
- Each parameter MUST show its name and value.
- Each parameter MUST have a checkbox indicating whether it will be kept.
- All parameters MUST be unchecked (removed) by default.
- Parameters being removed MUST be shown with strikethrough text.
- Each unchecked parameter MUST have a (+) button to create a removal transform.

### 3.7 Query Parameter Actions
- Checking a parameter MUST immediately keep it in the cleaned URL.
- Unchecking a parameter MUST immediately remove it from the cleaned URL.
- The (+) button beside an unchecked parameter MUST open the add transform dialog pre-filled with:
  - Name: "Remove {param} ({domain})"
  - Pattern: regex to remove that specific parameter

### 3.8 Dialog Actions
- The dialog MUST provide a prominent "Share" button that shares the cleaned URL.
- The dialog MUST provide a "Cancel" button that dismisses without action.
- The dialog MUST provide a "Copy" button to copy the cleaned URL.
- The dialog MUST provide a settings button (⚙) in top right for full configuration access.

---

## 4. URL Processing

### 4.1 Transform Application
- Transforms MUST be applied in their configured order.
- Only transforms that are both matching and enabled MUST be applied.
- Each transform MUST use the regex pattern for matching and replacement.
- The replacement string MAY be empty (effectively deleting matched content).
- Invalid regex patterns MUST NOT crash the application; they SHOULD be highlighted in red and skipped.

### 4.2 Query Parameter Filtering
- After transforms are applied, query parameters MUST be filtered.
- Only parameters with checkbox checked MUST be retained.
- The filtered query string MUST be properly formatted with `&` separators.
- If no parameters are kept, the URL MUST NOT include a `?` character.

### 4.3 URL Reconstruction
- The cleaned URL MUST preserve a valid scheme (`http` or `https`), otherwise become red and cannot be reshared.
- The scheme MAY be affected by a regex transform.
- The cleaned URL MAY change the original host (e.g., youtube.com → youtu.be).
- The cleaned URL MUST include only the filtered query parameters.

---

## 5. Configuration

### 5.1 Configuration Activity
- The application MUST provide a configuration activity accessible from the launcher.
- When launched without a URL intent, the application MUST open the configuration activity.
- The configuration activity MUST allow management of transforms.

### 5.2 Transform Configuration
- The configuration MUST store an ordered list of transforms.
- Each transform MUST have:
  - Name (string, REQUIRED)
  - Regex pattern (string, REQUIRED)
  - Replacement string (string, REQUIRED, MAY be empty)
  - Enabled state (boolean, REQUIRED)
- The user MUST be able to add, edit, delete, and reorder transforms.
- Transform order MUST be persisted and respected during processing.

### 5.3 Default Configuration
- The application MUST include default transforms for:
  - YouTube URL shortening (`youtube.com/watch?v=xxx` → `youtu.be/xxx`)
  - UTM parameters removal
  - Facebook click ID (`fbclid`) removal
  - Google click ID (`gclid`) removal
  - Amazon referral tag removal
  - Generic affiliate tracking removal
  - Query string cleanup

### 5.4 Persistence
- All configuration MUST be persisted across application restarts.
- Configuration MUST be stored using SharedPreferences with JSON serialization.

---

## 6. Sharing

### 6.1 Share Intent
- When the user confirms sharing, the application MUST create an `ACTION_SEND` intent.
- The intent MUST include the cleaned URL in `EXTRA_TEXT`.
- The intent MUST have MIME type `text/plain`.
- The application MUST display a system chooser for selecting the target application.

### 6.2 Post-Share Behavior
- After initiating the share, the application MUST finish the current activity.
- The application MUST NOT remain in the foreground after sharing.

---

## 7. User Interface

### 7.1 Theme
- The application MUST use a lime/dark green color scheme.
- Background SHOULD be light lime (#C5E1A5).
- Primary buttons MUST be dark green (#2E7D32) with white text.
- Text MUST be dark green (#1B5E20) for readability.

### 7.2 App Icon
- The icon MUST be an adaptive icon with dark green background.
- The foreground MUST show a lime spray bottle with "UV" text.
- The icon MUST include a cleaning cloth element.

### 7.3 Visual Feedback
- Disabled transforms MUST use strikethrough text.
- Removed query parameters MUST use strikethrough text.
- The URL preview MUST update immediately when selections change.
- Invalid regex patterns MUST be highlighted in red.

### 7.4 Drag Handles
- Drag handles MUST be present for reordering transforms.
- Drag handles SHOULD be easily recognizable (e.g., grip icon).

---

## 8. Size Optimization

### 8.1 APK Size
- The release APK MUST be under 2 MB.
- The application MUST enable R8/ProGuard minification for release builds.
- The application MUST enable resource shrinking for release builds.

### 8.2 Dependencies
- The application MUST minimize external dependencies.
- The application MUST NOT include unnecessary native libraries.
- The application MUST use built-in Android/Java regex functionality.

---

## 9. Error Handling

### 9.1 Invalid Regex
- Invalid regex patterns MUST NOT cause application crashes.
- Invalid regex patterns MUST be silently skipped during processing.
- Invalid regex patterns MUST be highlighted in red in the UI.

### 9.2 Malformed URLs
- Malformed URLs MUST NOT cause application crashes.
- If a URL cannot be parsed, the application SHOULD share it unmodified.

---

## 10. Compatibility

### 10.1 Android Version
- The application MUST support Android API level 21 (Lollipop) and above.
- The application MUST target Android API level 34.

### 10.2 Permissions
- The application MUST NOT require any permissions.
