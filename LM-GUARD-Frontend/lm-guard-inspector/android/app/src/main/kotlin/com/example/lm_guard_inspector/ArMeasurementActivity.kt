package com.example.lm_guard_inspector

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
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
 * Measures the real-world distance (in mm) between two points the inspector marks with a
 * fixed on-screen crosshair against a live camera view, using ARCore's depth/plane hit-testing
 * - the AR-equivalent of holding a ruler up to the package, for Rule 7's numeral-height
 * requirement (see `DeterministicRuleEngineService`'s `NUMERIC_BAND` case on the Java side,
 * which this feeds).
 *
 * Interaction is aim-and-press, not tap-anywhere: the inspector points the phone so the fixed
 * center crosshair sits on the target, then presses "Mark point" - a raw tap-anywhere design
 * (v1's first pass) let a finger's imprecise touch coordinate register a hit-test on a
 * completely different surface than intended (confirmed in testing: a stray tap produced a
 * physically nonsensical 750mm reading). Aiming with the whole phone is far more precise than
 * tapping a small on-screen target with a fingertip, and matches how mainstream AR measuring
 * apps (Google's/Apple's own "Measure") already do this.
 *
 * After both points are marked, the computed distance is shown with "Use this" / "Retry"
 * before anything is returned - a human sanity check before an implausible number ever reaches
 * a legal compliance finding.
 */
class ArMeasurementActivity : Activity(), GLSurfaceView.Renderer {

    private enum class Stage { AIMING_FIRST, AIMING_SECOND, REVIEWING }

    private var session: Session? = null
    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusText: TextView
    private lateinit var crosshair: CrosshairView
    private lateinit var markPointButton: Button
    private lateinit var resultPanel: LinearLayout
    private lateinit var resultText: TextView
    private val backgroundRenderer = BackgroundRenderer()
    private lateinit var displayRotationHelper: DisplayRotationHelper

    private var installRequested = false
    private var finished = false
    private var stage = Stage.AIMING_FIRST

    // World-space translations (metres) of the two marked points.
    private val hitPoints = mutableListOf<FloatArray>()
    private var lastHitUsedDepth = false
    private var pendingDistanceMm: Double? = null
    private var pendingTrackingQuality: String? = null

    /** Written on the GL render thread every frame, read on the main thread when "Mark point"
     * is pressed - a single-reference read/write, so a plain @Volatile is enough; the worst case
     * of a one-frame-stale value is harmless at 60fps. */
    @Volatile private var centerHit: HitResult? = null
    @Volatile private var centerHitUsedDepth = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ArCoreApk.getInstance().checkAvailability(this).isSupported) {
            finishWithResult(unsupported = true)
            return
        }

        displayRotationHelper = DisplayRotationHelper(this)
        buildUi()
        updateStageUi()
    }

    // ------------------------------------------------------------------
    // UI construction - plain Views, no extra assets/dependencies
    // ------------------------------------------------------------------

    private fun buildUi() {
        surfaceView = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(this@ArMeasurementActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        crosshair = CrosshairView(this)

        statusText = TextView(this).apply {
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(28, 16, 28, 16)
            gravity = Gravity.CENTER
        }

        markPointButton = Button(this).apply {
            text = "Mark point"
            isEnabled = false
            setOnClickListener { onMarkPoint() }
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener { finishWithResult(cancelled = true) }
        }

        val retryButton = Button(this).apply {
            text = "Retry"
            setOnClickListener { resetToFirstPoint() }
        }

        val useButton = Button(this).apply {
            text = "Use this measurement"
            setOnClickListener {
                val distance = pendingDistanceMm
                if (distance != null) {
                    finishWithResult(distanceMm = distance, trackingQuality = pendingTrackingQuality)
                }
            }
        }

        resultText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(32, 32, 32, 32)
            visibility = View.GONE
            addView(resultText)
            addView(LinearLayout(this@ArMeasurementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(retryButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(useButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 24, 24, 40)
            addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(markPointButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        }

        val root = FrameLayout(this).apply {
            addView(surfaceView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(crosshair, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(statusText, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            addView(bottomBar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM))
            addView(resultPanel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM))
        }
        setContentView(root)
    }

    private fun updateStageUi() {
        when (stage) {
            Stage.AIMING_FIRST -> {
                statusText.text = "Aim the crosshair at the TOP of the smallest printed numeral, then tap Mark point."
                resultPanel.visibility = View.GONE
                markPointButton.visibility = View.VISIBLE
            }
            Stage.AIMING_SECOND -> {
                statusText.text = "Now aim at the BOTTOM of the same numeral, then tap Mark point."
                resultPanel.visibility = View.GONE
                markPointButton.visibility = View.VISIBLE
            }
            Stage.REVIEWING -> {
                statusText.text = "Review the measurement below."
                markPointButton.visibility = View.GONE
                resultPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun onMarkPoint() {
        val hit = centerHit ?: return
        hitPoints.add(hit.hitPose.translation)
        lastHitUsedDepth = centerHitUsedDepth

        if (stage == Stage.AIMING_FIRST) {
            stage = Stage.AIMING_SECOND
            updateStageUi()
            return
        }

        val a = hitPoints[0]
        val b = hitPoints[1]
        val dx = (a[0] - b[0]).toDouble()
        val dy = (a[1] - b[1]).toDouble()
        val dz = (a[2] - b[2]).toDouble()
        val distanceMm = sqrt(dx * dx + dy * dy + dz * dz) * 1000.0

        pendingDistanceMm = distanceMm
        pendingTrackingQuality = if (lastHitUsedDepth) "DEPTH" else "PLANE"
        resultText.text = "Measured height: %.1f mm".format(distanceMm)
        stage = Stage.REVIEWING
        updateStageUi()
    }

    private fun resetToFirstPoint() {
        hitPoints.clear()
        pendingDistanceMm = null
        pendingTrackingQuality = null
        stage = Stage.AIMING_FIRST
        updateStageUi()
    }

    // ------------------------------------------------------------------
    // Activity/session lifecycle
    // ------------------------------------------------------------------

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
    // GLSurfaceView.Renderer - camera passthrough + a live hit-test under the fixed crosshair
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

        if (stage == Stage.REVIEWING) {
            return // no need to keep hit-testing once both points are marked
        }
        updateCenterHit(frame)
    }

    /** Hit-tests the exact center of the viewport every frame - not where a finger last tapped -
     * so the crosshair the inspector sees is always what "Mark point" will actually use. */
    private fun updateCenterHit(frame: Frame) {
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            centerHit = null
            runOnUiThread { onHitAvailabilityChanged(false) }
            return
        }

        val centerX = surfaceView.width / 2f
        val centerY = surfaceView.height / 2f
        val hits = frame.hitTest(centerX, centerY)

        // Prefer ARCore's Depth API (more accurate, works on non-planar/textured surfaces like
        // printed text) over a plane hit, over anything else - ARCore's own documented
        // graceful-degradation order.
        val chosen = hits.firstOrNull { it.trackable is DepthPoint }
            ?: hits.firstOrNull { (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true }
            ?: hits.firstOrNull()

        centerHit = chosen
        centerHitUsedDepth = chosen?.trackable is DepthPoint
        runOnUiThread { onHitAvailabilityChanged(chosen != null) }
    }

    private fun onHitAvailabilityChanged(hasHit: Boolean) {
        crosshair.setActive(hasHit)
        markPointButton.isEnabled = hasHit
    }

    // ------------------------------------------------------------------
    // Result
    // ------------------------------------------------------------------

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

    /** A simple fixed crosshair drawn at the center of the view - green when a valid AR hit is
     * currently under it (so "Mark point" would succeed), gray otherwise. Plain Canvas drawing,
     * no image asset needed. */
    private class CrosshairView(context: Context) : View(context) {
        private val activePaint = Paint().apply { color = Color.parseColor("#4CD964"); strokeWidth = 4f; style = Paint.Style.STROKE }
        private val inactivePaint = Paint().apply { color = Color.parseColor("#BBBBBB"); strokeWidth = 4f; style = Paint.Style.STROKE }
        private var active = false

        fun setActive(value: Boolean) {
            if (active != value) {
                active = value
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val paint = if (active) activePaint else inactivePaint
            val cx = width / 2f
            val cy = height / 2f
            val radius = 28f
            val armLength = 14f
            canvas.drawCircle(cx, cy, radius, paint)
            canvas.drawLine(cx - radius - armLength, cy, cx - radius, cy, paint)
            canvas.drawLine(cx + radius, cy, cx + radius + armLength, cy, paint)
            canvas.drawLine(cx, cy - radius - armLength, cx, cy - radius, paint)
            canvas.drawLine(cx, cy + radius, cx, cy + radius + armLength, paint)
        }
    }

    companion object {
        const val EXTRA_RESULT = "result"
        const val EXTRA_DISTANCE_MM = "distanceMm"
        const val EXTRA_TRACKING_QUALITY = "trackingQuality"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 4102
    }
}
