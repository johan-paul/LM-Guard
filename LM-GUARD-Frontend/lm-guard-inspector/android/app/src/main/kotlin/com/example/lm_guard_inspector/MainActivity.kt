package com.example.lm_guard_inspector

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Bridges to [ArMeasurementActivity] (Rule 7 numeral-height AR measurement) - the only native
 * platform-channel code in this app; every other native capability goes through a pure-Dart
 * plugin (`camera`, `image_picker`) instead. See the plan for why this one feature is different.
 */
class MainActivity : FlutterActivity() {

    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "measureNumeralHeightMm" -> {
                    if (pendingResult != null) {
                        // A previous measurement is still in flight - reject rather than silently
                        // dropping either caller's result.
                        result.error("ALREADY_IN_PROGRESS", "A measurement is already in progress", null)
                        return@setMethodCallHandler
                    }
                    pendingResult = result
                    startActivityForResult(Intent(this, ArMeasurementActivity::class.java), AR_MEASUREMENT_REQUEST_CODE)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AR_MEASUREMENT_REQUEST_CODE) return

        val result = pendingResult ?: return
        pendingResult = null

        if (resultCode != Activity.RESULT_OK || data == null) {
            result.success(mapOf("result" to "cancelled"))
            return
        }

        when (data.getStringExtra(ArMeasurementActivity.EXTRA_RESULT)) {
            "success" -> result.success(mapOf(
                "result" to "success",
                "distanceMm" to data.getDoubleExtra(ArMeasurementActivity.EXTRA_DISTANCE_MM, 0.0),
                "trackingQuality" to data.getStringExtra(ArMeasurementActivity.EXTRA_TRACKING_QUALITY),
            ))
            "unsupported" -> result.success(mapOf("result" to "unsupported"))
            else -> result.success(mapOf("result" to "cancelled"))
        }
    }

    companion object {
        private const val CHANNEL = "com.lmguard.inspector/ar_measurement"
        private const val AR_MEASUREMENT_REQUEST_CODE = 4103
    }
}
