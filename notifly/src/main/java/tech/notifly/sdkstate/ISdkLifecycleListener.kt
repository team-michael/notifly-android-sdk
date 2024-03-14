package tech.notifly.sdkstate

interface ISdkLifecycleListener {
    fun onStateChanged(prevState: NotiflySdkState, newState: NotiflySdkState)
}