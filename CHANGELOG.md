# 1.5.0

- Immersive improvements for push notification feature.
    - Application lifecycle listeners are added to avoid redundant session start logging.
    - Application lifecycle listeners are added to avoid redundant fetching of user states.
- Push notification click event handler interface is added to allow developers to customize push
  notification click events.
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
