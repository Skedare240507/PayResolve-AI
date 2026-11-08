// Toast notification manager for PayResolve
export interface Toast {
  id: string;
  type: 'success' | 'error' | 'info' | 'warning';
  title?: string;
  message: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  durationMs?: number;
}

type ToastListener = (toasts: Toast[]) => void;

class ToastManager {
  private toasts: Toast[] = [];
  private listeners: Set<ToastListener> = new Set();

  subscribe(listener: ToastListener) {
    this.listeners.add(listener);
    listener([...this.toasts]);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    for (const listener of this.listeners) {
      listener([...this.toasts]);
    }
  }

  show(toast: Omit<Toast, 'id'>): string {
    const id = 'toast_' + Math.random().toString(36).substring(2, 9);
    const newToast: Toast = { ...toast, id };
    this.toasts.push(newToast);
    this.notify();

    const duration = toast.durationMs ?? 4000;
    if (duration > 0) {
      setTimeout(() => {
        this.dismiss(id);
      }, duration);
    }

    return id;
  }

  success(message: string, options?: Partial<Omit<Toast, 'id' | 'type' | 'message'>>) {
    return this.show({ type: 'success', message, ...options });
  }

  error(message: string, options?: Partial<Omit<Toast, 'id' | 'type' | 'message'>>) {
    return this.show({ type: 'error', message, ...options });
  }

  info(message: string, options?: Partial<Omit<Toast, 'id' | 'type' | 'message'>>) {
    return this.show({ type: 'info', message, ...options });
  }

  warning(message: string, options?: Partial<Omit<Toast, 'id' | 'type' | 'message'>>) {
    return this.show({ type: 'warning', message, ...options });
  }

  dismiss(id: string) {
    this.toasts = this.toasts.filter(t => t.id !== id);
    this.notify();
  }

  clear() {
    this.toasts = [];
    this.notify();
  }
}

export const toast = new ToastManager();

