package tech.notifly.utils

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OSUtilTest {
    @Test
    fun `openURLInBrowserIntent should not create intent for scheme-less values`() {
        assertNull(OSUtil.openURLInBrowserIntent(Uri.parse("www.wirebarley.com")))
        assertNull(OSUtil.openURLInBrowserIntent(Uri.parse("019f367b-dfe9-7bb2-bca7-988a39dc7dad")))
        assertNull(OSUtil.openURLInBrowserIntent(Uri.parse("www.wirebarley.com?redirect=https://example.com")))
    }

    @Test
    fun `openURLInBrowserIntent should keep explicit http and https urls unchanged`() {
        val httpsIntent = OSUtil.openURLInBrowserIntent(Uri.parse("https://www.wirebarley.com"))
        val httpIntent = OSUtil.openURLInBrowserIntent(Uri.parse("http://www.wirebarley.com"))

        assertEquals(Intent.ACTION_VIEW, httpsIntent?.action)
        assertEquals("https://www.wirebarley.com", httpsIntent?.data.toString())
        assertTrue(httpsIntent?.categories?.contains(Intent.CATEGORY_BROWSABLE) == true)

        assertEquals(Intent.ACTION_VIEW, httpIntent?.action)
        assertEquals("http://www.wirebarley.com", httpIntent?.data.toString())
        assertTrue(httpIntent?.categories?.contains(Intent.CATEGORY_BROWSABLE) == true)
    }

    @Test
    fun `openURLInBrowserIntent should keep explicit custom scheme urls unchanged`() {
        val intent = OSUtil.openURLInBrowserIntent(Uri.parse("wirebarley://event/019f367b-dfe9-7bb2-bca7-988a39dc7dad"))
        val opaqueIntent = OSUtil.openURLInBrowserIntent(Uri.parse("mailto:support@wirebarley.com"))

        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("wirebarley://event/019f367b-dfe9-7bb2-bca7-988a39dc7dad", intent?.data.toString())
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_BROWSABLE) == true)

        assertEquals(Intent.ACTION_VIEW, opaqueIntent?.action)
        assertEquals("mailto:support@wirebarley.com", opaqueIntent?.data.toString())
        assertTrue(opaqueIntent?.categories?.contains(Intent.CATEGORY_BROWSABLE) == true)
    }
}
