# notifly-android-sdk

[![](https://jitpack.io/v/team-michael/notifly-android-sdk.svg)](https://jitpack.io/#team-michael/notifly-android-sdk)


_The Notifly SDK for Android_ is a user-friendly library for integrating Notifly into your Android applications. It simplifies the process of setting up and managing notifications, and is fully compatible with all Android versions supported by the AndroidX library.

## 1. How to set up the SDK in an Android application

Follow [How to get a Git project into your build](https://jitpack.io/#team-michael/notifly-android-sdk).

## 2. Setting up local development environment

We highly recommend using Android Studio for local development.

### Test app

This project contains a sample app to test the features. Follow the steps in sample app [README](./sample/README.md) for setup.

### Unit test, Lint

```shell
./gradlew testDebugUnitTest --rerun-tasks
./gradlew lint
```

These commands run as CI in Github actions.

## 3. Deployment

Notifly android SDK is distributed via Jitpack and Github release. 

To deploy a new version. follow these steps:

1. Update the version name in `gradle.properties`

```
version=1.0.0
```

_NOTE: the sdk version gets logged in all Notifly events without the prefix v._

2. On Github, [draft a new release](https://github.com/team-michael/notifly-android-sdk/releases).

3. Once released, go to [notifly-android-sdk](https://jitpack.io/#team-michael/notifly-android-sdk) Jitpack page. Click the version you've released, and click "Get It". You have to do it to trigger the build on Jitpack. 

4. Make sure your build is successful (if not, the Log file color will be red.).
