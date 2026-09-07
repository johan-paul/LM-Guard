/// Backend base URL, overridable at build/run time so it is never hardcoded
/// per environment:
///
///   flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080/api
///
/// Defaults to the Android-emulator loopback alias. A physical handset needs
/// the machine's LAN address instead.
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://lm-guard-backend.onrender.com/api',
  );
}
