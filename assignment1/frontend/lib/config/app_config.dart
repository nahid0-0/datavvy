/// Build-time configuration.
///
/// Override with: flutter run -d chrome --dart-define=API_BASE_URL=http://host:port
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  static const Duration requestTimeout = Duration(seconds: 60);
}
