package tech.notifly.sdkstate

import tech.notifly.utils.EventProducer

object NotiflySdkStateManager {
    @Volatile
    private var state = NotiflySdkState.NOT_INITIALIZED

    private val sdkLifecycleListeners = EventProducer<ISdkLifecycleListener>()

    fun getState(): NotiflySdkState {
        return state
    }

    fun setState(newState: NotiflySdkState) {
        if (state == newState) {
            return
        }
        val prevState = state
        state = newState

        sdkLifecycleListeners.fire { it.onStateChanged(prevState, newState) }
    }

    fun addSdkLifecycleListener(observer: ISdkLifecycleListener) {
        sdkLifecycleListeners.subscribe(observer)
    }

    fun removeSdkLifecycleListener(observer: ISdkLifecycleListener) {
        sdkLifecycleListeners.unsubscribe(observer)
    }
}