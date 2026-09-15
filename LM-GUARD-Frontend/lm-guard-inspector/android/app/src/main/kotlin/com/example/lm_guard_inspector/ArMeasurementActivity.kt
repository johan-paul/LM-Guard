package com.example.lm_guard_inspector

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * Measures the real-world distance (in mm) between two points the inspector taps on a live
 * camera view, using ARCore's depth/plane hit-testing - the AR-equivalent of holding a ruler up
 * to the package, for Rule 7's numeral-height requirement (see
 * `DeterministicRuleEngineService`'s `NUMERIC_BAND` case on the Java side, which this feeds).
 *
 * v1 is inspector-guided (tap top, then bottom, of one representative numeral) rather than
 * automatic whole-label measurement - see the plan for why. No 3D scene/object rendering beyond
 * the camera passthrough itself (see [BackgroundRenderer]): this is a measurement tool, not an
 * AR visualization, and the actual value that matters is the computed distance, not a polished
 * on-screen overlay.
 *
 * Launched via `startActivityForResult` from [MainActivity]'s MethodChannel handler; returns one
 * of three outcomes via the result [Intent] (see [EXTRA_RESULT]): `"success"` with
 * [EXTRA_DISTANCE_MM] and [EXTRA_TRACKING_QUALITY], `"unsupported"` (no ARCore on this device -
 * never a silently wrong number), or `"cancelled"`.
 */
class ArMeasurementActivity : Activity(), GLSurfaceView.Renderer {

    private var session: Session? = null
    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusText: TextView
    private val backgroundRenderer = BackgroundRenderer()
    private lateinit var displayRotationHelper: DisplayRotationHelper

    private var installRequested = false
    private var finished = false

    // World-space translations (metres) of the confirmed tap points, in order.
    private val hitPoints = mutableListOf<FloatArray>()
    @Volatile private var pendingTap: PointF? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ArCoreApk.getInstance().checkAvailability(this).isSupported) {
            finishWithResult(unsupported = true)
            return
        }

        displayRotationHelper = DisplayRotationHelper(this)

        statusText = TextView(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 48, 32, 32)
            text = "Tap the TOP of the smallest printed numeral in the declaration."
        }

        surfaceView = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(this@ArMeasurementActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    pendingTap = PointF(event.x, event.y)
                }
                true
            }
        }

        val root = FrameLayout(this).apply {
            addView(surfaceView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(statusText, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (finished) return

        if (session == null && !tryCreateSession()) {
            return // tryCreateSession already triggered an install/permission request, or finished us.
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            runOnUiThread { statusText.text = "Camera not available - close other camera apps and reopen this screen." }
            return
        }
        surfaceView.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            displayRotationHelper.onPause()
            surfaceView.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    /** @return true if a [Session] now exists and `onResume` should continue; false if an
     * ARCore-install request or a camera-permission request was issued instead (both resume
     * this activity's lifecycle again on their own once resolved), or the device turned out to
     * be unsupported (in which case this already called [finishWithResult]). */
    private fun tryCreateSession(): Boolean {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {}
            }

            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                return false
            }

            val newSession = Session(this)
            val config = Config(newSession)
            if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
            config.focusMode = Config.FocusMode.AUTO
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            newSession.configure(config)
            session = newSession
            return true
        } catch (e: UnavailableArcoreNotInstalledException) {
            finishWithResult(unsupported = true); return false
        } catch (e: UnavailableUserDeclinedInstallationException) {
            finishWithResult(unsupported = true); return false
        } catch (e: UnavailableApkTooOldException) {
            finishWithResult(unsupported = true); return false
        } catch (e: UnavailableSdkTooOldException) {
            finishWithResult(unsupported = true); return false
        } catch (e: UnavailableDeviceNotCompatibleException) {
            finishWithResult(unsupported = true); return false
        } catch (e: Exception) {
            // Any other session-creation failure: never guess, never crash the app - just
            // report unsupported the same as a hard ARCore incompatibility.
            finishWithResult(unsupported = true); return false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onResume()
            } else {
                finishWithResult(unsupported = true)
            }
        }
    }

    override fun onBackPressed() {
        finishWithResult(cancelled = true)
    }

    // ------------------------------------------------------------------
    // GLSurfaceView.Renderer - camera passthrough + driving the hit-test on a confirmed tap
    // ------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread(this)
        session?.setCameraTextureName(backgroundRenderer.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val currentSession = session ?: return

        displayRotationHelper.updateSessionIfNeeded(currentSession)
        currentSession.setCameraTextureName(backgroundRenderer.textureId)

        val frame = try {
            currentSession.update()
        } catch (e: CameraNotAvailableException) {
            return
        }
        backgroundRenderer.draw(frame)

        val tap = pendingTap
        if (tap != null) {
            pendingTap = null
            handleTap(frame, tap.x, tap.y)
        }
    }

    // ------------------------------------------------------------------
    // Measurement logic - the part with real legal/measurement consequence
    // ------------------------------------------------------------------

    private fun handleTap(frame: Frame, x: Float, y: Float) {
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            runOnUiThread { statusText.text = "Hold the camera steady - still finding the scene." }
            return
        }

        val hits = frame.hitTest(x, y)
        // Prefer ARCore's Depth API (more accurate, works on non-planar/textured surfaces like
        // printed text) over a plane hit, over anything else - device support for each varies,
        // this is ARCore's own documented graceful-degradation order.
        val chosen = hits.firstOrNull { it.trackable is DepthPoint }
            ?: hits.firstOrNull { (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true }
            ?: hits.firstOrNull()

        if (chosen == null) {
            runOnUiThread { statusText.text = "No surface detected there - tap directly on the printed text." }
            return
        }

        hitPoints.add(chosen.hitPose.translation)
        val usedDepth = chosen.trackable is DepthPoint

        if (hitPoints.size == 1) {
            runOnUiThread { statusText.text = "Now tap the BOTTOM of the same numeral." }
            return
        }

        val a = hitPoints[0]
        val b = hitPoints[1]
        val dx = (a[0] - b[0]).toDouble()
        val dy = (a[1] - b[1]).toDouble()
        val dz = (a[2] - b[2]).toDouble()
        val distanceMm = sqrt(dx * dx + dy * dy + dz * dz) * 1000.0

        finishWithResult(distanceMm = distanceMm, trackingQuality = if (usedDepth) "DEPTH" else "PLANE")
    }

    private fun finishWithResult(
        distanceMm: Double? = null,
        trackingQuality: String? = null,
        unsupported: Boolean = false,
        cancelled: Boolean = false,
    ) {
        if (finished) return
        finished = true

        val data = Intent()
        when {
            unsupported -> data.putExtra(EXTRA_RESULT, "unsupported")
            cancelled -> data.putExtra(EXTRA_RESULT, "cancelled")
            distanceMm != null -> {
                data.putExtra(EXTRA_RESULT, "success")
                data.putExtra(EXTRA_DISTANCE_MM, distanceMm)
                data.putExtra(EXTRA_TRACKING_QUALITY, trackingQuality)
            }
            else -> data.putExtra(EXTRA_RESULT, "cancelled")
        }
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_RESULT = "result"
        const val EXTRA_DISTANCE_MM = "distanceMm"
        const val EXTRA_TRACKING_QUALITY = "trackingQuality"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 4102
    }
}
