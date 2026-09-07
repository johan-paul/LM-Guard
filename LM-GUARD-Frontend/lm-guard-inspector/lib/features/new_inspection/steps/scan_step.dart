import 'dart:io';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/panels.dart';
import '../../../state/draft_controller.dart';
import 'widgets/ai_evaluation_panel.dart';

/// Step 3 — photograph the package. This is the only input the AI pipeline
/// needs: capturing (or retaking) a photo immediately uploads it and starts
/// analysis (see [DraftController.capturePackagePhoto]) - the inspector
/// takes the picture, the AI extracts the printed declarations and checks
/// them against the ruleset. Nothing here is typed in by hand.
class ScanStep extends StatelessWidget {
  const ScanStep({super.key});

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: <Widget>[
        const InfoBanner(
          message:
              'Photograph the principal display panel - the side carrying the '
              'MRP, net quantity and manufacturer declarations. The AI reads '
              'this photo; nothing below is entered by hand.',
          icon: Icons.document_scanner_outlined,
        ),
        const SizedBox(height: 16),
        _PhotoPreview(path: draft.packagePhotoPath),
        if (draft.captureError != null) ...<Widget>[
          const SizedBox(height: 12),
          InfoBanner(
            tone: BannerTone.danger,
            icon: Icons.error_outline,
            message: draft.captureError!,
          ),
        ],
        const SizedBox(height: 14),
        Row(
          children: <Widget>[
            Expanded(
              child: PrimaryButton(
                label: draft.packagePhotoCaptured ? 'Retake photo' : 'Take photo',
                icon: Icons.photo_camera_outlined,
                busy: draft.busy,
                onPressed: () => draft.capturePackagePhoto(context),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: SecondaryButton(
                label: 'Upload',
                icon: Icons.upload_outlined,
                onPressed: draft.busy
                    ? null
                    : () => draft.capturePackagePhoto(context, fromGallery: true),
              ),
            ),
          ],
        ),
        if (draft.packagePhotoCaptured) ...<Widget>[
          const SizedBox(height: 18),
          AiEvaluationPanel(draft: draft),
        ],
      ],
    );
  }
}

class _PhotoPreview extends StatelessWidget {
  const _PhotoPreview({required this.path});

  final String? path;

  @override
  Widget build(BuildContext context) {
    if (path == null) {
      return AspectRatio(
        aspectRatio: 4 / 3,
        child: Container(
          decoration: BoxDecoration(
            color: AppColors.navy,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Icon(
                  Icons.photo_camera_outlined,
                  size: 34,
                  color: Colors.white.withOpacity(0.75),
                ),
                const SizedBox(height: 8),
                Text(
                  'No photo captured yet',
                  style: TextStyle(fontSize: 12.5, color: Colors.white.withOpacity(0.75)),
                ),
              ],
            ),
          ),
        ),
      );
    }

    // On web, image_picker's XFile.path is a blob: URL with no filesystem
    // behind it - dart:io's File can't read it. Image.network handles blob:
    // URLs correctly there (the browser resolves them natively); everywhere
    // else path is a real file, which Image.file needs.
    final Widget image = kIsWeb
        ? Image.network(
            path!,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => _brokenImage(),
          )
        : Image.file(
            File(path!),
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => _brokenImage(),
          );

    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: AspectRatio(aspectRatio: 4 / 3, child: image),
    );
  }

  Widget _brokenImage() {
    return Container(
      color: AppColors.navy,
      alignment: Alignment.center,
      child: const Icon(Icons.broken_image_outlined, color: Colors.white54),
    );
  }
}
