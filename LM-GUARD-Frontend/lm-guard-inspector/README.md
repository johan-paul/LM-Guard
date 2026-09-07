# LM-GUARD Inspector

Field inspection application for Legal Metrology inspecting officers.
Flutter · Dart · Android-first · mock data.

This is the **officer's** application. The administrator console is the separate
React web project in `../lm-guard-admin`. The two never share code — only the
brand identity and the API contract they will both consume.

---

## Running it

The repository does not carry generated platform folders, so the first step
creates them against **your** installed Flutter SDK — this avoids Gradle and
AGP version mismatches.

```bash
cd lm-guard-inspector

# 1. generate the Android platform folder for your SDK
flutter create --platforms=android .

# 2. resolve packages
flutter pub get

# 3. run on a connected device or emulator
flutter run
```

Sign in with the demonstration officer account (also shown on the login screen):

```
Inspector ID   LM-INS-014
Password       inspect123
```

Requires Flutter 3.10 or newer. The only runtime dependency is `provider`;
date formatting and every widget are implemented in the project, so nothing
else needs to resolve.

---

## Layout invariants

The application once rendered a blank content area on every tab. The cause was
a single line in `AppPanel`:

```dart
Row(crossAxisAlignment: CrossAxisAlignment.stretch, …)   // ← never do this
```

`RenderFlex` lays a stretched horizontal Row's children out at
`constraints.maxHeight`. Inside a `ListView` that value is `double.infinity`,
so the children are handed an unsatisfiable constraint, the layout assertion
fires, and the enclosing sliver aborts — the AppBar and bottom navigation still
paint, because `Scaffold` lays those out separately. Everything else in the
console (`RenderSliverMultiBoxAdaptor`, `Unexpected null value` from
`RenderBox.size` reading `_size!` on a box that never got laid out, the
mouse-tracker errors on every pointer move) followed from it.

Three rules keep it fixed:

1. **Never stretch a Row in a scrollable.** Use `EqualHeightRow`
   (`core/widgets/panels.dart`), which wraps the stretch in `IntrinsicHeight`
   so the height is measured and finite before the Row lays out.
2. **A vertical accent is a border, not a stretched child.** `AppPanel` paints
   its status stripe with `Border(left: …)`, which takes its height from the
   child. A non-uniform border must not carry a `borderRadius` — the rounding
   and the hairline come from the `Material`'s `shape` instead.
3. **`Expanded` needs a bounded main axis.** Legal inside a `Scaffold` body
   Column; never inside a scrollable.

Run the checker before committing — it models these rules across every widget
tree in the project:

```bash
python3 tool/constraint_check.py .
```

It also reports null-check operators (`!`), unresolved imports and unbalanced
delimiters. Errors fail the run; `W` findings depend on where a widget is
placed by its caller and are informational.

---

## Design direction

A government field application, not a dashboard. Navy institutional headers,
white working surfaces, hairline borders, and status colour used only where it
carries compliance meaning.

| Token | Value | Used for |
| --- | --- | --- |
| `navy` | `#0B1F33` | App bar, welcome band, identity surfaces |
| `accent` | `#2563EB` | Primary actions, active navigation |
| `canvas` / `surface` | `#F6F8FB` / `#FFFFFF` | Background / cards |
| `ink` / `inkMuted` | `#172033` / `#667085` | Primary / secondary text |
| `border` | `#E4E7EC` | Hairline borders |
| success / warning / danger / critical | `#16A34A` `#D97706` `#DC2626` `#B42318` | Compliance and workflow state |

The navy and accent are the same values the admin console uses, so both
applications read as one system.

Field-use decisions: portrait lock, 48 px minimum touch targets, three-way
checklist selectors instead of typing, and a persistent bottom action bar so the
next step is always reachable one-handed.

---

## Project structure

```
lib/
  main.dart                     Entry point, portrait lock
  app.dart                      Providers + MaterialApp + auth gate

  core/
    theme/                      Colours, type scale, ThemeData, sizes
    utils/formatters.dart       Dates, initials, pluralisation (no intl)
    widgets/                    Chips, panels, buttons, fields, cards,
                                evidence thumbnail, headers, empty states

  data/
    models/                     Inspector, Product, ChecklistItem, Evidence,
                                Finding, Inspection, workflow enums
    mock/mock_data.dart         Seeded officer, products and inspections
    services/api_client.dart    REST client placeholder + endpoint paths
    services/evidence_service.dart  Capture interface + mock implementation
    repositories/               AuthRepository, InspectionRepository
                                (abstract + mock implementations)

  state/
    auth_controller.dart        Signed-in officer
    inspection_controller.dart  Work queue and derived counts
    draft_controller.dart       The in-flight inspection being recorded

  features/
    auth/                       Login
    shell/                      Bottom navigation + workflow launcher
    home/                       Today's work
    inspections/                My Inspections, record detail / report
    new_inspection/             Six-step workflow + steps/
    drafts/                     Unfinished records
    history/                    Submitted records
    profile/                    Officer account
```

Screens depend on the repository **interfaces** and on controllers — never on
`data/mock` directly. That is what makes the backend swap contained.

---

## Workflow

```
Login → Home → Assigned inspections → Start / Continue
  → 1 Inspection information
  → 2 Product identification   (search · scan · manual entry)
  → 3 Compliance checklist     (8 statutory declarations)
  → 4 Evidence capture
  → 5 Findings & violations
  → 6 Review  →  Save as draft  or  Submit
→ Inspection history
```

The review step blocks submission until the particulars, the product and the
full checklist are recorded; saving a draft is always available. Submission
asks for confirmation and locks the record on the device.

---

## Connecting the backend

Nothing in `features/` needs to change.

1. Add an HTTP package (`http` or `dio`) to `pubspec.yaml`.
2. Implement the methods in `data/services/api_client.dart`. The endpoint paths
   are already listed in `ApiRoutes`.
3. Write `ApiInspectionRepository` and `ApiAuthRepository` implementing the
   interfaces in `data/repositories/`.
4. In `lib/app.dart`, replace two lines:

```dart
Provider<InspectionRepository>(create: (_) => MockInspectionRepository()),
ChangeNotifierProvider<AuthController>(
  create: (_) => AuthController(MockAuthRepository()),
),
```

Response shapes must match the `fromJson` constructors in `data/models` — the
same shapes the admin console consumes. Enum `wireValue` strings are the
serialised form (`ASSIGNED`, `NON_COMPLIANT`, `CRITICAL`, …).

On an Android emulator the host machine is `10.0.2.2`; `ApiClient.baseUrl`
already defaults to `http://10.0.2.2:8080/api`.

### Enabling the camera

Evidence capture runs through `EvidenceService`, and the mock implementation
fabricates an evidence record with a generated placeholder. To capture real
photographs:

1. Add `image_picker: ^1.0.7` to `pubspec.yaml`.
2. Implement `EvidenceService.capture` with `ImagePicker` — the exact snippet is
   in the doc comment on that class.
3. Render `Image.file(File(item.filePath!))` in `core/widgets/evidence_thumb.dart`
   when `filePath` is not null.

---

## Notes

- Mock data is fictional. Rule references (`LMPC-…`) are illustrative
  demonstration rules for the prototype, not a reproduction of any statutory
  instrument.
- Everything lives in memory: restarting the app resets drafts and submissions.
- The officer application deliberately exposes no programme analytics, inspector
  management, zone administration or system settings — those belong to the
  administrator console.
