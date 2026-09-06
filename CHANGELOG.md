# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.22.1] - 2026-09-06

### Fixed

- Include the current event when evaluating in-app event-count targeting conditions, with serialized processing so concurrent events advance counts exactly once and failed scheduling does not retain the count.
- Generate unique random event IDs and reuse the original event payload across HTTP retries while refreshing authorization credentials.

## [1.22.0] - 2026-09-02

### Fixed

- Derive anonymous Notifly user IDs from the project ID and stable Android device ID instead of a nullable or rotating FCM token.

## [1.21.6] - 2026-07-29

### Fixed

- Republish the background SSE connection fix from 1.21.5 after JitPack failed to register the original release artifacts.

## [1.21.5] - 2026-07-29

### Fixed

- Prevent background app wakes from opening SSE connections by checking foreground state immediately before each initial or reconnect attempt.

## [1.21.4] - 2026-07-27

### Fixed

- Fix JitPack publishing by skipping local lint plugin resolution in the JitPack build environment.

## [1.21.3] - 2026-07-27

### Fixed

- Fix ad push expanded notification colors in Android light and dark modes so the title, body, opt-out label, icons, and divider remain readable when `unsubscribe_url` is present.

## [1.21.2] - 2026-07-07

### Fixed

- Stop auto-prefixing scheme-less popup and push link values with `http://`, so values like `www.example.com` or UUID-like payloads do not unexpectedly launch an external browser on Android. Explicit `http://`, `https://`, `data:`, and custom-scheme URIs remain openable.

## [1.21.1] - 2026-06-17

### Fixed

- Fix in-app popup briefly revealing the host app's privacy/secure overlay (e.g. splash screen) on apps that add it in `onUserLeaveHint`, by launching the popup with `FLAG_ACTIVITY_NO_USER_ACTION` so the host's `onUserLeaveHint` is not triggered.

## [1.21.0] - 2026-06-15

### Added

- **Ad push opt-out ("수신거부") area.** Push notifications carrying an `unsubscribe_url` custom-data field are treated as ad pushes and now render a "수신거부" row (icon + chevron) in the expanded notification via a custom `DecoratedCustomViewStyle` layout. Tapping the row opens the `unsubscribe_url` (custom scheme deep link or web page) and dismisses the notification. The opt-out tap is handled separately from a normal content click — it does not emit `push_click` or fire the notification-open callback — and the custom layout is applied after the notification interceptor so a host app's interceptor cannot override it.

## [1.20.0] - 2026-06-01

### Added

- **Real-time campaign data sync over SSE.**
  - Open a long-lived SSE channel from the SDK to Notifly server. When campaign state changes server-side (e.g. a new in-app message is triggered or a popup is updated), the SDK refreshes its local campaign data immediately instead of waiting for the next event-driven fetch.
  - On reconnect, the SDK sends `Last-Event-ID` so the server can replay popup entries that were missed during the disconnect window, recovering messages that fired while offline.
  - If the SSE channel cannot reach OPEN state within the fallback threshold, the SDK falls back to the legacy event-driven sync path automatically.

### Changed

- Reconnect backoff uses full jitter (100ms ~ 10s) across all attempts to disperse reconnect bursts after server-side disconnects such as rolling deploys.
- Reduce SSE verbose logging. Keep: `[sse] connected`, `[sse] disconnected`, `[sse] sync received` plus error logs.

## [1.19.3] - 2026-05-12

### Fixed

- Fix pending commands remaining queued when `setUserId` is queued before `setUserProperties`.

## [1.19.2] - 2026-04-14

### Added

- Support in-app browser mode for in-app message links: add `?nf_open_mode=in_app_browser` to open URLs in Chrome Custom Tabs instead of an external browser.

## [1.19.1] - 2026-03-25

- Fix in-app popup bottom content being obscured by navigation bar on Android 15 devices with edge-to-edge enforcement (targetSdk 35+).

## [1.19.0] - 2026-02-25

- Support cancellation conditions for in-app message campaigns: scheduled popups can now be cancelled by subsequent events during the delay period.

## [1.18.9] - 2026-02-23

- Fix IS_NULL/IS_NOT_NULL segment condition operators always returning false due to early valueType null check.
- Fix isValuePresent returning false for non-String types (Int, Boolean, etc.) with IS_NOT_NULL operator.

## [1.18.8] - 2026-02-12

- Fix campaign sync or in-app message initialization failure permanently disabling SDK event tracking.

## [1.18.7] - 2026-02-09

- Fix in-app message not showing on apps using foreground service.

## [1.18.6] - 2025-11-21

- Force software rendering for in-app message WebView to avoid hardware acceleration issues on Samsung Galaxy S25 Edge device family.

## [1.18.5] - 2025-11-13

- Fix in-app message Activity transparency issues with React Native New Architecture (Fabric) to ensure underlying screen remains visible through dimmed overlay.

## [1.18.4] - 2025-10-23

- Renamed notification channels for importance levels to improve clarity in system settings.

## [1.18.3] - 2025-10-01

- Force software rendering for in-app message WebView to avoid hardware acceleration issues on Samsung Galaxy Z Fold7 device family.

## [1.18.2] - 2025-07-24

- Handle external user ID mismatch between database and SDK

## [1.18.1] - 2025-07-16

- Pass `templateName` event param to in_app_message_show event.

## [1.18.0] - 2025-05-13

- Add event logs when push notification delivery is not successful.

## [1.17.1] - 2025-05-09

- Force software rendering for in-app message WebView to avoid hardware acceleration issues on Samsung Galaxy S25 device family.

## [1.17.0] - 2025-04-16

- Support in-app message template with transparent background.

## [1.16.1] - 2025-03-14

- Pass `link` event param to main_button_click event callback.

## [1.16.0] - 2025-03-10

- Added `IInAppMessageEventListener` to provide a interface to listen events from InAppMessage WebView.

## [1.15.0] - 2025-02-21

- Added timeout for image loading from FCMBroadcastReceiver.

## [1.14.0] - 2025-02-10

- Disable hardware acceleration for NotiflyWebView.

## [1.13.0] - 2025-01-15

- Added `getNotiflyUserId` method to get the Notifly user ID.

## [1.12.0] - 2024-12-02

### Changed

- Ensure the execution order of `setUserId` and `setUserProperties`.

## [1.11.0] - 2024-09-25

### Changed

- Remove `runBloking` from `FCMBroadcastReceiver`.

### Added

- `postBuildAsync` interface into `INotificationInterceptor`.

## [1.10.0] - 2024-08-29

### Changed

- Use `notifly_device_id` as partition key for event logging.

### Added

- `addNotificationInterceptor` interface to provide a customization of NotificationCompat.

## [1.9.4] - 2024-08-16

### Added

- `CATEGORY_BROWSABLE` flag to the intent when opening the URL in the push notification.

## [1.9.3] - 2024-08-05

### Fixed

- Crashing issue when the app revalidates the campaign data from the server.

## [1.9.2] - 2024-08-02

### Fixed

- Issue where the notifly user ID sometimes gets out-of-sync with the external user ID.

## [1.9.1] - 2024-07-26

### Changed

- Call `WebView.resumeTimers` explicitly at the start of the in-app message rendering process to
  prevent the in-app message from not being displayed in some cases.

## [1.9.0] - 2024-07-25

### Added

- Refresh campaign data when certain conditions are met to mitigate the problem of stale data
  remaining in memory.
  - The app is focused (foreground)
  - `campaignRevalidationInterval` has passed since the last campaign data refresh

## [1.8.3] - 2024-07-23

### Changed

- `PriorityQueue` to `PriorityBlockingQueue` to avoid `NullPointerException` caused by concurrent
  access to the queue.

## [1.8.2] - 2024-07-08

### Fixed

- Recents being ignored when the app is launched with notification click event.

## [1.8.1] - 2024-07-08

### Fixed

- `push_delivered` event logging when notification permission is not granted.

## [1.8.0] - 2024-07-03

### Changed

- API hostname for tracking events.

## [1.7.4] - 2024-07-03

### Removed

- WebView timer manipulations such as `pauseTimers` and `resumeTimers` for in-app messages.

## [1.7.3] - 2024-07-01

### Removed

- Redundant `Set User ID` call from the SDK.

## [1.7.2] - 2024-06-19

### Added

- InAppMessagePrefs class to manage in-app message preferences.

## [1.7.1] - 2024-06-14

### Fixed

- Crash issue when EncryptedSharedPreferences creation has failed.

### Changed

- Downgraded the minimum SDK version to 21.

## [1.7.0] - 2024-06-14

### Added

- `setTimezone`, `setPhoneNumber`, `setEmail` methods.
- Automatic tracking of the user's timezone for device property.

## [1.6.0] - 2024-05-27

### Added

- Support for `Triggering conditions` feature for in-app-message.
- Request header `X-Notifly-SDK-Version` and `X-Notifly-SDK-Wrapper` to identify the SDK version and
  derived SDK such as Flutter, React Native SDKs.

### Changed

- Uses `updated_at` instead of `last_updated_timestamp` for campaign.

## [1.5.1] - 2024-05-03

### Added

- Support option to disable push notification badge on Android 8.0 and above.

## [1.5.0] - 2024-03-18

### Added

- Improvements for push notification feature.
  - Application lifecycle listeners are added to avoid redundant session start logging.
  - Application lifecycle listeners are added to avoid redundant fetching of user states.
- Push notification click event handler interface.

### Removed

- Okhttp3 dependency from the SDK.

### Changed

- Reorganized Proguard rules and consumer proguard rules.

## [1.4.3] - 2024-03-05

### Fixed

- App restarting issue when push notification is clicked on foreground state.

## [1.4.2] - 2024-02-22

### Added

- Separate notification channels based on the importance of the notification.
- Importance of the notification is determined by the `imp` field in the push notification data payload.

## [1.4.1] - 2024-02-15

### Fixed

- Unexpected behavior of in-app message for hybrid webview applications.

## [1.4.0] - 2024-01-19

### Added

- Support for triggering event parameter filtering.
- Support for IS_NULL and IS_NOT_NULL operator for segmentation conditions.
- Support for user metadata segmentation conditions.
  - External User ID segmentation
  - Random bucket number segmentation
- Verbose logs.

### Changed

- Implemented updated specifications for managing user states.
- Increased timestamp precision for event timestamp from milliseconds to microseconds.

### Fixed

- Ensures non-null UserData.

## [1.3.0] - 2023-10-05

### Added

- Implemented pending mechanism while refreshing user states.
- Added hide until feature for in-app messages.
- Support for campaign re-eligibility settings for in-app messages.

### Changed

- Several breaking improvements for stability.

## [1.2.1] - 2023-09-11

### Fixed

- Several fixes in in-app-message feature.
  - Fix ordering problem between ingestion of event and exposure of app popup.
  - Fix unexpected behavior when url of main button in in-app-message is invalid.
  - Fix unexpected behavior when in-app-message content loading is slow.

## [1.2.0] - 2023-08-14

### Fixed

- Unexpected behavior for whitelisting feature.

## [1.1.9] - 2023-07-26

### Added

- Option to disable in app message, especially for applications using WebView.
- You can disable in app message feature by calling Notifly.disableInAppMessage().
- CAUTION: THIS FUNCTION SHOULD BE CALLED BEFORE Notifly.initialize(...) IS CALLED.
