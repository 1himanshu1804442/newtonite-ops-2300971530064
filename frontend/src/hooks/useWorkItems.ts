import { useState, useEffect, useCallback, useMemo } from 'react';
import { WorkItem, FilterState, DashboardTab, WorkItemStatus } from '../types';
import { api, ConcurrencyConflictError, ForbiddenError } from '../services/api';
import { usePersona } from './usePersona';

const DEFAULT_FILTERS: FilterState = {
  tab: 'ALL',
  search: '',
  team: 'ALL',
  priority: 'ALL',
  status: 'ALL',
};

export function useWorkItems() {
  const { activePersona } = usePersona();
  const [items, setItems] = useState<WorkItem[]>([]);
  const [filters, setFilters] = useState<FilterState>(DEFAULT_FILTERS);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Load items from API with the current filter settings
  const loadItems = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await api.fetchWorkItems(filters);
      setItems(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error loading work items';
      console.error('[useWorkItems] Failed to fetch work items:', err);
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  }, [filters, activePersona.id]);

  useEffect(() => {
    loadItems();
  }, [loadItems]);

  // Tab count calculations across current items or full set
  const tabCounts = useMemo(() => {
    const assignedToMe = items.filter((i) => i.assigneeId === activePersona.id).length;
    const unassignedUrgent = items.filter((i) => i.assigneeId === null && i.priority === 'URGENT').length;
    const awaitingApproval = items.filter((i) => i.status === 'AWAITING_APPROVAL').length;
    return {
      ALL: items.length,
      ASSIGNED_TO_ME: assignedToMe,
      UNASSIGNED_URGENT: unassignedUrgent,
      AWAITING_APPROVAL: awaitingApproval,
    };
  }, [items, activePersona.id]);

  const setTab = useCallback((tab: DashboardTab) => {
    setFilters((prev) => ({ ...prev, tab }));
  }, []);

  const setSearch = useCallback((search: string) => {
    setFilters((prev) => ({ ...prev, search }));
  }, []);

  const setTeam = useCallback((team: string) => {
    setFilters((prev) => ({ ...prev, team }));
  }, []);

  const setPriority = useCallback((priority: string) => {
    setFilters((prev) => ({ ...prev, priority }));
  }, []);

  const setStatusFilter = useCallback((status: string) => {
    setFilters((prev) => ({ ...prev, status }));
  }, []);

  const resetFilters = useCallback(() => {
    setFilters(DEFAULT_FILTERS);
  }, []);

  // Instant atomic claim action
  const claimItem = useCallback(
    async (id: string, currentVersion: number) => {
      try {
        const updated = await api.claimWorkItem(id, currentVersion);
        setItems((prev) => prev.map((item) => (item.id === id ? updated : item)));
        return { success: true, item: updated };
      } catch (err) {
        console.error(`[useWorkItems] Error claiming item ${id}:`, err);
        if (err instanceof ConcurrencyConflictError) {
          // Re-fetch to sync fresh state
          await loadItems();
          return { success: false, conflict: true, message: err.message, payload: err.payload };
        }
        if (err instanceof ForbiddenError) {
          return { success: false, forbidden: true, message: err.message };
        }
        return { success: false, message: err instanceof Error ? err.message : 'Claim failed' };
      }
    },
    [loadItems]
  );

  // Transition status action
  const transitionItemStatus = useCallback(
    async (id: string, newStatus: WorkItemStatus, currentVersion: number) => {
      try {
        const updated = await api.transitionStatus(id, newStatus, currentVersion);
        setItems((prev) => prev.map((item) => (item.id === id ? updated : item)));
        return { success: true, item: updated };
      } catch (err) {
        console.error(`[useWorkItems] Error transitioning item ${id}:`, err);
        if (err instanceof ConcurrencyConflictError) {
          await loadItems();
          return { success: false, conflict: true, message: err.message, payload: err.payload };
        }
        if (err instanceof ForbiddenError) {
          return { success: false, forbidden: true, message: err.message };
        }
        return { success: false, message: err instanceof Error ? err.message : 'Transition failed' };
      }
    },
    [loadItems]
  );

  return {
    items,
    filters,
    isLoading,
    errorMessage,
    tabCounts,
    setTab,
    setSearch,
    setTeam,
    setPriority,
    setStatusFilter,
    resetFilters,
    refresh: loadItems,
    claimItem,
    transitionItemStatus,
  };
}
