package tech.notifly.command.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.inapp.InAppMessageManager
import tech.notifly.kmp.identity.UserIdTransitionPolicy
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.Logger
import tech.notifly.utils.N
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyTimerUtil
import tech.notifly.utils.NotiflyUserUtil

internal fun normalizeUserId(userId: String?): String? = if (userId.isNullOrEmpty()) null else userId

enum class CommandType {
    SET_USER_ID,
    SET_USER_PROPERTIES,
    TRACK_EVENT,
}

abstract class CommandBase : Comparable<CommandBase> {
    private val timestamp = NotiflyTimerUtil.getTimestampMicros()

    abstract val commandType: CommandType
    protected abstract val payload: PayloadBase

    open fun execute() {
        Logger.v("Executing command: $commandType")
    }

    override fun compareTo(other: CommandBase): Int = timestamp.compareTo(other.timestamp)
}

class SetUserIdCommand(
    override val payload: SetUserIdPayload,
) : CommandBase() {
    override val commandType = CommandType.SET_USER_ID

    override fun execute() {
        super.execute()

        val context = payload.context
        val previousExternalUserId =
            NotiflyStorage.get(
                context,
                NotiflyStorageItem.EXTERNAL_USER_ID,
            )
        val userId = payload.userId
        val transition =
            UserIdTransitionPolicy.evaluate(
                normalizeUserId(previousExternalUserId),
                normalizeUserId(userId),
            )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (transition.shouldClear) {
                    NotiflyUserUtil.removeUserId(context)
                } else if (transition.changed) {
                    NotiflyUserUtil.setUserProperties(
                        context,
                        mapOf(
                            N.KEY_EXTERNAL_USER_ID to userId,
                        ),
                    )
                } else {
                    // No-op
                }
                // Refresh state only when user ID is changed
                if (transition.shouldSync) {
                    InAppMessageManager.refresh(context, transition.shouldMerge)
                }
                // Preserve the existing behavior of clearing state whenever the requested ID is anonymous.
                if (transition.shouldClear) {
                    InAppMessageManager.clearUserState()
                }

                NotiflySdkStateManager.setState(NotiflySdkState.READY)
            } catch (e: Exception) {
                Logger.e("[Notifly] setUserId failed", e)
                NotiflySdkStateManager.setState(NotiflySdkState.FAILED)
            }
        }
    }
}

class SetUserPropertiesCommand(
    override val payload: SetUserPropertiesPayload,
) : CommandBase() {
    override val commandType = CommandType.SET_USER_PROPERTIES

    override fun execute() {
        super.execute()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotiflyUserUtil.setUserProperties(payload.context, payload.params)
            } catch (e: Exception) {
                Logger.w("Notifly setUserProperties failed", e)
            }
        }
    }
}

class TrackEventCommand(
    override val payload: TrackEventPayload,
) : CommandBase() {
    override val commandType = CommandType.TRACK_EVENT

    override fun execute() {
        super.execute()
        NotiflyLogUtil.logEventNonBlocking(
            payload.context,
            payload.eventName,
            payload.eventParams,
            payload.segmentationEventParamKeys,
            payload.isInternalEvent,
        )
    }
}
