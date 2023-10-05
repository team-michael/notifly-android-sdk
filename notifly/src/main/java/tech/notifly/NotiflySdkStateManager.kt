package tech.notifly

object NotiflySdkStateManager {
    @Volatile
    private var state = NotiflySdkState.NOT_INITIALIZED

    private val observers = mutableListOf<SdkStateObserver>()

    fun getState(): NotiflySdkState {
        return state
    }

    fun setState(newState: NotiflySdkState) {
        if (state == newState) {
            return
        }
        val prevState = state
        state = newState
        observers.forEach { it.onStateChanged(prevState, newState) }
    }

    fun registerObserver(observer: SdkStateObserver) {
        observers.add(observer)
    }
}