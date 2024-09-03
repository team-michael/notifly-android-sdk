package tech.notifly.application

interface IApplicationLifecycleHandler {
    /**
     * Called when the application is brought into the foreground.
     */
    fun onFocus(first: Boolean)

    /**
     * Called when the application has been brought out of the foreground, to the background.
     */
    fun onUnfocused()
}

open class BaseApplicationLifecycleHandler : IApplicationLifecycleHandler {
    override fun onFocus(first: Boolean) {}

    override fun onUnfocused() {}
}
