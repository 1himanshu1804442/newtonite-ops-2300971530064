import React, { useState } from 'react';
import { useWorkItems } from '../hooks/useWorkItems';
import { usePersona } from '../hooks/usePersona';
import { Header } from '../components/Header';
import { DashboardTabs } from '../components/DashboardTabs';
import { DashboardFilters } from '../components/DashboardFilters';
import { WorkItemCard } from '../components/WorkItemCard';
import { WorkItemDetailModal } from '../components/WorkItemDetailModal';
import { CreateWorkItemModal } from '../components/CreateWorkItemModal';
import { WorkItem } from '../types';
import {
  Inbox,
  AlertCircle,
  Activity,
  Layers,
  CheckCircle2,
  Lock,
  Cpu,
  RefreshCw,
} from 'lucide-react';

export const DashboardPage: React.FC = () => {
  const { activePersona } = usePersona();
  const {
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
    refresh,
    claimItem,
  } = useWorkItems();

  // Selected work item for detail modal
  const [selectedItem, setSelectedItem] = useState<WorkItem | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState<boolean>(false);

  // Create work item modal state
  const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);

  // Toast notification state
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'conflict' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'conflict' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => {
      setToast(null);
    }, 4000);
  };

  const handleCardClick = (item: WorkItem) => {
    setSelectedItem(item);
    setIsDetailOpen(true);
  };

  const handleItemUpdated = (updated: WorkItem) => {
    setSelectedItem(updated);
    refresh();
  };

  const handleItemCreated = (newItem: WorkItem) => {
    refresh();
    showToast(`Work item ${newItem.ticketNumber} created successfully!`, 'success');
  };

  const handleClaim = async (id: string, version: number) => {
    const res = await claimItem(id, version);
    if (res.success && res.item) {
      showToast(`Claimed ticket ${res.item.ticketNumber} atomically!`, 'success');
      return { success: true };
    } else if (res.conflict) {
      showToast('This item was updated by another user. Reloading...', 'conflict');
      return { success: false, conflict: true, message: res.message };
    } else {
      showToast(res.message || 'Failed to claim ticket.', 'error');
      return { success: false, message: res.message };
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#0B0F17] text-gray-100">
      {/* Top Application Header */}
      <Header />

      {/* Main Operational Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* Role & Operational Scenario Banner */}
        <div className="bg-[#111827] border border-gray-800 rounded-2xl p-5 shadow-xl relative overflow-hidden">
          <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-gradient-to-l from-indigo-950/20 to-transparent pointer-events-none"></div>

          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
            <div>
              <div className="flex items-center space-x-2.5">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse"></span>
                <h2 className="text-lg font-bold text-white">Operations Under Pressure Console</h2>
                <span className="text-xs font-mono text-gray-400 bg-gray-800/80 px-2 py-0.5 rounded border border-gray-700">
                  Active User: {activePersona.name} ({activePersona.role})
                </span>
              </div>
              <p className="text-xs text-gray-400 mt-1.5 max-w-2xl leading-relaxed">
                Demonstrating atomic queue claiming, optimistic locking (OCC 409 conflict detection), idempotent submission guarantees, and server-guarded RBAC approval workflows.
              </p>
            </div>

            <div className="flex items-center space-x-3 text-xs font-mono">
              <div className="bg-[#0b111d] border border-gray-700/80 rounded-lg p-2.5 flex items-center space-x-2">
                <Cpu className="w-4 h-4 text-indigo-400" />
                <div>
                  <div className="text-gray-400 text-[10px]">OCC &amp; RBAC</div>
                  <div className="text-gray-200 font-semibold">Enforced</div>
                </div>
              </div>

              <div className="bg-[#0b111d] border border-gray-700/80 rounded-lg p-2.5 flex items-center space-x-2">
                <Lock className="w-4 h-4 text-purple-400" />
                <div>
                  <div className="text-gray-400 text-[10px]">Permission Tier</div>
                  <div className="text-purple-300 font-semibold">{activePersona.role}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Dashboard Tabs & Filters */}
        <div className="bg-[#111827]/70 border border-gray-800 rounded-2xl p-4 sm:p-5 shadow-lg space-y-2">
          {/* Tabs */}
          <DashboardTabs
            activeTab={filters.tab}
            onTabChange={setTab}
            counts={tabCounts}
          />

          {/* Filter Toolbar */}
          <DashboardFilters
            filters={filters}
            onSearchChange={setSearch}
            onTeamChange={setTeam}
            onPriorityChange={setPriority}
            onStatusChange={setStatusFilter}
            onResetFilters={resetFilters}
            onRefresh={refresh}
            onCreateOpen={() => setIsCreateOpen(true)}
            isLoading={isLoading}
          />
        </div>

        {/* Error Alert if fetching failed */}
        {errorMessage && (
          <div className="bg-rose-950/60 border border-rose-800 p-4 rounded-xl text-xs text-rose-200 flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
              <span>{errorMessage}</span>
            </div>
            <button
              onClick={refresh}
              className="px-2.5 py-1 bg-rose-800 hover:bg-rose-700 text-white rounded text-xs font-semibold"
            >
              Retry
            </button>
          </div>
        )}

        {/* Work Items Grid */}
        {isLoading && items.length === 0 ? (
          <div className="py-24 text-center">
            <RefreshCw className="w-8 h-8 animate-spin mx-auto text-indigo-500 mb-3" />
            <p className="text-sm text-gray-400 font-mono">Syncing operational work queue...</p>
          </div>
        ) : items.length === 0 ? (
          <div className="bg-[#111827] border border-gray-800/80 rounded-2xl p-12 text-center max-w-lg mx-auto shadow-lg">
            <div className="w-12 h-12 rounded-xl bg-gray-800 flex items-center justify-center text-gray-500 mx-auto mb-3">
              <Inbox className="w-6 h-6" />
            </div>
            <h3 className="text-base font-semibold text-gray-200">No matching work items</h3>
            <p className="text-xs text-gray-400 mt-1 max-w-sm mx-auto leading-relaxed">
              No tickets found matching the selected filter criteria or tab view.
            </p>
            <div className="mt-4 flex items-center justify-center space-x-3">
              <button
                onClick={resetFilters}
                className="px-3 py-1.5 bg-gray-800 hover:bg-gray-700 text-gray-200 rounded-lg text-xs font-medium transition-colors"
              >
                Clear Filters
              </button>
              <button
                onClick={() => setIsCreateOpen(true)}
                className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-medium transition-colors"
              >
                Create Work Item
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {items.map((item) => (
              <WorkItemCard
                key={item.id}
                item={item}
                onClick={handleCardClick}
                onClaim={handleClaim}
              />
            ))}
          </div>
        )}
      </main>

      {/* Floating Toast Notification */}
      {toast && (
        <div
          className={`fixed bottom-6 right-6 z-50 px-4 py-3 rounded-xl shadow-2xl text-xs font-semibold flex items-center space-x-2.5 transition-all duration-300 border ${
            toast.type === 'conflict'
              ? 'bg-amber-950 text-amber-200 border-amber-600/80'
              : toast.type === 'error'
              ? 'bg-rose-950 text-rose-200 border-rose-700'
              : 'bg-emerald-950 text-emerald-200 border-emerald-600/80'
          }`}
        >
          {toast.type === 'conflict' ? (
            <AlertCircle className="w-4 h-4 text-amber-400 flex-shrink-0" />
          ) : toast.type === 'error' ? (
            <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
          ) : (
            <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
          )}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Work Item Detail Modal */}
      <WorkItemDetailModal
        item={selectedItem}
        isOpen={isDetailOpen}
        onClose={() => setIsDetailOpen(false)}
        onItemUpdated={handleItemUpdated}
      />

      {/* Create Work Item Modal */}
      <CreateWorkItemModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onItemCreated={handleItemCreated}
      />
    </div>
  );
};
