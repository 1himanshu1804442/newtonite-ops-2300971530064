import React, { useState, useEffect, useCallback } from 'react';
import {
  WorkItem,
  WorkItemStatus,
  ActivityItem,
  ConflictErrorPayload,
} from '../types';
import { usePersona } from '../hooks/usePersona';
import { api, ConcurrencyConflictError, ForbiddenError } from '../services/api';
import {
  X,
  Clock,
  Tag,
  User,
  ShieldCheck,
  AlertTriangle,
  Send,
  MessageSquare,
  ArrowRight,
  GitCommit,
  CheckCircle,
  RefreshCw,
  Lock,
} from 'lucide-react';

interface Props {
  item: WorkItem | null;
  isOpen: boolean;
  onClose: () => void;
  onItemUpdated: (updated: WorkItem) => void;
}

export const WorkItemDetailModal: React.FC<Props> = ({
  item,
  isOpen,
  onClose,
  onItemUpdated,
}) => {
  const { activePersona } = usePersona();

  // Local state for current modal item (allows instant in-modal synchronization)
  const [currentItem, setCurrentItem] = useState<WorkItem | null>(item);
  const [activities, setActivities] = useState<ActivityItem[]>([]);
  const [isLoadingActivities, setIsLoadingActivities] = useState<boolean>(false);

  // Concurrency conflict banner state
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [isReloadingFresh, setIsReloadingFresh] = useState<boolean>(false);

  // Action loading states
  const [isClaiming, setIsClaiming] = useState<boolean>(false);
  const [isTransitioning, setIsTransitioning] = useState<boolean>(false);
  const [commentText, setCommentText] = useState<string>('');
  const [isSubmittingComment, setIsSubmittingComment] = useState<boolean>(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Sync internal state when opened with a new item
  useEffect(() => {
    setCurrentItem(item);
    setConflictMessage(null);
    setActionError(null);
    setCommentText('');
  }, [item]);

  // Load activities when modal is open and item is present
  const loadActivities = useCallback(async (workItemId: string) => {
    setIsLoadingActivities(true);
    try {
      const data = await api.fetchActivities(workItemId);
      setActivities(data);
    } catch (err) {
      console.error(`[WorkItemDetailModal] Failed to fetch activities for ${workItemId}:`, err);
    } finally {
      setIsLoadingActivities(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen && currentItem) {
      loadActivities(currentItem.id);
    }
  }, [isOpen, currentItem?.id, loadActivities]);

  if (!isOpen || !currentItem) {
    return null;
  }

  // Reload fresh work item from server after a concurrency conflict
  const reloadFreshState = async () => {
    setIsReloadingFresh(true);
    try {
      const fresh = await api.fetchWorkItemById(currentItem.id);
      setCurrentItem(fresh);
      onItemUpdated(fresh);
      await loadActivities(fresh.id);
      setConflictMessage(null);
      setActionError(null);
    } catch (err) {
      console.error('[WorkItemDetailModal] Failed to reload fresh work item:', err);
    } finally {
      setIsReloadingFresh(false);
    }
  };

  // Atomic Claim handler
  const handleClaim = async () => {
    setIsClaiming(true);
    setActionError(null);
    try {
      const updated = await api.claimWorkItem(currentItem.id, currentItem.version);
      setCurrentItem(updated);
      onItemUpdated(updated);
      await loadActivities(currentItem.id);
    } catch (err) {
      if (err instanceof ConcurrencyConflictError) {
        setConflictMessage('This item was updated by another user. Reloading...');
        // Automatically sync fresh state to demonstrate recovery
        setTimeout(() => {
          reloadFreshState();
        }, 1200);
      } else if (err instanceof ForbiddenError) {
        setActionError(err.message);
      } else {
        setActionError(err instanceof Error ? err.message : 'Claim failed.');
      }
    } finally {
      setIsClaiming(false);
    }
  };

  // Status transition handler
  const handleTransition = async (nextStatus: WorkItemStatus) => {
    setIsTransitioning(true);
    setActionError(null);
    try {
      const updated = await api.transitionStatus(currentItem.id, nextStatus, currentItem.version);
      setCurrentItem(updated);
      onItemUpdated(updated);
      await loadActivities(currentItem.id);
    } catch (err) {
      if (err instanceof ConcurrencyConflictError) {
        setConflictMessage('This item was updated by another user. Reloading...');
        setTimeout(() => {
          reloadFreshState();
        }, 1200);
      } else if (err instanceof ForbiddenError) {
        setActionError(err.message);
      } else {
        setActionError(err instanceof Error ? err.message : 'Transition failed.');
      }
    } finally {
      setIsTransitioning(false);
    }
  };

  // Add Comment handler
  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;

    setIsSubmittingComment(true);
    setActionError(null);
    try {
      const newActivity = await api.addComment(currentItem.id, commentText);
      setActivities((prev) => [...prev, newActivity]);
      setCommentText('');
    } catch (err) {
      console.error('[WorkItemDetailModal] Failed to add comment:', err);
      setActionError(err instanceof Error ? err.message : 'Failed to post comment.');
    } finally {
      setIsSubmittingComment(false);
    }
  };

  // Determine transition permissions based on active persona role
  const isViewer = activePersona.role === 'VIEWER';
  const isMember = activePersona.role === 'MEMBER';
  const isLeadOrAdmin = activePersona.role === 'LEAD' || activePersona.role === 'ADMIN';

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/70 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6 animate-fadeIn">
      <div className="relative w-full max-w-4xl bg-[#111827] border border-gray-700/80 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[92vh]">
        {/* Real-time Concurrency Conflict Notification Banner */}
        {conflictMessage && (
          <div className="bg-gradient-to-r from-amber-950/90 via-rose-950/90 to-amber-950/90 border-b border-rose-500/40 p-3.5 px-6 flex items-center justify-between text-rose-200 text-sm animate-pulse">
            <div className="flex items-center space-x-3">
              <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0" />
              <div>
                <span className="font-semibold text-white">Concurrency Conflict:</span>{' '}
                {conflictMessage}
              </div>
            </div>
            <button
              onClick={reloadFreshState}
              disabled={isReloadingFresh}
              className="inline-flex items-center space-x-1.5 px-3 py-1 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-semibold shadow transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isReloadingFresh ? 'animate-spin' : ''}`} />
              <span>{isReloadingFresh ? 'Syncing...' : 'Sync Fresh Data'}</span>
            </button>
          </div>
        )}

        {/* Modal Header */}
        <div className="p-6 border-b border-gray-800 flex items-start justify-between bg-[#0f1624]">
          <div className="space-y-1">
            <div className="flex items-center space-x-3">
              <span className="font-mono text-sm font-bold text-indigo-400 bg-indigo-950/80 border border-indigo-700/60 px-2.5 py-0.5 rounded">
                {currentItem.ticketNumber}
              </span>

              {/* Version Indicator */}
              <span
                className="text-xs font-mono text-gray-300 bg-gray-800 px-2 py-0.5 rounded border border-gray-700 flex items-center"
                title="Optimistic Concurrency Control (OCC) Entity Version"
              >
                Version: v{currentItem.version}
              </span>

              <span className="text-xs font-mono text-gray-400 uppercase tracking-wider">
                {currentItem.team}
              </span>

              <span
                className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${
                  currentItem.priority === 'URGENT'
                    ? 'bg-rose-950/80 text-rose-300 border-rose-700'
                    : currentItem.priority === 'HIGH'
                    ? 'bg-amber-950/80 text-amber-300 border-amber-700'
                    : 'bg-blue-950/80 text-blue-300 border-blue-700'
                }`}
              >
                {currentItem.priority}
              </span>

              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-gray-800 text-gray-300 border border-gray-700">
                {currentItem.status.replace('_', ' ')}
              </span>
            </div>

            <h2 className="text-xl font-bold text-white pt-1">{currentItem.title}</h2>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-gray-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body: Split 2-column layout */}
        <div className="flex-1 overflow-y-auto p-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column (2 spans): Details & Activity Timeline */}
          <div className="lg:col-span-2 space-y-6">
            {/* Description */}
            <div className="bg-[#0b111d] p-4 rounded-xl border border-gray-800">
              <h4 className="text-xs font-mono uppercase text-gray-400 tracking-wider mb-2">
                Operational Brief &amp; Incident Notes
              </h4>
              <p className="text-sm text-gray-200 leading-relaxed whitespace-pre-wrap">
                {currentItem.description}
              </p>
            </div>

            {/* Tags */}
            {currentItem.tags.length > 0 && (
              <div>
                <h4 className="text-xs font-mono uppercase text-gray-400 tracking-wider mb-2">
                  Tags &amp; Classifications
                </h4>
                <div className="flex flex-wrap gap-2">
                  {currentItem.tags.map((tag) => (
                    <span
                      key={tag}
                      className="inline-flex items-center text-xs font-medium text-indigo-300 bg-indigo-950/50 border border-indigo-700/40 px-2.5 py-1 rounded-md"
                    >
                      <Tag className="w-3 h-3 mr-1 text-indigo-400" />
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Append-Only Activity Timeline */}
            <div>
              <div className="flex items-center justify-between mb-3 border-b border-gray-800 pb-2">
                <h4 className="text-sm font-semibold text-white flex items-center">
                  <GitCommit className="w-4 h-4 mr-1.5 text-indigo-400" />
                  Append-Only Activity Audit Timeline
                </h4>
                <span className="text-xs text-gray-500 font-mono">
                  {activities.length} Recorded Events
                </span>
              </div>

              {isLoadingActivities ? (
                <div className="py-6 text-center text-sm text-gray-500">
                  <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-indigo-400" />
                  Loading audit log...
                </div>
              ) : activities.length === 0 ? (
                <div className="py-6 text-center text-sm text-gray-500 italic">
                  No activity entries recorded yet.
                </div>
              ) : (
                <div className="space-y-3 max-h-64 overflow-y-auto pr-2">
                  {activities.map((act) => (
                    <div
                      key={act.id}
                      className="flex items-start space-x-3 text-xs bg-[#0c121e] p-3 rounded-lg border border-gray-800/80"
                    >
                      <div className="mt-0.5 p-1 rounded bg-indigo-950/60 text-indigo-400 border border-indigo-800/40">
                        {act.type === 'COMMENT' ? (
                          <MessageSquare className="w-3.5 h-3.5" />
                        ) : act.type === 'CLAIMED' ? (
                          <User className="w-3.5 h-3.5 text-emerald-400" />
                        ) : (
                          <GitCommit className="w-3.5 h-3.5 text-purple-400" />
                        )}
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-semibold text-gray-200">
                            {act.authorName}{' '}
                            <span className="text-[10px] font-mono text-gray-400">
                              ({act.authorRole})
                            </span>
                          </span>
                          <span className="text-[10px] text-gray-500 font-mono">
                            {new Date(act.createdAt).toLocaleTimeString([], {
                              hour: '2-digit',
                              minute: '2-digit',
                            })}
                          </span>
                        </div>
                        <p className="text-gray-300 leading-relaxed">{act.content}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Add Comment Form */}
              <form onSubmit={handleAddComment} className="mt-4">
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={commentText}
                    onChange={(e) => setCommentText(e.target.value)}
                    placeholder="Append operational comment or status update..."
                    disabled={isSubmittingComment}
                    className="flex-1 bg-[#0b111d] border border-gray-700 rounded-lg px-3 py-2 text-xs text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                  <button
                    type="submit"
                    disabled={isSubmittingComment || !commentText.trim()}
                    className="inline-flex items-center space-x-1.5 px-3 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold disabled:opacity-40 transition-colors shadow"
                  >
                    <Send className="w-3 h-3" />
                    <span>Post</span>
                  </button>
                </div>
              </form>
            </div>
          </div>

          {/* Right Column (1 span): Controls & RBAC Enforcement */}
          <div className="space-y-6 lg:border-l lg:border-gray-800 lg:pl-6">
            {/* Ticket Assignment & Atomic Claim */}
            <div className="bg-[#0f1624] p-4 rounded-xl border border-gray-800">
              <h4 className="text-xs font-mono uppercase text-gray-400 tracking-wider mb-2.5">
                Assignment &amp; Ownership
              </h4>

              {currentItem.assigneeId ? (
                <div className="flex items-center space-x-2.5">
                  <div className="w-8 h-8 rounded-full bg-indigo-600 flex items-center justify-center text-white font-bold text-xs shadow">
                    {currentItem.assigneeName ? currentItem.assigneeName.charAt(0) : 'U'}
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-gray-100">
                      {currentItem.assigneeName}
                    </div>
                    <div className="text-[11px] text-gray-400 font-mono">Assigned Engineer</div>
                  </div>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="text-xs text-amber-400 bg-amber-950/40 border border-amber-800/40 p-2 rounded-lg">
                    This work item is unassigned.
                  </div>

                  <button
                    onClick={handleClaim}
                    disabled={isClaiming || isViewer}
                    title={isViewer ? 'Auditors with VIEWER role cannot claim items' : 'Atomically claim this work item'}
                    className="w-full py-2 px-3 bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white text-xs font-semibold rounded-lg shadow transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center space-x-2"
                  >
                    <User className="w-4 h-4" />
                    <span>{isClaiming ? 'Claiming Atomically...' : 'Claim Work Item'}</span>
                  </button>
                </div>
              )}
            </div>

            {/* Workflow State Machine Transition Controls */}
            <div className="bg-[#0f1624] p-4 rounded-xl border border-gray-800">
              <div className="flex items-center justify-between mb-2.5">
                <h4 className="text-xs font-mono uppercase text-gray-400 tracking-wider">
                  Workflow State Machine
                </h4>
                <ShieldCheck className="w-4 h-4 text-indigo-400" />
              </div>

              <div className="space-y-2">
                {/* Status: OPEN -> IN_PROGRESS */}
                {currentItem.status === 'OPEN' && (
                  <button
                    onClick={() => handleTransition('IN_PROGRESS')}
                    disabled={isTransitioning || isViewer}
                    className="w-full py-2 px-3 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-lg shadow transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center space-x-1.5"
                  >
                    <span>Start Work (IN_PROGRESS)</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                )}

                {/* Status: IN_PROGRESS -> AWAITING_APPROVAL */}
                {currentItem.status === 'IN_PROGRESS' && (
                  <button
                    onClick={() => handleTransition('AWAITING_APPROVAL')}
                    disabled={isTransitioning || isViewer}
                    className="w-full py-2 px-3 bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold rounded-lg shadow transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center space-x-1.5"
                  >
                    <span>Request Approval (AWAITING_APPROVAL)</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                )}

                {/* Status: AWAITING_APPROVAL -> RESOLVED */}
                {currentItem.status === 'AWAITING_APPROVAL' && (
                  <div>
                    <button
                      onClick={() => handleTransition('RESOLVED')}
                      disabled={isTransitioning || !isLeadOrAdmin}
                      title={
                        !isLeadOrAdmin
                          ? 'Approval requires Team Lead or Admin privilege.'
                          : 'Approve and resolve work item'
                      }
                      className={`w-full py-2 px-3 text-white text-xs font-semibold rounded-lg shadow transition-all flex items-center justify-center space-x-1.5 ${
                        isLeadOrAdmin
                          ? 'bg-emerald-600 hover:bg-emerald-500 cursor-pointer'
                          : 'bg-gray-800 text-gray-500 cursor-not-allowed border border-gray-700'
                      }`}
                    >
                      {!isLeadOrAdmin && <Lock className="w-3.5 h-3.5 mr-1 text-gray-500" />}
                      <span>Approve &amp; Resolve</span>
                      <CheckCircle className="w-3.5 h-3.5 ml-1" />
                    </button>

                    {!isLeadOrAdmin && (
                      <p className="text-[11px] text-amber-400/90 mt-1.5 bg-amber-950/30 p-1.5 rounded border border-amber-800/40">
                        Approval requires Team Lead or Admin privilege. Switch persona to test.
                      </p>
                    )}
                  </div>
                )}

                {/* Status: RESOLVED -> CLOSED */}
                {currentItem.status === 'RESOLVED' && (
                  <div>
                    <button
                      onClick={() => handleTransition('CLOSED')}
                      disabled={isTransitioning || !isLeadOrAdmin}
                      title={
                        !isLeadOrAdmin
                          ? 'Closing tickets requires Team Lead or Admin privilege.'
                          : 'Close completed work item'
                      }
                      className={`w-full py-2 px-3 text-white text-xs font-semibold rounded-lg shadow transition-all flex items-center justify-center space-x-1.5 ${
                        isLeadOrAdmin
                          ? 'bg-slate-700 hover:bg-slate-600 cursor-pointer'
                          : 'bg-gray-800 text-gray-500 cursor-not-allowed border border-gray-700'
                      }`}
                    >
                      {!isLeadOrAdmin && <Lock className="w-3.5 h-3.5 mr-1 text-gray-500" />}
                      <span>Close Ticket (CLOSED)</span>
                    </button>

                    {!isLeadOrAdmin && (
                      <p className="text-[11px] text-amber-400/90 mt-1.5 bg-amber-950/30 p-1.5 rounded border border-amber-800/40">
                        Closing requires Team Lead or Admin privilege.
                      </p>
                    )}
                  </div>
                )}

                {currentItem.status === 'CLOSED' && (
                  <div className="text-center py-2 text-xs text-gray-400 italic bg-gray-800/50 rounded-lg">
                    This work item has been closed.
                  </div>
                )}
              </div>

              {isViewer && (
                <div className="mt-3 text-[11px] text-gray-400 bg-gray-800/70 p-2 rounded border border-gray-700 flex items-center space-x-1.5">
                  <Lock className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
                  <span>Viewers have read-only access. Mutation buttons are locked.</span>
                </div>
              )}
            </div>

            {/* Error banner if action fails */}
            {actionError && (
              <div className="text-xs text-rose-300 bg-rose-950/60 border border-rose-800 p-2.5 rounded-lg flex items-start space-x-2">
                <AlertTriangle className="w-4 h-4 text-rose-400 flex-shrink-0 mt-0.5" />
                <span>{actionError}</span>
              </div>
            )}

            {/* Audit Metadata */}
            <div className="text-[11px] font-mono text-gray-400 space-y-1.5 pt-2 border-t border-gray-800">
              <div className="flex justify-between">
                <span>Created:</span>
                <span className="text-gray-300">
                  {new Date(currentItem.createdAt).toLocaleString()}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Last Updated:</span>
                <span className="text-gray-300">
                  {new Date(currentItem.updatedAt).toLocaleString()}
                </span>
              </div>
              <div className="flex justify-between">
                <span>OCC Version:</span>
                <span className="text-indigo-400 font-bold">#{currentItem.version}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
