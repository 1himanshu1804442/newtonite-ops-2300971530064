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
      const match = SEEDED_PERSONAS.find(
        (p) => p.id === parsed.id || p.email === parsed.email || p.name === parsed.name
      );
      if (match) {
        // If the ID was updated, synchronize back to localStorage
        if (parsed.id !== match.id) {
          localStorage.setItem(PERSONA_STORAGE_KEY, JSON.stringify(match));
        }
        return match;
      }
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

// Helper to build headers with active persona info
function getAuthHeaders(idempotencyKey?: string): HeadersInit {
  const persona = getStoredPersona();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-User-Id': persona.id,
    'X-User-Name': persona.name,
    'X-User-Role': persona.role,
  };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return headers;
}

// Helper to map backend WorkItemResponse to frontend WorkItem interface
function mapToWorkItem(item: any): WorkItem {
  const teamCode = item.team?.code || 'OPS';
  const shortId = (item.id || '').replace(/-/g, '').slice(0, 4).toUpperCase();
  return {
    id: item.id,
    ticketNumber: `${teamCode}-${shortId}`,
    title: item.title,
    description: item.description,
    team: (item.team?.name?.includes('Security')
      ? 'SECURITY'
      : item.team?.name?.includes('Data')
      ? 'COMPLIANCE'
      : item.team?.name?.includes('Reliability')
      ? 'PAYMENTS'
      : 'OPERATIONS') as any,
    priority: item.priority === 'CRITICAL' ? 'URGENT' : item.priority,
    status: item.status,
    assigneeId: item.assignee?.id || null,
    assigneeName: item.assignee?.name || null,
    tags: [item.type, item.priority, teamCode].filter(Boolean),
    version: typeof item.version === 'number' ? item.version : 0,
    createdAt: item.createdAt || new Date().toISOString(),
    updatedAt: item.updatedAt || new Date().toISOString(),
  };
}

// ============================================================================
// API Service Implementation
// Connected directly to Spring Boot backend on /api
// ============================================================================

export const api = {
  /**
   * Fetch work items according to filters and active tab directly from backend
   */
  async fetchWorkItems(filters: FilterState): Promise<WorkItem[]> {
    const activePersona = getStoredPersona();
    try {
      const queryParams = new URLSearchParams();
      queryParams.set('size', '50');

      // Only pass valid enums to backend if not 'ALL'
      if (filters.status && filters.status !== 'ALL') {
        queryParams.set('status', filters.status);
      }

      if (filters.priority && filters.priority !== 'ALL') {
        queryParams.set(
          'priority',
          filters.priority === 'URGENT' ? 'CRITICAL' : filters.priority
        );
      }

      // Handle tab-specific filtering
      if (filters.tab === 'ASSIGNED_TO_ME') {
        queryParams.set('assigneeId', activePersona.id);
      } else if (filters.tab === 'AWAITING_APPROVAL') {
        queryParams.set('status', 'AWAITING_APPROVAL');
      }

      const response = await fetch(`${BASE_URL}/work-items?${queryParams.toString()}`, {
        method: 'GET',
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      const rawList: any[] = Array.isArray(data) ? data : data.content || [];
      let result = rawList.map(mapToWorkItem);

      // Frontend tab refinement
      if (filters.tab === 'UNASSIGNED_URGENT') {
        result = result.filter(
          (item) => item.assigneeId === null && (item.priority === 'URGENT' || item.priority === 'HIGH')
        );
      } else if (filters.tab === 'ASSIGNED_TO_ME') {
        result = result.filter((item) => item.assigneeId === activePersona.id);
      }

      // Filter by Team
      if (filters.team && filters.team !== 'ALL') {
        result = result.filter((item) => item.team === filters.team);
      }

      // Filter by Priority (if set)
      if (filters.priority && filters.priority !== 'ALL') {
        result = result.filter((item) => item.priority === filters.priority);
      }

      // Filter by Status (if set)
      if (filters.status && filters.status !== 'ALL') {
        result = result.filter((item) => item.status === filters.status);
      }

      // Search filter
      if (filters.search && filters.search.trim()) {
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

      return result;
    } catch (error) {
      console.error('[NewtoniteAPI] Remote fetch failed:', error);
      throw error;
    }
  },

  /**
   * Fetch a single work item by ID
   */
  async fetchWorkItemById(id: string): Promise<WorkItem> {
    const response = await fetch(`${BASE_URL}/work-items/${id}`, {
      method: 'GET',
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch work item ${id}: HTTP ${response.status}`);
    }

    const data = await response.json();
    return mapToWorkItem(data);
  },

  /**
   * Create a new work item with Idempotency Key protection
   */
  async createWorkItem(data: CreateWorkItemRequest, idempotencyKey: string): Promise<WorkItem> {
    const activePersona = getStoredPersona();
    // Default team ID for Platform Engineering in DataSeeder
    const defaultTeamId = '2a216dbd-2ce4-4a72-8dcf-3b8caf6d45ac';

    const payload = {
      title: data.title,
      description: data.description,
      type: 'TASK',
      priority: data.priority === 'URGENT' ? 'CRITICAL' : data.priority,
      teamId: defaultTeamId,
      creatorId: activePersona.id,
    };

    const response = await fetch(`${BASE_URL}/work-items`, {
      method: 'POST',
      headers: getAuthHeaders(idempotencyKey),
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error(`Failed to create work item: HTTP ${response.status}`);
    }

    const created = await response.json();
    return mapToWorkItem(created);
  },

  /**
   * Atomically claim a work item for the active user via backend
   */
  async claimWorkItem(id: string, _expectedVersion: number): Promise<WorkItem> {
    const activePersona = getStoredPersona();

    if (activePersona.role === 'VIEWER') {
      throw new ForbiddenError('Auditors with VIEWER role have read-only privileges.');
    }

    const response = await fetch(`${BASE_URL}/work-items/${id}/claim`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ userId: activePersona.id }),
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

    const claimed = await response.json();
    return mapToWorkItem(claimed);
  },

  /**
   * Transition work item status enforcing state machine and OCC on backend
   */
  async transitionStatus(
    id: string,
    newStatus: WorkItemStatus,
    expectedVersion: number
  ): Promise<WorkItem> {
    const activePersona = getStoredPersona();

    if (activePersona.role === 'VIEWER') {
      throw new ForbiddenError('Auditors with VIEWER role have read-only privileges.');
    }

    if (
      (newStatus === 'RESOLVED' || newStatus === 'CLOSED') &&
      activePersona.role === 'MEMBER'
    ) {
      throw new ForbiddenError('Approval requires Team Lead or Admin privilege.');
    }

    const response = await fetch(`${BASE_URL}/work-items/${id}/transition`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({
        targetStatus: newStatus,
        actorId: activePersona.id,
        expectedVersion: expectedVersion,
        reason: 'Transition via operations console',
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
      const body = await response.json().catch(() => null);
      const msg = body?.message || 'Approval requires Team Lead or Admin privilege.';
      throw new ForbiddenError(msg);
    }

    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || `Status transition failed: HTTP ${response.status}`);
    }

    const transitioned = await response.json();
    return mapToWorkItem(transitioned);
  },

  /**
   * Fetch activity timeline and comments for a work item from backend audit ledger
   */
  async fetchActivities(workItemId: string): Promise<ActivityItem[]> {
    const response = await fetch(`${BASE_URL}/work-items/${workItemId}/activities`, {
      method: 'GET',
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error(`Failed to load activities: HTTP ${response.status}`);
    }

    const rawActivities: any[] = await response.json();
    return rawActivities.map((a) => ({
      id: a.id,
      workItemId: a.workItemId,
      type: a.actionType === 'STATUS_CHANGED' ? 'STATUS_CHANGE' : a.actionType,
      authorName: a.actor?.name || 'System',
      authorRole: 'MEMBER',
      content: a.description,
      fromStatus: a.previousState as WorkItemStatus,
      toStatus: a.newState as WorkItemStatus,
      createdAt: a.createdAt,
    }));
  },

  /**
   * Post an operational comment
   */
  async addComment(workItemId: string, content: string): Promise<ActivityItem> {
    const activePersona = getStoredPersona();
    return {
      id: `act-${Date.now()}`,
      workItemId,
      type: 'COMMENT',
      authorName: activePersona.name,
      authorRole: activePersona.role,
      content,
      createdAt: new Date().toISOString(),
    };
  },
};
