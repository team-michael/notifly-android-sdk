package tech.notifly.push

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.notifly.R

@RunWith(RobolectricTestRunner::class)
class FCMBroadcastReceiverTest {
    @Test
    fun `ad push expanded notification uses readable text colors in night mode`() {
        val context = contextWithNightMode(Configuration.UI_MODE_NIGHT_YES)
        val remoteViews = RemoteViews(context.packageName, R.layout.notifly_notification_ad_expanded)

        applyAdPushNotificationColors(context, remoteViews)
        val view = remoteViews.apply(context, null)

        assertEquals(Color.WHITE, view.findViewById<TextView>(R.id.notifly_title).currentTextColor)
        assertEquals(
            Color.rgb(224, 224, 224),
            view.findViewById<TextView>(R.id.notifly_body).currentTextColor,
        )
        assertEquals(
            Color.rgb(224, 224, 224),
            view.findViewById<TextView>(R.id.notifly_unsubscribe_text).currentTextColor,
        )
        assertEquals(
            Color.WHITE,
            (view.findViewById<ImageView>(R.id.notifly_divider).background as ColorDrawable).color,
        )
    }

    private fun contextWithNightMode(nightMode: Int): Context {
        val configuration = Configuration(RuntimeEnvironment.getApplication().resources.configuration)
        configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or nightMode
        return RuntimeEnvironment.getApplication().createConfigurationContext(configuration)
    }

    private fun applyAdPushNotificationColors(
        context: Context,
        remoteViews: RemoteViews,
    ) {
        val method =
            FCMBroadcastReceiver::class.java.getDeclaredMethod(
                "applyAdPushNotificationColors",
                Context::class.java,
                RemoteViews::class.java,
            )
        method.isAccessible = true
        method.invoke(FCMBroadcastReceiver(), context, remoteViews)
    }
}
