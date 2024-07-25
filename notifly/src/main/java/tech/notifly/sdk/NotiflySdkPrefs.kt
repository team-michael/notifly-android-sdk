package tech.notifly.sdk

import tech.notifly.inapp.InAppMessageManager

object NotiflySdkPrefs {
    val inAppMessage = InAppMessagePrefs()
}

class InAppMessagePrefs {
    private var intentFlagsForInAppLinkOpening: Int? = null

    fun setCampaignRevalidationIntervalMillis(interval: Long) {
        InAppMessageManager.campaignRevalidationIntervalMillis = interval
    }

    fun getCampaignRevalidationIntervalMillis(): Long {
        return InAppMessageManager.campaignRevalidationIntervalMillis
    }

    fun setIntentFlagsForInAppLinkOpening(flags: Int) {
        intentFlagsForInAppLinkOpening = flags
    }

    fun getIntentFlagsForInAppLinkOpening(): Int? {
        return intentFlagsForInAppLinkOpening
    }
}
