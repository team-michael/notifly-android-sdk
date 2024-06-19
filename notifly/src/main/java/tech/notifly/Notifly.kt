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
                    override fun onFirstFocus() {
                        CoroutineScope(Dispatchers.IO).launch {
                            initializeInAppMessageManagerAndStartSession(context)
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

    @JvmStatic
    fun setPhoneNumber(context: Context, phoneNumber: String) {
        setUserProperties(
            context, mapOf(
                N.KEY_PHONE_NUMBER_PROPERTY to phoneNumber
            )
        )
    }

    @JvmStatic
    fun setEmail(context: Context, email: String) {
        setUserProperties(
            context, mapOf(
                N.KEY_EMAIL_PROPERTY to email
            )
        )
    }

    @JvmStatic
    fun setTimezone(context: Context, timezone: String) {
        setUserProperties(
            context, mapOf(
                N.KEY_TIMEZONE_PROPERTY to timezone
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
    fun addNotificationClickListener(listener: INotificationClickListener) {
        PushNotificationManager.addClickListener(listener)
    }

    @JvmStatic
    fun setLogLevel(level: Int) {
        Logger.setLogLevel(level)
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setSdkVersion(token: NotiflySdkControlToken, version: String) {
        NotiflySdkWrapperInfo.setSdkVersion(version)
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun setSdkType(token: NotiflySdkControlToken, type: NotiflySdkWrapperType) {
        NotiflySdkWrapperInfo.setSdkType(type)
    }
}
