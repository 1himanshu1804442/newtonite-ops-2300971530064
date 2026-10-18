import React from 'react';
import { PersonaSwitcher } from './PersonaSwitcher';
import { Activity, Shield, Zap } from 'lucide-react';

export const Header: React.FC = () => {
  return (
    <header className="border-b border-gray-800 bg-[#0d131f]/90 backdrop-blur-md sticky top-0 z-40 px-6 py-3.5 transition-colors">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Left: Branding & System Architecture Badges */}
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-purple-600 flex items-center justify-center text-white shadow-lg shadow-indigo-500/20 ring-1 ring-white/10">
              <Zap className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="text-lg font-bold text-white tracking-tight">Newtonite Ops</h1>
                <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  Mission Critical
                </span>
              </div>
              <p className="text-xs text-gray-400">Incident Triage &amp; Concurrency Under Pressure</p>
            </div>
          </div>

          <div className="hidden md:flex items-center space-x-2 pl-4 border-l border-gray-800 text-xs">
            <span className="flex items-center text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded-full font-mono text-[11px]">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse mr-1.5"></span>
              OCC &amp; Outbox Active
            </span>
            <span className="flex items-center text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 px-2 py-0.5 rounded-full font-mono text-[11px]">
              <Shield className="w-3 h-3 mr-1" />
              RBAC Guarded
            </span>
          </div>
        </div>

        {/* Right: Persona Switcher Dropdown */}
        <div className="flex items-center space-x-3">
          <PersonaSwitcher />
        </div>
      </div>
    </header>
  );
};
