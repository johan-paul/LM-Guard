import 'package:flutter/foundation.dart';

import '../data/repositories/auth_repository.dart';
import '../data/models/inspector.dart';

/// Holds the signed-in officer for the lifetime of the session.
class AuthController extends ChangeNotifier {
  AuthController(this._repository);

  final AuthRepository _repository;

  Inspector? _inspector;
  bool _busy = false;
  String? _error;

  Inspector? get inspector => _inspector;
  bool get isSignedIn => _inspector != null;
  bool get busy => _busy;
  String? get error => _error;

  Future<bool> signIn(String inspectorId, String password) async {
    _busy = true;
    _error = null;
    notifyListeners();

    try {
      _inspector = await _repository.signIn(
        inspectorId: inspectorId,
        password: password,
      );
      return true;
    } on AuthException catch (exception) {
      _error = exception.message;
      return false;
    } catch (_) {
      _error = 'Sign-in could not be completed. Try again.';
      return false;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> signOut() async {
    await _repository.signOut();
    _inspector = null;
    _error = null;
    notifyListeners();
  }

  void clearError() {
    if (_error == null) return;
    _error = null;
    notifyListeners();
  }
}
