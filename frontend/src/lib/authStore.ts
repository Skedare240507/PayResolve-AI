import { useState, useEffect } from 'react';
import { api, type UserDto, type ForgotPasswordResponse, ApiError } from './api';

export interface UserAccount {
  id?: number;
  username: string;
  email: string;
  role: 'operator' | 'viewer';
  authenticated: boolean;
  createdAt?: string;
}

const STORAGE_SESSION_KEY = 'payresolve_current_user';

function normalizeRole(role: string): 'operator' | 'viewer' {
  return role.toUpperCase() === 'OPERATOR' ? 'operator' : 'viewer';
}

function userDtoToAccount(dto: UserDto): UserAccount {
  return {
    id: dto.id,
    username: dto.username,
    email: dto.email,
    role: normalizeRole(dto.role),
    authenticated: !!dto.authenticated,
    createdAt: new Date().toISOString(),
  };
}

export const getCurrentUser = (): UserAccount | null => {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem(STORAGE_SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

export const setCurrentUser = (user: UserAccount | null) => {
  if (typeof window === 'undefined') return;
  if (user) {
    localStorage.setItem(STORAGE_SESSION_KEY, JSON.stringify(user));
  } else {
    localStorage.removeItem(STORAGE_SESSION_KEY);
  }
  window.dispatchEvent(new Event('payresolve_auth_changed'));
};

export const logoutUser = async () => {
  try {
    await api.auth.logout();
  } catch {
    // ignore
  } finally {
    setCurrentUser(null);
  }
};

export const authenticateUser = async (
  username: string,
  password: string
): Promise<{ success: boolean; user?: UserAccount; error?: string }> => {
  try {
    const dto = await api.auth.login({ username, password });
    const account = userDtoToAccount(dto);
    setCurrentUser(account);
    return { success: true, user: account };
  } catch (err: unknown) {
    let msg = 'Invalid username or password.';
    if (err instanceof ApiError) {
      msg = err.problemDetail?.detail || err.message;
    } else if (err instanceof Error) {
      msg = err.message;
    }
    return { success: false, error: msg };
  }
};

export const registerUser = async (
  account: { username: string; email: string; password?: string; passwordHash?: string }
): Promise<{ success: boolean; user?: UserAccount; error?: string }> => {
  try {
    const password = account.password || account.passwordHash || 'demoPass123';
    const dto = await api.auth.signup({
      username: account.username,
      email: account.email,
      password,
    });
    const userAccount = userDtoToAccount(dto);
    setCurrentUser(userAccount);
    return { success: true, user: userAccount };
  } catch (err: unknown) {
    let msg = 'Failed to register account.';
    if (err instanceof ApiError) {
      msg = err.problemDetail?.detail || err.message;
    } else if (err instanceof Error) {
      msg = err.message;
    }
    return { success: false, error: msg };
  }
};

export const requestPasswordReset = async (
  identifier: string
): Promise<ForgotPasswordResponse> => {
  return api.auth.forgotPassword({ identifier });
};

export const performPasswordReset = async (
  token: string,
  newPassword: string
): Promise<{ success: boolean; message: string }> => {
  return api.auth.resetPassword({ token, newPassword });
};

/**
 * React hook for consuming and reacting to auth state.
 */
export function useAuth() {
  const [user, setUser] = useState<UserAccount | null>(() => getCurrentUser());
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    const checkMe = async () => {
      try {
        const dto = await api.auth.me();
        if (isMounted) {
          if (dto && dto.authenticated) {
            const acc = userDtoToAccount(dto);
            setUser(acc);
            setCurrentUser(acc);
          } else {
            setUser(null);
            setCurrentUser(null);
          }
        }
      } catch {
        if (isMounted) {
          // If session is unauthenticated, preserve local cache or clear
          const cached = getCurrentUser();
          setUser(cached);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    checkMe();

    const handleAuthChange = () => {
      setUser(getCurrentUser());
    };

    window.addEventListener('payresolve_auth_changed', handleAuthChange);
    return () => {
      isMounted = false;
      window.removeEventListener('payresolve_auth_changed', handleAuthChange);
    };
  }, []);

  return {
    user,
    loading,
    login: authenticateUser,
    logout: logoutUser,
    register: registerUser,
    requestPasswordReset,
    performPasswordReset,
  };
}
