package tech.notifly.command

import tech.notifly.command.models.CommandBase
import tech.notifly.command.models.CommandType
import tech.notifly.sdk.ISdkLifecycleListener
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.utils.Logger
import java.util.concurrent.PriorityBlockingQueue

object CommandDispatcher : ISdkLifecycleListener {
    private val pendingCommandsQueue: PriorityBlockingQueue<CommandBase> = PriorityBlockingQueue()

    fun dispatch(command: CommandBase) {
        when (NotiflySdkStateManager.getState()) {
            NotiflySdkState.FAILED -> {
                Logger.e("[Notifly] Notifly SDK has failed to operate. Cannot execute command ${command.commandType.name}")
                return
            }

            NotiflySdkState.READY -> {
                executeReadyCommand(command)
            }

            else -> {
                Logger.v("[Notifly] Notifly SDK is not currently active. Adding command ${command.commandType.name} to the queue..")
                pendingCommandsQueue.add(command)
            }
        }
    }

    override fun onStateChanged(
        prevState: NotiflySdkState,
        newState: NotiflySdkState,
    ) {
        Logger.v("[Notifly] Notifly SDK state changed: $prevState -> $newState")
        if (newState == NotiflySdkState.READY) {
            Logger.v("==== Executing pending commands ====")
            while (pendingCommandsQueue.isNotEmpty()) {
                val command = pendingCommandsQueue.poll()
                if (command != null) {
                    executeReadyCommand(command)
                    if (command.commandType == CommandType.SET_USER_ID) {
                        Logger.v("==== Stop executing pending commands due to the recurring set user ID. ====")
                        break
                    }
                }
            }
        }
    }

    private fun executeReadyCommand(command: CommandBase) {
        // SET_USER_ID refreshes user state asynchronously. Mark it as REFRESHING even when
        // it came from the pending queue, otherwise commands queued after it can remain stuck
        // because SetUserIdCommand's final READY transition would be READY -> READY no-op.
        val doesCommandNeedRefresh = command.commandType == CommandType.SET_USER_ID
        if (doesCommandNeedRefresh) {
            NotiflySdkStateManager.setState(NotiflySdkState.REFRESHING)
        }

        command.execute()
    }
}
