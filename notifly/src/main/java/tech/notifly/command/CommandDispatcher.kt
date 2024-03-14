package tech.notifly.command

import tech.notifly.sdkstate.NotiflySdkState
import tech.notifly.sdkstate.NotiflySdkStateManager
import tech.notifly.sdkstate.ISdkLifecycleListener
import tech.notifly.command.models.CommandBase
import tech.notifly.command.models.CommandType
import tech.notifly.utils.Logger
import java.util.PriorityQueue

object CommandDispatcher : ISdkLifecycleListener {
    private val pendingCommandsQueue: PriorityQueue<CommandBase> = PriorityQueue()

    fun dispatch(command: CommandBase) {
        when (NotiflySdkStateManager.getState()) {
            NotiflySdkState.FAILED -> {
                Logger.e("[Notifly] Notifly SDK has failed to operate. Cannot execute command ${command.commandType.name}")
                return
            }

            NotiflySdkState.READY -> {
                command.execute()
            }

            else -> {
                Logger.v("[Notifly] Notifly SDK is not currently active. Adding command ${command.commandType.name} to the queue..")
                pendingCommandsQueue.add(command)
            }
        }
    }

    override fun onStateChanged(prevState: NotiflySdkState, newState: NotiflySdkState) {
        Logger.v("[Notifly] Notifly SDK state changed: $prevState -> $newState")
        if (newState == NotiflySdkState.READY) {
            Logger.v("==== Executing pending commands ====")
            while (pendingCommandsQueue.isNotEmpty()) {
                val command = pendingCommandsQueue.poll()
                if (command != null) {
                    command.execute()
                    if (command.commandType == CommandType.SET_USER_ID) {
                        Logger.v("==== Stop executing pending commands due to the recurring set user ID. ====")
                        break
                    }
                }
            }
        }
    }
}