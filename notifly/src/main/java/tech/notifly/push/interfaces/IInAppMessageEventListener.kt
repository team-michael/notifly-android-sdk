package tech.notifly.push.interfaces

import org.json.JSONObject

interface IInAppMessageEventListener {
    /**
     * Called when a user fires event from in app message.
     *
     * @param eventName,
     * @param elementName
     * @param extraData
     */
    fun handleEvent(
        eventName: String,
        elementName: String,
        extraData: JSONObject?,
    )
}
