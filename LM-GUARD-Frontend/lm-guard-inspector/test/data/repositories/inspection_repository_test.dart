import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:lm_guard_inspector/data/models/ai_evaluation.dart';
import 'package:lm_guard_inspector/data/models/checklist_item.dart';
import 'package:lm_guard_inspector/data/models/enums.dart';
import 'package:lm_guard_inspector/data/models/product_suggestion.dart';
import 'package:lm_guard_inspector/data/repositories/inspection_repository.dart';
import 'package:lm_guard_inspector/data/services/api_client.dart';

/// The Information step's product-name field and the Checklist/Evidence
/// steps' "what did the AI actually see" rendering both depend on this
/// repository's JSON -> model mapping being right; these tests pin that
/// mapping down with a mocked HTTP client rather than a real backend.
void main() {
  ApiInspectionRepository buildRepository(http.Client client) {
    final ApiClient apiClient = ApiClient(baseUrl: 'https://test.local', client: client);
    return ApiInspectionRepository(apiClient);
  }

  http.Response jsonOk(Map<String, dynamic> data) {
    return http.Response.bytes(
      utf8.encode(jsonEncode(<String, dynamic>{'success': true, 'data': data})),
      200,
      headers: <String, String>{'content-type': 'application/json; charset=utf-8'},
    );
  }

  group('runAiEvaluation checklist mapping', () {
    const ChecklistItem mrpItem = ChecklistItem(
      id: 'CHK-01',
      ruleRef: 'LMPC-PRC-003',
      title: 'MRP declared',
      guidance: 'Check the MRP is printed clearly.',
    );

    test('a compliant field carries its extracted value as detectedIssue', () async {
      final MockClient client = MockClient((http.Request request) async {
        return jsonOk(<String, dynamic>{
          'fields': <Map<String, dynamic>>[
            <String, dynamic>{'name': 'MRP', 'value': '₹99.00', 'confidence': 0.92},
          ],
          'violations': <Map<String, dynamic>>[],
        });
      });

      final AIEvaluation evaluation =
          await buildRepository(client).runAiEvaluation('INS-1', const <ChecklistItem>[mrpItem]);

      final AiChecklistResult result = evaluation.checklistResults.single;
      expect(result.aiSuggestedStatus, CheckResult.compliant);
      expect(result.detectedIssue, '₹99.00');
    });

    test('a field the backend never returned carries no detected value', () async {
      final MockClient client = MockClient((http.Request request) async {
        return jsonOk(<String, dynamic>{'fields': <Map<String, dynamic>>[], 'violations': <Map<String, dynamic>>[]});
      });

      final AIEvaluation evaluation =
          await buildRepository(client).runAiEvaluation('INS-1', const <ChecklistItem>[mrpItem]);

      expect(evaluation.checklistResults.single.detectedIssue, isNull);
    });

    test('a violation carries the backend observedValue as its value', () async {
      final MockClient client = MockClient((http.Request request) async {
        return jsonOk(<String, dynamic>{
          'fields': <Map<String, dynamic>>[],
          'violations': <Map<String, dynamic>>[
            <String, dynamic>{
              'ruleCode': 'LM-PC-7-2-3-NUMERAL-HEIGHT',
              'fieldName': 'MRP',
              'finding': 'MRP is missing the unit price.',
              'severity': 'MAJOR',
              'status': 'NON_COMPLIANT',
              'observedValue': 'Rs. 99 (no /kg or /L shown)',
              'decisionConfidence': 0.81,
            },
          ],
        });
      });

      final AIEvaluation evaluation =
          await buildRepository(client).runAiEvaluation('INS-1', const <ChecklistItem>[mrpItem]);

      expect(evaluation.violations.single.value, 'Rs. 99 (no /kg or /L shown)');
      expect(evaluation.checklistResults.single.detectedIssue, 'Rs. 99 (no /kg or /L shown)');
    });
  });

  group('searchProducts', () {
    test('maps each result and passes the query through as a search param', () async {
      Uri? capturedUri;
      final MockClient client = MockClient((http.Request request) async {
        capturedUri = request.url;
        return jsonOk(<String, dynamic>{
          'items': <Map<String, dynamic>>[
            <String, dynamic>{'id': 'PRD-1', 'productName': 'Lifebuoy Bath Soap 100g', 'brand': 'Lifebuoy'},
          ],
        });
      });

      final List<ProductSuggestion> results = await buildRepository(client).searchProducts('lifebuoy');

      expect(capturedUri?.queryParameters['search'], 'lifebuoy');
      expect(results.single.id, 'PRD-1');
      expect(results.single.name, 'Lifebuoy Bath Soap 100g');
      expect(results.single.displayLabel, 'Lifebuoy Bath Soap 100g (Lifebuoy)');
    });

    test('a blank query returns no results without calling the backend', () async {
      bool called = false;
      final MockClient client = MockClient((http.Request request) async {
        called = true;
        return jsonOk(<String, dynamic>{'items': <Map<String, dynamic>>[]});
      });

      final List<ProductSuggestion> results = await buildRepository(client).searchProducts('   ');

      expect(results, isEmpty);
      expect(called, isFalse);
    });
  });
}
