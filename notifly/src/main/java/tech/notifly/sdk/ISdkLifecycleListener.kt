package tech.notifly.sdk

interface ISdkLifecycleListener {
    fun onStateChanged(prevState: NotiflySdkState, newState: NotiflySdkState)
}