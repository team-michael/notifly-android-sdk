package tech.notifly

import android.app.NotificationChannel
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.command.CommandDispatcher
import tech.notifly.command.models.SetUserIdCommand
import tech.notifly.command.models.SetUserIdPayload
import tech.notifly.command.models.SetUserPropertiesCommand
import tech.notifly.command.models.SetUserPropertiesPayload
import tech.notifly.command.models.TrackEventCommand
import tech.notifly.command.models.TrackEventPayload
import tech.notifly.inapp.InAppMessageManager
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyNotificationChannelUtil
import tech.notifly.utils.NotiflySDKInfoUtil
import tech.notifly.utils.NotiflyUserUtil

object Notifly {
    @JvmStatic
    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
    ) {
        if (NotiflySdkStateManager.getState() != NotiflySdkState.NOT_INITIALIZED) {
            Logger.e("Notifly SDK is not in expected state. Skipping initialization.")
            return
        }

        NotiflySdkStateManager.registerObserver(CommandDispatcher)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Start Session
                // Set Required Properties from User
                NotiflyStorage.put(context, NotiflyStorageItem.PROJECT_ID, projectId)
                NotiflyStorage.put(context, NotiflyStorageItem.USERNAME, username)
                NotiflyStorage.put(context, NotiflyStorageItem.PASSWORD, password)

                InAppMessageManager.initialize(context)
                NotiflyUserUtil.sessionStart(context)

                NotiflySdkStateManager.setState(NotiflySdkState.READY)
            } catch (e: Exception) {
                Logger.e("Notifly initialization failed:", e)
                NotiflySdkStateManager.setState(NotiflySdkState.FAILED)
            }
        }
    }


    @JvmStatic
    @JvmOverloads
    fun setUserId(
        context: Context,
        userId: String? = null,
    ) {
        CommandDispatcher.dispatch(
            SetUserIdCommand(
                SetUserIdPayload(
                    context = context, userId = userId
                )
            )
        )
    }

    @JvmStatic
    fun disableInAppMessage() {
        InAppMessageManager.disable()
    }

    @JvmStatic
    fun setUserProperties(context: Context, params: Map<String, Any?>) {
        CommandDispatcher.dispatch(
            SetUserPropertiesCommand(
                SetUserPropertiesPayload(
                    context = context, params = params
                )
            )
        )
    }

    @JvmStatic
    @JvmOverloads
    fun trackEvent(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?> = emptyMap(),
        segmentationEventParamKeys: List<String>? = null,
    ) {
        CommandDispatcher.dispatch(
            TrackEventCommand(
                TrackEventPayload(
                    context = context,
                    eventName = eventName,
                    eventParams = eventParams,
                    segmentationEventParamKeys = segmentationEventParamKeys,
                    isInternalEvent = false
                )
            )
        )
    }

    @JvmStatic
    fun setLogLevel(level: Int) {
        Logger.setLogLevel(level)
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setSdkVersion(token: NotiflyControlToken, version: String) {
        NotiflySDKInfoUtil.setSdkVersion(version)
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setSdkType(token: NotiflyControlToken, type: NotiflySdkType) {
        NotiflySDKInfoUtil.setSdkType(type)
    }
}
