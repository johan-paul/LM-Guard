/**
 * Static reference data for inspector-related dropdowns.
 *
 * The inspector directory itself now comes from the real backend
 * (`GET /api/inspectors`, `GET /api/inspectors/zones`) - see `services/inspectionService.js`.
 * `ZONES` here is a fixed selector list matching the zones the backend seeds on
 * startup (`DemoZoneSeeder`); it stays a static list because there is no
 * "create/edit zone" screen to source it from live data instead.
 */

export const ZONES = [
  { id: 'ZN-01', name: 'Coimbatore North', code: 'CBE-N', office: 'Zonal Office, Gandhipuram', district: 'Coimbatore' },
  { id: 'ZN-02', name: 'Coimbatore South', code: 'CBE-S', office: 'Zonal Office, Sundarapuram', district: 'Coimbatore' },
  { id: 'ZN-03', name: 'Coimbatore West', code: 'CBE-W', office: 'Zonal Office, Thadagam Road', district: 'Coimbatore' },
  { id: 'ZN-04', name: 'Tiruppur', code: 'TUP', office: 'District Office, Kumaran Road', district: 'Tiruppur' },
  { id: 'ZN-05', name: 'Erode', code: 'ERD', office: 'District Office, Perundurai Road', district: 'Erode' },
];

export const ZONE_NAMES = ZONES.map((z) => z.name);

export const INSPECTOR_RANKS = ['Senior Inspector', 'Inspecting Officer', 'Field Inspector'];
