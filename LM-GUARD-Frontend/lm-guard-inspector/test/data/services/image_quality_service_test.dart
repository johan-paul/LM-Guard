import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:lm_guard_inspector/data/services/image_quality_service.dart';

/// Writes `image` as a PNG to a fresh temp file and returns its path -
/// [ImageQualityService.assess] reads from a file path (mirroring what
/// `image_picker`/the camera package hand back), not raw bytes, so tests
/// need a real file on disk the same way production does.
Future<String> _writeTempImage(img.Image image, String name) async {
  final Directory dir = await Directory.systemTemp.createTemp('image_quality_test');
  final File file = File('${dir.path}/$name.png');
  await file.writeAsBytes(img.encodePng(image));
  return file.path;
}

img.Image _sharpCheckerboard({int size = 500, int cell = 10}) {
  final img.Image image = img.Image(width: size, height: size);
  for (int y = 0; y < size; y++) {
    for (int x = 0; x < size; x++) {
      final bool light = ((x ~/ cell) + (y ~/ cell)).isEven;
      final int v = light ? 255 : 0;
      image.setPixelRgb(x, y, v, v, v);
    }
  }
  return image;
}

img.Image _flat({int size = 500, int value = 220}) {
  final img.Image image = img.Image(width: size, height: size);
  img.fill(image, color: img.ColorRgb8(value, value, value));
  return image;
}

void main() {
  final ImageQualityService service = const ImageQualityService();

  test('a sharp, well-lit, normal-sized image is usable with no issues', () async {
    final String path = await _writeTempImage(_sharpCheckerboard(), 'sharp');

    final ImageQualityResult result = await service.assess(path);

    expect(result.usable, isTrue);
    expect(result.issues, isEmpty);
  });

  test('a small image is flagged as low resolution', () async {
    final String path = await _writeTempImage(_sharpCheckerboard(size: 120, cell: 4), 'small');

    final ImageQualityResult result = await service.assess(path);

    expect(result.usable, isFalse);
    expect(result.issues, contains(ImageQualityIssue.lowResolution));
  });

  test('a uniformly dark image is flagged as low light', () async {
    final String path = await _writeTempImage(_flat(value: 10), 'dark');

    final ImageQualityResult result = await service.assess(path);

    expect(result.usable, isFalse);
    expect(result.issues, contains(ImageQualityIssue.lowLight));
  });

  test('a flat image with no edges at all is flagged as blurred', () async {
    // Bright enough to not also trip low-light, so this isolates the blur signal.
    final String path = await _writeTempImage(_flat(value: 200), 'flat');

    final ImageQualityResult result = await service.assess(path);

    expect(result.usable, isFalse);
    expect(result.issues, contains(ImageQualityIssue.blur));
  });

  test('an undecodable file degrades to usable rather than blocking capture', () async {
    final Directory dir = await Directory.systemTemp.createTemp('image_quality_test');
    final File file = File('${dir.path}/not_an_image.png');
    await file.writeAsBytes(<int>[1, 2, 3, 4, 5]);

    final ImageQualityResult result = await service.assess(file.path);

    expect(result.usable, isTrue);
    expect(result.issues, isEmpty);
  });

  test('reason gives one clear instruction even when multiple issues fire', () {
    const ImageQualityResult result = ImageQualityResult(
      usable: false,
      issues: <String>[ImageQualityIssue.blur, ImageQualityIssue.lowLight],
    );

    expect(result.reason, contains('blurred'));
  });
}
