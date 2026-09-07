import 'package:camera/camera.dart' show XFile;
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart' as picker_lib;

import '../../features/new_inspection/steps/widgets/web_camera_screen.dart';
import '../models/enums.dart';
import '../models/evidence.dart';

/// Captures evidence for an inspection. The interface is deliberately narrow
/// so screens never depend on how a photo actually gets taken - see
/// [CameraEvidenceService] for the real implementation and
/// [MockEvidenceService] for the placeholder one used in tests.
///
/// `capture` takes a [BuildContext] because on web it needs to push a live
/// camera screen (see [CameraEvidenceService]); implementations that don't
/// need one (like [MockEvidenceService]) simply ignore it.
abstract class EvidenceService {
  Future<EvidenceItem?> capture({
    required BuildContext context,
    required String label,
    EvidenceKind kind,
  });

  Future<EvidenceItem?> pickFromGallery({required String label});
}

/// Real device camera/gallery capture.
///
/// On native platforms (Android/iOS) `image_picker`'s camera source already
/// opens the real OS camera app, so it's used unchanged. On Flutter Web,
/// `image_picker`'s camera source is just a plain `<input type=file capture>`
/// - desktop browsers render that as an ordinary file-open dialog, never a
/// live camera - so web capture instead pushes [WebCameraScreen], which
/// drives `getUserMedia()` directly via the `camera` package for a genuine
/// in-page live preview.
///
/// Returns `null` when the officer cancels - not an error, just "nothing
/// captured". `filePath` is the on-device path `Image.file` already knows
/// how to render (see `core/widgets/evidence_thumb.dart`); uploading it to
/// the backend is a separate step the caller (`DraftController`) drives once
/// an inspection id exists to attach it to.
class CameraEvidenceService implements EvidenceService {
  CameraEvidenceService({picker_lib.ImagePicker? picker})
      : _picker = picker ?? picker_lib.ImagePicker();

  final picker_lib.ImagePicker _picker;
  int _counter = 0;

  String _nextId() {
    _counter++;
    return 'EV-LOCAL-${DateTime.now().millisecondsSinceEpoch}-$_counter';
  }

  @override
  Future<EvidenceItem?> capture({
    required BuildContext context,
    required String label,
    EvidenceKind kind = EvidenceKind.photo,
  }) async {
    final XFile? shot = kIsWeb
        ? await Navigator.of(context).push<XFile>(
            MaterialPageRoute<XFile>(builder: (_) => WebCameraScreen(label: label)),
          )
        : await _picker.pickImage(source: picker_lib.ImageSource.camera, imageQuality: 85);
    if (shot == null) return null;
    return EvidenceItem(
      id: _nextId(),
      kind: kind,
      label: label,
      capturedAt: DateTime.now(),
      filePath: shot.path,
    );
  }

  @override
  Future<EvidenceItem?> pickFromGallery({required String label}) async {
    final XFile? shot =
        await _picker.pickImage(source: picker_lib.ImageSource.gallery, imageQuality: 85);
    if (shot == null) return null;
    return EvidenceItem(
      id: _nextId(),
      kind: EvidenceKind.photo,
      label: label,
      capturedAt: DateTime.now(),
      filePath: shot.path,
    );
  }
}

/// Placeholder implementation - no real camera, no real file. Kept for
/// environments without camera access (widget tests, desktop dev runs).
class MockEvidenceService implements EvidenceService {
  MockEvidenceService();

  int _counter = 500;

  String _nextId() {
    _counter++;
    return 'EV-${_counter.toString().padLeft(4, '0')}';
  }

  @override
  Future<EvidenceItem?> capture({
    required BuildContext context,
    required String label,
    EvidenceKind kind = EvidenceKind.photo,
  }) async {
    await Future<void>.delayed(const Duration(milliseconds: 450));
    return EvidenceItem(
      id: _nextId(),
      kind: kind,
      label: label,
      capturedAt: DateTime.now(),
      paletteSeed: _counter % 9,
    );
  }

  @override
  Future<EvidenceItem?> pickFromGallery({required String label}) async {
    await Future<void>.delayed(const Duration(milliseconds: 350));
    return EvidenceItem(
      id: _nextId(),
      kind: EvidenceKind.photo,
      label: label,
      capturedAt: DateTime.now(),
      paletteSeed: _counter % 9,
    );
  }
}
