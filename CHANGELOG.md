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
