package tech.notifly.application

interface IApplicationLifecycleHandler {
    /**
     *
     */
    fun onFirstFocus()

    /**
     * Called when the application is brought into the foreground.
     */
    fun onFocus()

    /**
     * Called when the application has been brought out of the foreground, to the background.
     */
    fun onUnfocused()
}

open class BaseApplicationLifecycleHandler : IApplicationLifecycleHandler {
    override fun onFirstFocus() {}

    override fun onFocus() {}

    override fun onUnfocused() {}
}