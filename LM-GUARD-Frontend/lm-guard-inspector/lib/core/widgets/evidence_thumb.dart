import 'dart:io';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import '../../data/models/enums.dart';
import '../../data/models/evidence.dart';
import '../theme/app_colors.dart';

/// Preview tile for a captured evidence item.
///
/// Renders the real photograph: the on-device file ([EvidenceItem.filePath])
/// when there is one - immediately after capture, before an upload completes -
/// otherwise the backend-hosted copy ([EvidenceItem.imageUrl]), which is the
/// only thing available once a photo was uploaded or loaded back from a
/// previously-saved inspection. Only mock evidence (neither set) falls back
/// to the generated placeholder panel.
class EvidenceThumb extends StatelessWidget {
  const EvidenceThumb({super.key, required this.item, this.size = 62});

  final EvidenceItem item;
  final double size;

  static const List<Color> _tints = <Color>[
    Color(0xFF16324F),
    Color(0xFF1D4066),
    Color(0xFF25507E),
    Color(0xFF334D66),
    Color(0xFF1F3B57),
  ];

  @override
  Widget build(BuildContext context) {
    final Color tint = _tints[item.paletteSeed.abs() % _tints.length];

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: tint,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: _image() ?? _placeholder(),
    );
  }

  Widget? _image() {
    final String? filePath = item.filePath;
    if (filePath != null) {
      // On web, image_picker's XFile.path is a blob: URL - dart:io's File
      // can't read it, but Image.network resolves blob: URLs natively there.
      return kIsWeb
          ? Image.network(
              filePath,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => _placeholder(),
            )
          : Image.file(
              File(filePath),
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => _placeholder(),
            );
    }
    final String? imageUrl = item.imageUrl;
    if (imageUrl != null) {
      return Image.network(
        imageUrl,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _placeholder(),
        loadingBuilder: (context, child, progress) =>
            progress == null ? child : _placeholder(),
      );
    }
    return null;
  }

  Widget _placeholder() {
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        CustomPaint(painter: _PanelPainter()),
        Center(
          child: Icon(
            _iconFor(item.kind),
            size: size * 0.3,
            color: Colors.white.withOpacity(0.85),
          ),
        ),
      ],
    );
  }

  static IconData _iconFor(EvidenceKind kind) {
    switch (kind) {
      case EvidenceKind.photo:
        return Icons.photo_camera_outlined;
      case EvidenceKind.document:
        return Icons.description_outlined;
      case EvidenceKind.note:
        return Icons.sticky_note_2_outlined;
    }
  }
}

/// Draws a faint label-panel motif so mock evidence reads as a captured
/// package panel rather than an empty box.
class _PanelPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final Paint line = Paint()
      ..color = Colors.white.withOpacity(0.16)
      ..strokeWidth = 1;

    final double inset = size.width * 0.16;
    final Rect panel = Rect.fromLTWH(
      inset,
      inset,
      size.width - inset * 2,
      size.height - inset * 2,
    );
    canvas.drawRect(panel, line..style = PaintingStyle.stroke);

    final Paint bar = Paint()..color = Colors.white.withOpacity(0.13);
    final double barHeight = size.height * 0.045;
    for (int i = 0; i < 3; i++) {
      canvas.drawRect(
        Rect.fromLTWH(
          panel.left + panel.width * 0.12,
          panel.top + panel.height * (0.22 + i * 0.22),
          panel.width * (i == 2 ? 0.42 : 0.7),
          barHeight,
        ),
        bar,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _PanelPainter oldDelegate) => false;
}
