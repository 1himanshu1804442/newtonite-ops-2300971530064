import React, { useState, useEffect } from 'react';
import { CreateWorkItemRequest, Team, Priority, WorkItem } from '../types';
import { api } from '../services/api';
import { X, Plus, Key, AlertCircle, Shield, CheckCircle } from 'lucide-react';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onItemCreated: (newItem: WorkItem) => void;
}

// Generate standard RFC4122 version 4 UUID for idempotency protection
function generateUUIDv4(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const CreateWorkItemModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onItemCreated,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [team, setTeam] = useState<Team>('OPERATIONS');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [tagsInput, setTagsInput] = useState('');

  // Idempotency Key generated per form session
  const [idempotencyKey, setIdempotencyKey] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Generate fresh idempotency key each time modal opens
  useEffect(() => {
    if (isOpen) {
      setIdempotencyKey(generateUUIDv4());
      setTitle('');
      setDescription('');
      setTagsInput('');
      setErrorMsg(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim()) {
      setErrorMsg('Title and description are required.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);

    const tags = tagsInput
      .split(',')
      .map((t) => t.trim())
      .filter((t) => t.length > 0);

    const payload: CreateWorkItemRequest = {
      title: title.trim(),
      description: description.trim(),
      team,
      priority,
      tags,
    };

    try {
      // Pass the idempotency key in the request
      const created = await api.createWorkItem(payload, idempotencyKey);
      onItemCreated(created);
      onClose();
    } catch (err) {
      console.error('[CreateWorkItemModal] Submission failed:', err);
      setErrorMsg(err instanceof Error ? err.message : 'Failed to create work item');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="relative w-full max-w-xl bg-[#111827] border border-gray-700 rounded-2xl shadow-2xl overflow-hidden animate-fadeIn">
        {/* Header */}
        <div className="p-5 border-b border-gray-800 flex items-center justify-between bg-[#0f1624]">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 bg-indigo-600/20 text-indigo-400 rounded-lg border border-indigo-500/30">
              <Plus className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Create Operational Work Item</h3>
              <p className="text-xs text-gray-400">
                Dispatched with transactional idempotency protection
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white p-1 rounded-lg hover:bg-gray-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Idempotency Key Inspection Box */}
          <div className="bg-[#0b111d] p-3 rounded-lg border border-indigo-950/80 flex items-center justify-between">
            <div className="flex items-center space-x-2 text-xs text-indigo-300">
              <Key className="w-4 h-4 text-indigo-400 flex-shrink-0" />
              <div>
                <span className="font-semibold text-gray-200">Idempotency-Key Header:</span>
                <span className="font-mono text-[11px] text-indigo-300 ml-2 block sm:inline truncate max-w-xs">
                  {idempotencyKey}
                </span>
              </div>
            </div>
            <span className="text-[10px] uppercase font-mono text-emerald-400 bg-emerald-950/60 border border-emerald-800/50 px-1.5 py-0.5 rounded">
              Deduplication Guard
            </span>
          </div>

          {/* Title */}
          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
              Work Item Title *
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Wire Transfer Reconciliation Lag on DB-02"
              required
              className="w-full bg-[#0b111d] border border-gray-700 rounded-lg px-3.5 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all"
            />
          </div>

          {/* Team & Priority Row */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
                Target Team
              </label>
              <select
                value={team}
                onChange={(e) => setTeam(e.target.value as Team)}
                className="w-full bg-[#0b111d] border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all"
              >
                <option value="OPERATIONS">Operations</option>
                <option value="PAYMENTS">Payments</option>
                <option value="COMPLIANCE">Compliance</option>
                <option value="SECURITY">Security</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
                Priority
              </label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value as Priority)}
                className="w-full bg-[#0b111d] border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all"
              >
                <option value="URGENT">URGENT (P0)</option>
                <option value="HIGH">HIGH (P1)</option>
                <option value="MEDIUM">MEDIUM (P2)</option>
                <option value="LOW">LOW (P3)</option>
              </select>
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
              Incident Details &amp; Background *
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={4}
              placeholder="Provide complete diagnostic notes, merchant IDs, logs, or error codes..."
              required
              className="w-full bg-[#0b111d] border border-gray-700 rounded-lg px-3.5 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all"
            />
          </div>

          {/* Tags */}
          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
              Tags (Comma separated)
            </label>
            <input
              type="text"
              value={tagsInput}
              onChange={(e) => setTagsInput(e.target.value)}
              placeholder="Payment Freeze, High Value, Tier-1 Merchant, Database"
              className="w-full bg-[#0b111d] border border-gray-700 rounded-lg px-3.5 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all"
            />
          </div>

          {/* Error Message */}
          {errorMsg && (
            <div className="text-xs text-rose-300 bg-rose-950/60 border border-rose-800 p-2.5 rounded-lg flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          {/* Modal Actions */}
          <div className="flex items-center justify-end space-x-3 pt-4 border-t border-gray-800">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-medium text-gray-400 hover:text-white rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center space-x-2 px-5 py-2 bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white text-xs font-semibold rounded-lg shadow-md disabled:opacity-40 transition-all"
            >
              {isSubmitting ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                  <span>Submitting...</span>
                </>
              ) : (
                <>
                  <CheckCircle className="w-4 h-4" />
                  <span>Create Item (Guarded)</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
