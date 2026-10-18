import React, { useState } from 'react';
import { WorkItem, Priority, WorkItemStatus } from '../types';
import { usePersona } from '../hooks/usePersona';
import { Clock, Tag, UserPlus, CheckCircle2, AlertCircle, ArrowUpRight } from 'lucide-react';

interface Props {
  item: WorkItem;
  onClick: (item: WorkItem) => void;
  onClaim: (id: string, version: number) => Promise<{ success: boolean; conflict?: boolean; message?: string }>;
}

export const WorkItemCard: React.FC<Props> = ({ item, onClick, onClaim }) => {
  const { activePersona } = usePersona();
  const [isClaiming, setIsClaiming] = useState(false);

  const getPriorityStyle = (priority: Priority) => {
    switch (priority) {
      case 'URGENT':
        return 'bg-rose-950/70 text-rose-300 border-rose-700/80';
      case 'HIGH':
        return 'bg-amber-950/70 text-amber-300 border-amber-700/80';
      case 'MEDIUM':
        return 'bg-sky-950/70 text-sky-300 border-sky-700/80';
      case 'LOW':
        return 'bg-slate-900 text-slate-300 border-slate-700';
    }
  };

  const getStatusStyle = (status: WorkItemStatus) => {
    switch (status) {
      case 'OPEN':
        return 'bg-slate-800 text-slate-300 border-slate-700';
      case 'IN_PROGRESS':
        return 'bg-blue-950/80 text-blue-300 border-blue-700/80';
      case 'AWAITING_APPROVAL':
        return 'bg-purple-950/80 text-purple-300 border-purple-700/80';
      case 'RESOLVED':
        return 'bg-emerald-950/80 text-emerald-300 border-emerald-700/80';
      case 'CLOSED':
        return 'bg-gray-900 text-gray-400 border-gray-800';
    }
  };

  const handleClaimClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsClaiming(true);
    try {
      await onClaim(item.id, item.version);
    } finally {
      setIsClaiming(false);
    }
  };

  const formatRelativeTime = (dateStr: string) => {
    try {
      const diffSec = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
      if (diffSec < 60) return 'Just now';
      if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
      if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
      return `${Math.floor(diffSec / 86400)}d ago`;
    } catch {
      return dateStr;
    }
  };

  return (
    <div
      onClick={() => onClick(item)}
      className="group relative bg-[#111827] hover:bg-[#151f33] border border-gray-800 hover:border-indigo-500/50 rounded-xl p-5 cursor-pointer transition-all duration-200 shadow-lg hover:shadow-indigo-500/10 flex flex-col justify-between"
    >
      <div>
        {/* Top row: Ticket Number, Version, Priority & Status Badges */}
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center space-x-2">
            <span className="font-mono text-xs font-semibold text-indigo-400 bg-indigo-950/60 border border-indigo-700/40 px-2 py-0.5 rounded">
              {item.ticketNumber}
            </span>
            <span
              className="text-[11px] font-mono text-gray-400 bg-gray-800/80 px-1.5 py-0.5 rounded border border-gray-700"
              title={`Optimistic Concurrency Version ${item.version}`}
            >
              v{item.version}
            </span>
            <span className="text-xs font-mono text-gray-400 uppercase tracking-wider">
              {item.team}
            </span>
          </div>

          <div className="flex items-center space-x-2">
            <span
              className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getPriorityStyle(
                item.priority
              )}`}
            >
              {item.priority === 'URGENT' && (
                <span className="w-1.5 h-1.5 rounded-full bg-rose-400 animate-ping mr-1.5"></span>
              )}
              {item.priority}
            </span>

            <span
              className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getStatusStyle(
                item.status
              )}`}
            >
              {item.status.replace('_', ' ')}
            </span>
          </div>
        </div>

        {/* Title */}
        <h3 className="text-base font-semibold text-gray-100 group-hover:text-white mb-2 line-clamp-1 flex items-center justify-between">
          <span>{item.title}</span>
          <ArrowUpRight className="w-4 h-4 text-gray-500 group-hover:text-indigo-400 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0 ml-2" />
        </h3>

        {/* Description Excerpt */}
        <p className="text-sm text-gray-400 line-clamp-2 leading-relaxed mb-4">
          {item.description}
        </p>

        {/* Tags */}
        {item.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mb-4">
            {item.tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center text-[11px] font-medium text-gray-300 bg-gray-800/70 px-2 py-0.5 rounded border border-gray-700/60"
              >
                <Tag className="w-2.5 h-2.5 mr-1 text-gray-400" />
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Footer: Assignee & Claim Button */}
      <div className="pt-3 border-t border-gray-800/80 flex items-center justify-between text-xs text-gray-400">
        <div className="flex items-center space-x-2">
          {item.assigneeId ? (
            <div className="flex items-center space-x-1.5 text-gray-300">
              <div className="w-5 h-5 rounded-full bg-indigo-700 flex items-center justify-center text-[10px] text-white font-bold">
                {item.assigneeName ? item.assigneeName.charAt(0) : 'U'}
              </div>
              <span className="font-medium truncate max-w-[120px]">{item.assigneeName}</span>
            </div>
          ) : (
            <span className="text-amber-400/80 font-medium flex items-center">
              <AlertCircle className="w-3.5 h-3.5 mr-1" />
              Unassigned
            </span>
          )}
        </div>

        <div className="flex items-center space-x-3">
          <span className="flex items-center text-gray-500 font-mono">
            <Clock className="w-3 h-3 mr-1" />
            {formatRelativeTime(item.updatedAt)}
          </span>

          {!item.assigneeId && activePersona.role !== 'VIEWER' && (
            <button
              onClick={handleClaimClick}
              disabled={isClaiming}
              className="inline-flex items-center space-x-1 px-2.5 py-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded font-medium transition-colors shadow-sm disabled:opacity-50"
            >
              <UserPlus className="w-3 h-3" />
              <span>{isClaiming ? 'Claiming...' : 'Claim'}</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
