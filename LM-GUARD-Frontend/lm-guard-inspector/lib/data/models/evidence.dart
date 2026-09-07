import 'enums.dart';

/// A captured item of evidence attached to an inspection.
///
/// [filePath] holds an on-device file path - set immediately after capture,
/// before the upload to the backend completes (or if it fails). [imageUrl]
/// holds the backend-hosted URL once the photo has been uploaded, or when the
/// item was loaded back from an inspection fetched from the server (a photo
/// captured in an earlier session has no on-device file to point to at all -
/// only the server copy). [EvidenceThumb] renders whichever is available,
/// preferring the local file since it needs no network round trip.
class EvidenceItem {
  const EvidenceItem({
    required this.id,
    required this.kind,
    required this.label,
    required this.capturedAt,
    this.description = '',
    this.filePath,
    this.imageUrl,
    this.paletteSeed = 0,
  });

  final String id;
  final EvidenceKind kind;
  final String label;
  final DateTime capturedAt;
  final String description;
  final String? filePath;
  final String? imageUrl;

  /// Chooses the placeholder rendering so mock evidence is visually distinct.
  final int paletteSeed;

  EvidenceItem copyWith({String? label, String? description}) {
    return EvidenceItem(
      id: id,
      kind: kind,
      label: label ?? this.label,
      capturedAt: capturedAt,
      description: description ?? this.description,
      filePath: filePath,
      imageUrl: imageUrl,
      paletteSeed: paletteSeed,
    );
  }

  factory EvidenceItem.fromJson(Map<String, dynamic> json) {
    return EvidenceItem(
      id: json['id'] as String,
      kind: EvidenceKind.values.firstWhere(
        (EvidenceKind value) => value.wireValue == json['kind'],
        orElse: () => EvidenceKind.photo,
      ),
      label: json['label'] as String? ?? '',
      capturedAt: DateTime.parse(json['capturedAt'] as String),
      description: json['description'] as String? ?? '',
      filePath: json['filePath'] as String?,
      imageUrl: json['imageUrl'] as String?,
      paletteSeed: json['paletteSeed'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'kind': kind.wireValue,
        'label': label,
        'capturedAt': capturedAt.toIso8601String(),
        'description': description,
        'filePath': filePath,
        'imageUrl': imageUrl,
        'paletteSeed': paletteSeed,
      };
}
