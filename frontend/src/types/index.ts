// User Role hierarchy matching backend RBAC rules
export type UserRole = 'LEAD' | 'MEMBER' | 'VIEWER' | 'ADMIN';

// Operational Team domains
export type Team = 'OPERATIONS' | 'PAYMENTS' | 'COMPLIANCE' | 'SECURITY';

// Predefined user persona for live role testing
export interface UserPersona {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  team: Team | 'ALL';
  avatarInitials: string;
  description: string;
}

// Work item lifecycle statuses enforced by the state machine
export type WorkItemStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'AWAITING_APPROVAL'
  | 'RESOLVED'
  | 'CLOSED';

// Urgency levels for operational queue triage
export type Priority = 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW';

// Core operational Work Item entity
export interface WorkItem {
  id: string;
  ticketNumber: string;
  title: string;
  description: string;
  team: Team;
  priority: Priority;
  status: WorkItemStatus;
  assigneeId: string | null;
  assigneeName: string | null;
  tags: string[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

// Activity types recorded in the append-only audit timeline
export type ActivityType =
  | 'CREATED'
  | 'CLAIMED'
  | 'STATUS_CHANGE'
  | 'COMMENT'
  | 'ASSIGNED';

export interface ActivityItem {
  id: string;
  workItemId: string;
  type: ActivityType;
  authorName: string;
  authorRole: UserRole;
  content: string;
  fromStatus?: WorkItemStatus;
  toStatus?: WorkItemStatus;
  createdAt: string;
}

// DTO for creating a new work item with Idempotency Key protection
export interface CreateWorkItemRequest {
  title: string;
  description: string;
  team: Team;
  priority: Priority;
  tags: string[];
}

// Available dashboard tab filters
export type DashboardTab =
  | 'ALL'
  | 'ASSIGNED_TO_ME'
  | 'UNASSIGNED_URGENT'
  | 'AWAITING_APPROVAL';

// Filter state for dashboard querying
export interface FilterState {
  tab: DashboardTab;
  search: string;
  team: string; // 'ALL' or Team
  priority: string; // 'ALL' or Priority
  status: string; // 'ALL' or WorkItemStatus
}

// Conflict error payload returned on 409 responses
export interface ConflictErrorPayload {
  errorCode: string;
  message: string;
  claimedBy?: string;
  claimedAt?: string;
  currentVersion?: number;
  freshItem?: WorkItem;
}
