import React from 'react';
import { FilterState, Team, Priority, WorkItemStatus } from '../types';
import { Search, Filter, X, Plus, RefreshCw } from 'lucide-react';

interface Props {
  filters: FilterState;
  onSearchChange: (search: string) => void;
  onTeamChange: (team: string) => void;
  onPriorityChange: (priority: string) => void;
  onStatusChange: (status: string) => void;
  onResetFilters: () => void;
  onRefresh: () => void;
  onCreateOpen: () => void;
  isLoading: boolean;
}

export const DashboardFilters: React.FC<Props> = ({
  filters,
  onSearchChange,
  onTeamChange,
  onPriorityChange,
  onStatusChange,
  onResetFilters,
  onRefresh,
  onCreateOpen,
  isLoading,
}) => {
  const teams: (Team | 'ALL')[] = ['ALL', 'OPERATIONS', 'PAYMENTS', 'COMPLIANCE', 'SECURITY'];
  const priorities: (Priority | 'ALL')[] = ['ALL', 'URGENT', 'HIGH', 'MEDIUM', 'LOW'];
  const statuses: (WorkItemStatus | 'ALL')[] = [
    'ALL',
    'OPEN',
    'IN_PROGRESS',
    'AWAITING_APPROVAL',
    'RESOLVED',
    'CLOSED',
  ];

  const hasActiveFilters =
    filters.search !== '' ||
    filters.team !== 'ALL' ||
    filters.priority !== 'ALL' ||
    filters.status !== 'ALL';

  return (
    <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3 py-4">
      {/* Left: Search and Dropdown Filters */}
      <div className="flex flex-wrap items-center gap-2.5 flex-1">
        {/* Search Input */}
        <div className="relative min-w-[240px] flex-1 max-w-sm">
          <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={filters.search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search ticket, title, tags, assignee..."
            className="w-full bg-[#111827] border border-gray-700 rounded-lg pl-9 pr-8 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
          />
          {filters.search && (
            <button
              onClick={() => onSearchChange('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-200"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Team Dropdown */}
        <div className="relative">
          <select
            value={filters.team}
            onChange={(e) => onTeamChange(e.target.value)}
            className="bg-[#111827] border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all cursor-pointer font-sans"
          >
            {teams.map((t) => (
              <option key={t} value={t} className="bg-[#111827] text-gray-200">
                {t === 'ALL' ? 'All Teams' : `Team: ${t}`}
              </option>
            ))}
          </select>
        </div>

        {/* Priority Dropdown */}
        <div className="relative">
          <select
            value={filters.priority}
            onChange={(e) => onPriorityChange(e.target.value)}
            className="bg-[#111827] border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all cursor-pointer"
          >
            {priorities.map((p) => (
              <option key={p} value={p} className="bg-[#111827] text-gray-200">
                {p === 'ALL' ? 'All Priorities' : `Priority: ${p}`}
              </option>
            ))}
          </select>
        </div>

        {/* Status Dropdown */}
        <div className="relative">
          <select
            value={filters.status}
            onChange={(e) => onStatusChange(e.target.value)}
            className="bg-[#111827] border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all cursor-pointer"
          >
            {statuses.map((s) => (
              <option key={s} value={s} className="bg-[#111827] text-gray-200">
                {s === 'ALL' ? 'All Statuses' : s.replace('_', ' ')}
              </option>
            ))}
          </select>
        </div>

        {/* Reset Filters Button */}
        {hasActiveFilters && (
          <button
            onClick={onResetFilters}
            className="inline-flex items-center space-x-1 px-2.5 py-2 text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-950/30 rounded-lg border border-rose-800/50 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
            <span>Reset</span>
          </button>
        )}

        {/* Refresh button */}
        <button
          onClick={onRefresh}
          disabled={isLoading}
          title="Refresh Work Items Queue"
          className="p-2 text-gray-400 hover:text-white bg-[#111827] hover:bg-[#1f293d] border border-gray-700 rounded-lg transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-indigo-400' : ''}`} />
        </button>
      </div>

      {/* Right: Create Ticket Button */}
      <div className="flex items-center justify-end">
        <button
          onClick={onCreateOpen}
          className="inline-flex items-center space-x-2 px-4 py-2 bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white text-sm font-medium rounded-lg shadow-md hover:shadow-indigo-500/25 transition-all duration-150 transform active:scale-95"
        >
          <Plus className="w-4 h-4" />
          <span>New Work Item</span>
        </button>
      </div>
    </div>
  );
};
