# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
