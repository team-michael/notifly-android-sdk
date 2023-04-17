package tech.notifly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import tech.notifly.utils.NotiflyLogUtil

internal class NotiflyBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_LINK = "KEY_LINK"
        const val KEY_CAMPAIGN_ID = "KEY_CAMPAIGN_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(Notifly.TAG, "NotiflyBroadcastReceiver.onReceive")

        val link = intent.getStringExtra(KEY_LINK)!!
        val campaignId = intent.getStringExtra(KEY_CAMPAIGN_ID)!!

        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        NotiflyLogUtil.logEvent(context, "push_click", mapOf("campaign_id" to campaignId, "status" to "background"), listOf(), true)
    }
}