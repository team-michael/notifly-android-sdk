package tech.notifly.inapp

import android.app.Activity
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import org.json.JSONObject

object InAppMessageUtils {

    fun getViewDimensions(
        modalProps: JSONObject?,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {

        val viewWidth: Float = when {
            modalProps == null -> screenWidth.toFloat()
            modalProps.has("width") -> modalProps.getInt("width").toFloat()
            modalProps.has("width_vw") -> screenWidth * (modalProps.getInt("width_vw") / 100f)
            modalProps.has("width_vh") -> screenHeight * (modalProps.getInt("width_vh") / 100f)
            else -> screenWidth.toFloat()
        }

        val viewHeight: Float = when {
            modalProps == null -> screenHeight.toFloat()
            modalProps.has("height") -> modalProps.getInt("height").toFloat()
            modalProps.has("height_vh") -> screenHeight * (modalProps.getInt("height_vh") / 100f)
            modalProps.has("height_vw") -> screenWidth * (modalProps.getInt("height_vw") / 100f)
            else -> screenHeight.toFloat()
        }

        val minWidth = getNullableIntProperty(modalProps, "min_width")
        val maxWidth = getNullableIntProperty(modalProps, "max_width")
        val minHeight = getNullableIntProperty(modalProps, "min_height")
        val maxHeight = getNullableIntProperty(modalProps, "max_height")

        return Pair(
            getViewDimensionWithLimits(viewWidth, minWidth, maxWidth),
            getViewDimensionWithLimits(viewHeight, minHeight, maxHeight)
        )
    }

    private fun getNullableIntProperty(props: JSONObject?, name: String): Int? {
        return props?.let {
            if (it.has(name)) it.getInt(name) else null
        }
    }

    private fun getViewDimensionWithLimits(dimension: Float, min: Int?, max: Int?): Float {
        return when {
            min != null && dimension < min -> min.toFloat()
            max != null && dimension > max -> max.toFloat()
            else -> dimension
        }
    }

    @Suppress("DEPRECATION")
    fun getScreenWidthAndHeight(activity: Activity, density: Float): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
    }

}
