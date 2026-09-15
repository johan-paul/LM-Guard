import 'package:flutter/services.dart';

/// Outcome of one AR numeral-height measurement attempt.
enum ArMeasurementOutcome { success, unsupported, cancelled }

class ArMeasurementResult {
  const ArMeasurementResult._({
    required this.outcome,
    this.distanceMm,
    this.trackingQuality,
  });

  const ArMeasurementResult.success(double distanceMm, String? trackingQuality)
      : this._(outcome: ArMeasurementOutcome.success, distanceMm: distanceMm, trackingQuality: trackingQuality);

  const ArMeasurementResult.unsupported() : this._(outcome: ArMeasurementOutcome.unsupported);

  const ArMeasurementResult.cancelled() : this._(outcome: ArMeasurementOutcome.cancelled);

  final ArMeasurementOutcome outcome;

  /// Millimetres between the two tapped points. Only set when [outcome] is
  /// [ArMeasurementOutcome.success].
  final double? distanceMm;

  /// `"DEPTH"` (ARCore's Depth API - more accurate) or `"PLANE"` (plane-hit-test fallback on a
  /// device without depth support). Only set on success; purely informational today, but kept
  /// distinct from the distance itself so a future confidence heuristic can use it without
  /// another native round trip.
  final String? trackingQuality;
}

/// Thin wrapper around the native (Android/ARCore only, see the plan for why) numeral-height
/// measurement screen - [MainActivity.kt]'s MethodChannel launches
/// `ArMeasurementActivity`, which owns the entire camera/tap/hit-test UX natively; this class
/// only shuttles the final result back into Dart.
///
/// There is currently no iOS implementation (no `ios/` platform folder in this repo, and
/// ARKit's LiDAR-based measurement would need to be built and tested on a Mac this project
/// doesn't have access to) - calling this on iOS/web/any other platform returns
/// [ArMeasurementOutcome.unsupported], the same as an Android device without ARCore, so callers
/// only ever need to handle one "not available" case rather than a platform-specific one.
class ArMeasurementService {
  const ArMeasurementService();

  static const MethodChannel _channel = MethodChannel('com.lmguard.inspector/ar_measurement');

  Future<ArMeasurementResult> measureNumeralHeightMm() async {
    try {
      final Map<Object?, Object?>? raw =
          await _channel.invokeMapMethod<Object?, Object?>('measureNumeralHeightMm');
      if (raw == null) return const ArMeasurementResult.cancelled();

      switch (raw['result'] as String?) {
        case 'success':
          final double? distanceMm = (raw['distanceMm'] as num?)?.toDouble();
          if (distanceMm == null) return const ArMeasurementResult.cancelled();
          return ArMeasurementResult.success(distanceMm, raw['trackingQuality'] as String?);
        case 'unsupported':
          return const ArMeasurementResult.unsupported();
        default:
          return const ArMeasurementResult.cancelled();
      }
    } on MissingPluginException {
      // No native handler at all - iOS, web, desktop, or an Android build that predates this
      // feature. Same "not available" outcome a real Android device without ARCore produces,
      // so callers have exactly one case to handle either way.
      return const ArMeasurementResult.unsupported();
    } on PlatformException {
      // A genuine native-side failure (e.g. "already in progress") - never surfaced as a
      // measurement, never guessed at; the officer just retries.
      return const ArMeasurementResult.cancelled();
    }
  }
}
