import '../models/checklist_item.dart';
import '../models/enums.dart';
import '../models/evidence.dart';
import '../models/finding.dart';
import '../models/inspection.dart';
import '../models/inspector.dart';
import '../models/product.dart';

/// Seed data for the prototype.
///
/// Dates are generated relative to the current day so the work queue always
/// looks live. Replace this class with API responses — the shapes match the
/// models' `fromJson` constructors exactly.
class MockData {
  const MockData._();

  static DateTime _at(int daysFromToday, int hour, [int minute = 0]) {
    final DateTime now = DateTime.now();
    final DateTime base = DateTime(now.year, now.month, now.day, hour, minute);
    return base.subtract(Duration(days: daysFromToday));
  }

  /* ------------------------------------------------------------------ */
  /* Officer                                                             */
  /* ------------------------------------------------------------------ */

  static const Inspector inspector = Inspector(
    id: 'LM-INS-014',
    name: 'S. Kumar',
    designation: 'Inspecting Officer',
    zone: 'Coimbatore North',
    email: 's.kumar@legalmetrology.example',
    phone: '+91 98430 11204',
    office: 'Zonal Office, Gandhipuram, Coimbatore',
  );

  /// Demonstration credentials shown on the sign-in screen. The backend
  /// authenticates by email, so this must be an email address for the "Use"
  /// button to be worth anything against a real (seeded) backend account.
  static const String demoInspectorId = 's.kumar@legalmetrology.example';
  static const String demoPassword = 'inspect123';

  /* ------------------------------------------------------------------ */
  /* Reference data                                                      */
  /* ------------------------------------------------------------------ */

  static const List<RuleReference> rules = <RuleReference>[
    RuleReference('LMPC-DECL-001', 'Consumer care declaration', 'Mandatory declaration'),
    RuleReference('LMPC-QTY-002', 'Standard unit of net quantity', 'Quantity & measure'),
    RuleReference('LMPC-PRC-003', 'Retail sale price inclusivity', 'Price declaration'),
    RuleReference('LMPC-ORG-004', 'Country of origin declaration', 'Origin & import'),
    RuleReference('LMPC-IDN-005', 'Manufacturer & packer identity', 'Identity & address'),
    RuleReference('LMPC-CHR-006', 'Date of manufacture or packing', 'Mandatory declaration'),
    RuleReference('LMPC-FNT-007', 'Minimum font height & panel ratio', 'Legibility & font'),
    RuleReference('LMPC-QTY-008', 'Quantity deficiency tolerance', 'Quantity & measure'),
  ];

  static const List<String> zones = <String>[
    'Coimbatore North',
    'Coimbatore South',
    'Coimbatore West',
    'Tiruppur',
    'Erode',
  ];

  /// The statutory declaration checklist applied to every inspection.
  static List<ChecklistItem> checklistTemplate() {
    return const <ChecklistItem>[
      ChecklistItem(
        id: 'CHK-01',
        ruleRef: 'LMPC-DECL-001',
        title: 'Consumer care declaration present',
        guidance: 'Name, address and a telephone number or email for consumer complaints.',
      ),
      ChecklistItem(
        id: 'CHK-02',
        ruleRef: 'LMPC-PRC-003',
        title: 'Retail sale price declared and unaltered',
        guidance: 'A single inclusive price with no over-stickering or erasure.',
      ),
      ChecklistItem(
        id: 'CHK-03',
        ruleRef: 'LMPC-QTY-002',
        title: 'Net quantity in standard units',
        guidance: 'Declared in g, kg, mL, L or number. Approximations are not accepted.',
      ),
      ChecklistItem(
        id: 'CHK-04',
        ruleRef: 'LMPC-IDN-005',
        title: 'Manufacturer or packer identity complete',
        guidance: 'Full name and postal address including PIN code.',
      ),
      ChecklistItem(
        id: 'CHK-05',
        ruleRef: 'LMPC-ORG-004',
        title: 'Country of origin declared',
        guidance: 'Required on imported and part-imported commodities.',
      ),
      ChecklistItem(
        id: 'CHK-06',
        ruleRef: 'LMPC-CHR-006',
        title: 'Date of manufacture or packing legible',
        guidance: 'Month and year printed clearly on the principal display panel.',
      ),
      ChecklistItem(
        id: 'CHK-07',
        ruleRef: 'LMPC-FNT-007',
        title: 'Declaration font height adequate',
        guidance: 'Character height meets the minimum for the measured panel area.',
      ),
      ChecklistItem(
        id: 'CHK-08',
        ruleRef: 'LMPC-FNT-012',
        title: 'Packaging condition acceptable',
        guidance: 'Panel is not torn, folded or obscured by secondary labelling.',
      ),
    ];
  }

  /* ------------------------------------------------------------------ */
  /* Products                                                            */
  /* ------------------------------------------------------------------ */

  static final Product sunflowerOil = Product(
    id: 'PRD-2026-082',
    name: 'SunFresh Refined Sunflower Oil 1 L',
    brand: 'SunFresh',
    manufacturer: 'SunFresh Industries Ltd',
    category: 'Edible Oils',
    batchNumber: 'SF-2026-0731',
    manufacturedOn: _at(38, 9),
    expiresOn: _at(-300, 9),
    barcode: '8901234567890',
    netQuantity: '1 L',
    mrp: 'Rs. 185.00',
  );

  static final Product instantNoodles = Product(
    id: 'PRD-2026-045',
    name: 'ABC Instant Noodles Masala 280 g',
    brand: 'ABC Noodles',
    manufacturer: 'ABC Foods Pvt Ltd',
    category: 'Staples & Grains',
    batchNumber: 'ABC-2026-0812',
    manufacturedOn: _at(24, 9),
    expiresOn: _at(-160, 9),
    barcode: '8901234511234',
    netQuantity: '280 g',
    mrp: 'Rs. 62.00',
  );

  static final Product biscuits = Product(
    id: 'PRD-2026-019',
    name: 'FreshBite Cashew Biscuits 200 g',
    brand: 'FreshBite',
    manufacturer: 'ABC Foods Pvt Ltd',
    category: 'Bakery & Biscuits',
    batchNumber: 'FB-2026-0803',
    manufacturedOn: _at(33, 9),
    expiresOn: _at(-120, 9),
    barcode: '8901234522345',
    netQuantity: '200 g',
    mrp: 'Rs. 45.00',
  );

  static final Product drinkingWater = Product(
    id: 'PRD-2026-104',
    name: 'PureDrop Packaged Drinking Water 1 L',
    brand: 'PureDrop',
    manufacturer: 'PureDrop Beverages Pvt Ltd',
    category: 'Packaged Water',
    batchNumber: 'PD-2026-0826',
    manufacturedOn: _at(10, 9),
    expiresOn: _at(-170, 9),
    barcode: '8901234533456',
    netQuantity: '1 L',
    mrp: 'Rs. 20.00',
  );

  static final Product shampoo = Product(
    id: 'PRD-2026-067',
    name: 'DailyCare Herbal Shampoo 340 mL',
    brand: 'DailyCare',
    manufacturer: 'DailyCare Consumer Products',
    category: 'Personal Care',
    batchNumber: 'DC-2026-0619',
    manufacturedOn: _at(78, 9),
    expiresOn: _at(-640, 9),
    barcode: '8901234544567',
    netQuantity: '340 mL',
    mrp: 'Rs. 249.00',
  );

  static final Product basmatiRice = Product(
    id: 'PRD-2026-028',
    name: 'Grainway Premium Basmati Rice 5 kg',
    brand: 'Grainway',
    manufacturer: 'Grainway Agro Mills',
    category: 'Staples & Grains',
    batchNumber: 'GW-2026-0709',
    manufacturedOn: _at(58, 9),
    expiresOn: _at(-480, 9),
    barcode: '8901234555678',
    netQuantity: '5 kg',
    mrp: 'Rs. 680.00',
  );

  static List<Product> catalogue() => <Product>[
        sunflowerOil,
        instantNoodles,
        biscuits,
        drinkingWater,
        shampoo,
        basmatiRice,
      ];

  /* ------------------------------------------------------------------ */
  /* Inspections                                                         */
  /* ------------------------------------------------------------------ */

  static List<Inspection> inspections() {
    final List<ChecklistItem> blank = checklistTemplate();

    return <Inspection>[
      // --- Assigned for today -------------------------------------------
      Inspection(
        id: 'INS-2026-00124',
        establishment: 'ABC Food Products Pvt Ltd',
        location: 'Ganapathy Main Road, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.routine,
        priority: Priority.high,
        status: InspectionStatus.assigned,
        assignedOn: _at(1, 17),
        dueOn: _at(0, 18),
        updatedAt: _at(1, 17),
        checklist: blank,
      ),
      Inspection(
        id: 'INS-2026-00125',
        establishment: 'SunFresh Depot — Kurichi',
        location: 'Kurichi Industrial Estate, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.complaint,
        priority: Priority.high,
        status: InspectionStatus.assigned,
        assignedOn: _at(1, 16),
        dueOn: _at(0, 18),
        updatedAt: _at(1, 16),
        checklist: blank,
      ),
      Inspection(
        id: 'INS-2026-00126',
        establishment: 'Sri Balaji Super Market',
        location: 'Saibaba Colony, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.routine,
        priority: Priority.medium,
        status: InspectionStatus.assigned,
        assignedOn: _at(0, 8),
        dueOn: _at(-1, 18),
        updatedAt: _at(0, 8),
        checklist: blank,
      ),
      Inspection(
        id: 'INS-2026-00127',
        establishment: 'Grainway Wholesale Mandi',
        location: 'Mettupalayam Road, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.drive,
        priority: Priority.low,
        status: InspectionStatus.assigned,
        assignedOn: _at(0, 8),
        dueOn: _at(-3, 18),
        updatedAt: _at(0, 8),
        checklist: blank,
      ),

      // --- In progress ---------------------------------------------------
      Inspection(
        id: 'INS-2026-00121',
        establishment: 'PureDrop Distribution Centre',
        location: 'Peelamedu, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.followUp,
        priority: Priority.medium,
        status: InspectionStatus.inProgress,
        assignedOn: _at(1, 10),
        dueOn: _at(0, 18),
        updatedAt: _at(0, 10, 45),
        product: drinkingWater,
        checklist: <ChecklistItem>[
          blank[0].copyWith(result: CheckResult.compliant),
          blank[1].copyWith(result: CheckResult.compliant),
          blank[2].copyWith(result: CheckResult.compliant),
          blank[3].copyWith(result: CheckResult.compliant),
          blank[4].copyWith(result: CheckResult.compliant),
          blank[5].copyWith(
            result: CheckResult.nonCompliant,
            note: 'Packing date printed across the bottle seam, not readable.',
          ),
          blank[6],
          blank[7],
        ],
        evidence: <EvidenceItem>[
          EvidenceItem(
            id: 'EV-0431',
            kind: EvidenceKind.photo,
            label: 'Principal display panel',
            capturedAt: _at(0, 10, 32),
            description: 'Full panel captured under shelf lighting.',
            paletteSeed: 1,
          ),
          EvidenceItem(
            id: 'EV-0432',
            kind: EvidenceKind.photo,
            label: 'Packing date region',
            capturedAt: _at(0, 10, 38),
            description: 'Ink-jet code falls across the moulding seam.',
            paletteSeed: 2,
          ),
        ],
      ),

      // --- Draft ----------------------------------------------------------
      Inspection(
        id: 'INS-2026-00118',
        establishment: 'ABC Foods Retail Outlet',
        location: 'RS Puram, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.routine,
        priority: Priority.high,
        status: InspectionStatus.draft,
        assignedOn: _at(2, 9),
        dueOn: _at(0, 18),
        updatedAt: _at(0, 10, 45),
        product: instantNoodles,
        checklist: <ChecklistItem>[
          blank[0].copyWith(result: CheckResult.compliant),
          blank[1].copyWith(result: CheckResult.compliant),
          blank[2].copyWith(
            result: CheckResult.nonCompliant,
            note: 'Net quantity reduced to 280 g while the price is unchanged.',
          ),
          blank[3].copyWith(result: CheckResult.compliant),
          blank[4].copyWith(result: CheckResult.compliant),
          blank[5],
          blank[6],
          blank[7],
        ],
        findings: <Finding>[
          Finding(
            id: 'FND-0219',
            sequence: 1,
            ruleRef: 'LMPC-QTY-002',
            ruleName: 'Standard unit of net quantity',
            severity: Severity.high,
            description:
                'Declared net quantity reduced from 320 g to 280 g with no revision to the printed retail sale price.',
            evidenceIds: <String>['EV-0417'],
          ),
        ],
        evidence: <EvidenceItem>[
          EvidenceItem(
            id: 'EV-0417',
            kind: EvidenceKind.photo,
            label: 'Net quantity declaration',
            capturedAt: _at(0, 10, 21),
            description: 'Reduced pack size against the earlier record.',
            paletteSeed: 3,
          ),
        ],
        officerNotes: 'Premises manager informed. Awaiting stock register copy.',
      ),
      Inspection(
        id: 'INS-2026-00115',
        establishment: 'DailyCare Stockist — Ganapathy',
        location: 'Ganapathy, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.routine,
        priority: Priority.medium,
        status: InspectionStatus.draft,
        assignedOn: _at(3, 9),
        dueOn: _at(-1, 18),
        updatedAt: _at(1, 16, 10),
        product: shampoo,
        checklist: <ChecklistItem>[
          blank[0].copyWith(result: CheckResult.compliant),
          blank[1].copyWith(result: CheckResult.compliant),
          blank[2].copyWith(result: CheckResult.compliant),
          blank[3].copyWith(
            result: CheckResult.nonCompliant,
            note: 'PIN code absent from the packer address.',
          ),
          blank[4],
          blank[5],
          blank[6],
          blank[7],
        ],
        evidence: <EvidenceItem>[
          EvidenceItem(
            id: 'EV-0402',
            kind: EvidenceKind.photo,
            label: 'Packer address block',
            capturedAt: _at(1, 15, 58),
            paletteSeed: 4,
          ),
        ],
      ),

      // --- Submitted / completed -------------------------------------------
      Inspection(
        id: 'INS-2026-00109',
        establishment: 'Grainway Agro Mills Outlet',
        location: 'Mettupalayam Road, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.routine,
        priority: Priority.high,
        status: InspectionStatus.submitted,
        assignedOn: _at(4, 9),
        dueOn: _at(3, 18),
        updatedAt: _at(3, 15, 20),
        submittedAt: _at(3, 15, 20),
        product: basmatiRice,
        checklist: _allCompliantExcept(blank, <int>[1]),
        findings: <Finding>[
          Finding(
            id: 'FND-0205',
            sequence: 1,
            ruleRef: 'LMPC-PRC-003',
            ruleName: 'Retail sale price inclusivity',
            severity: Severity.critical,
            description:
                'Secondary price sticker applied over the printed retail sale price.',
            evidenceIds: <String>['EV-0388'],
          ),
        ],
        evidence: <EvidenceItem>[
          EvidenceItem(
            id: 'EV-0388',
            kind: EvidenceKind.photo,
            label: 'Over-stickered price panel',
            capturedAt: _at(3, 14, 50),
            description: 'Adhesive label over the printed price.',
            paletteSeed: 5,
          ),
          EvidenceItem(
            id: 'EV-0389',
            kind: EvidenceKind.photo,
            label: 'Shelf display',
            capturedAt: _at(3, 14, 55),
            paletteSeed: 6,
          ),
        ],
        officerNotes: 'Sample retained. Compounding notice recommended.',
      ),
      Inspection(
        id: 'INS-2026-00104',
        establishment: 'FreshBite Retail — Saibaba Colony',
        location: 'Saibaba Colony, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.followUp,
        priority: Priority.medium,
        status: InspectionStatus.completed,
        assignedOn: _at(6, 9),
        dueOn: _at(5, 18),
        updatedAt: _at(5, 11, 30),
        submittedAt: _at(5, 11, 30),
        product: biscuits,
        checklist: _allCompliantExcept(blank, <int>[]),
        evidence: <EvidenceItem>[
          EvidenceItem(
            id: 'EV-0361',
            kind: EvidenceKind.photo,
            label: 'Principal display panel',
            capturedAt: _at(5, 11, 12),
            paletteSeed: 7,
          ),
        ],
        officerNotes: 'Declarations verified. No further action required.',
      ),
      Inspection(
        id: 'INS-2026-00098',
        establishment: 'SunFresh Retail Counter',
        location: 'Town Hall, Coimbatore',
        zone: 'Coimbatore North',
        type: InspectionType.complaint,
        priority: Priority.high,
        status: InspectionStatus.completed,
        assignedOn: _at(9, 9),
        dueOn: _at(8, 18),
        updatedAt: _at(8, 16, 5),
        submittedAt: _at(8, 16, 5),
        product: sunflowerOil,
        checklist: _allCompliantExcept(blank, <int>[0]),
        findings: <Finding>[
          Finding(
            id: 'FND-0188',
            sequence: 1,
            ruleRef: 'LMPC-DECL-001',
            ruleName: 'Consumer care declaration',
            severity: Severity.high,
            status: FindingStatus.resolved,
            description: 'Consumer care contact block absent from the back panel.',
            evidenceIds: <String>['EV-0330'],
          ),
        ],
        evidence: <EvidenceItem>[
          EvidenceItem(
            id: 'EV-0330',
            kind: EvidenceKind.photo,
            label: 'Back panel',
            capturedAt: _at(8, 15, 40),
            paletteSeed: 8,
          ),
        ],
      ),
    ];
  }

  static List<ChecklistItem> _allCompliantExcept(
    List<ChecklistItem> template,
    List<int> failedIndexes,
  ) {
    final List<ChecklistItem> result = <ChecklistItem>[];
    for (int i = 0; i < template.length; i++) {
      result.add(
        template[i].copyWith(
          result: failedIndexes.contains(i)
              ? CheckResult.nonCompliant
              : CheckResult.compliant,
        ),
      );
    }
    return result;
  }
}
