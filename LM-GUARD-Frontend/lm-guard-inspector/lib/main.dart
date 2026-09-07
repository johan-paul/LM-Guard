import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  _lockToPortraitOnMobile();
  runApp(const LmGuardInspectorApp());
}

/// Field use: portrait only, so a one-handed grip never rotates the form.
///
/// Only attempted on touch platforms. A desktop browser cannot lock screen
/// orientation, and `setPreferredOrientations` returns a rejected future
/// there — which surfaces in the console as an uncaught async error on every
/// start-up. Guarding the call keeps the web console clean without changing
/// behaviour on a handset.
void _lockToPortraitOnMobile() {
  if (kIsWeb) return;
  if (defaultTargetPlatform != TargetPlatform.android &&
      defaultTargetPlatform != TargetPlatform.iOS) {
    return;
  }
  SystemChrome.setPreferredOrientations(<DeviceOrientation>[
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
}
