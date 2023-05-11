package tech.notifly.inapp

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.constraintlayout.widget.ConstraintLayout

class TouchInterceptorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    var onTouchOutsideWebView: (() -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        onTouchOutsideWebView?.let {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is WebView) {
                    val inWebView = isPointInWebView(ev.x, ev.y, child)
                    if (!inWebView) {
                        it()
                        return true
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun isPointInWebView(x: Float, y: Float, view: View): Boolean {
        return x > view.left && x < view.right && y > view.top && y < view.bottom
    }
}
