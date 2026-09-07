import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

function readStoredUser() {
  try {
    const savedUser = sessionStorage.getItem('lm_guard_user');
    return savedUser ? JSON.parse(savedUser) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser);
  const [loading, setLoading] = useState(false);
  // True while a stored token is being re-validated against the backend on load.
  const [checkingSession, setCheckingSession] = useState(true);

  // A token can outlive the session it belongs to (server restart, expiry,
  // account disabled). Confirm it against /auth/me before trusting the
  // cached profile, so a stale token doesn't render the app as "signed in"
  // right up until the first API call fails.
  useEffect(() => {
    const token = sessionStorage.getItem('lm_guard_token');
    if (!token) {
      setCheckingSession(false);
      return;
    }
    api
      .get('/auth/me')
      .then(({ data }) => {
        if (data?.data) {
          setUser(data.data);
          sessionStorage.setItem('lm_guard_user', JSON.stringify(data.data));
        }
      })
      .catch(() => {
        sessionStorage.removeItem('lm_guard_user');
        sessionStorage.removeItem('lm_guard_token');
        setUser(null);
      })
      .finally(() => setCheckingSession(false));
  }, []);

  const login = async (email, password) => {
    setLoading(true);
    try {
      const { data } = await api.post('/auth/login', { email, password });
      const auth = data?.data;
      if (!auth?.user || !auth?.token) {
        throw new Error('Sign-in failed. Check the credentials and try again.');
      }
      if (auth.user.role !== 'ADMIN') {
        throw new Error(
          'This console is for administrators only. Inspecting officers should use the LM-GUARD field application.',
        );
      }

      sessionStorage.setItem('lm_guard_token', auth.token);
      sessionStorage.setItem('lm_guard_user', JSON.stringify(auth.user));
      setUser(auth.user);
      return auth.user;
    } catch (err) {
      throw new Error(err.message || 'Sign-in failed. Check the credentials and try again.');
    } finally {
      setLoading(false);
    }
  };

  /**
   * Self-service officer registration (unauthenticated, matches the public
   * `POST /auth/register` endpoint). Always provisions an INSPECTOR account —
   * this console never signs the caller into it, since inspecting officers
   * use the separate field application.
   */
  const register = async ({ name, email, password }) => {
    setLoading(true);
    try {
      const { data } = await api.post('/auth/register', { name, email, password, role: 'INSPECTOR' });
      return data?.data?.user;
    } catch (err) {
      throw new Error(err.message || 'Registration could not be completed.');
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    sessionStorage.removeItem('lm_guard_user');
    sessionStorage.removeItem('lm_guard_token');
    setUser(null);
  };

  const value = {
    user,
    // Defense in depth: even if a non-admin profile ever ended up cached
    // (e.g. a stale session from before this check existed), the console
    // treats it as signed out rather than rendering admin screens for it.
    isAuthenticated: !!user && user.role === 'ADMIN',
    login,
    register,
    logout,
    loading,
    checkingSession,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
