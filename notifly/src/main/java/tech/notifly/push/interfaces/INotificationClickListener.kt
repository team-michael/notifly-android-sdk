package tech.notifly.push.interfaces

interface INotificationClickListener {
    /**
     * Called when a user clicks on a notification.
     *
     * @param event
     */
    fun onClick(event: INotificationClickEvent)
}
