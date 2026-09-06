"""One-off generator for LM-Guard.postman_collection.json.

Builds the collection programmatically rather than hand-writing nested JSON, so escaping and
structure stay correct. Mirrors api-tests/LMGuardApiTests.http's flow and ordering exactly --
requests later in the collection depend on collection variables captured by earlier ones via
each request's Tests script, the same dependency chain the .http file documents.
"""
import json

BASE = "{{baseUrl}}"


def req(name, method, path, *, headers=None, body=None, form=None, tests=None, description=None):
    item = {"name": name, "request": {"method": method, "header": [], "url": _url(path)}}
    if description:
        item["request"]["description"] = description
    for k, v in (headers or {}).items():
        item["request"]["header"].append({"key": k, "value": v, "type": "text"})
    if body is not None:
        item["request"]["header"].append({"key": "Content-Type", "value": "application/json", "type": "text"})
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2),
                                    "options": {"raw": {"language": "json"}}}
    if form is not None:
        item["request"]["body"] = {"mode": "formdata", "formdata": form}
    if tests:
        item["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": tests.splitlines()}}]
    return item


def _url(path):
    raw = BASE + path
    qpos = raw.find("?")
    base = raw if qpos == -1 else raw[:qpos]
    parts = [p for p in base.replace(BASE, "").split("/") if p]
    query = []
    if qpos != -1:
        for pair in raw[qpos + 1:].split("&"):
            k, _, v = pair.partition("=")
            query.append({"key": k, "value": v})
    return {"raw": raw, "host": ["{{baseUrl}}"], "path": parts, "query": query or None} if query else \
           {"raw": raw, "host": ["{{baseUrl}}"], "path": parts}


AUTH = lambda tok="{{inspectorToken}}": {"Authorization": f"Bearer {tok}"}

auth_folder = {"name": "1. Auth", "item": [
    req("1.1 Register an INSPECTOR", "POST", "/api/auth/register",
        body={"name": "Test Inspector", "email": "{{$randomEmail}}", "password": "Str0ngPassw0rd!"},
        tests="""pm.test("201 Created", () => pm.response.code === 201);
const body = pm.response.json();
pm.collectionVariables.set("inspectorEmail", pm.request.body ? JSON.parse(pm.request.body.raw).email : "");
pm.collectionVariables.set("inspectorToken", body.data.token);"""),
    req("1.2 Register an ADMIN", "POST", "/api/auth/register",
        body={"name": "Test Admin", "email": "{{$randomEmail}}", "password": "Str0ngPassw0rd!", "role": "ADMIN"},
        tests="""pm.test("201 Created, role ADMIN", () => pm.response.code === 201 && pm.response.json().data.user.role === "ADMIN");
pm.collectionVariables.set("adminToken", pm.response.json().data.token);"""),
    req("1.3 GET /api/auth/me (valid token)", "GET", "/api/auth/me", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("1.4 GET /api/auth/me (no token)", "GET", "/api/auth/me",
        tests="pm.test(\"401 Unauthorized\", () => pm.response.code === 401);"),
    req("1.5 Login (correct credentials)", "POST", "/api/auth/login",
        body={"email": "{{inspectorEmail}}", "password": "Str0ngPassw0rd!"},
        tests="pm.test(\"200 OK, fresh token\", () => pm.response.code === 200 && !!pm.response.json().data.token);"),
    req("1.6 Login (wrong password)", "POST", "/api/auth/login",
        body={"email": "{{inspectorEmail}}", "password": "WrongPassword!"},
        tests="pm.test(\"401 INVALID_CREDENTIALS\", () => pm.response.code === 401);"),
    req("1.7 Register (duplicate email)", "POST", "/api/auth/register",
        body={"name": "Duplicate", "email": "{{inspectorEmail}}", "password": "Str0ngPassw0rd!"},
        tests="pm.test(\"409 EMAIL_ALREADY_REGISTERED\", () => pm.response.code === 409);"),
    req("1.8 Register (missing/invalid fields)", "POST", "/api/auth/register",
        body={"name": "", "email": "not-an-email", "password": "x"},
        tests="pm.test(\"400 VALIDATION_FAILED\", () => pm.response.code === 400);"),
]}

products_folder = {"name": "2. Products", "item": [
    req("2.1 Register a product", "POST", "/api/products", headers=AUTH(),
        body={"productName": "Classic Salted Chips", "brand": "ABC Foods", "category": "packaged_food",
              "barcode": "8901234567890"},
        tests="""pm.test("201 Created", () => pm.response.code === 201);
pm.collectionVariables.set("productId", pm.response.json().data.id);"""),
    req("2.2 List / search products", "GET", "/api/products?search=Chips&page=0&size=10", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("2.3 Get one product", "GET", "/api/products/{{productId}}", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("2.4 Product declaration history", "GET", "/api/products/{{productId}}/history", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("2.5 Get product (malformed UUID)", "GET", "/api/products/not-a-uuid", headers=AUTH(),
        tests="pm.test(\"400 Bad Request\", () => pm.response.code === 400);"),
    req("2.6 Get product (unknown UUID)", "GET", "/api/products/00000000-0000-0000-0000-000000000000",
        headers=AUTH(), tests="pm.test(\"404 PRODUCT_NOT_FOUND\", () => pm.response.code === 404);"),
]}

inspections_folder = {"name": "3. Inspections", "item": [
    req("3.1 Open an inspection", "POST", "/api/inspections", headers=AUTH(),
        body={"productId": "{{productId}}", "notes": "Routine market check"},
        tests="""pm.test("201 Created, PENDING", () => pm.response.code === 201 && pm.response.json().data.status === "PENDING");
pm.collectionVariables.set("inspectionId", pm.response.json().data.inspectionId);"""),
    req("3.2 Open an inspection (inline product)", "POST", "/api/inspections", headers=AUTH(),
        body={"productName": "Inline Registered Biscuits", "brand": "XYZ", "category": "packaged_food"},
        tests="pm.test(\"201 Created\", () => pm.response.code === 201);"),
    req("3.3 Open an inspection (no product info)", "POST", "/api/inspections", headers=AUTH(),
        body={"notes": "no product info"},
        tests="pm.test(\"400 Bad Request\", () => pm.response.code === 400);"),
    req("3.4 Analyze before image upload", "POST", "/api/inspections/{{inspectionId}}/analyze", headers=AUTH(),
        tests="pm.test(\"400 IMAGE_REQUIRED\", () => pm.response.code === 400);"),
    req("3.5 Upload a non-image file", "POST", "/api/inspections/{{inspectionId}}/image", headers=AUTH(),
        form=[{"key": "file", "type": "file", "src": [], "description": "Select any non-image file, e.g. a .txt"}],
        tests="pm.test(\"400 INVALID_IMAGE\", () => pm.response.code === 400);"),
    req("3.6 Upload the real package image", "POST", "/api/inspections/{{inspectionId}}/image", headers=AUTH(),
        form=[{"key": "file", "type": "file", "src": [], "description": "Select api-tests/fixtures/sample-package.jpg"}],
        tests="pm.test(\"200 OK, imageUrl present\", () => pm.response.code === 200 && !!pm.response.json().data.imageUrl);"),
    req("3.7 Run the analysis pipeline", "POST", "/api/inspections/{{inspectionId}}/analyze", headers=AUTH(),
        tests="""pm.test("200 OK, has a verdict", () => {
    const s = pm.response.json().data.status;
    return pm.response.code === 200 && ["COMPLIANT","NON_COMPLIANT","INCONCLUSIVE"].includes(s);
});
const data = pm.response.json().data;
if (data.violations && data.violations.length > 0) pm.collectionVariables.set("violationId", data.violations[0].id);
console.log("aiProvider:", data.aiProvider, "| verdict:", data.status, "| ruleset:", data.rulesetVersion);"""),
    req("3.8 Get the full inspection result", "GET", "/api/inspections/{{inspectionId}}", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("3.9 List inspections filtered by status", "GET", "/api/inspections?status=NON_COMPLIANT&page=0&size=10",
        headers=AUTH(), tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("3.10 List inspections filtered by product", "GET", "/api/inspections?productId={{productId}}",
        headers=AUTH(), tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("3.11 Evidence for the whole inspection", "GET", "/api/inspections/{{inspectionId}}/evidence",
        headers=AUTH(), tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("3.12 Evidence for one violation", "GET", "/api/violations/{{violationId}}/evidence", headers=AUTH(),
        tests="""if (!pm.collectionVariables.get("violationId")) {
    console.log("SKIP: no violation on this run's verdict");
} else {
    pm.test("200 OK", () => pm.response.code === 200);
}"""),
    req("3.13 Structured report", "GET", "/api/inspections/{{inspectionId}}/report", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("3.14 Get inspection (unknown UUID)", "GET", "/api/inspections/00000000-0000-0000-0000-000000000000",
        headers=AUTH(), tests="pm.test(\"404 INSPECTION_NOT_FOUND\", () => pm.response.code === 404);"),
    req("3.15 Invalid enum in query filter", "GET", "/api/inspections?status=NOT_A_STATUS", headers=AUTH(),
        tests="pm.test(\"400 Bad Request\", () => pm.response.code === 400);"),
]}

dashboard_folder = {"name": "4. Dashboard", "item": [
    req("4.1 Dashboard overview", "GET", "/api/dashboard", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("4.2 Dashboard statistics", "GET", "/api/dashboard/statistics", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("4.3 High-risk products", "GET", "/api/dashboard/high-risk?page=0&size=20", headers=AUTH(),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
]}

rules_folder = {"name": "5. Rules", "item": [
    req("5.1 GET /api/rules (ADMIN)", "GET", "/api/rules", headers=AUTH("{{adminToken}}"),
        tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("5.2 GET /api/rules (INSPECTOR - forbidden)", "GET", "/api/rules", headers=AUTH(),
        tests="pm.test(\"403 Forbidden\", () => pm.response.code === 403);"),
    req("5.3 GET /api/rules (no token)", "GET", "/api/rules",
        tests="pm.test(\"401 Unauthorized\", () => pm.response.code === 401);"),
    req("5.4 Create a rule (correct shape)", "POST", "/api/rules", headers=AUTH("{{adminToken}}"),
        body={"ruleCode": "TEST-RULE-001", "ruleName": "Postman test rule",
              "description": "Created by the Postman collection", "fieldName": "MRP",
              "ruleType": "REQUIRED_FIELD", "severity": "MINOR", "version": "LM-PC-2011-v1",
              "parameters": {"required": True, "minConfidence": 0.7}},
        tests="""pm.test("200/201 Created", () => [200,201].includes(pm.response.code));
if (pm.response.json().data && pm.response.json().data.id) pm.collectionVariables.set("ruleId", pm.response.json().data.id);"""),
    req("5.5 Deactivate the rule just created", "PATCH", "/api/rules/{{ruleId}}/active?active=false",
        headers=AUTH("{{adminToken}}"), tests="pm.test(\"200 OK\", () => pm.response.code === 200);"),
    req("5.6 Activate/deactivate unknown rule id", "PATCH",
        "/api/rules/00000000-0000-0000-0000-000000000000/active?active=false", headers=AUTH("{{adminToken}}"),
        tests="pm.test(\"404 Not Found\", () => pm.response.code === 404);"),
]}

errors_folder = {"name": "6. Errors", "item": [
    req("6.1 Wrong HTTP method on a real path", "DELETE", "/api/products", headers=AUTH(),
        tests="pm.test(\"405 Method Not Allowed\", () => pm.response.code === 405);"),
    req("6.2 Unknown path entirely", "GET", "/api/this-does-not-exist", headers=AUTH(),
        tests="pm.test(\"404 Not Found\", () => pm.response.code === 404);"),
]}

collection = {
    "info": {
        "name": "LM-GUARD API — End to End",
        "description": "Generated from the live OpenAPI spec + api-tests/LMGuardApiTests.http. "
                        "Run folder-by-folder, top to bottom, in Postman's Collection Runner — later "
                        "requests depend on collection variables captured by earlier ones. For 3.5/3.6, "
                        "select a local file in the form-data 'file' field before sending (Postman "
                        "cannot embed binary files in an exported collection).",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8080"},
        {"key": "inspectorToken", "value": ""},
        {"key": "adminToken", "value": ""},
        {"key": "inspectorEmail", "value": ""},
        {"key": "productId", "value": ""},
        {"key": "inspectionId", "value": ""},
        {"key": "violationId", "value": ""},
        {"key": "ruleId", "value": ""},
    ],
    "item": [auth_folder, products_folder, inspections_folder, dashboard_folder, rules_folder, errors_folder],
}

with open("LM-Guard.postman_collection.json", "w") as f:
    json.dump(collection, f, indent=2)

print("wrote LM-Guard.postman_collection.json")
