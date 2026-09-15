import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lm_guard_inspector/data/services/ar_measurement_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const MethodChannel channel = MethodChannel('com.lmguard.inspector/ar_measurement');
  final ArMeasurementService service = const ArMeasurementService();
  final List<MethodCall> calls = <MethodCall>[];

  void setHandler(Future<Object?> Function(MethodCall call) handler) {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall call) {
        calls.add(call);
        return handler(call);
      },
    );
  }

  tearDown(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('parses a successful measurement', () async {
    setHandler((_) async => <String, Object?>{
          'result': 'success',
          'distanceMm': 3.25,
          'trackingQuality': 'DEPTH',
        });

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.success);
    expect(result.distanceMm, 3.25);
    expect(result.trackingQuality, 'DEPTH');
    expect(calls, hasLength(1));
    expect(calls.single.method, 'measureNumeralHeightMm');
  });

  test('parses an integer distance without throwing (native side sends a Double, but be lenient)', () async {
    setHandler((_) async => <String, Object?>{
          'result': 'success',
          'distanceMm': 4,
          'trackingQuality': 'PLANE',
        });

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.success);
    expect(result.distanceMm, 4.0);
  });

  test('reports unsupported when the device/app declines', () async {
    setHandler((_) async => <String, Object?>{'result': 'unsupported'});

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.unsupported);
    expect(result.distanceMm, isNull);
  });

  test('reports cancelled when the officer backs out', () async {
    setHandler((_) async => <String, Object?>{'result': 'cancelled'});

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.cancelled);
  });

  test('reports cancelled on a null native result', () async {
    setHandler((_) async => null);

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.cancelled);
  });

  test('reports unsupported when no native handler exists at all (iOS/web/desktop)', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.unsupported);
  });

  test('reports cancelled, not a crash, on a native PlatformException', () async {
    setHandler((_) async => throw PlatformException(code: 'ALREADY_IN_PROGRESS', message: 'busy'));

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.cancelled);
  });

  test('reports cancelled on a success result with no distanceMm rather than guessing a value', () async {
    setHandler((_) async => <String, Object?>{'result': 'success'});

    final ArMeasurementResult result = await service.measureNumeralHeightMm();

    expect(result.outcome, ArMeasurementOutcome.cancelled);
  });
}
