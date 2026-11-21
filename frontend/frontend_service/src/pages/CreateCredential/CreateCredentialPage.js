import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './CreateCredentialPage.css';

const CREDENTIAL_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
]);

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
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
      pushRole(val.roleName || val.name || val.authority || val.role);
    }
  };

  pushRole(source.roleName);
  pushRole(source.roles);
  pushRole(source.authorities);
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
    // ignore parse error
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
    // ignore parse error
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

const CreateCredentialPage = ({
  currentUser: currentUserProp,
  canManageDevices: canManageDevicesProp,
  canManageCredentials: canManageCredentialsProp,
}) => {
  const { deviceId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const initialUser =
    currentUserProp || location.state?.currentUser || readStoredUser();

  const [resolvedUser, setResolvedUser] = useState(initialUser);
  const [checkingPermission, setCheckingPermission] = useState(true);

  const [formData, setFormData] = useState({
    credentialName: '',
    credentialType: 'SNMP',
    protocolType: 'SNMPV2C',
    communityString: '',
    port: '161',
    isActive: true
  });

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [showCommunity, setShowCommunity] = useState(false);

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

  const canManageCredentials = useMemo(() => {
    if (typeof canManageCredentialsProp === 'boolean') {
      return canManageCredentialsProp;
    }
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    if (!resolvedUser || userRoles.size === 0) {
      return false;
    }
    return Array.from(userRoles).some((role) =>
      CREDENTIAL_MANAGE_ROLES.has(role)
    );
  }, [
    canManageCredentialsProp,
    canManageDevicesProp,
    resolvedUser,
    userRoles,
  ]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    setErrorMsg('');
  };

  const handleBack = () => {
    if (deviceId) {
      navigate(`/devices/${deviceId}`, {
        state: { activeTab: 'credentials' }
      });
    } else {
      navigate('/dashboard');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (checkingPermission || !canManageCredentials) return;

    setErrorMsg('');

    const port = Number(formData.port);

    if (!formData.credentialName.trim()) {
      setErrorMsg('Vui lòng nhập tên thông tin xác thực.');
      return;
    }

    if (!formData.communityString.trim()) {
      setErrorMsg('Vui lòng nhập chuỗi Community.');
      return;
    }

    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      setErrorMsg('Cổng phải là số nguyên từ 1 đến 65535.');
      return;
    }

    const payload = {
      deviceId: Number(deviceId),
      credentialName: formData.credentialName.trim(),
      credentialType: formData.credentialType,
      protocolType: formData.protocolType,
      communityString: formData.communityString.trim(),
      port,
      isActive: formData.isActive
    };

    try {
      setLoading(true);
      await axiosClient.post('/device-credentials', payload);
      alert('Thêm thông tin xác thực thành công.');
      handleBack();
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message ||
        'Không thể thêm thông tin xác thực. Vui lòng thử lại.'
      );
    } finally {
      setLoading(false);
    }
  };

  if (checkingPermission) {
    return (
      <div className={`create-credential-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
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

  if (!canManageCredentials) {
    return (
      <div className={`create-credential-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="create-credential-card">
          <div className="permission-denied-box">
            <div className="permission-denied-icon">!</div>
            <h2 className="permission-denied-title">Không có quyền truy cập</h2>
            <p className="permission-message">
              Bạn không có quyền thêm thông tin xác thực cho thiết bị này. Chỉ tài khoản có vai trò SUPER_ADMIN hoặc NETWORK_ADMIN mới được phép truy cập trang này.
            </p>
          </div>
          <div className="create-credential-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={handleBack}
            >
              Quay lại
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`create-credential-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="create-credential-card">
        <h2 className="create-credential-title">THÊM THÔNG TIN XÁC THỰC</h2>
        <p className="create-credential-subtitle">Mã thiết bị: {deviceId}</p>

        {errorMsg && (
          <div className="create-credential-alert error">
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="create-credential-form">
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="credentialName">Tên thông tin xác thực *</label>
              <div className="input-wrapper">
                <input
                  id="credentialName"
                  name="credentialName"
                  type="text"
                  value={formData.credentialName}
                  onChange={handleChange}
                  placeholder="Nhập tên thông tin xác thực"
                  maxLength={100}
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="credentialType">Loại thông tin xác thực</label>
              <div className="input-wrapper">
                <select
                  id="credentialType"
                  name="credentialType"
                  value={formData.credentialType}
                  onChange={handleChange}
                  disabled={loading}
                  className="credential-select"
                >
                  <option value="SNMP">SNMP</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="protocolType">Phiên bản giao thức</label>
              <div className="input-wrapper">
                <select
                  id="protocolType"
                  name="protocolType"
                  value={formData.protocolType}
                  onChange={handleChange}
                  disabled={loading}
                  className="credential-select"
                >
                  <option value="SNMPV1">SNMP V1</option>
                  <option value="SNMPV2C">SNMP V2C</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="port">Cổng kết nối *</label>
              <div className="input-wrapper">
                <input
                  id="port"
                  name="port"
                  type="number"
                  min="1"
                  max="65535"
                  value={formData.port}
                  onChange={handleChange}
                  placeholder="161"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group full-width password-group">
              <label htmlFor="communityString">Chuỗi Community *</label>
              <div className="input-wrapper">
                <input
                  id="communityString"
                  name="communityString"
                  type={showCommunity ? 'text' : 'password'}
                  value={formData.communityString}
                  onChange={handleChange}
                  placeholder="Nhập chuỗi Community"
                  autoComplete="new-password"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="toggle-password-button"
                  onClick={() => setShowCommunity(!showCommunity)}
                  tabIndex={-1}
                >
                  <EyeIcon show={showCommunity} />
                </button>
              </div>
            </div>
          </div>

          <div className="checkbox-group">
            <input
              type="checkbox"
              id="isActive"
              name="isActive"
              checked={formData.isActive}
              onChange={handleChange}
              disabled={loading}
            />
            <label htmlFor="isActive">
              Kích hoạt thông tin xác thực khi giám sát thiết bị
            </label>
          </div>

          <div className="create-credential-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={handleBack}
              disabled={loading}
            >
              Hủy
            </button>

            <button
              type="submit"
              className="submit-btn"
              disabled={loading}
            >
              {loading ? 'ĐANG LƯU...' : 'Thêm Thông Tin Xác Thực'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateCredentialPage;