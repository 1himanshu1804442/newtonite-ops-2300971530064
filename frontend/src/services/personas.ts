import { UserPersona } from '../types';

// Pre-seeded personas matching backend PostgreSQL DataSeeder exactly
export const SEEDED_PERSONAS: UserPersona[] = [
  {
    id: '3c08224f-6862-45fa-aa35-0e01614593c1',
    name: 'Elena Rostova',
    email: 'elena.rostova@newtonite.io',
    role: 'LEAD',
    team: 'OPERATIONS',
    avatarInitials: 'ER',
    description: 'Platform Engineering Lead (Full review & approval permissions for Platform)',
  },
  {
    id: 'e3107915-d5cf-44fe-aeb3-aec600722ca5',
    name: 'Marcus Chen',
    email: 'marcus.chen@newtonite.io',
    role: 'MEMBER',
    team: 'OPERATIONS',
    avatarInitials: 'MC',
    description: 'Platform Specialist (Can claim and work on items; requires Lead approval to resolve)',
  },
  {
    id: '6776d326-fc6b-4a3b-bdb6-02cde94b0488',
    name: 'James Wilson',
    email: 'james.wilson@newtonite.io',
    role: 'VIEWER',
    team: 'SECURITY',
    avatarInitials: 'JW',
    description: 'Security Operations Auditor (Read-only observer; mutation controls disabled)',
  },
  {
    id: 'd38e8291-6df7-4c14-a8c3-17e6b228af28',
    name: 'Priya Sharma',
    email: 'priya.sharma@newtonite.io',
    role: 'LEAD',
    team: 'SECURITY',
    avatarInitials: 'PS',
    description: 'Security Operations Lead (Approves critical security & compliance incidents)',
  },
  {
    id: '0e678d13-eac0-487d-99f6-b1126709661a',
    name: 'Alex Rivera',
    email: 'alex.rivera@newtonite.io',
    role: 'ADMIN',
    team: 'ALL',
    avatarInitials: 'AR',
    description: 'Global Engineering Admin (Full administrative rights across all team domains)',
  },
  {
    id: '5ebc80da-3c57-432d-83ec-342ff2f9cf10',
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
