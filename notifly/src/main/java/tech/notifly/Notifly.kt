package tech.notifly

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.application.ApplicationService
import tech.notifly.application.BaseApplicationLifecycleHandler
import tech.notifly.application.IApplicationService
import tech.notifly.command.CommandDispatcher
import tech.notifly.command.models.SetUserIdCommand
import tech.notifly.command.models.SetUserIdPayload
import tech.notifly.command.models.SetUserPropertiesCommand
import tech.notifly.command.models.SetUserPropertiesPayload
import tech.notifly.command.models.TrackEventCommand
import tech.notifly.command.models.TrackEventPayload
import tech.notifly.http.HttpClientOptions
import tech.notifly.http.IHttpClient
import tech.notifly.http.impl.HttpClient
import tech.notifly.http.impl.HttpConnectionFactory
import tech.notifly.inapp.InAppMessageManager
import tech.notifly.push.PushNotificationManager
import tech.notifly.push.interfaces.INotificationClickListener
import tech.notifly.sdk.NotiflySdkControlToken
import tech.notifly.sdk.NotiflySdkPrefs
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.sdk.NotiflySdkWrapperInfo
import tech.notifly.sdk.NotiflySdkWrapperType
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.Logger
import tech.notifly.utils.N
import tech.notifly.utils.NotiflyUserUtil
import tech.notifly.utils.NotiflyUtil

object Notifly {
    private var isInitialized: Boolean = false
    private val initLock: Any = Any()

    @JvmStatic
    val preferences = NotiflySdkPrefs

    /**
     * Initialize Notifly SDK from the context.
     * @param context The context of the application.
     * @param projectId The project ID of the Notifly project.
     * @param username Provided username of the Notifly Project.
     * @param password Provided password of the Notifly Project.
     */
    @JvmStatic
    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
    ) {
        if (!NotiflyUtil.isValidProjectId(projectId)) {
            Logger.e("Invalid project ID. Please check your project ID.")
            return
        }
        storeProjectMetadata(context, projectId, username, password)
        initializeWithContext(context)
    }

    /**
     * Initialize Notifly SDK from the context.
     * This method assumes that the project metadata is already stored in the storage.
     *
     * DO NOT CALL THIS METHOD DIRECTLY.
     */
    fun initializeWithContext(context: Context): Boolean {
        synchronized(initLock) {
            if (isInitialized) {
                Logger.d("Notifly SDK is already initialized. Skipping initialization.")
                return true
            }

            try {
                val projectId = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
                val username = NotiflyStorage.get(context, NotiflyStorageItem.USERNAME)
                val password = NotiflyStorage.get(context, NotiflyStorageItem.PASSWORD)

                if (projectId.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty()) {
                    Logger.e("Project metadata is missing.")
                    isInitialized = false
                    return false
                }

                val applicationService = ApplicationService()
                val httpClient =
                    HttpClient(HttpConnectionFactory(), HttpClientOptions(120000, 60000))

                NotiflyServiceProvider.register(IApplicationService::class.java, applicationService)
                NotiflyServiceProvider.register(IHttpClient::class.java, httpClient)

                applicationService.addApplicationLifecycleHandler(object :
                    BaseApplicationLifecycleHandler() {
                    override fun onFocus(first: Boolean) {
                        if (first) {
                            // Application is brought into the foreground for the first time
                            CoroutineScope(Dispatchers.IO).launch {
                                initializeInAppMessageManagerAndStartSession(context)
                            }
                        } else {
                            // Application is brought into the foreground
                            CoroutineScope(Dispatchers.IO).launch {
                                InAppMessageManager.maybeRevalidateCampaigns(context)
                            }
                        }
                    }
                })
                applicationService.start(context)

                NotiflySdkStateManager.addSdkLifecycleListener(CommandDispatcher)
                NotiflySdkStateManager.setState(NotiflySdkState.READY)

                isInitialized = true
                return true
            } catch (e: Exception) {
                Logger.e("Notifly initialization failed:", e)
                NotiflySdkStateManager.setState(NotiflySdkState.FAILED)

                isInitialized = false
                return false
            }
        }
    }

    private fun storeProjectMetadata(
        context: Context,
        projectId: String,
        username: String,
        password: String,
    ) {
        NotiflyStorage.put(context, NotiflyStorageItem.PROJECT_ID, projectId)
        NotiflyStorage.put(context, NotiflyStorageItem.USERNAME, username)
        NotiflyStorage.put(context, NotiflyStorageItem.PASSWORD, password)
    }

    private suspend fun initializeInAppMessageManagerAndStartSession(context: Context) {
        try {
            // Start Session
            // Set Required Properties from User
            InAppMessageManager.initialize(context)
        } catch (e: Exception) {
            Logger.e("Failed to initialize in app message manager:", e)
        }

        try {
            NotiflyUserUtil.sessionStart(context)
        } catch (e: Exception) {
            Logger.e("Failed to start session:", e)
        }
    }

    /**
     * Sets the user ID for the current user.
     * If the user ID is null, the user ID will be cleared.
     *
     * @param context The context of the application.
     * @param userId The user ID of the current user.
     */
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

    /**
     * Disables in-app message.
     * In-app message will not be shown after this method is called.
     * Generally you don't need to call this method.
     * Consider disabling in-app message when your application is webview-based and you want to expose in-browser messages (in-web messages).
     */
    @JvmStatic
    fun disableInAppMessage() {
        InAppMessageManager.disable()
    }

    /**
     * Sets the user properties for the current user.
     * User properties are key-value pairs that can be used to segment users.
     *
     * There are special property keys that are reserved for specific purposes:
     * - `$phone_number`: The phone number of the user. You can also use `setPhoneNumber` method to set this property.
     * - `$email`: The email address of the user. You can also use `setEmail` method to set this property.
     * - `$timezone`: The timezone ID of the user. You can also use `setTimezone` method to set this property.
     *
     * @param context The context of the application.
     * @param params The user properties to set.
     */
    @JvmStatic
    fun setUserProperties(context: Context, params: Map<String, Any?>) {
        if (params.isEmpty()) {
            Logger.w("Empty user properties. Please provide at least one user property.")
            return
        }
        if (params[N.KEY_TIMEZONE_PROPERTY] != null) {
            val timezone = params[N.KEY_TIMEZONE_PROPERTY] as? String
            if (timezone == null || !NotiflyUtil.isValidTimezoneId(timezone)) {
                Logger.w("Invalid timezone ID $timezone. Please check your timezone ID. Omitting timezone property.")
                val newParams = params.toMutableMap().apply {
                    remove(N.KEY_TIMEZONE_PROPERTY)
                }
                return setUserProperties(context, newParams)
            }
        }
        CommandDispatcher.dispatch(
            SetUserPropertiesCommand(
                SetUserPropertiesPayload(
                    context = context, params = params
                )
            )
        )
    }

    /**
     * Sets the phone number of the current user.
     * This is a convenience method for setting the phone number user property.
     * You can also use `setUserProperties` method with `$phone_number` key to set the phone number.
     *
     * @param context The context of the application.
     * @param phoneNumber The phone number of the user.
     */
    @JvmStatic
    fun setPhoneNumber(context: Context, phoneNumber: String) {
        setUserProperties(
            context, mapOf(
                N.KEY_PHONE_NUMBER_PROPERTY to phoneNumber
            )
        )
    }

    /**
     * Sets the email address of the current user.
     * This is a convenience method for setting the email user property.
     * You can also use `setUserProperties` method with `$email` key to set the email.
     *
     * @param context The context of the application.
     * @param email The email address of the user.
     */
    @JvmStatic
    fun setEmail(context: Context, email: String) {
        setUserProperties(
            context, mapOf(
                N.KEY_EMAIL_PROPERTY to email
            )
        )
    }

    /**
     * Sets the timezone ID of the current user.
     * This is a convenience method for setting the timezone user property.
     * You can also use `setUserProperties` method with `$timezone` key to set the timezone.
     *
     * @param context The context of the application.
     * @param timezone
     * The timezone ID of the user. For example, "Asia/Seoul".
     * Invalid timezone IDs will be ignored.
     * See [IANA Timezone Database](https://www.iana.org/time-zones) for the list of valid timezone IDs.
     */
    @JvmStatic
    fun setTimezone(context: Context, timezone: String) {
        setUserProperties(
            context, mapOf(
                N.KEY_TIMEZONE_PROPERTY to timezone
            )
        )
    }

    /**
     * Tracks an event with the given event name and event parameters.
     * Event parameters are key-value pairs that can be used to provide additional information about the event.
     *
     * @param context The context of the application.
     * @param eventName The name of the event.
     * @param eventParams The event parameters.
     * @param segmentationEventParamKeys
     * The keys to segment the event parameters. Currently limited to single key only.
     * If the key is provided, you can perform the advanced event-based segmentation with the parameter.
     */
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

    /**
     * Adds listener for push notification click event.
     * @param listener The listener to add.
     */
    @JvmStatic
    fun addNotificationClickListener(listener: INotificationClickListener) {
        PushNotificationManager.addClickListener(listener)
    }

    /**
     * Sets the log level for the Notifly SDK.
     * @param level The log level to set.
     */
    @JvmStatic
    fun setLogLevel(level: Int) {
        Logger.setLogLevel(level)
    }

    /**
     * This is the internal method for setting the SDK version.
     * This method is not intended to be called by the client.
     *
     * DO NOT CALL THIS METHOD DIRECTLY.
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setSdkVersion(token: NotiflySdkControlToken, version: String) {
        NotiflySdkWrapperInfo.setSdkVersion(version)
    }

    /**
     * This is the internal method for setting the SDK type.
     * This method is not intended to be called by the client.
     *
     * DO NOT CALL THIS METHOD DIRECTLY.
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setSdkType(token: NotiflySdkControlToken, type: NotiflySdkWrapperType) {
        NotiflySdkWrapperInfo.setSdkType(type)
    }
}
