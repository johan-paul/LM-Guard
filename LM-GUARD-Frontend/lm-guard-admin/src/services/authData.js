/**
 * Reference data for the sign-in screen's demonstration-account panel.
 *
 * Authentication itself goes through the real backend (`POST /api/auth/login`
 * via AuthContext) — this file no longer performs any authentication of its
 * own. The account below must actually exist in the backend (created with
 * `POST /api/auth/register`, role `ADMIN`) for the "Use" button to work.
 */
export const DEMO_CREDENTIALS = [
  { label: 'Administrator', email: 'admin@lmguard.gov.in', password: 'admin123' },
];

export const ADMIN_CREDENTIALS = DEMO_CREDENTIALS;
