import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './RegisterPage.css';

const USER_ADMIN_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
]);

const PASSWORD_POLICY_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/;

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  if (!trimmed) return '';
  return trimmed.startsWith('ROLE_') ? trimmed : `ROLE_${trimmed}`;
};

const extractRoles = (source) => {
  const rolesSet = new Set();
  if (!source) return rolesSet;

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
      pushRole(val.roleName || val.role_name || val.name || val.authority || val.role);
    }
  };

  pushRole(source.roleName);
  pushRole(source.role_name);
  pushRole(source.role);
  pushRole(source.authority);
  pushRole(source.roles);
  pushRole(source.authorities);
  pushRole(source.realmRoles);
  pushRole(source.realm_roles);
  pushRole(source.realm_access?.roles);
  pushRole(source.scope);

  return rolesSet;
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

const readStoredUser = () => {
  try {
    const rawUser = localStorage.getItem('current_user');
    if (rawUser) {
      return JSON.parse(rawUser);
    }
  } catch {
  }

  const storedRole = localStorage.getItem('user_role');
  let storedRoles = [];
  try {
    const rawRoles = localStorage.getItem('user_roles');
    if (rawRoles) {
      const parsed = JSON.parse(rawRoles);
      if (Array.isArray(parsed)) storedRoles = parsed;
    }
  } catch {
  }

  if (storedRole || storedRoles.length > 0) {
    return {
      roleName: storedRole || '',
      roles: storedRoles,
    };
  }

  const accessToken = localStorage.getItem('access_token');
  if (accessToken) {
    const claims = decodeJwtPayload(accessToken);
    if (claims) {
      return claims;
    }
  }

  return null;
};

const EyeIcon = ({ show }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    {show ? (
      <>
        <path strokeLinecap="round" strokeLinejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" />
        <line x1="1" y1="1" x2="23" y2="23" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ) : (
      <>
        <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
      </>
    )}
  </svg>
);

const RegisterPage = ({
  currentUser: currentUserProp,
  canManageUsers: canManageUsersProp,
}) => {
  const navigate = useNavigate();
  const location = useLocation();

  const initialUser =
    currentUserProp || location.state?.currentUser || readStoredUser();

  const [resolvedUser, setResolvedUser] = useState(initialUser);
  const [checkingPermission, setCheckingPermission] = useState(true);

  const [formData, setFormData] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    email: '',
    fullName: '',
    phoneNumber: '',
    roleName: '',
    sendEmailVerify: true,
  });

  const [roles, setRoles] = useState([]);
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingRoles, setLoadingRoles] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  useEffect(() => {
    let isMounted = true;

    const verifyPermission = async () => {
      const accessToken = localStorage.getItem('access_token');
      if (!accessToken) {
        if (isMounted) {
          setResolvedUser(null);
          setCheckingPermission(false);
        }
        return;
      }

      try {
        const res = await axiosClient.get('/profile/me');
        const profileData = res.data?.data ?? res.data ?? null;
        if (isMounted) {
          setResolvedUser(profileData);
        }
      } catch {
        if (isMounted) {
          setResolvedUser(
            currentUserProp || location.state?.currentUser || readStoredUser()
          );
        }
      } finally {
        if (isMounted) {
          setCheckingPermission(false);
        }
      }
    };

    verifyPermission();

    return () => {
      isMounted = false;
    };
  }, [currentUserProp, location.state?.currentUser]);

  const userRoles = useMemo(
    () => extractRoles(resolvedUser),
    [resolvedUser]
  );

  const canRegisterUsers = useMemo(() => {
    if (typeof canManageUsersProp === 'boolean') {
      return canManageUsersProp;
    }
    if (!resolvedUser || userRoles.size === 0) {
      return false;
    }
    return Array.from(userRoles).some((role) => USER_ADMIN_ROLES.has(role));
  }, [canManageUsersProp, resolvedUser, userRoles]);

  useEffect(() => {
    if (checkingPermission) return;

    if (!canRegisterUsers) {
      setLoadingRoles(false);
      return;
    }

    setLoadingRoles(true);
    axiosClient
      .get('/users/roles')
      .then((res) => {
        const data = res.data?.data ?? res.data ?? [];
        const list = Array.isArray(data) ? data : [];
        setRoles(list);
        if (list.length > 0) {
          const firstRole = typeof list[0] === 'string' ? list[0] : list[0].name;
          setFormData((prev) => ({
            ...prev,
            roleName: prev.roleName || normalizeRole(firstRole),
          }));
        }
      })
      .catch(() => setErrorMsg('Không thể tải danh sách Roles từ hệ thống!'))
      .finally(() => setLoadingRoles(false));
  }, [checkingPermission, canRegisterUsers]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    setErrorMsg('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (checkingPermission || !canRegisterUsers) return;

    setErrorMsg('');

    if (!formData.username.trim()) {
      setErrorMsg('Vui lòng nhập username!');
      return;
    }

    if (!formData.email.trim()) {
      setErrorMsg('Vui lòng nhập email!');
      return;
    }

    if (!PASSWORD_POLICY_REGEX.test(formData.password)) {
      setErrorMsg(
        'Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt!'
      );
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setErrorMsg('Mật khẩu xác nhận không khớp!');
      return;
    }

    if (!formData.fullName.trim()) {
      setErrorMsg('Vui lòng nhập họ và tên!');
      return;
    }

    const normalizedRole = normalizeRole(formData.roleName);
    if (!normalizedRole) {
      setErrorMsg('Vui lòng chọn vai trò cho tài khoản!');
      return;
    }

    setLoading(true);

    try {
      await axiosClient.post('/auth/register', {
        username: formData.username.trim(),
        password: formData.password,
        confirmPassword: formData.confirmPassword,
        email: formData.email.trim(),
        fullName: formData.fullName.trim(),
        phoneNumber: formData.phoneNumber.trim(),
        roleName: normalizedRole,
        sendEmailVerify: formData.sendEmailVerify,
      });

      alert('Tạo tài khoản mới thành công!');
      navigate('/dashboard');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Tạo tài khoản thất bại!');
    } finally {
      setLoading(false);
    }
  };

  if (checkingPermission) {
    return (
      <div className={`register-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span
            className="spinner"
            style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }}
          />
          Đang kiểm tra quyền truy cập...
        </div>
      </div>
    );
  }

  if (!canRegisterUsers) {
    return (
      <div className={`register-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="register-card">
          <div className="permission-denied-box">
            <div className="permission-denied-icon">!</div>
            <h2 className="permission-denied-title">Không có quyền truy cập</h2>
            <p className="permission-message">
              Bạn không có quyền tạo tài khoản mới trong hệ thống. Chỉ tài khoản có vai trò SUPER_ADMIN mới được phép truy cập trang này.
            </p>
          </div>
          <div className="register-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={() => navigate('/dashboard')}
            >
              Quay lại
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`register-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="register-card">
        <h2 className="register-title">TẠO TÀI KHOẢN MỚI</h2>

        {errorMsg && (
          <div className="register-alert error">
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="register-form">
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="username">Username *</label>
              <div className="input-wrapper">
                <input
                  id="username"
                  type="text"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  placeholder="Nhập username"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="email">Email *</label>
              <div className="input-wrapper">
                <input
                  id="email"
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="user@example.com"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group password-group">
              <label htmlFor="password">Mật khẩu *</label>
              <div className="input-wrapper">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="toggle-password-button"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  <EyeIcon show={showPassword} />
                </button>
              </div>
            </div>

            <div className="form-group password-group">
              <label htmlFor="confirmPassword">Xác nhận mật khẩu *</label>
              <div className="input-wrapper">
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="toggle-password-button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  tabIndex={-1}
                >
                  <EyeIcon show={showConfirmPassword} />
                </button>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="fullName">Họ và Tên *</label>
              <div className="input-wrapper">
                <input
                  id="fullName"
                  type="text"
                  name="fullName"
                  value={formData.fullName}
                  onChange={handleChange}
                  placeholder="Nhập họ và tên"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="phoneNumber">Số điện thoại</label>
              <div className="input-wrapper">
                <input
                  id="phoneNumber"
                  type="text"
                  name="phoneNumber"
                  value={formData.phoneNumber}
                  onChange={handleChange}
                  placeholder="0901234567"
                  disabled={loading}
                />
              </div>
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="roleName">Vai trò (Role) *</label>
            <div className="input-wrapper">
              <select
                id="roleName"
                name="roleName"
                value={formData.roleName}
                onChange={handleChange}
                disabled={loading || loadingRoles}
                className="role-select"
              >
                {roles.map((r) => {
                  const rawRole = typeof r === 'string' ? r : r.name;
                  const roleVal = normalizeRole(rawRole);
                  return (
                    <option key={roleVal} value={roleVal}>
                      {roleVal.replace('ROLE_', '')}
                    </option>
                  );
                })}
              </select>
            </div>
          </div>

          <div className="checkbox-group">
            <input
              type="checkbox"
              id="sendEmailVerify"
              name="sendEmailVerify"
              checked={formData.sendEmailVerify}
              onChange={handleChange}
              disabled={loading}
            />
            <label htmlFor="sendEmailVerify">
              Gửi email xác thực tài khoản sau khi tạo
            </label>
          </div>

          <div className="register-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={() => navigate('/dashboard')}
              disabled={loading}
            >
              Hủy
            </button>

            <button
              type="submit"
              className="submit-btn"
              disabled={loading || loadingRoles}
            >
              {loading ? 'ĐANG TẠO...' : 'Tạo Tài Khoản'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default RegisterPage;