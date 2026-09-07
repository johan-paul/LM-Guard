import 'package:image_picker/image_picker.dart';

import '../models/enums.dart';
import '../models/evidence.dart';

/// Captures evidence for an inspection. The interface is deliberately narrow
/// so screens never depend on how a photo actually gets taken - see
/// [CameraEvidenceService] for the real implementation and
/// [MockEvidenceService] for the placeholder one used in tests.
abstract class EvidenceService {
  Future<EvidenceItem?> capture({required String label, EvidenceKind kind});

  Future<EvidenceItem?> pickFromGallery({required String label});
}

/// Real device camera/gallery capture, via `image_picker`.
///
/// Returns `null` when the officer cancels the picker - not an error, just
/// "nothing captured". `filePath` is the on-device path `Image.file` already
/// knows how to render (see `core/widgets/evidence_thumb.dart`); uploading it
/// to the backend is a separate step the caller (`DraftController`) drives
/// once an inspection id exists to attach it to.
class CameraEvidenceService implements EvidenceService {
  CameraEvidenceService({ImagePicker? picker}) : _picker = picker ?? ImagePicker();

  final ImagePicker _picker;
  int _counter = 0;

  String _nextId() {
    _counter++;
    return 'EV-LOCAL-${DateTime.now().millisecondsSinceEpoch}-$_counter';
  }

  @override
  Future<EvidenceItem?> capture({
    required String label,
    EvidenceKind kind = EvidenceKind.photo,
  }) async {
    final XFile? shot = await _picker.pickImage(source: ImageSource.camera, imageQuality: 85);
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
    final XFile? shot = await _picker.pickImage(source: ImageSource.gallery, imageQuality: 85);
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
