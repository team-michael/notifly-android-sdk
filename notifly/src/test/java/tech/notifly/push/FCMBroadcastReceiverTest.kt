package tech.notifly.push

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
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
    fun `ad push expanded notification uses system night mode colors`() {
        RuntimeEnvironment.setQualifiers("+night")
        val appDayContext = contextWithNightMode(Configuration.UI_MODE_NIGHT_NO)
        val remoteViews = RemoteViews(appDayContext.packageName, R.layout.notifly_notification_ad_expanded)

        applyAdPushNotificationColors(remoteViews)
        val view = remoteViews.apply(appDayContext, null)

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
        assertEquals(
            Color.rgb(224, 224, 224),
            colorOf(view.findViewById<ImageView>(R.id.notifly_unsubscribe_icon).colorFilter as PorterDuffColorFilter),
        )
        assertEquals(
            Color.rgb(224, 224, 224),
            colorOf(view.findViewById<ImageView>(R.id.notifly_unsubscribe_arrow).colorFilter as PorterDuffColorFilter),
        )
    }

    @Test
    fun `ad push expanded notification uses system light mode colors`() {
        RuntimeEnvironment.setQualifiers("+notnight")
        val appNightContext = contextWithNightMode(Configuration.UI_MODE_NIGHT_YES)
        val remoteViews = RemoteViews(appNightContext.packageName, R.layout.notifly_notification_ad_expanded)

        applyAdPushNotificationColors(remoteViews)
        val view = remoteViews.apply(appNightContext, null)

        assertEquals(Color.rgb(33, 33, 33), view.findViewById<TextView>(R.id.notifly_title).currentTextColor)
        assertEquals(
            Color.rgb(97, 97, 97),
            view.findViewById<TextView>(R.id.notifly_body).currentTextColor,
        )
        assertEquals(
            Color.rgb(97, 97, 97),
            view.findViewById<TextView>(R.id.notifly_unsubscribe_text).currentTextColor,
        )
        assertEquals(
            Color.BLACK,
            (view.findViewById<ImageView>(R.id.notifly_divider).background as ColorDrawable).color,
        )
        assertEquals(
            Color.rgb(97, 97, 97),
            colorOf(view.findViewById<ImageView>(R.id.notifly_unsubscribe_icon).colorFilter as PorterDuffColorFilter),
        )
        assertEquals(
            Color.rgb(97, 97, 97),
            colorOf(view.findViewById<ImageView>(R.id.notifly_unsubscribe_arrow).colorFilter as PorterDuffColorFilter),
        )
    }

    private fun contextWithNightMode(nightMode: Int): Context {
        val configuration = Configuration(RuntimeEnvironment.getApplication().resources.configuration)
        configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or nightMode
        return RuntimeEnvironment.getApplication().createConfigurationContext(configuration)
    }

    private fun applyAdPushNotificationColors(remoteViews: RemoteViews) {
        val method =
            FCMBroadcastReceiver::class.java.getDeclaredMethod(
                "applyAdPushNotificationColors",
                RemoteViews::class.java,
            )
        method.isAccessible = true
        method.invoke(FCMBroadcastReceiver(), remoteViews)
    }

    private fun colorOf(colorFilter: PorterDuffColorFilter): Int {
        val field = PorterDuffColorFilter::class.java.getDeclaredField("mColor")
        field.isAccessible = true
        return field.getInt(colorFilter)
    }
}
