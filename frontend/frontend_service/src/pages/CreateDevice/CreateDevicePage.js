import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { axiosClient } from '../../api/axiosClient';

import '../../styles/common.css';
import './CreateDevicePage.css';

const DEVICE_MANAGE_ROLES = new Set([
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

const DeviceInfoSection = ({ device, onChange, disabled }) => {
  const fields = [
    {
      name: 'deviceName',
      label: 'Tên thiết bị',
      placeholder: 'Ví dụ: Cisco C9300-24T',
    },
    {
      name: 'ipAddress',
      label: 'Địa chỉ IP',
      placeholder: '172.16.200.1',
    },
    {
      name: 'deviceType',
      label: 'Loại thiết bị',
      placeholder: 'Router, Switch...',
    },
  ];

  return (
    <section className="device-create-section">
      <div className="device-create-section-title">
        <div>
          <h3>Thông tin thiết bị</h3>
          <p>Nhập thông tin cơ bản của thiết bị mạng cần giám sát.</p>
        </div>
      </div>

      <div className="form-grid">
        {fields.map((field) => (
          <div
            className={`form-group ${
              field.name === 'deviceName' ? 'device-full-width' : ''
            }`}
            key={field.name}
          >
            <label htmlFor={field.name}>{field.label} *</label>
            <input
              id={field.name}
              name={field.name}
              value={device[field.name]}
              onChange={onChange}
              placeholder={field.placeholder}
              disabled={disabled}
              required
            />
          </div>
        ))}
      </div>
    </section>
  );
};

const CreateDevicePage = ({
  currentUser: currentUserProp,
  canManageDevices: canManageDevicesProp,
}) => {
  const navigate = useNavigate();
  const location = useLocation();

  const initialUser =
    currentUserProp || location.state?.currentUser || readStoredUser();

  const [resolvedUser, setResolvedUser] = useState(initialUser);
  const [checkingPermission, setCheckingPermission] = useState(true);

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  const [adding, setAdding] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const [deviceForm, setDeviceForm] = useState({
    deviceName: '',
    ipAddress: '',
    deviceType: '',
  });

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    const handleChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleChange);

    return () => mediaQuery.removeEventListener('change', handleChange);
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

  const canManageDevices = useMemo(() => {
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    if (!resolvedUser || userRoles.size === 0) {
      return false;
    }
    return Array.from(userRoles).some((role) =>
      DEVICE_MANAGE_ROLES.has(role)
    );
  }, [canManageDevicesProp, resolvedUser, userRoles]);

  const handleDeviceChange = (e) => {
    const { name, value } = e.target;

    setDeviceForm((prev) => ({ ...prev, [name]: value }));
    setErrorMsg('');
  };

  const validateForm = () => {
    const { deviceName, ipAddress, deviceType } = deviceForm;

    if (!deviceName.trim() || !ipAddress.trim() || !deviceType.trim()) {
      return 'Vui lòng nhập đầy đủ thông tin thiết bị!';
    }

    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (checkingPermission || !canManageDevices) return;

    setErrorMsg('');

    const validationError = validateForm();

    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    try {
      setAdding(true);

      const res = await axiosClient.post('/devices', {
        deviceName: deviceForm.deviceName.trim(),
        ipAddress: deviceForm.ipAddress.trim(),
        deviceType: deviceForm.deviceType.trim(),
      });

      const apiRes = res.data;

      if (apiRes?.success === false) {
        setErrorMsg(apiRes.message || 'Thêm thiết bị thất bại!');
        return;
      }

      alert(apiRes?.message || 'Đã gửi yêu cầu thêm thiết bị thành công!');

      navigate('/dashboard', {
        state: { refreshDevices: true },
      });
    } catch (err) {
      const message =
        err.response?.data?.message ||
        err.message ||
        'Đã xảy ra lỗi không xác định.';

      setErrorMsg(message);
    } finally {
      setAdding(false);
    }
  };

  const handleCancel = () => {
    if (!adding) {
      navigate('/dashboard');
    }
  };

  if (checkingPermission) {
    return (
      <div
        className={`register-page device-create-page device-create-loading-state ${
          isDarkMode ? 'dark-mode' : 'light-mode'
        }`}
      >
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

  if (!canManageDevices) {
    return (
      <div
        className={`register-page device-create-page ${
          isDarkMode ? 'dark-mode' : 'light-mode'
        }`}
      >
        <div className="register-card device-create-card">
          <div className="permission-denied-box">
            <div className="permission-denied-icon">!</div>
            <h2 className="permission-denied-title">Không có quyền truy cập</h2>
            <p className="permission-message">
              Bạn không có quyền thêm thiết bị mới vào hệ thống. Chỉ tài khoản có vai trò SUPER_ADMIN hoặc NETWORK_ADMIN mới được phép truy cập trang này.
            </p>
          </div>
          <div className="register-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={handleCancel}
            >
              Quay lại
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className={`register-page device-create-page ${
        isDarkMode ? 'dark-mode' : 'light-mode'
      }`}
    >
      <div className="register-card device-create-card">
        <div className="device-create-header">
          <button
            type="button"
            className="device-back-button"
            onClick={handleCancel}
            disabled={adding}
          >
            ←
          </button>

          <h2 className="register-title">THÊM THIẾT BỊ MỚI</h2>

          <p className="device-create-description">
            Tạo yêu cầu thêm thiết bị mạng mới vào hệ thống giám sát.
          </p>
        </div>

        {errorMsg && (
          <div className="register-alert error device-error">
            <span style={{ whiteSpace: 'pre-line' }}>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="register-form">
          <DeviceInfoSection
            device={deviceForm}
            onChange={handleDeviceChange}
            disabled={adding}
          />

          <div className="register-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={handleCancel}
              disabled={adding}
            >
              Hủy
            </button>

            <button
              type="submit"
              className="submit-btn"
              disabled={adding}
            >
              {adding ? 'ĐANG GỬI YÊU CẦU...' : '+ THÊM THIẾT BỊ'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateDevicePage;