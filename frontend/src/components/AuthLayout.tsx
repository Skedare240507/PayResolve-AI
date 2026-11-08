import React from 'react';
import { Link } from 'react-router-dom';
import { PayResolveMark } from './PayResolveLogo';
import { ThemeToggle } from './ThemeToggle';

interface AuthLayoutProps {
  children: React.ReactNode;
}

export const AuthLayout: React.FC<AuthLayoutProps> = ({ children }) => {
  return (
    <div className="min-h-screen w-full flex flex-col md:flex-row font-sans text-slate-900 dark:text-slate-100 bg-white dark:bg-[#0b0f17]">
      {/* Left Gradient Panel */}
      <div className="w-full md:w-[45%] lg:w-[44%] bg-gradient-to-b from-[#071f49] via-[#0b337c] to-[#0f4eb7] text-white p-8 sm:p-12 lg:p-16 flex flex-col justify-between relative overflow-hidden shrink-0">
        
        {/* Top Logo Badge */}
        <div className="z-10 flex items-center justify-between">
          <Link 
            to="/" 
            className="inline-flex items-center space-x-2 px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/15 border border-white/20 text-white shadow-sm cursor-pointer backdrop-blur-xs transition-colors"
          >
            <PayResolveMark size={22} />
            <span className="text-sm font-semibold tracking-tight text-white flex items-center gap-1.5">
              PayResolve
              <span className="text-[10px] bg-blue-500/80 px-1 py-0.5 rounded text-white font-mono leading-none">
                AI
              </span>
            </span>
          </Link>
        </div>

        {/* Center Hero Heading */}
        <div className="my-auto py-12 z-10 space-y-4 max-w-md">
          <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight leading-[1.12] text-white">
            Every payment, with proof.
          </h1>
          <p className="text-blue-100/90 text-sm sm:text-base leading-relaxed font-normal">
            Investigate PayPal payment exceptions with an evidence graph and an AI investigator. Humans approve every action.
          </p>
        </div>

        {/* Bottom Disclaimer */}
        <div className="text-xs text-blue-200/70 font-normal z-10">
          Sandbox only. Synthetic data. No real money.
        </div>

        {/* Ambient background glows */}
        <div className="absolute -top-24 -right-24 w-96 h-96 bg-blue-400/20 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-24 -left-24 w-80 h-80 bg-sky-300/15 rounded-full blur-3xl pointer-events-none" />
      </div>

      {/* Right Content Panel */}
      <div className="w-full md:w-[55%] lg:w-[56%] flex flex-col justify-between p-6 sm:p-12 lg:p-20 bg-white dark:bg-[#0b0f17] relative">
        <div className="flex justify-end">
          <ThemeToggle />
        </div>
        <div className="w-full max-w-md mx-auto my-auto py-8">
          {children}
        </div>
        <div className="text-center text-xs text-slate-400 dark:text-slate-500 opacity-0 pointer-events-none">
          PayResolve AI
        </div>
      </div>
    </div>
  );
};
