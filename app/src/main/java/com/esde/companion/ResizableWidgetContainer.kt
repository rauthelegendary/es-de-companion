package com.esde.companion

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout  // Changed from FrameLayout
import com.esde.companion.WidgetView

class ResizableWidgetContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
    var currentActiveWidget: WidgetView? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val active = currentActiveWidget ?: return super.onInterceptTouchEvent(ev)

        // If we have an active widget, check if we are touching its extended corners
        val childX = ev.x - active.left
        val childY = ev.y - active.top

        if (active.isTouchingExtendedCorner(childX, childY)) {
            return true // Intercept! We are resizing the buried widget.
        }

        // If the active widget is MOVING (or just selected and we want to drag its body), intercept.
        if (active.currentMode == WidgetMode.MOVING || active.currentMode == WidgetMode.SELECTED) {
            // Only intercept if the touch is actually inside the active widget's bounds
            if (ev.x >= active.left && ev.x <= active.right && ev.y >= active.top && ev.y <= active.bottom) {
                return true
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val active = currentActiveWidget ?: return super.onTouchEvent(event)

        // Manually route the touch to the active widget, bypassing Z-order
        val transformed = MotionEvent.obtain(event)
        transformed.setLocation(event.x - active.left, event.y - active.top)
        val handled = active.dispatchTouchEvent(transformed)
        transformed.recycle()

        return handled
    }
}