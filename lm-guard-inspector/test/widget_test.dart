import 'package:flutter_test/flutter_test.dart';

import 'package:lm_guard_inspector/app.dart';

void main() {
  testWidgets('App boots to the sign-in screen', (WidgetTester tester) async {
    await tester.pumpWidget(const LmGuardInspectorApp());
    await tester.pumpAndSettle();

    expect(find.text('Sign in'), findsWidgets);
  });
}
