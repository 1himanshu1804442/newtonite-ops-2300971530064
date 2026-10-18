import React, { useState, useRef, useEffect } from 'react';
import { usePersona } from '../hooks/usePersona';
import { UserRole } from '../types';
import { Users, ChevronDown, Check, ShieldAlert, ShieldCheck, Eye, UserCheck } from 'lucide-react';

export const PersonaSwitcher: React.FC = () => {
  const { activePersona, switchPersona, personas } = usePersona();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const getRoleBadge = (role: UserRole) => {
    switch (role) {
      case 'ADMIN':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-rose-500/20 text-rose-300 border border-rose-500/30">
            <ShieldAlert className="w-3 h-3 mr-1" />
            ADMIN
          </span>
        );
      case 'LEAD':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-purple-500/20 text-purple-300 border border-purple-500/30">
            <ShieldCheck className="w-3 h-3 mr-1" />
            LEAD
          </span>
        );
      case 'MEMBER':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-500/20 text-blue-300 border border-blue-500/30">
            <UserCheck className="w-3 h-3 mr-1" />
            MEMBER
          </span>
        );
      case 'VIEWER':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber-500/20 text-amber-300 border border-amber-500/30">
            <Eye className="w-3 h-3 mr-1" />
            VIEWER
          </span>
        );
    }
  };

  return (
    <div className="relative inline-block text-left" ref={dropdownRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center space-x-3 px-3 py-2 bg-[#111827] hover:bg-[#1f293d] border border-gray-700 rounded-lg text-left transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-sm"
        title="Switch persona to test authorization permissions live"
      >
        <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center text-white font-bold text-xs shadow">
          {activePersona.avatarInitials}
        </div>
        <div className="flex flex-col">
          <div className="flex items-center space-x-2">
            <span className="text-sm font-semibold text-gray-100">{activePersona.name}</span>
            {getRoleBadge(activePersona.role)}
          </div>
          <span className="text-xs text-gray-400 font-mono">Team: {activePersona.team}</span>
        </div>
        <ChevronDown className={`w-4 h-4 text-gray-400 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-96 rounded-xl bg-[#131b2c] border border-gray-700/80 shadow-2xl z-50 overflow-hidden ring-1 ring-black ring-opacity-5">
          <div className="p-3 bg-[#0f1624] border-b border-gray-800">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-gray-300 uppercase tracking-wider flex items-center">
                <Users className="w-3.5 h-3.5 mr-1.5 text-indigo-400" />
                Live Role &amp; Permission Switcher
              </span>
              <span className="text-[11px] text-gray-400 bg-gray-800 px-2 py-0.5 rounded">
                6 Seeded Personas
              </span>
            </div>
            <p className="text-[11px] text-gray-400 mt-1">
              Select a persona below to instantaneously simulate claims and state machine transitions under different permission levels.
            </p>
          </div>

          <div className="max-h-80 overflow-y-auto divide-y divide-gray-800/60 p-1">
            {personas.map((persona) => {
              const isSelected = persona.id === activePersona.id;
              return (
                <button
                  key={persona.id}
                  onClick={() => {
                    switchPersona(persona.id);
                    setIsOpen(false);
                  }}
                  className={`w-full text-left p-3 rounded-lg flex items-start space-x-3 transition-colors ${
                    isSelected
                      ? 'bg-indigo-950/40 border border-indigo-500/30'
                      : 'hover:bg-gray-800/40 border border-transparent'
                  }`}
                >
                  <div
                    className={`w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-xs font-bold text-white shadow ${
                      isSelected ? 'bg-indigo-600 ring-2 ring-indigo-400' : 'bg-gray-700'
                    }`}
                  >
                    {persona.avatarInitials}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-0.5">
                      <span className="text-sm font-medium text-gray-100 truncate">{persona.name}</span>
                      {getRoleBadge(persona.role)}
                    </div>
                    <div className="text-xs text-gray-400 font-mono mb-1">
                      {persona.team} &bull; {persona.email}
                    </div>
                    <p className="text-xs text-gray-400 leading-relaxed">{persona.description}</p>
                  </div>
                  {isSelected && (
                    <Check className="w-4 h-4 text-indigo-400 flex-shrink-0 mt-1" />
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
