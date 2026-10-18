import {
  WorkItem,
  ActivityItem,
  CreateWorkItemRequest,
  FilterState,
  UserPersona,
  WorkItemStatus,
  ConflictErrorPayload,
} from '../types';
import { DEFAULT_PERSONA, SEEDED_PERSONAS } from './personas';

const BASE_URL = '/api';

// Current active persona storage key
const PERSONA_STORAGE_KEY = 'newtonite_active_persona';

// Retrieve active persona from localStorage or fallback to default
export function getStoredPersona(): UserPersona {
  try {
    const raw = localStorage.getItem(PERSONA_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      const match = SEEDED_PERSONAS.find((p) => p.id === parsed.id);
      if (match) return match;
    }
  } catch (err) {
    console.error('[NewtoniteAPI] Failed to parse active persona from storage:', err);
  }
  return DEFAULT_PERSONA;
}

// Persist active persona to localStorage
export function setStoredPersona(persona: UserPersona): void {
  try {
    localStorage.setItem(PERSONA_STORAGE_KEY, JSON.stringify(persona));
  } catch (err) {
    console.error('[NewtoniteAPI] Failed to persist active persona:', err);
  }
}

// Custom error class representing concurrency collisions (HTTP 409)
export class ConcurrencyConflictError extends Error {
  public payload?: ConflictErrorPayload;
  constructor(message: string, payload?: ConflictErrorPayload) {
    super(message);
    this.name = 'ConcurrencyConflictError';
    this.payload = payload;
  }
}

// Custom error class representing authorization rejection (HTTP 403)
export class ForbiddenError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ForbiddenError';
  }
}

// ============================================================================
// Fallback Mock In-Memory Store
// Used when backend service is unreachable, ensuring instant UI testing and
// realistic simulation of OCC (409 Conflict) and RBAC rules.
// ============================================================================
let mockWorkItems: WorkItem[] = [
  {
    id: 'wi-101',
    ticketNumber: 'NWT-1042',
    title: 'High-Value Payment Settlement Discrepancy ($2.4M)',
    description: 'Automated reconciliation detected unmatched wire transfer batch for European merchant accounts. Immediate audit and manual hold required.',
    team: 'PAYMENTS',
    priority: 'URGENT',
    status: 'OPEN',
    assigneeId: null,
    assigneeName: null,
    tags: ['Payment Freeze', 'VIP Partner', 'High Value', 'Audit Required'],
    version: 1,
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
  {
    id: 'wi-102',
    ticketNumber: 'NWT-1043',
    title: 'Suspicious API Key Spike & Velocity Threshold Alert',
    description: 'Account #8812 experienced 15,000 auth attempts in 60 seconds from distributed IP ranges. Automated throttle triggered.',
    team: 'SECURITY',
    priority: 'URGENT',
    status: 'IN_PROGRESS',
    assigneeId: 'user-2',
    assigneeName: 'Bob Smith',
    tags: ['Security', 'Rate Limit', 'Investigating'],
    version: 3,
    createdAt: new Date(Date.now() - 3600000 * 5).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 1).toISOString(),
  },
  {
    id: 'wi-103',
    ticketNumber: 'NWT-1044',
    title: 'Quarterly KYC Re-Verification Batch Approval',
    description: '14 Tier-1 enterprise customers submitted beneficial ownership documentation. Verified by compliance analyst; awaiting Lead sign-off.',
    team: 'COMPLIANCE',
    priority: 'HIGH',
    status: 'AWAITING_APPROVAL',
    assigneeId: 'user-2',
    assigneeName: 'Bob Smith',
    tags: ['KYC', 'Compliance', 'Sign-Off Needed'],
    version: 4,
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 7200000).toISOString(),
  },
  {
    id: 'wi-104',
    ticketNumber: 'NWT-1045',
    title: 'Database Replica Replication Lag Warning (>450ms)',
    description: 'Read-replica DB-03 in eu-central-1 region is reporting delayed WAL replay. High read load from analytics queries suspected.',
    team: 'OPERATIONS',
    priority: 'MEDIUM',
    status: 'RESOLVED',
    assigneeId: 'user-1',
    assigneeName: 'Alice Chen',
    tags: ['Infrastructure', 'PostgreSQL', 'Performance'],
    version: 5,
    createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    updatedAt: new Date(Date.now() - 14400000).toISOString(),
  },
  {
    id: 'wi-105',
    ticketNumber: 'NWT-1046',
    title: 'Unassigned Critical Outage: Webhook Relay Deadlock',
    description: 'Outbound merchant webhook dispatcher worker pool is depleted. Over 1,200 pending events queued in outbox table.',
    team: 'OPERATIONS',
    priority: 'URGENT',
    status: 'OPEN',
    assigneeId: null,
    assigneeName: null,
    tags: ['Outbox', 'Critical Incident', 'Webhooks'],
    version: 1,
    createdAt: new Date(Date.now() - 1800000).toISOString(),
    updatedAt: new Date(Date.now() - 1800000).toISOString(),
  },
];

let mockActivities: ActivityItem[] = [
  {
    id: 'act-1',
    workItemId: 'wi-101',
    type: 'CREATED',
    authorName: 'System Monitor',
    authorRole: 'ADMIN',
    content: 'Work item created from automated anomaly detector stream.',
    toStatus: 'OPEN',
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
  {
    id: 'act-2',
    workItemId: 'wi-102',
    type: 'CREATED',
    authorName: 'Security Automation',
    authorRole: 'ADMIN',
    content: 'Incident flagged by rate-limiting sentinel.',
    toStatus: 'OPEN',
    createdAt: new Date(Date.now() - 3600000 * 5).toISOString(),
  },
  {
    id: 'act-3',
    workItemId: 'wi-102',
    type: 'CLAIMED',
    authorName: 'Bob Smith',
    authorRole: 'MEMBER',
    content: 'Claimed ticket for immediate network inspection.',
    createdAt: new Date(Date.now() - 3600000 * 4).toISOString(),
  },
  {
    id: 'act-4',
    workItemId: 'wi-102',
    type: 'STATUS_CHANGE',
    authorName: 'Bob Smith',
    authorRole: 'MEMBER',
    content: 'Transitioned status to IN_PROGRESS.',
    fromStatus: 'OPEN',
    toStatus: 'IN_PROGRESS',
    createdAt: new Date(Date.now() - 3600000 * 4).toISOString(),
  },
  {
    id: 'act-5',
    workItemId: 'wi-102',
    type: 'COMMENT',
    authorName: 'Bob Smith',
    authorRole: 'MEMBER',
    content: 'Blocked offending IP range /24 at firewall boundary. Monitoring traffic degradation.',
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
  {
    id: 'act-6',
    workItemId: 'wi-103',
    type: 'STATUS_CHANGE',
    authorName: 'Bob Smith',
    authorRole: 'MEMBER',
    content: 'Verification checklist completed. Submitting for Team Lead sign-off.',
    fromStatus: 'IN_PROGRESS',
    toStatus: 'AWAITING_APPROVAL',
    createdAt: new Date(Date.now() - 7200000).toISOString(),
  },
];

// Cache for idempotency keys to demonstrate duplicate protection in mock mode
const mockIdempotencyStore = new Set<string>();

// Helper to build headers with active persona info
function getAuthHeaders(idempotencyKey?: string): HeadersInit {
  const persona = getStoredPersona();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-User-Id': persona.id,
    'X-User-Name': persona.name,
    'X-User-Role': persona.role,
    'X-User-Team': persona.team,
  };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return headers;
}

// ============================================================================
// API Service Implementation
// Strictly encapsulates all network operations and error logging
// ============================================================================

export const api = {
  /**
   * Fetch work items according to filters and active tab
   */
  async fetchWorkItems(filters: FilterState): Promise<WorkItem[]> {
    const activePersona = getStoredPersona();
    try {
      const queryParams = new URLSearchParams({
        tab: filters.tab,
        search: filters.search,
        team: filters.team,
        priority: filters.priority,
        status: filters.status,
      });

      const response = await fetch(`${BASE_URL}/work-items?${queryParams.toString()}`, {
        method: 'GET',
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}: ${response.statusText}`);
      }

      const data: WorkItem[] = await response.json();
      return data;
    } catch (error) {
      console.warn('[NewtoniteAPI] Remote fetch failed, falling back to local dataset:', error);

      // Local filtering logic matching backend criteria
      let result = [...mockWorkItems];

      // Tab filtering
      if (filters.tab === 'ASSIGNED_TO_ME') {
        result = result.filter((item) => item.assigneeId === activePersona.id);
      } else if (filters.tab === 'UNASSIGNED_URGENT') {
        result = result.filter((item) => item.assigneeId === null && item.priority === 'URGENT');
      } else if (filters.tab === 'AWAITING_APPROVAL') {
        result = result.filter((item) => item.status === 'AWAITING_APPROVAL');
      }

      // Team filter
      if (filters.team !== 'ALL') {
        result = result.filter((item) => item.team === filters.team);
      }

      // Priority filter
      if (filters.priority !== 'ALL') {
        result = result.filter((item) => item.priority === filters.priority);
      }

      // Status filter
      if (filters.status !== 'ALL') {
        result = result.filter((item) => item.status === filters.status);
      }

      // Search filter
      if (filters.search.trim()) {
        const query = filters.search.toLowerCase().trim();
        result = result.filter(
          (item) =>
            item.title.toLowerCase().includes(query) ||
            item.ticketNumber.toLowerCase().includes(query) ||
            item.description.toLowerCase().includes(query) ||
            item.tags.some((t) => t.toLowerCase().includes(query)) ||
            (item.assigneeName && item.assigneeName.toLowerCase().includes(query))
        );
      }

      // Sort by newest updated
      result.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
      return result;
    }
  },

  /**
   * Fetch a single work item by ID
   */
  async fetchWorkItemById(id: string): Promise<WorkItem> {
    try {
      const response = await fetch(`${BASE_URL}/work-items/${id}`, {
        method: 'GET',
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch work item ${id}: HTTP ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.warn(`[NewtoniteAPI] Remote fetch item ${id} failed, checking local mock:`, error);
      const found = mockWorkItems.find((w) => w.id === id);
      if (!found) {
        throw new Error(`Work item with id ${id} not found.`);
      }
      return { ...found };
    }
  },

  /**
   * Create a new work item with Idempotency Key protection
   */
  async createWorkItem(data: CreateWorkItemRequest, idempotencyKey: string): Promise<WorkItem> {
    const activePersona = getStoredPersona();
    try {
      const response = await fetch(`${BASE_URL}/work-items`, {
        method: 'POST',
        headers: getAuthHeaders(idempotencyKey),
        body: JSON.stringify(data),
      });

      if (!response.ok) {
        throw new Error(`Failed to create work item: HTTP ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.warn('[NewtoniteAPI] Remote createWorkItem failed, executing in local mock:', error);

      // Check Idempotency Key in local mock
      if (mockIdempotencyStore.has(idempotencyKey)) {
        console.info(`[NewtoniteAPI] Idempotent request detected for key: ${idempotencyKey}`);
        // Return existing item with highest ticket ID
        return { ...mockWorkItems[0] };
      }
      mockIdempotencyStore.add(idempotencyKey);

      const nextNum = 1040 + mockWorkItems.length + 1;
      const newItem: WorkItem = {
        id: `wi-${Date.now()}`,
        ticketNumber: `NWT-${nextNum}`,
        title: data.title,
        description: data.description,
        team: data.team,
        priority: data.priority,
        status: 'OPEN',
        assigneeId: null,
        assigneeName: null,
        tags: data.tags,
        version: 1,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      mockWorkItems.unshift(newItem);

      // Record activity
      mockActivities.unshift({
        id: `act-${Date.now()}`,
        workItemId: newItem.id,
        type: 'CREATED',
        authorName: activePersona.name,
        authorRole: activePersona.role,
        content: `Created work item ${newItem.ticketNumber}.`,
        toStatus: 'OPEN',
        createdAt: new Date().toISOString(),
      });

      return newItem;
    }
  },

  /**
   * Atomically claim a work item for the active user
   * Verifies OCC and raises ConcurrencyConflictError if already claimed
   */
  async claimWorkItem(id: string, expectedVersion: number): Promise<WorkItem> {
    const activePersona = getStoredPersona();

    if (activePersona.role === 'VIEWER') {
      throw new ForbiddenError('Auditors with VIEWER role have read-only privileges.');
    }

    try {
      const response = await fetch(`${BASE_URL}/work-items/${id}/claim`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ expectedVersion }),
      });

      if (response.status === 409) {
        const conflictData: ConflictErrorPayload = await response.json().catch(() => ({
          errorCode: 'ITEM_ALREADY_CLAIMED',
          message: 'This work item has already been claimed by another team member.',
        }));
        throw new ConcurrencyConflictError(conflictData.message, conflictData);
      }

      if (response.status === 403) {
        throw new ForbiddenError('You are not authorized to claim this ticket.');
      }

      if (!response.ok) {
        throw new Error(`Claim failed with status ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      if (error instanceof ConcurrencyConflictError || error instanceof ForbiddenError) {
        throw error;
      }

      console.warn(`[NewtoniteAPI] Remote claim failed, running atomic claim locally:`, error);
      const item = mockWorkItems.find((w) => w.id === id);
      if (!item) {
        throw new Error(`Work item ${id} not found.`);
      }

      // Concurrency check 1: already claimed
      if (item.assigneeId !== null) {
        throw new ConcurrencyConflictError(
          `This work item has already been claimed by ${item.assigneeName}.`,
          {
            errorCode: 'ITEM_ALREADY_CLAIMED',
            message: `This work item has already been claimed by ${item.assigneeName}.`,
            claimedBy: item.assigneeName || 'Another user',
            claimedAt: item.updatedAt,
            currentVersion: item.version,
            freshItem: { ...item },
          }
        );
      }

      // Concurrency check 2: version mismatch
      if (item.version !== expectedVersion) {
        throw new ConcurrencyConflictError('This item was updated by another user. Reloading...', {
          errorCode: 'STALE_OBJECT_VERSION',
          message: 'This item was updated by another user. Reloading...',
          currentVersion: item.version,
          freshItem: { ...item },
        });
      }

      // Successful atomic assignment
      item.assigneeId = activePersona.id;
      item.assigneeName = activePersona.name;
      item.version += 1;
      item.updatedAt = new Date().toISOString();

      mockActivities.unshift({
        id: `act-${Date.now()}`,
        workItemId: item.id,
        type: 'CLAIMED',
        authorName: activePersona.name,
        authorRole: activePersona.role,
        content: `Claimed ticket ${item.ticketNumber} atomically.`,
        createdAt: new Date().toISOString(),
      });

      return { ...item };
    }
  },

  /**
   * Transition work item status enforcing state machine and OCC
   */
  async transitionStatus(
    id: string,
    newStatus: WorkItemStatus,
    expectedVersion: number
  ): Promise<WorkItem> {
    const activePersona = getStoredPersona();

    // Client-side quick RBAC check for instant user feedback
    if (activePersona.role === 'VIEWER') {
      throw new ForbiddenError('Auditors with VIEWER role have read-only privileges.');
    }

    if (
      (newStatus === 'RESOLVED' || newStatus === 'CLOSED') &&
      activePersona.role === 'MEMBER'
    ) {
      throw new ForbiddenError('Approval requires Team Lead or Admin privilege.');
    }

    try {
      const response = await fetch(`${BASE_URL}/work-items/${id}/status`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          status: newStatus,
          expectedVersion,
        }),
      });

      if (response.status === 409) {
        const conflictData: ConflictErrorPayload = await response.json().catch(() => ({
          errorCode: 'OPTIMISTIC_LOCK_COLLISION',
          message: 'This item was updated by another user. Reloading...',
        }));
        throw new ConcurrencyConflictError(conflictData.message, conflictData);
      }

      if (response.status === 403) {
        const msg = await response.text().catch(() => 'Approval requires Team Lead or Admin privilege.');
        throw new ForbiddenError(msg);
      }

      if (!response.ok) {
        throw new Error(`Status transition failed: HTTP ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      if (error instanceof ConcurrencyConflictError || error instanceof ForbiddenError) {
        throw error;
      }

      console.warn(`[NewtoniteAPI] Remote status transition failed, evaluating locally:`, error);
      const item = mockWorkItems.find((w) => w.id === id);
      if (!item) {
        throw new Error(`Work item ${id} not found.`);
      }

      // Check OCC version
      if (item.version !== expectedVersion) {
        throw new ConcurrencyConflictError('This item was updated by another user. Reloading...', {
          errorCode: 'STALE_OBJECT_VERSION',
          message: 'This item was updated by another user. Reloading...',
          currentVersion: item.version,
          freshItem: { ...item },
        });
      }

      const prevStatus = item.status;
      item.status = newStatus;
      item.version += 1;
      item.updatedAt = new Date().toISOString();

      mockActivities.unshift({
        id: `act-${Date.now()}`,
        workItemId: item.id,
        type: 'STATUS_CHANGE',
        authorName: activePersona.name,
        authorRole: activePersona.role,
        content: `Transitioned status from ${prevStatus} to ${newStatus}.`,
        fromStatus: prevStatus,
        toStatus: newStatus,
        createdAt: new Date().toISOString(),
      });

      return { ...item };
    }
  },

  /**
   * Fetch activity timeline and comments for a work item
   */
  async fetchActivities(workItemId: string): Promise<ActivityItem[]> {
    try {
      const response = await fetch(`${BASE_URL}/work-items/${workItemId}/activities`, {
        method: 'GET',
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to load activities: HTTP ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.warn(`[NewtoniteAPI] Remote fetch activities failed, returning local timeline:`, error);
      return mockActivities
        .filter((a) => a.workItemId === workItemId)
        .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
    }
  },

  /**
   * Add a comment to a work item
   */
  async addComment(workItemId: string, comment: string): Promise<ActivityItem> {
    const activePersona = getStoredPersona();
    if (!comment.trim()) {
      throw new Error('Comment cannot be empty.');
    }

    try {
      const response = await fetch(`${BASE_URL}/work-items/${workItemId}/comments`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ comment }),
      });

      if (!response.ok) {
        throw new Error(`Failed to add comment: HTTP ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.warn(`[NewtoniteAPI] Remote addComment failed, appending to local timeline:`, error);
      const newActivity: ActivityItem = {
        id: `act-${Date.now()}`,
        workItemId,
        type: 'COMMENT',
        authorName: activePersona.name,
        authorRole: activePersona.role,
        content: comment.trim(),
        createdAt: new Date().toISOString(),
      };

      mockActivities.push(newActivity);
      return newActivity;
    }
  },
};
