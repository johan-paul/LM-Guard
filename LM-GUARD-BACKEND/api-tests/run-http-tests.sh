#!/usr/bin/env bash
# Executable companion to LMGuardApiTests.http — runs the same sequence via curl so it can be
# re-run from a CI job or a terminal with no IDE extension needed. Requires: curl, python3.
#
# Usage:
#   ./run-http-tests.sh [baseUrl]
#   BASE_URL=http://localhost:8080 ./run-http-tests.sh
#
# Every check prints PASS or FAIL with the actual status code observed. A FAIL does not stop
# the run — later checks that depend on an earlier failure will legitimately fail too, and the
# summary at the end says so rather than hiding it. Exit code is the number of failed checks
# (0 = every check passed).
set -uo pipefail

BASE="${1:-${BASE_URL:-http://localhost:8080}}"
JAR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE="$JAR_DIR/fixtures/sample-package.jpg"
TS="$(date +%s)"

PASS=0
FAIL=0
RESULTS=()

# --- helpers -----------------------------------------------------------------------------

check() {
  # check <name> <expected_status> <actual_status> [extra condition already evaluated to 0/1]
  local name="$1" expected="$2" actual="$3" extra_ok="${4:-1}"
  if [[ "$actual" == "$expected" && "$extra_ok" == "1" ]]; then
    echo "PASS  [$name] -> HTTP $actual"
    PASS=$((PASS + 1))
    RESULTS+=("PASS|$name|$actual")
  else
    echo "FAIL  [$name] -> expected HTTP $expected, got HTTP $actual (extra_ok=$extra_ok)"
    FAIL=$((FAIL + 1))
    RESULTS+=("FAIL|$name|expected=$expected actual=$actual")
  fi
}

json_get() {
  # json_get <file> <python-expression-on-d>
  # Python here is the native Windows interpreter, which cannot resolve a Git-Bash /tmp/...
  # path directly -- convert to a Windows path first (a no-op if cygpath is unavailable,
  # e.g. on a real Linux/macOS runner).
  local winpath
  winpath="$(cygpath -w "$1" 2>/dev/null || echo "$1")"
  python -c "
import json, sys
try:
    d = json.load(open(r'$winpath', encoding='utf-8'))
    print($2)
except Exception as e:
    print('__ERROR__:' + str(e))
"
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

req() {
  # req <outfile> <curl args...>  — returns HTTP status on stdout
  local out="$1"; shift
  curl -s -o "$out" -w "%{http_code}" "$@"
}

echo "=== LM-GUARD API test run against $BASE ==="
echo

# =====================================================================================
# 1. AUTH
# =====================================================================================

STATUS=$(req "$TMP/reg_inspector.json" -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Test Inspector\",\"email\":\"inspector_$TS@lmguard.test\",\"password\":\"Str0ngPassw0rd!\"}")
INSPECTOR_TOKEN=$(json_get "$TMP/reg_inspector.json" "d['data']['token']")
check "1.1 register inspector" 201 "$STATUS" "$([[ -n "$INSPECTOR_TOKEN" && "$INSPECTOR_TOKEN" != __ERROR__* ]] && echo 1 || echo 0)"

STATUS=$(req "$TMP/reg_admin.json" -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Test Admin\",\"email\":\"admin_$TS@lmguard.test\",\"password\":\"Str0ngPassw0rd!\",\"role\":\"ADMIN\"}")
ADMIN_TOKEN=$(json_get "$TMP/reg_admin.json" "d['data']['token']")
ADMIN_ROLE=$(json_get "$TMP/reg_admin.json" "d['data']['user']['role']")
check "1.2 register admin" 201 "$STATUS" "$([[ "$ADMIN_ROLE" == "ADMIN" ]] && echo 1 || echo 0)"

STATUS=$(req "$TMP/me.json" "$BASE/api/auth/me" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "1.3 auth/me with valid token" 200 "$STATUS"

STATUS=$(req "$TMP/me_noauth.json" "$BASE/api/auth/me")
check "1.4 auth/me with no token" 401 "$STATUS"

STATUS=$(req "$TMP/login_ok.json" -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"inspector_$TS@lmguard.test\",\"password\":\"Str0ngPassw0rd!\"}")
check "1.5 login correct credentials" 200 "$STATUS"

STATUS=$(req "$TMP/login_bad.json" -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"inspector_$TS@lmguard.test\",\"password\":\"WrongPassword!\"}")
check "1.6 login wrong password" 401 "$STATUS"

STATUS=$(req "$TMP/dup.json" -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Dup\",\"email\":\"inspector_$TS@lmguard.test\",\"password\":\"Str0ngPassw0rd!\"}")
check "1.7 duplicate register" 409 "$STATUS"

STATUS=$(req "$TMP/badreg.json" -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' -d '{"email":"not-an-email"}')
check "1.8 register missing/invalid fields" 400 "$STATUS"

echo

# =====================================================================================
# 2. PRODUCTS
# =====================================================================================

STATUS=$(req "$TMP/product.json" -X POST "$BASE/api/products" -H "Authorization: Bearer $INSPECTOR_TOKEN" -H 'Content-Type: application/json' \
  -d '{"productName":"Classic Salted Chips","brand":"ABC Foods","category":"packaged_food","barcode":"8901234567890"}')
PRODUCT_ID=$(json_get "$TMP/product.json" "d['data']['id']")
CATEGORY=$(json_get "$TMP/product.json" "d['data']['category']")
check "2.1 create product" 201 "$STATUS" "$([[ "$CATEGORY" == "PACKAGED_FOOD" ]] && echo 1 || echo 0)"

STATUS=$(req "$TMP/search.json" "$BASE/api/products?search=Chips&page=0&size=10" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "2.2 list/search products" 200 "$STATUS"

STATUS=$(req "$TMP/getprod.json" "$BASE/api/products/$PRODUCT_ID" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "2.3 get product by id" 200 "$STATUS"

STATUS=$(req "$TMP/history.json" "$BASE/api/products/$PRODUCT_ID/history" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "2.4 product history" 200 "$STATUS"

STATUS=$(req "$TMP/baduuid.json" "$BASE/api/products/not-a-uuid" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "2.5 get product malformed uuid" 400 "$STATUS"

STATUS=$(req "$TMP/unknownprod.json" "$BASE/api/products/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "2.6 get product unknown uuid" 404 "$STATUS"

echo

# =====================================================================================
# 3. INSPECTIONS
# =====================================================================================

STATUS=$(req "$TMP/inspection.json" -X POST "$BASE/api/inspections" -H "Authorization: Bearer $INSPECTOR_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"notes\":\"Routine market check\"}")
INSPECTION_ID=$(json_get "$TMP/inspection.json" "d['data']['inspectionId']")
check "3.1 create inspection (existing product)" 201 "$STATUS"

STATUS=$(req "$TMP/inline.json" -X POST "$BASE/api/inspections" -H "Authorization: Bearer $INSPECTOR_TOKEN" -H 'Content-Type: application/json' \
  -d '{"productName":"Inline Registered Biscuits","brand":"XYZ","category":"packaged_food"}')
check "3.2 create inspection (inline product)" 201 "$STATUS"

STATUS=$(req "$TMP/noproduct.json" -X POST "$BASE/api/inspections" -H "Authorization: Bearer $INSPECTOR_TOKEN" -H 'Content-Type: application/json' \
  -d '{"notes":"no product info"}')
check "3.3 create inspection (neither productId nor productName)" 400 "$STATUS"

STATUS=$(req "$TMP/noimage.json" -X POST "$BASE/api/inspections/$INSPECTION_ID/analyze" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.4 analyze before image upload" 400 "$STATUS"

echo "not an image" > "$TMP/notimage.txt"
# curl here is the native Windows (mingw64) build: `-F file=@<path>` needs a Windows-style
# path, not a Git-Bash /tmp/... one, or curl fails with a local read error (exit 26) before
# it ever sends a request.
NOTIMAGE_WIN="$(cygpath -w "$TMP/notimage.txt" 2>/dev/null || echo "$TMP/notimage.txt")"
STATUS=$(req "$TMP/badimage.json" -X POST "$BASE/api/inspections/$INSPECTION_ID/image" -H "Authorization: Bearer $INSPECTOR_TOKEN" \
  -F "file=@$NOTIMAGE_WIN;type=text/plain")
check "3.5 upload non-image file" 400 "$STATUS"

if [[ -f "$FIXTURE" ]]; then
  FIXTURE_WIN="$(cygpath -w "$FIXTURE" 2>/dev/null || echo "$FIXTURE")"
  STATUS=$(req "$TMP/upload.json" -X POST "$BASE/api/inspections/$INSPECTION_ID/image" -H "Authorization: Bearer $INSPECTOR_TOKEN" \
    -F "file=@$FIXTURE_WIN;type=image/jpeg")
  check "3.6 upload real package image" 200 "$STATUS"
else
  echo "SKIP  [3.6 upload real package image] -> fixture not found at $FIXTURE"
fi

STATUS=$(req "$TMP/analyze.json" -X POST "$BASE/api/inspections/$INSPECTION_ID/analyze" -H "Authorization: Bearer $INSPECTOR_TOKEN")
VERDICT=$(json_get "$TMP/analyze.json" "d['data']['status']")
check "3.7 run analysis pipeline" 200 "$STATUS" "$([[ "$VERDICT" =~ ^(COMPLIANT|NON_COMPLIANT|INCONCLUSIVE)$ ]] && echo 1 || echo 0)"
echo "      verdict=$VERDICT rulesetVersion=$(json_get "$TMP/analyze.json" "d['data'].get('rulesetVersion')")"
VIOLATION_ID=$(json_get "$TMP/analyze.json" "d['data']['violations'][0]['id'] if d['data'].get('violations') else ''")

STATUS=$(req "$TMP/getinspection.json" "$BASE/api/inspections/$INSPECTION_ID" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.8 get inspection result" 200 "$STATUS"

STATUS=$(req "$TMP/listinsp.json" "$BASE/api/inspections?status=$VERDICT&page=0&size=10" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.9 list inspections filtered by status" 200 "$STATUS"

STATUS=$(req "$TMP/listbyprod.json" "$BASE/api/inspections?productId=$PRODUCT_ID" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.10 list inspections filtered by productId" 200 "$STATUS"

STATUS=$(req "$TMP/evidence.json" "$BASE/api/inspections/$INSPECTION_ID/evidence" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.11 inspection evidence" 200 "$STATUS"

if [[ -n "$VIOLATION_ID" && "$VIOLATION_ID" != __ERROR__* ]]; then
  STATUS=$(req "$TMP/violevidence.json" "$BASE/api/violations/$VIOLATION_ID/evidence" -H "Authorization: Bearer $INSPECTOR_TOKEN")
  check "3.12 violation evidence" 200 "$STATUS"
else
  echo "SKIP  [3.12 violation evidence] -> no violation on this run (verdict was $VERDICT)"
fi

STATUS=$(req "$TMP/report.json" "$BASE/api/inspections/$INSPECTION_ID/report" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.13 inspection report" 200 "$STATUS"

STATUS=$(req "$TMP/unknowninsp.json" "$BASE/api/inspections/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.14 get inspection unknown uuid" 404 "$STATUS"

STATUS=$(req "$TMP/badenum.json" "$BASE/api/inspections?status=NOT_A_STATUS" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "3.15 invalid enum in query filter" 400 "$STATUS"

echo

# =====================================================================================
# 4. DASHBOARD
# =====================================================================================

STATUS=$(req "$TMP/dash.json" "$BASE/api/dashboard" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "4.1 dashboard overview" 200 "$STATUS"

STATUS=$(req "$TMP/stats.json" "$BASE/api/dashboard/statistics" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "4.2 dashboard statistics" 200 "$STATUS"

STATUS=$(req "$TMP/highrisk.json" "$BASE/api/dashboard/high-risk?page=0&size=20" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "4.3 dashboard high-risk" 200 "$STATUS"

echo

# =====================================================================================
# 5. RULES (ADMIN)
# =====================================================================================

STATUS=$(req "$TMP/rules.json" "$BASE/api/rules" -H "Authorization: Bearer $ADMIN_TOKEN")
RULE_VERSION=$(json_get "$TMP/rules.json" "d['data']['version']")
check "5.1 get ruleset as ADMIN" 200 "$STATUS"
echo "      active ruleset version = $RULE_VERSION"

STATUS=$(req "$TMP/rulesforbidden.json" "$BASE/api/rules" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "5.2 get ruleset as INSPECTOR (should be forbidden)" 403 "$STATUS"

STATUS=$(req "$TMP/rulesnoauth.json" "$BASE/api/rules")
check "5.3 get ruleset with no token" 401 "$STATUS"

STATUS=$(req "$TMP/badrule.json" -X POST "$BASE/api/rules" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"ruleCode":"TEST-BAD-SHAPE","ruleName":"Wrong field names","field":"BATCH_NUMBER","type":"REQUIRED_FIELD","severity":"MINOR","version":"LM-PC-2011-v1"}')
check "5.4 create rule with wrong field names" 400 "$STATUS"

STATUS=$(req "$TMP/newrule.json" -X POST "$BASE/api/rules" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"ruleCode":"TEST-SWAGGER-CHECK-'"$TS"'","ruleName":"Swagger endpoint smoke-test rule","description":"Created during an automated endpoint verification pass","fieldName":"BATCH_NUMBER","ruleType":"REQUIRED_FIELD","ruleDefinition":"{\"required\":true,\"minConfidence\":0.70,\"finding\":\"Batch number not detected\"}","severity":"MINOR","version":"LM-PC-2011-v1","active":true}')
RULE_ID=$(json_get "$TMP/newrule.json" "d['data']['id']")
check "5.5 create rule with correct shape" 200 "$STATUS"

if [[ -n "$RULE_ID" && "$RULE_ID" != __ERROR__* ]]; then
  STATUS=$(req "$TMP/deactivate.json" -X PATCH "$BASE/api/rules/$RULE_ID/active?active=false" -H "Authorization: Bearer $ADMIN_TOKEN")
  check "5.6 deactivate the rule just created" 200 "$STATUS"
else
  echo "SKIP  [5.6 deactivate rule] -> 5.5 did not return an id"
fi

STATUS=$(req "$TMP/unknownrule.json" -X PATCH "$BASE/api/rules/00000000-0000-0000-0000-000000000000/active?active=false" -H "Authorization: Bearer $ADMIN_TOKEN")
check "5.7 activate/deactivate unknown rule id" 404 "$STATUS"

echo

# =====================================================================================
# 6. CROSS-CUTTING ERROR HANDLING
# =====================================================================================

STATUS=$(req "$TMP/malformed.json" -X POST "$BASE/api/products" -H "Authorization: Bearer $INSPECTOR_TOKEN" -H 'Content-Type: application/json' -d '{not valid json')
check "6.1 malformed JSON body" 400 "$STATUS"

STATUS=$(req "$TMP/wrongmethod.json" -X DELETE "$BASE/api/products" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "6.2 wrong HTTP method" 405 "$STATUS"

STATUS=$(req "$TMP/unknownpath.json" "$BASE/api/this-does-not-exist" -H "Authorization: Bearer $INSPECTOR_TOKEN")
check "6.3 unknown path" 404 "$STATUS"

echo
echo "=== Summary: $PASS passed, $FAIL failed, $((PASS + FAIL)) total ==="

# Machine-readable summary alongside the human-readable one.
python -c "
import json
print(json.dumps({'totalChecks': $((PASS + FAIL)), 'passed': $PASS, 'failed': $FAIL}, indent=2))
" > "$JAR_DIR/api-test-results.json"
echo "Wrote $JAR_DIR/api-test-results.json"

exit "$FAIL"
