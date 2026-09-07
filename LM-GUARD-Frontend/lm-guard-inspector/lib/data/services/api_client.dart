import 'dart:convert';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:http/http.dart' as http;

import '../../core/config/app_config.dart';

/// Thrown when the backend responds with `success: false`, or the request
/// fails outright (network error, non-JSON response).
class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode, this.errorCode});

  final String message;
  final int? statusCode;
  final String? errorCode;

  @override
  String toString() => message;
}

/// REST client for the LM-GUARD Spring Boot backend.
///
/// Every endpoint responds inside the shared `ApiResponse` envelope
/// (`{success, message, data, errorCode, timestamp}`); [_unwrap] strips that
/// envelope so repositories only ever see the payload in `data`.
class ApiClient {
  ApiClient({String? baseUrl, http.Client? client})
      : baseUrl = baseUrl ?? AppConfig.apiBaseUrl,
        _client = client ?? http.Client();

  final String baseUrl;
  final http.Client _client;

  String? _authToken;

  /// Bearer token issued by `POST /auth/login`.
  void setAuthToken(String? token) => _authToken = token;

  Map<String, String> get _headers => <String, String>{
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (_authToken != null) 'Authorization': 'Bearer $_authToken',
      };

  Uri _uri(String path) => Uri.parse('$baseUrl$path');

  dynamic _unwrap(http.Response response) {
    Map<String, dynamic> body;
    try {
      body = jsonDecode(response.body) as Map<String, dynamic>;
    } catch (_) {
      throw ApiException(
        'The server returned an unexpected response (HTTP ${response.statusCode}).',
        statusCode: response.statusCode,
      );
    }

    final bool success = body['success'] as bool? ?? (response.statusCode < 300);
    if (!success) {
      throw ApiException(
        body['message'] as String? ?? 'The request could not be completed.',
        statusCode: response.statusCode,
        errorCode: body['errorCode'] as String?,
      );
    }
    return body['data'];
  }

  Future<dynamic> get(String path) async {
    final http.Response response = await _client.get(_uri(path), headers: _headers);
    return _unwrap(response);
  }

  Future<dynamic> post(String path, Map<String, dynamic> body) async {
    final http.Response response = await _client.post(
      _uri(path),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _unwrap(response);
  }

  Future<dynamic> put(String path, Map<String, dynamic> body) async {
    final http.Response response = await _client.put(
      _uri(path),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _unwrap(response);
  }

  Future<dynamic> patch(String path, Map<String, dynamic> body) async {
    final http.Response response = await _client.patch(
      _uri(path),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _unwrap(response);
  }

  /// Multipart upload (package image / officer evidence). [query] becomes URL
  /// query parameters (the officer-evidence endpoint takes `label`/`description`
  /// that way, since the body is the file itself).
  ///
  /// [filePath] is whatever `image_picker`'s `XFile.path` returned. On mobile/
  /// desktop that is a real filesystem path `MultipartFile.fromPath` can read
  /// directly; on web it is a `blob:` URL with no filesystem behind it at all,
  /// and `fromPath` throws `UnsupportedError` there (a bug independent of the
  /// caller - it doesn't touch the network, so the backend never sees the
  /// request). Fetching that blob URL and building the multipart file from its
  /// bytes instead works identically on every platform.
  Future<dynamic> uploadFile(String path, String filePath,
      {String field = 'file', Map<String, String>? query}) async {
    final Uri uri = query == null ? _uri(path) : _uri(path).replace(queryParameters: query);
    final http.MultipartFile file = kIsWeb
        ? http.MultipartFile.fromBytes(
            field,
            (await http.get(Uri.parse(filePath))).bodyBytes,
            filename: 'upload.jpg',
          )
        : await http.MultipartFile.fromPath(field, filePath);
    final http.MultipartRequest request = http.MultipartRequest('POST', uri)
      ..headers.addAll(<String, String>{
        if (_authToken != null) 'Authorization': 'Bearer $_authToken',
      })
      ..files.add(file);
    final http.StreamedResponse streamed = await _client.send(request);
    final http.Response response = await http.Response.fromStream(streamed);
    return _unwrap(response);
  }
}

/// Endpoint paths the inspector application uses - mirrors the routes
/// actually implemented by the Spring Boot backend.
///
/// The backend's inspection lifecycle now matches this app's: `/analyze`
/// records the rule engine's verdict as an *advisory* `aiSuggestedStatus`
/// only, and `/submit` is where the inspector's own final decision becomes
/// the inspection's authoritative status.
class ApiRoutes {
  const ApiRoutes._();

  static const String login = '/auth/login';
  static const String register = '/auth/register';
  static const String me = '/auth/me';
  static const String inspections = '/inspections';
  static const String products = '/products';
  static const String rules = '/rules';
  static const String zones = '/zones';

  static String inspection(String id) => '/inspections/$id';
  static String uploadImage(String id) => '/inspections/$id/image';
  static String analyze(String id) => '/inspections/$id/analyze';
  static String submitInspection(String id) => '/inspections/$id/submit';
  static String identifyProduct(String id) => '/inspections/$id/product';
  static String notes(String id) => '/inspections/$id/notes';
  static String checklist(String id) => '/inspections/$id/checklist';
  static String findings(String id) => '/inspections/$id/findings';
  static String officerEvidence(String id) => '/inspections/$id/officer-evidence';
  static String evidence(String id) => '/inspections/$id/evidence';
  static String report(String id) => '/inspections/$id/report';
  static String productHistory(String id) => '/products/$id/history';
  static String productInspectionSummary(String id) => '/products/$id/inspection-summary';
}
