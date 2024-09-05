package tech.notifly.inapp

import android.app.Activity
import android.graphics.Insets
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowMetrics
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class InAppMessageUtilsTest {
    @Test
    fun `getViewDimensions should return screen dimensions when modal props are null`() {
        // Given
        val screenWidth = 400f
        val screenHeight = 800f

        // When
        val (width, height) = InAppMessageUtils.getViewDimensions(null, screenWidth, screenHeight)

        // Then
        assertEquals(screenWidth, width)
        assertEquals(screenHeight, height)
    }

    @Test
    fun `getViewDimensions should return dimensions based on modal props`() {
        // Given
        val modalProps = JSONObject()
        modalProps.put("width", 200)
        modalProps.put("height", 400)

        // When
        val (width, height) = InAppMessageUtils.getViewDimensions(modalProps, 400f, 800f)

        // Then
        assertEquals(200f, width)
        assertEquals(400f, height)
    }

    @Test
    fun `getViewDimensions should return dimensions within min and max limits`() {
        // Given
        val modalProps = JSONObject()
        modalProps.put("width", 100)
        modalProps.put("min_width", 150)
        modalProps.put("max_width", 250)
        modalProps.put("height", 500)
        modalProps.put("max_height", 400)

        // When
        val (width, height) = InAppMessageUtils.getViewDimensions(modalProps, 400f, 800f)

        // Then
        assertEquals(150f, width)
        assertEquals(400f, height)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getScreenWidthAndHeight should return screen width and height on SDK R`() {
        // Given
        val activity = mockk<Activity>()
        val density = 2f
        val windowMetrics = mockk<WindowMetrics>()
        val windowInsets = mockk<WindowInsets>()

        every { activity.windowManager.currentWindowMetrics } returns windowMetrics
        every { windowMetrics.bounds } returns Rect(0, 0, 800, 1600)
        every { windowMetrics.windowInsets } returns windowInsets
        every { windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()) } returns Insets.of(0, 0, 0, 0)

        // When
        val (width, height) = InAppMessageUtils.getScreenWidthAndHeight(activity, density)

        // Then
        assertEquals(400f, width)
        assertEquals(800f, height)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `getScreenWidthAndHeight should return screen width and height on SDK M`() {
        // Given
        val activity = mockk<Activity>()
        val density = 2f
        every { activity.windowManager.defaultDisplay } returns
            mockk {
                every { getSize(any()) } answers {
                    val point = args[0] as Point
                    point.x = 400
                    point.y = 800
                }
            }

        // When
        val (width, height) = InAppMessageUtils.getScreenWidthAndHeight(activity, density)

        // Then
        assertEquals(200f, width)
        assertEquals(400f, height)
    }

    @Test
    fun `getKSTCalendarDateString should return KST date string with offset`() {
        // Given
        val daysOffset = 1
        val expectedDate =
            Calendar
                .getInstance()
                .apply {
                    add(Calendar.DAY_OF_YEAR, daysOffset)
                }.time
        val expectedFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val expectedDateString = expectedFormat.format(expectedDate)

        // When
        val actualDateString = InAppMessageUtils.getKSTCalendarDateString(daysOffset)

        // Then
        assertEquals(expectedDateString, actualDateString)
    }

    @Test
    fun `getViewDimensions should return correct width when width_vw is specified`() {
        // Given
        val modalProps =
            JSONObject().apply {
                put("width_vw", 50)
            }
        val screenWidth = 1000f
        val screenHeight = 2000f

        // When
        val (width, _) = InAppMessageUtils.getViewDimensions(modalProps, screenWidth, screenHeight)

        // Then
        assertEquals(500f, width)
    }

    @Test
    fun `getViewDimensions should return correct width when width_vh is specified`() {
        // Given
        val modalProps =
            JSONObject().apply {
                put("width_vh", 25)
            }
        val screenWidth = 1000f
        val screenHeight = 2000f

        // When
        val (width, _) = InAppMessageUtils.getViewDimensions(modalProps, screenWidth, screenHeight)

        // Then
        assertEquals(500f, width)
    }

    @Test
    fun `getViewDimensions should return correct height when height_vh is specified`() {
        // Given
        val modalProps =
            JSONObject().apply {
                put("height_vh", 75)
            }
        val screenWidth = 1000f
        val screenHeight = 2000f

        // When
        val (_, height) = InAppMessageUtils.getViewDimensions(modalProps, screenWidth, screenHeight)

        // Then
        assertEquals(1500f, height)
    }

    @Test
    fun `getViewDimensions should return correct height when height_vw is specified`() {
        // Given
        val modalProps =
            JSONObject().apply {
                put("height_vw", 60)
            }
        val screenWidth = 1000f
        val screenHeight = 2000f

        // When
        val (_, height) = InAppMessageUtils.getViewDimensions(modalProps, screenWidth, screenHeight)

        // Then
        assertEquals(600f, height)
    }

    @Test
    fun `getKSTCalendarDateString should return current date when offset is 0`() {
        // Given
        val expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)

        // When
        val result = InAppMessageUtils.getKSTCalendarDateString(0)

        // Then
        assertEquals(expectedDate, result)
    }

    @Test
    fun `getKSTCalendarDateString should return future date when offset is positive`() {
        // Given
        val offset = 3
        val expectedDate =
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
        val expectedDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(expectedDate.time)

        // When
        val result = InAppMessageUtils.getKSTCalendarDateString(offset)

        // Then
        assertEquals(expectedDateString, result)
    }

    @Test
    fun `getKSTCalendarDateString should return past date when offset is negative`() {
        // Given
        val offset = -2
        val expectedDate =
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
        val expectedDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(expectedDate.time)

        // When
        val result = InAppMessageUtils.getKSTCalendarDateString(offset)

        // Then
        assertEquals(expectedDateString, result)
    }
}
