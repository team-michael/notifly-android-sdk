package tech.notifly.push.interfaces

import org.json.JSONObject

interface IInAppMessageEventListener {
    /**
     * Called when a user fires event from in app message.
     *
     * @param eventName,
     * @param eventParams
     */
    fun handleEvent(
        eventName: String,
        eventParams: JSONObject?,
    )
}
