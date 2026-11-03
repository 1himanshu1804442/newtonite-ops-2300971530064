import { UserPersona } from '../types';

// Pre-seeded personas matching backend DataSeeder exactly
export const SEEDED_PERSONAS: UserPersona[] = [
  {
    id: '9f6b8384-35a1-4963-949d-f85177d162bf',
    name: 'Elena Rostova',
    email: 'elena.rostova@newtonite.io',
    role: 'LEAD',
    team: 'OPERATIONS',
    avatarInitials: 'ER',
    description: 'Platform Engineering Lead (Full review & approval permissions for Platform)',
  },
  {
    id: '1c435d43-93ec-440d-beb3-66b6fcc07eb7',
    name: 'Marcus Chen',
    email: 'marcus.chen@newtonite.io',
    role: 'MEMBER',
    team: 'OPERATIONS',
    avatarInitials: 'MC',
    description: 'Platform Specialist (Can claim and work on items; requires Lead approval to resolve)',
  },
  {
    id: 'd1d02db7-3b03-499e-b647-b49b2decb049',
    name: 'James Wilson',
    email: 'james.wilson@newtonite.io',
    role: 'VIEWER',
    team: 'SECURITY',
    avatarInitials: 'JW',
    description: 'Security Operations Auditor (Read-only observer; mutation controls disabled)',
  },
  {
    id: '36b7470c-fa49-46f2-89dd-75be7f02ece3',
    name: 'Priya Sharma',
    email: 'priya.sharma@newtonite.io',
    role: 'LEAD',
    team: 'SECURITY',
    avatarInitials: 'PS',
    description: 'Security Operations Lead (Approves critical security & compliance incidents)',
  },
  {
    id: 'b9c1115c-0cbc-44f2-96ac-57c009eb175d',
    name: 'Alex Rivera',
    email: 'alex.rivera@newtonite.io',
    role: 'ADMIN',
    team: 'ALL',
    avatarInitials: 'AR',
    description: 'Global Engineering Admin (Full administrative rights across all team domains)',
  },
  {
    id: '5eb66c45-95d1-43aa-a5bc-49ef0a399fa3',
    name: 'Sarah Connor',
    email: 'sarah.connor@newtonite.io',
    role: 'ADMIN',
    team: 'ALL',
    avatarInitials: 'SC',
    description: 'SRE Reliability Admin (Full operational and approval access)',
  },
];

// Helper to retrieve default persona (Elena Rostova, Lead)
export const DEFAULT_PERSONA: UserPersona = SEEDED_PERSONAS[0];
