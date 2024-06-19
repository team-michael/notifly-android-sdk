package tech.notifly.sdk

object NotiflySdkPrefs {
    val inAppMessage = InAppMessagePrefs()
}

class InAppMessagePrefs {
    private var intentFlagsForInAppLinkOpening: Int? = null

    fun setIntentFlagsForInAppLinkOpening(flags: Int) {
        intentFlagsForInAppLinkOpening = flags
    }
    fun getIntentFlagsForInAppLinkOpening(): Int? {
        return intentFlagsForInAppLinkOpening
    }
}
