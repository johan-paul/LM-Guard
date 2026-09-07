import '../mock/mock_data.dart';
import '../models/inspector.dart';
import '../services/api_client.dart';

/// Thrown when sign-in fails. Screens render [message] directly.
class AuthException implements Exception {
  const AuthException(this.message);

  final String message;

  @override
  String toString() => message;
}

abstract class AuthRepository {
  Future<Inspector> signIn({required String inspectorId, required String password});

  Future<void> signOut();

  Future<void> changePassword({required String currentPassword, required String newPassword});
}

/// Prototype authentication against the seeded officer record.
///
/// Replace with a call to `POST /auth/login`, storing the returned JWT via
/// `ApiClient.setAuthToken`.
class MockAuthRepository implements AuthRepository {
  @override
  Future<Inspector> signIn({
    required String inspectorId,
    required String password,
  }) async {
    await Future<void>.delayed(const Duration(milliseconds: 700));

    final String id = inspectorId.trim();
    if (id.isEmpty || password.isEmpty) {
      throw const AuthException('Enter your inspector ID and password.');
    }

    final bool idMatches =
        id.toUpperCase() == MockData.demoInspectorId.toUpperCase() ||
            id.toLowerCase() == MockData.inspector.email.toLowerCase();

    if (!idMatches || password != MockData.demoPassword) {
      throw const AuthException(
        'Those credentials were not recognised. Check your inspector ID and password.',
      );
    }

    return MockData.inspector;
  }

  @override
  Future<void> signOut() async {
    await Future<void>.delayed(const Duration(milliseconds: 200));
  }

  @override
  Future<void> changePassword({required String currentPassword, required String newPassword}) async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}

/// Authenticates against the real backend (`POST /auth/login`).
///
/// The backend identifies users by email, not an "inspector ID" — the sign-in
/// screen's field is treated as the email address. The backend's `User`
/// record only carries `id, name, email, role, enabled, createdAt`; it has no
/// zone/designation/phone/office columns yet, so those [Inspector] fields are
/// left blank rather than invented. `role` is checked so a signed-in Admin
/// account is rejected here — the field application is for INSPECTOR
/// accounts only.
class ApiAuthRepository implements AuthRepository {
  ApiAuthRepository(this._client);

  final ApiClient _client;

  @override
  Future<Inspector> signIn({
    required String inspectorId,
    required String password,
  }) async {
    final String email = inspectorId.trim();
    if (email.isEmpty || password.isEmpty) {
      throw const AuthException('Enter your email and password.');
    }

    final Map<String, dynamic> data;
    try {
      data = await _client.post(
        ApiRoutes.login,
        <String, dynamic>{'email': email, 'password': password},
      ) as Map<String, dynamic>;
    } on ApiException catch (exception) {
      throw AuthException(exception.message);
    }

    final Map<String, dynamic> user = data['user'] as Map<String, dynamic>;
    if (user['role'] != 'INSPECTOR') {
      throw const AuthException(
        'This sign-in is for inspecting officers only. Administrators use the LM-GUARD web console.',
      );
    }

    _client.setAuthToken(data['token'] as String);

    return Inspector(
      id: user['id'] as String,
      name: user['name'] as String,
      designation: 'Inspecting Officer',
      zone: '',
      email: user['email'] as String? ?? email,
      phone: '',
      office: '',
      accountStatus: (user['enabled'] as bool? ?? true) ? 'Active' : 'Inactive',
    );
  }

  @override
  Future<void> signOut() async {
    _client.setAuthToken(null);
  }

  @override
  Future<void> changePassword({required String currentPassword, required String newPassword}) async {
    try {
      await _client.changePassword(currentPassword, newPassword);
    } on ApiException catch (exception) {
      throw AuthException(exception.message);
    }
  }
}
