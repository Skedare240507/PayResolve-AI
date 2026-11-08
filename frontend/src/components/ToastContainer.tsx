import React, { useEffect, useState } from 'react';
import { toast, type Toast } from '../lib/toast';
import { GoogleIcon } from './GoogleIcon';

export const ToastContainer: React.FC = () => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  useEffect(() => {
    return toast.subscribe(setToasts);
  }, []);

  if (toasts.length === 0) return null;

  return (
    <aside 
      aria-label="Notifications"
      className="fixed bottom-6 right-6 z-50 flex flex-col space-y-3 pointer-events-none max-w-sm w-full"
    >
      {toasts.map(t => {
        let iconName = 'info';
        let iconColor = 'text-blue-500';
        let borderClass = 'border-slate-200 dark:border-slate-800';

        if (t.type === 'success') {
          iconName = 'check_circle';
          iconColor = 'text-emerald-500';
          borderClass = 'border-emerald-200/80 dark:border-emerald-800/80';
        } else if (t.type === 'error') {
          iconName = 'error';
          iconColor = 'text-red-500';
          borderClass = 'border-red-200/80 dark:border-red-800/80';
        } else if (t.type === 'warning') {
          iconName = 'warning';
          iconColor = 'text-amber-500';
          borderClass = 'border-amber-200/80 dark:border-amber-800/80';
        }

        return (
          <div
            key={t.id}
            role="alert"
            className={`pointer-events-auto bg-white dark:bg-[#131b26] border ${borderClass} rounded-xl shadow-lg p-3.5 flex items-start space-x-3 transition-all transform animate-in fade-in slide-in-from-bottom-2 duration-200`}
          >
            <div className={`mt-0.5 shrink-0 ${iconColor}`}>
              <GoogleIcon name={iconName} className="text-lg" />
            </div>
            <div className="flex-1 min-w-0">
              {t.title && (
                <div className="text-xs font-semibold text-slate-900 dark:text-white">
                  {t.title}
                </div>
              )}
              <div className="text-xs text-slate-700 dark:text-slate-300 break-words leading-relaxed">
                {t.message}
              </div>
            </div>
            {t.action && (
              <button
                type="button"
                onClick={() => {
                  t.action?.onClick();
                  toast.dismiss(t.id);
                }}
                className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer shrink-0"
              >
                {t.action.label}
              </button>
            )}
            <button
              type="button"
              onClick={() => toast.dismiss(t.id)}
              className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer shrink-0 ml-1"
            >
              <GoogleIcon name="close" className="text-sm" />
            </button>
          </div>
        );
      })}
    </aside>
  );
};
