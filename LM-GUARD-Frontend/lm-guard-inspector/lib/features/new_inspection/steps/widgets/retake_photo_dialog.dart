import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../data/services/image_quality_service.dart';

/// Blocking prompt shown immediately after a captured photo fails the
/// client-side quality pre-check (see [ImageQualityService]) - the photo is
/// never uploaded in this case, so there is only one way forward: retake it.
/// Deliberately no "use it anyway" action; a photo the on-device heuristic
/// already flags as unreadable is not worth spending a full server round
/// trip on, and letting it through would only surface as a much less
/// actionable "inconclusive" verdict far later in the workflow.
Future<void> showRetakePhotoDialog(BuildContext context, ImageQualityResult quality) {
  return showDialog<void>(
    context: context,
    builder: (BuildContext context) => AlertDialog(
      backgroundColor: AppColors.surface,
      title: const Text('Photo quality too low', style: AppText.sectionTitle),
      content: Text(quality.reason, style: AppText.body),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Retake Photo'),
        ),
      ],
    ),
  );
}
