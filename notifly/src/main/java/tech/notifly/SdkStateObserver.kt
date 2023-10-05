package tech.notifly

interface SdkStateObserver {
    fun onStateChanged(prevState: NotiflySdkState, newState: NotiflySdkState)
}