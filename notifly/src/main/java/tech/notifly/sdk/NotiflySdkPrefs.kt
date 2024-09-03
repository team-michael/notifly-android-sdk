package tech.notifly.sdk

import tech.notifly.inapp.InAppMessageManager

object NotiflySdkPrefs {
    val inAppMessage = InAppMessagePrefs()
}

class InAppMessagePrefs {
    private var intentFlagsForInAppLinkOpening: Int? = null

    /**
     * Sets the interval in milliseconds for revalidating the campaign.
     * The default value is 10 minutes (600,000 milliseconds).
     * The value should be greater than 3 minutes (180,000 milliseconds).
     * @param interval The interval in milliseconds.
     */
    fun setCampaignRevalidationIntervalMillis(interval: Long) {
        InAppMessageManager.campaignRevalidationIntervalMillis = interval
    }

    fun getCampaignRevalidationIntervalMillis(): Long = InAppMessageManager.campaignRevalidationIntervalMillis

    /**
     * Sets the intent flags for opening the in-app link.
     * @param flags The intent flags.
     */
    fun setIntentFlagsForInAppLinkOpening(flags: Int) {
        intentFlagsForInAppLinkOpening = flags
    }

    fun getIntentFlagsForInAppLinkOpening(): Int? = intentFlagsForInAppLinkOpening
}
