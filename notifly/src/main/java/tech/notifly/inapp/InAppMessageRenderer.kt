package tech.notifly.inapp

import kotlinx.coroutines.suspendCancellableCoroutine
import tech.notifly.kmp.popup.PopupFactory
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRendererConfig
import tech.notifly.sdk.NotiflySdkInfo
import tech.notifly.utils.Logger
import kotlin.coroutines.resume

/** Adapts KMP rendering to the Android scheduler without owning popup presentation. */
internal class InAppMessageRenderer(
    projectId: String,
    baseUrl: String = "https://render.notifly.tech",
) {
    private val renderer =
        PopupFactory.create(
            PopupRendererConfig(projectId, baseUrl, "notifly/android/${NotiflySdkInfo.getSdkVersion()}"),
        )

    /** Returns rendered HTML, or skips the popup; coroutine cancellation also cancels the KMP request. */
    suspend fun render(input: PopupRenderInput): String? =
        suspendCancellableCoroutine { continuation ->
            val task =
                renderer.render(input) { output ->
                    val html =
                        when (output.outcome) {
                            "rendered" -> output.html
                            "failed" -> {
                                Logger.w("[Notifly] Popup rendering failed: ${output.errorCode}")
                                null
                            }
                            else -> null
                        }
                    continuation.resume(html)
                }
            continuation.invokeOnCancellation { task.cancel() }
        }
}
