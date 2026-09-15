import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:http/http.dart' as http;
import 'package:image/image.dart' as img;

/// Machine-readable quality issue codes - deliberately the same vocabulary
/// `preprocessing.py::ImageQuality.issue_codes()` uses server-side, so a
/// warning surfaced from either layer reads consistently.
class ImageQualityIssue {
  static const String blur = 'BLUR';
  static const String lowLight = 'LOW_LIGHT';
  static const String lowResolution = 'LOW_RESOLUTION';
}

/// Result of a client-side image-quality pre-check.
class ImageQualityResult {
  const ImageQualityResult({required this.usable, required this.issues});

  final bool usable;
  final List<String> issues;

  /// A short, officer-facing reason to show alongside the retake prompt.
  /// Deliberately not a 1:1 dump of every issue code - a photo blocked for
  /// multiple reasons still gets one clear instruction, not a checklist.
  String get reason {
    if (issues.contains(ImageQualityIssue.blur)) {
      return 'The photo looks blurred. Hold the camera steady and make sure '
          'the text is in focus before capturing.';
    }
    if (issues.contains(ImageQualityIssue.lowLight)) {
      return 'The photo is too dark to read reliably. Retake it in better '
          'light, or turn on the flash.';
    }
    if (issues.contains(ImageQualityIssue.lowResolution)) {
      return 'The photo is too small/low-resolution to read the printed '
          'text reliably. Move closer to the principal display panel.';
    }
    return 'This photo may be too poor quality to analyse reliably.';
  }
}

/// Cheap client-side counterpart to the server's `assess_quality()`
/// (`ai-service/app/preprocessing.py`) - deliberately not a port of the same
/// algorithm (Laplacian variance, HSV glare detection, etc. need OpenCV,
/// which has no Flutter-native equivalent worth adding as a dependency for
/// this). This is a coarser, faster heuristic meant to catch the *obviously*
/// unusable photo (a thumb over the lens, a pitch-dark room, a heavily
/// motion-blurred shot) immediately after capture, before the round trip to
/// upload + full server-side analysis - the server-side quality gate (see
/// `AiEvaluationPanel`'s warnings, sourced from the same pipeline) remains
/// the authoritative backstop for anything this pass lets through.
class ImageQualityService {
  const ImageQualityService();

  static const int _sampleDimension = 300;
  static const int _minSourceDimension = 400;
  static const double _darkMeanLumaThreshold = 40; // 0..255
  static const double _blurEdgeVarianceThreshold = 4.0;

  Future<ImageQualityResult> assess(String filePath) async {
    try {
      final Uint8List bytes = await _readBytes(filePath);
      final img.Image? decoded = img.decodeImage(bytes);
      if (decoded == null) {
        // Undecodable is the server's problem to report clearly; never block
        // a capture client-side on a decode failure this check cannot
        // diagnose.
        return const ImageQualityResult(usable: true, issues: <String>[]);
      }

      final List<String> issues = <String>[];
      if (decoded.width < _minSourceDimension || decoded.height < _minSourceDimension) {
        issues.add(ImageQualityIssue.lowResolution);
      }

      final img.Image sample = img.copyResize(
        decoded,
        width: decoded.width >= decoded.height ? _sampleDimension : null,
        height: decoded.height > decoded.width ? _sampleDimension : null,
      );
      final img.Image gray = img.grayscale(sample);

      final double meanLuma = _meanLuma(gray);
      if (meanLuma < _darkMeanLumaThreshold) {
        issues.add(ImageQualityIssue.lowLight);
      }

      final double edgeVariance = _edgeVariance(gray);
      if (edgeVariance < _blurEdgeVarianceThreshold) {
        issues.add(ImageQualityIssue.blur);
      }

      return ImageQualityResult(usable: issues.isEmpty, issues: issues);
    } catch (_) {
      // Never block a capture because THIS heuristic itself failed (corrupt
      // read, unsupported codec this decoder doesn't handle, etc.) - that is
      // exactly what the server-side pipeline already degrades gracefully
      // on, and is a strictly better place for such a failure to surface.
      return const ImageQualityResult(usable: true, issues: <String>[]);
    }
  }

  Future<Uint8List> _readBytes(String path) async {
    // Mirrors `_PhotoPreview`'s own kIsWeb split (scan_step.dart): on web,
    // image_picker/the camera package hand back a blob: URL with no
    // filesystem behind it, which only an HTTP-style fetch (the browser
    // resolves blob: URLs natively) can read; everywhere else it's a real
    // file path.
    if (kIsWeb) {
      final http.Response response = await http.get(Uri.parse(path));
      return response.bodyBytes;
    }
    return File(path).readAsBytes();
  }

  double _meanLuma(img.Image gray) {
    double sum = 0;
    final int count = gray.width * gray.height;
    for (final img.Pixel pixel in gray) {
      sum += pixel.r; // grayscale: r/g/b are equal, luma is just the one channel
    }
    return count == 0 ? 0 : sum / count;
  }

  /// A cheap blur proxy: variance of the horizontal gradient magnitude.
  /// A sharp, in-focus photo of printed text has strong, varied edges; a
  /// blurred one is comparatively flat everywhere. Not a Laplacian (no
  /// convolution kernel support needed) but the same underlying intuition
  /// `preprocessing.py::assess_quality`'s Laplacian-variance check uses -
  /// "how much does local intensity vary", cheap enough to run on a
  /// downsampled image on every capture without a native dependency.
  double _edgeVariance(img.Image gray) {
    final int width = gray.width;
    final int height = gray.height;
    if (width < 2 || height < 2) return 0;

    final List<double> gradients = <double>[];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width - 1; x++) {
        final num a = gray.getPixel(x, y).r;
        final num b = gray.getPixel(x + 1, y).r;
        gradients.add((a - b).abs().toDouble());
      }
    }
    if (gradients.isEmpty) return 0;

    final double mean = gradients.reduce((double a, double b) => a + b) / gradients.length;
    final double variance = gradients
            .map((double g) => (g - mean) * (g - mean))
            .reduce((double a, double b) => a + b) /
        gradients.length;
    return variance;
  }
}
