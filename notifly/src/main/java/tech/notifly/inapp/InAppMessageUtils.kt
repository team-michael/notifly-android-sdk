package tech.notifly.inapp

import android.app.Activity
import android.graphics.Point
import android.os.Build
import android.view.WindowInsets
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object InAppMessageUtils {
    fun getViewDimensions(
        modalProps: JSONObject?,
        screenWidth: Float,
        screenHeight: Float,
    ): Pair<Float, Float> {
        val viewWidth: Float =
            when {
                modalProps == null -> screenWidth
                modalProps.has("width") -> modalProps.getInt("width").toFloat()
                modalProps.has("width_vw") -> screenWidth * (modalProps.getInt("width_vw") / 100f)
                modalProps.has("width_vh") -> screenHeight * (modalProps.getInt("width_vh") / 100f)
                else -> screenWidth
            }

        val viewHeight: Float =
            when {
                modalProps == null -> screenHeight
                modalProps.has("height") -> modalProps.getInt("height").toFloat()
                modalProps.has("height_vh") -> screenHeight * (modalProps.getInt("height_vh") / 100f)
                modalProps.has("height_vw") -> screenWidth * (modalProps.getInt("height_vw") / 100f)
                else -> screenHeight
            }

        val minWidth = getNullableIntProperty(modalProps, "min_width")
        val maxWidth = getNullableIntProperty(modalProps, "max_width")
        val minHeight = getNullableIntProperty(modalProps, "min_height")
        val maxHeight = getNullableIntProperty(modalProps, "max_height")

        return Pair(
            getViewDimensionWithLimits(viewWidth, minWidth, maxWidth),
            getViewDimensionWithLimits(viewHeight, minHeight, maxHeight),
        )
    }

    private fun getNullableIntProperty(
        props: JSONObject?,
        name: String,
    ): Int? =
        props?.let {
            if (it.has(name)) it.getInt(name) else null
        }

    private fun getViewDimensionWithLimits(
        dimension: Float,
        min: Int?,
        max: Int?,
    ): Float =
        when {
            min != null && dimension < min -> min.toFloat()
            max != null && dimension > max -> max.toFloat()
            else -> dimension
        }

    @Suppress("DEPRECATION")
    fun getScreenWidthAndHeight(
        activity: Activity,
        density: Float,
    ): Pair<Float, Float> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets =
                windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val screenWidthPx = windowMetrics.bounds.width() - insets.left - insets.right
            val screenHeightPx = windowMetrics.bounds.height() - insets.top - insets.bottom
            val screenWidthDp = (screenWidthPx / density)
            val screenHeightDp = (screenHeightPx / density)
            Pair(screenWidthDp, screenHeightDp)
        } else {
            val screenSize = Point()
            activity.windowManager.defaultDisplay.getSize(screenSize)
            val screenWidthDp = (screenSize.x / density)
            val screenHeightDp = (screenSize.y / density)
            Pair(screenWidthDp, screenHeightDp)
        }

    fun getKSTCalendarDateString(daysOffset: Int = 0): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysOffset)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
}
