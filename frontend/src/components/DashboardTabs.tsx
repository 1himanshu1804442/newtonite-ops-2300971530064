import React from 'react';
import { DashboardTab } from '../types';
import { Layers, User, AlertOctagon, CheckSquare } from 'lucide-react';

interface Props {
  activeTab: DashboardTab;
  onTabChange: (tab: DashboardTab) => void;
  counts: {
    ALL: number;
    ASSIGNED_TO_ME: number;
    UNASSIGNED_URGENT: number;
    AWAITING_APPROVAL: number;
  };
}

export const DashboardTabs: React.FC<Props> = ({ activeTab, onTabChange, counts }) => {
  const tabs = [
    {
      id: 'ALL' as DashboardTab,
      label: 'All Work Items',
      icon: Layers,
      count: counts.ALL,
      badgeColor: 'bg-gray-800 text-gray-300',
    },
    {
      id: 'ASSIGNED_TO_ME' as DashboardTab,
      label: 'Assigned to Me',
      icon: User,
      count: counts.ASSIGNED_TO_ME,
      badgeColor: 'bg-indigo-950 text-indigo-300 border border-indigo-700/50',
    },
    {
      id: 'UNASSIGNED_URGENT' as DashboardTab,
      label: 'Unassigned Urgent',
      icon: AlertOctagon,
      count: counts.UNASSIGNED_URGENT,
      badgeColor:
        counts.UNASSIGNED_URGENT > 0
          ? 'bg-rose-950 text-rose-300 border border-rose-700/60 animate-pulse'
          : 'bg-gray-800 text-gray-400',
    },
    {
      id: 'AWAITING_APPROVAL' as DashboardTab,
      label: 'Awaiting My Approval',
      icon: CheckSquare,
      count: counts.AWAITING_APPROVAL,
      badgeColor:
        counts.AWAITING_APPROVAL > 0
          ? 'bg-purple-950 text-purple-300 border border-purple-700/60'
          : 'bg-gray-800 text-gray-400',
    },
  ];

  return (
    <div className="flex border-b border-gray-800 space-x-1 sm:space-x-4 overflow-x-auto pb-px">
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            onClick={() => onTabChange(tab.id)}
            className={`flex items-center space-x-2 py-3 px-3.5 border-b-2 font-medium text-sm whitespace-nowrap transition-all duration-150 ${
              isActive
                ? 'border-indigo-500 text-indigo-400 bg-indigo-500/5'
                : 'border-transparent text-gray-400 hover:text-gray-200 hover:border-gray-700'
            }`}
          >
            <Icon className={`w-4 h-4 ${isActive ? 'text-indigo-400' : 'text-gray-400'}`} />
            <span>{tab.label}</span>
            <span className={`text-xs px-2 py-0.5 rounded-full font-mono ${tab.badgeColor}`}>
              {tab.count}
            </span>
          </button>
        );
      })}
    </div>
  );
};
