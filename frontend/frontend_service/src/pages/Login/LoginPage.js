import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './LoginPage.css';

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  return trimmed.startsWith('ROLE_') ? trimmed : `ROLE_${trimmed}`;
};

const extractRolesFromPayload = (tokenData, userData) => {
  const rolesSet = new Set();

  const pushRole = (val) => {
    if (!val) return;
    if (Array.isArray(val)) {
      val.forEach((item) => pushRole(item));
      return;
    }
    if (typeof val === 'string') {
      val
        .split(/[\s,]+/)
        .map((r) => normalizeRole(r))
        .filter(Boolean)
        .forEach((r) => rolesSet.add(r));
    } else if (typeof val === 'object') {
      pushRole(val.roleName || val.name || val.authority || val.role);
    }
  };

  pushRole(userData?.roleName);
  pushRole(userData?.roles);
  pushRole(userData?.authorities);
  pushRole(tokenData?.roleName);
  pushRole(tokenData?.roles);
  pushRole(tokenData?.authorities);
  pushRole(tokenData?.scope);

  return Array.from(rolesSet);
};

const decodeJwtPayload = (token) => {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const jsonStr = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join('')
    );
    return JSON.parse(jsonStr);
  } catch {
    return null;
  }
};

const LoginPage = ({ onLoginSuccess }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  const navigate = useNavigate();

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (event) => setIsDarkMode(event.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  const handleLogin = async (e) => {
    e.preventDefault();
    setErrorMsg('');

    const trimmedUsername = username.trim();
    if (!trimmedUsername || !password) {
      setErrorMsg('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!');
      return;
    }

    setLoading(true);

    try {
      const response = await axiosClient.post('/auth/login', {
        username: trimmedUsername,
        password,
      });

      const tokenData = response.data?.data ?? response.data;
      const accessToken = tokenData?.access_token || tokenData?.accessToken;
      const refreshToken = tokenData?.refresh_token || tokenData?.refreshToken;

      if (!accessToken || !refreshToken) {
        setErrorMsg('Đăng nhập thất bại. Không nhận được thông tin xác thực.');
        return;
      }

      localStorage.setItem('access_token', accessToken);
      localStorage.setItem('refresh_token', refreshToken);

      let userProfile =
        tokenData?.user ||
        tokenData?.currentUser ||
        tokenData?.profile ||
        null;

      if (!userProfile) {
        try {
          const meRes = await axiosClient.get('/profile/me');
          userProfile = meRes.data?.data ?? meRes.data ?? null;
        } catch {
          userProfile = null;
        }
      }

      const jwtClaims = decodeJwtPayload(accessToken);
      const roles = extractRolesFromPayload(
        { ...tokenData, ...(jwtClaims || {}) },
        userProfile
      );

      const primaryRole =
        roles.find((r) => r === 'ROLE_SUPER_ADMIN') ||
        roles.find((r) => r === 'ROLE_NETWORK_ADMIN') ||
        roles.find((r) => r === 'ROLE_NOC_OPERATOR') ||
        roles.find((r) => r === 'ROLE_REPORT_MANAGER') ||
        roles[0] ||
        normalizeRole(userProfile?.roleName) ||
        'ROLE_GUEST_VIEWER';

      const currentUser = {
        ...(userProfile || {}),
        id: userProfile?.id ?? tokenData?.userId ?? tokenData?.id ?? jwtClaims?.sub ?? null,
        username: userProfile?.username || tokenData?.username || jwtClaims?.preferred_username || trimmedUsername,
        roleName: primaryRole,
        roles,
      };

      localStorage.setItem('user_role', primaryRole);
      localStorage.setItem('user_roles', JSON.stringify(roles));
      localStorage.setItem('current_user', JSON.stringify(currentUser));

      if (typeof onLoginSuccess === 'function') {
        onLoginSuccess(currentUser);
      }

      navigate('/dashboard', {
        state: { currentUser, roles, roleName: primaryRole },
      });
    } catch (error) {
      setErrorMsg(
        error.response?.data?.message ||
          'Tên đăng nhập hoặc mật khẩu không chính xác!'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="auth-backdrop-glow" />

      <form className="auth-card" onSubmit={handleLogin}>
        <div className="auth-header">
          <div className="brand-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h2 className="auth-title">Đăng Nhập</h2>
          <p className="auth-subtitle">Chào mừng bạn quay trở lại</p>
        </div>

        {errorMsg && (
          <div className="login-error">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <span>{errorMsg}</span>
          </div>
        )}

        <div className="form-group">
          <label htmlFor="username">Tên đăng nhập</label>
          <div className="input-wrapper">
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                setErrorMsg('');
              }}
              required
              autoComplete="username"
              placeholder="Nhập tên đăng nhập"
              disabled={loading}
            />
          </div>
        </div>

        <div className="form-group password-group">
          <label htmlFor="password">Mật khẩu</label>
          <div className="input-wrapper">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setErrorMsg('');
              }}
              required
              autoComplete="current-password"
              placeholder="Nhập mật khẩu"
              disabled={loading}
            />
            <button
              type="button"
              className="toggle-password-button"
              onClick={() => setShowPassword(!showPassword)}
              tabIndex={-1}
            >
              {showPassword ? (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" />
                  <line x1="1" y1="1" x2="23" y2="23" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              )}
            </button>
          </div>
        </div>

        <div className="forgot-password-container">
          <button
            type="button"
            className="forgot-password-button"
            onClick={() => navigate('/forgot-password')}
            disabled={loading}
          >
            Quên mật khẩu?
          </button>
        </div>

        <button
          type="submit"
          className="primary-button"
          disabled={loading}
        >
          {loading ? (
            <span className="button-loading-state">
              <span className="spinner" />
              Đang xử lý...
            </span>
          ) : (
            'ĐĂNG NHẬP'
          )}
        </button>
      </form>
    </div>
  );
};

export default LoginPage;