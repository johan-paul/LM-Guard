package com.example.lm_guard_inspector

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import com.google.ar.core.Session

/**
 * Tracks device rotation/display-size changes and forwards them to the ARCore [Session] so its
 * projection matrix and camera-image transform stay correct - standard ARCore sample
 * boilerplate (unchanged across ARCore SDK versions), reused as-is rather than rewritten, since
 * getting this subtly wrong silently skews every pose/hit-test the measurement feature depends
 * on.
 */
class DisplayRotationHelper(private val activity: Activity) : DisplayManager.DisplayListener {

    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private val display: Display
    private val displayManager: DisplayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    init {
        @Suppress("DEPRECATION")
        display = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }

    fun onResume() {
        displayManager.registerDisplayListener(this, null)
    }

    fun onPause() {
        displayManager.unregisterDisplayListener(this)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            val displayRotation = display.rotation
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }
}
