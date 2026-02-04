# URL Vinegar
C-lean and mean-ingful URL cleaner and shortener

# RFC 2119 Requirements

## Objective

- URL Vinegar MUST allow User to clean-up URLs of tracking info and any unwanted metadata.
- The app MUST be able to apply changes and re-share with a single click when defauls are okay.
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

### 3.2 Transform List
- The dialog MUST display a list of the configured regex transforms that match current URL.
- Each transform MUST show its name and pattern.
- Non-matching transforms SHOULD not be displayed until a change in the URL makes them match.
- Matching transforms MUST have a checkbox to enable or disable them.
- Enabled transforms MUST be checked by default according to their stored enabled state.

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

### 3.5 Adding Transforms
- The dialog MUST provide a button to add a new transform.
- The user SHOULD be able to insert a new transform at any position in the list.
- When adding a transform, the application MUST prompt for:
  - Name (REQUIRED)
  - Regex pattern (REQUIRED)
  - Replacement string (OPTIONAL, defaults to empty string)
- After adding a transform, the application MUST ask whether to save it to configuration.
- If the user chooses to save, the transform MUST be persisted at the corresponding position.

### 3.6 Query Parameter List
- The dialog MUST display all query parameters present in the URL.
- Each parameter MUST show its name and value.
- Each parameter MUST have a checkbox indicating whether it will be kept.
- Parameters in the whitelist MUST be checked (kept) by default.
- Parameters not in the whitelist MUST be unchecked (removed) by default.
- Whitelisted parameters SHOULD be visually distinguished from non-whitelisted parameters.

### 3.7 Query Parameter Toggling
- When the user checks (keeps) a parameter that is NOT in the whitelist, the application MUST prompt:
  - "Keep this time only" — MUST keep the parameter without modifying configuration.
  - "Add to whitelist" — MUST add the parameter to the whitelist configuration.
    - When adding to whitelist, the application SHOULD prompt for an optional description/annotation.
- When the user unchecks (removes) a parameter that IS in the whitelist, the application MUST prompt:
  - "This time only" — MUST remove the parameter without modifying configuration.
  - "Remove from whitelist" — MUST remove the parameter from the whitelist configuration.
  - Long press ondescriptions SHOULD open an "edit description" dialog.

### 3.8 Dialog Actions
- The dialog MUST provide a "Share" button that shares the cleaned URL.
- The dialog MUST provide a "Cancel" button that dismisses without action.
- The dialog SHOULD provide access to the full configuration activity.

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
- Only parameters marked as "keep" in the processing state MUST be retained.
- The filtered query string MUST be properly formatted with `&` separators.
- If no parameters are kept, the URL MUST NOT include a `?` character.

### 4.3 URL Reconstruction
- The cleaned URL MUST preserve a valid scheme (`http` or `https`), otherwise become red and cannot be reshared.
- The scheme MAY be affected by a regex tramsform
- If a transform leaves an invalid scheme, an error dialog will show and the transform will be disabled for this URL.
- The cleaned URL MAY change the original host.
- The cleaned URL MAY change the path (after transform application).
- The cleaned URL MUST include only the filtered query parameters.

---

## 5. Configuration

### 5.1 Configuration Activity
- The application MUST provide a configuration activity accessible from the launcher.
- When launched without a URL intent, the application MUST open the configuration activity.
- The configuration activity MUST allow management of transforms and allowed parameters.

### 5.2 Transform Configuration
- The configuration MUST store an ordered list of transforms.
- Each transform MUST have:
  - Name (string, REQUIRED)
  - Regex pattern (string, REQUIRED)
  - Replacement string (string, REQUIRED, MAY be empty)
  - Enabled state (boolean, REQUIRED)
- The user MUST be able to add new transforms.
- The user MUST be able to delete existing transforms.
- The user MUST be able to edit existing transforms.
- The user MUST be able to reorder transforms.
- Transform order MUST be persisted and respected during processing.

### 5.3 Allowed Parameters Configuration
- The configuration MUST store a set of allowed (whitelisted) query parameters.
- Each allowed parameter MUST have:
  - Name (string, REQUIRED)
  - Description/annotation (string, OPTIONAL)
- The user MUST be able to add new allowed parameters.
- The user MUST be able to delete existing allowed parameters.
- The user MUST be able to edit existing allowed parameters.

### 5.4 Persistence
- All configuration MUST be persisted across application restarts.
- Configuration SHOULD be stored using SharedPreferences or equivalent lightweight storage.
- The application MUST provide sensible default transforms and allowed parameters.

### 5.5 Default Configuration
- The application SHOULD include default transforms for common tracking parameters:
  - UTM parameters (`utm_source`, `utm_medium`, `utm_campaign`, etc.)
  - Facebook click ID (`fbclid`)
  - Google click ID (`gclid`)
  - Amazon referral tags
- The application SHOULD include default allowed parameters for common use cases:
  - Search queries (`q`)
  - Item identifiers (`id`)
  - Pagination (`page`)

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

### 7.1 Minimal Footprint
- The application SHOULD minimize UI complexity.
- The application SHOULD use system-provided UI components where possible.
- The application MAY use AlertDialog for prompts and confirmations.

### 7.2 Visual Feedback
- Matching transforms MUST be visually distinct from non-matching transforms.
- Disabled transforms SHOULD use strikethrough text or similar indication.
- Whitelisted parameters SHOULD be visually distinct from non-whitelisted parameters.
- The URL preview MUST update immediately when selections change.

### 7.3 Drag Handles
- Drag handles MUST be present for reordering transforms.
- Drag handles SHOULD be easily recognizable (e.g., grip icon).
- Drag-and-drop MUST provide visual feedback during the drag operation.

---

## 8. Size Optimization

### 8.1 APK Size
- The release APK SHOULD be under 3 MB.
- The application SHOULD enable R8/ProGuard minification for release builds.
- The application SHOULD enable resource shrinking for release builds.

### 8.2 Dependencies
- The application SHOULD minimize external dependencies.
- The application MUST NOT include unnecessary native libraries.
- The application SHOULD use built-in Android/Java regex functionality.

---

## 9. Error Handling

### 9.1 Invalid Regex
- Invalid regex patterns MUST NOT cause application crashes.
- Invalid regex patterns SHOULD be silently skipped during processing.
- The configuration UI MAY validate regex patterns and warn the user.

### 9.2 Malformed URLs
- Malformed URLs MUST NOT cause application crashes.
- If a URL cannot be parsed, the application SHOULD share it unmodified.

---

## 10. Compatibility

### 10.1 Android Version
- The application MUST support Android API level 21 (Lollipop) and above.
- The application SHOULD target the latest stable Android API level.

### 10.2 Permissions
- The application MUST NOT require unnecessary permissions.
- The application MAY request `INTERNET` permission if needed for future features.
