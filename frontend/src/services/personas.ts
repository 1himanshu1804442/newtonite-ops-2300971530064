import { UserPersona } from '../types';

// Pre-seeded personas to demonstrate RBAC and permissions live
export const SEEDED_PERSONAS: UserPersona[] = [
  {
    id: 'user-1',
    name: 'Alice Chen',
    email: 'alice.chen@newtonite.io',
    role: 'LEAD',
    team: 'OPERATIONS',
    avatarInitials: 'AC',
    description: 'Operations Team Lead (Full review & approval permissions for Operations)',
  },
  {
    id: 'user-2',
    name: 'Bob Smith',
    email: 'bob.smith@newtonite.io',
    role: 'MEMBER',
    team: 'OPERATIONS',
    avatarInitials: 'BS',
    description: 'Operations Specialist (Can claim and work on items; requires Lead approval to resolve)',
  },
  {
    id: 'user-3',
    name: 'Charlie Davis',
    email: 'charlie.davis@newtonite.io',
    role: 'VIEWER',
    team: 'OPERATIONS',
    avatarInitials: 'CD',
    description: 'Operations Auditor (Read-only observer; mutation controls disabled)',
  },
  {
    id: 'user-4',
    name: 'Diana Prince',
    email: 'diana.prince@newtonite.io',
    role: 'LEAD',
    team: 'PAYMENTS',
    avatarInitials: 'DP',
    description: 'Payments Team Lead (Approves critical settlement & freeze tickets)',
  },
  {
    id: 'user-5',
    name: 'Evan Wright',
    email: 'evan.wright@newtonite.io',
    role: 'MEMBER',
    team: 'PAYMENTS',
    avatarInitials: 'EW',
    description: 'Payments Investigator (Triages payment dispute incidents)',
  },
  {
    id: 'user-6',
    name: 'Fiona Gallagher',
    email: 'fiona.gallagher@newtonite.io',
    role: 'ADMIN',
    team: 'ALL',
    avatarInitials: 'FG',
    description: 'Global Compliance Admin (Full administrative rights across all team domains)',
  },
];

// Helper to retrieve the default persona (Alice Chen, Lead)
export const DEFAULT_PERSONA: UserPersona = SEEDED_PERSONAS[0];
