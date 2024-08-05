# 1.9.3

- Fix crashing issue when the app revalidates the campaign data from the server. 

# 1.9.2

- Fix the issue that the notifly user ID sometimes gets out-of-sync with the external user ID.

# 1.9.1

- Call `WebView.resumeTimers` explicitly at the start of the in-app message rendering process to prevent
  the in-app message from not being displayed in some cases.

# 1.9.0

- To mitigate the problem that campaign data remains in memory with stale data, the SDK now
  refreshes the campaign data when below conditions are all met:
  - The app is focused (foreground)
  - `campaignRevalidationInterval` has passed since the last campaign data refresh

# 1.8.3

- Change `PriorityQueue` to `PriorityBlockingQueue` to avoid `NullPointerException` caused by
  concurrent access to the queue.

# 1.8.2

- Fix recents are ignored when the app is launched with notification click event.

# 1.8.1

- Fix `push_delivered` event logging when notification permission is not granted

# 1.8.0

- Change API hostname for tracking events

# 1.7.4

- Remove WebView timer manipulations such as `pauseTimers` and `resumeTimers` for in-app messages.

# 1.7.3

- Remove redundant `Set User ID` call from the SDK.

# 1.7.2

- Add InAppMessagePrefs class to manage in-app message preferences.

# 1.7.1

- Fix crash issue when EncryptedSharedPreferences creation has failed.
- Downgrade the minimum SDK version to 21.

# 1.7.0

- Add `setTimezone`, `setPhoneNumber`, `setEmail` methods.
- Now SDK automatically tracks the user's timezone for device property.

# 1.6.0

- `Triggering conditions` feature is now supported for in-app-message.
    - `triggering_event` field is not used anymore.
- Uses `updated_at` instead of `last_updated_timestamp` for campaign.
- Add request header `X-Notifly-SDK-Version` and `X-Notifly-SDK-Wrapper` to identify the SDK version
  and derived SDK such as Flutter, React Native SDKs.

# 1.5.1

- Support option to disable push notification badge on Android 8.0 and above.

# 1.5.0

- Lots of improvements for push notification feature.
    - Application lifecycle listeners are added to avoid redundant session start logging.
    - Application lifecycle listeners are added to avoid redundant fetching of user states.
- Push notification click event handler interface is added to allow developers to customize push
  notification click events.
- Okhttp3 dependency is now removed from the SDK.
- Proguard rules and consumer proguard rules are re-organized to avoid unexpected behavior.

# 1.4.3

- Fix app restarting issue when push notification is clicked on foreground state.

# 1.4.2

- Separate notification channels based on the importance of the notification
- Importance of the notification is determined by the `imp` field in the push notification data
  payload

# 1.4.1

- Fix unexpected behavior of in-app message for hybrid webview applications

# 1.4.0

- Add support for triggering event parameter filtering
- Add support for IS_NULL and IS_NOT_NULL operator for segmentation conditions
- Add support for user metadata segmentation conditions
    - External User ID segmentation
    - Random bucket number segmentation
- Implement updated specifications for managing user states
- Increase timestamp precision for event timestamp from milliseconds to microseconds
- Ensures non-null UserData
- Add verbose logs

# 1.3.0

- Several breaking improvements for stability
- Implemented pending mechanism while refreshing user states
- Added hide until feature for in-app messages
- Now SDK supports campaign re-eligibility settings for in-app messages

# 1.2.1

- Several fixes in in-app-message feature
    - Fix ordering problem between ingestion of event and exposure of app popup.
    - Fix unexpected behavior when url of main button in in-app-message is invalid
    - Fix unexpected behavior when in-app-message content loading is slow.

# 1.2.0

- Fix unexpected behavior for whitelisting feature

# 1.1.9

- Add option to disable in app message, especially for applications using WebView.
- You can disable in app message feature by calling Notifly.disableInAppMessage().
- CAUTION: THIS FUNCTION SHOULD BE CALLED BEFORE Notifly.initialize(...) IS CALLED.
