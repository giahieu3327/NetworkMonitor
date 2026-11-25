import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './CreateOidConfigPage.css';

const OID_CONFIG_MANAGE_ROLES = new Set([
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

const initialForm = {
  metricScope: 'DEVICE',
  metricType: '',
  oidKind: 'SINGLE',
  oidPattern: '',
  dataType: 'INTEGER',
  multiplier: '1',
  interfaceIndex: '',
  isActive: true,
};

const SCOPE_OPTIONS = [
  { value: 'DEVICE', label: 'DEVICE - Thông tin thiết bị' },
  { value: 'INTERFACE', label: 'INTERFACE - Giao diện mạng' },
  { value: 'ARP', label: 'ARP - Bảng địa chỉ ARP' },
  { value: 'METRIC', label: 'METRIC - Thông số giám sát' },
];

const DATA_TYPE_OPTIONS = [
  'INTEGER',
  'GAUGE',
  'GAUGE32',
  'COUNTER',
  'COUNTER32',
  'COUNTER64',
  'TIMETICKS',
  'STRING',
  'IPADDRESS',
  'OCTET_STRING',
];

const CreateOidConfigPage = ({
  currentUser: currentUserProp,
  canManageDevices: canManageDevicesProp,
  canManageOidConfigs: canManageOidConfigsProp,
}) => {
  const { deviceId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  const device = location.state?.device || null;
  const initialUser =
    currentUserProp || location.state?.currentUser || readStoredUser();

  const [resolvedUser, setResolvedUser] = useState(initialUser);
  const [checkingPermission, setCheckingPermission] = useState(true);

  const [formData, setFormData] = useState(initialForm);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

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

  const canManageOidConfigs = useMemo(() => {
    if (typeof canManageOidConfigsProp === 'boolean') {
      return canManageOidConfigsProp;
    }
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    if (!resolvedUser || userRoles.size === 0) {
      return false;
    }
    return Array.from(userRoles).some((role) =>
      OID_CONFIG_MANAGE_ROLES.has(role)
    );
  }, [
    canManageOidConfigsProp,
    canManageDevicesProp,
    resolvedUser,
    userRoles,
  ]);

  useEffect(() => {
    if (!deviceId || !Number.isInteger(Number(deviceId)) || Number(deviceId) <= 0) {
      setErrorMsg('Không tìm thấy thiết bị cần cấu hình OID.');
    }
  }, [deviceId]);

  const handleBack = () => {
    if (deviceId) {
      navigate(`/devices/${deviceId}`, {
        state: { activeTab: 'oidconfig' },
      });
    } else {
      navigate('/dashboard');
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    setErrorMsg('');
  };

  const validateForm = () => {
    if (!formData.metricScope) {
      return 'Vui lòng chọn Metric Scope.';
    }

    if (!formData.metricType.trim()) {
      return 'Vui lòng nhập Metric Type.';
    }

    if (!formData.oidKind) {
      return 'Vui lòng chọn OID Kind.';
    }

    if (!formData.oidPattern.trim()) {
      return 'Vui lòng nhập OID Pattern.';
    }

    if (!/^\d+(\.\d+)*$/.test(formData.oidPattern.trim())) {
      return 'OID chỉ được chứa chữ số và dấu chấm, không được có dấu chấm ở đầu hoặc cuối.';
    }

    if (!DATA_TYPE_OPTIONS.includes(formData.dataType)) {
      return 'Kiểu dữ liệu không hợp lệ.';
    }

    const multiplier = Number(formData.multiplier);

    if (!Number.isFinite(multiplier) || multiplier <= 0) {
      return 'Multiplier phải là số lớn hơn 0.';
    }

    if (formData.interfaceIndex !== '') {
      const interfaceIndex = Number(formData.interfaceIndex);

      if (!Number.isInteger(interfaceIndex) || interfaceIndex <= 0) {
        return 'Interface Index phải là số nguyên lớn hơn 0.';
      }
    }

    if (
      formData.metricScope === 'INTERFACE' &&
      formData.oidKind === 'SINGLE' &&
      !formData.interfaceIndex
    ) {
      return 'Vui lòng nhập Interface Index cho INTERFACE + SINGLE.';
    }

    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (checkingPermission || !canManageOidConfigs) return;

    setErrorMsg('');

    const validationError = validateForm();

    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    const payload = {
      deviceId: Number(deviceId),
      metricScope: formData.metricScope,
      metricType: formData.metricType.trim(),
      oidKind: formData.oidKind,
      oidPattern: formData.oidPattern.trim(),
      dataType: formData.dataType,
      multiplier: Number(formData.multiplier),
      interfaceIndex:
        formData.interfaceIndex !== ''
          ? Number(formData.interfaceIndex)
          : null,
      isActive: formData.isActive,
    };

    try {
      setLoading(true);
      await axiosClient.post('/oid-configs', payload);
      alert('Thêm cấu hình OID thành công.');
      handleBack();
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message ||
        'Không thể tạo cấu hình OID. Vui lòng kiểm tra lại thông tin.'
      );
    } finally {
      setLoading(false);
    }
  };

  const deviceTitle =
    device?.deviceName || device?.name || `Mã thiết bị: ${deviceId}`;

  if (checkingPermission) {
    return (
      <div className={`create-oid-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
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

  if (!canManageOidConfigs) {
    return (
      <div className={`create-oid-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="create-oid-card">
          <div className="permission-denied-box">
            <div className="permission-denied-icon">!</div>
            <h2 className="permission-denied-title">Không có quyền truy cập</h2>
            <p className="permission-message">
              Bạn không có quyền thêm cấu hình OID cho thiết bị này. Chỉ tài khoản có vai trò SUPER_ADMIN hoặc NETWORK_ADMIN mới được phép truy cập trang này.
            </p>
          </div>
          <div className="create-oid-actions">
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
    <div className={`create-oid-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="create-oid-card">
        <h2 className="create-oid-title">THÊM CẤU HÌNH OID</h2>
        <p className="create-oid-subtitle">
          {deviceTitle}
          {device?.ipAddress ? ` (${device.ipAddress})` : ''}
        </p>

        {errorMsg && (
          <div className="create-oid-alert error">
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="create-oid-form">
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="metricScope">Metric Scope *</label>
              <div className="input-wrapper">
                <select
                  id="metricScope"
                  name="metricScope"
                  value={formData.metricScope}
                  onChange={handleChange}
                  disabled={loading}
                  className="oid-select"
                >
                  {SCOPE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="metricType">Metric Type *</label>
              <div className="input-wrapper">
                <input
                  id="metricType"
                  name="metricType"
                  type="text"
                  value={formData.metricType}
                  onChange={handleChange}
                  placeholder="Ví dụ: System Description"
                  maxLength={150}
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="oidKind">OID Kind *</label>
              <div className="input-wrapper">
                <select
                  id="oidKind"
                  name="oidKind"
                  value={formData.oidKind}
                  onChange={handleChange}
                  disabled={loading}
                  className="oid-select"
                >
                  <option value="SINGLE">SINGLE - SNMP GET</option>
                  <option value="TABLE_COLUMN">TABLE_COLUMN - SNMP WALK</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="dataType">Data Type *</label>
              <div className="input-wrapper">
                <select
                  id="dataType"
                  name="dataType"
                  value={formData.dataType}
                  onChange={handleChange}
                  disabled={loading}
                  className="oid-select"
                >
                  {DATA_TYPE_OPTIONS.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-group full-width">
              <label htmlFor="oidPattern">OID Pattern *</label>
              <div className="input-wrapper">
                <input
                  id="oidPattern"
                  name="oidPattern"
                  type="text"
                  value={formData.oidPattern}
                  onChange={handleChange}
                  placeholder="Ví dụ: 1.3.6.1.2.1.1.1.0"
                  required
                  disabled={loading}
                />
              </div>
              <small className="field-hint">
                SINGLE thường sử dụng OID scalar kết thúc bằng .0; TABLE_COLUMN sử dụng OID của cột bảng.
              </small>
            </div>

            <div className="form-group">
              <label htmlFor="multiplier">Multiplier *</label>
              <div className="input-wrapper">
                <input
                  id="multiplier"
                  name="multiplier"
                  type="number"
                  min="0.000001"
                  step="any"
                  value={formData.multiplier}
                  onChange={handleChange}
                  required
                  disabled={loading}
                />
              </div>
              <small className="field-hint">
                Giá trị SNMP sẽ được nhân với hệ số này.
              </small>
            </div>

            <div className="form-group">
              <label htmlFor="interfaceIndex">
                Interface Index {formData.metricScope === 'INTERFACE' && formData.oidKind === 'SINGLE' ? '*' : ''}
              </label>
              <div className="input-wrapper">
                <input
                  id="interfaceIndex"
                  name="interfaceIndex"
                  type="number"
                  min="1"
                  step="1"
                  value={formData.interfaceIndex}
                  onChange={handleChange}
                  placeholder={
                    formData.metricScope === 'INTERFACE' && formData.oidKind === 'SINGLE'
                      ? 'Ví dụ: 2'
                      : 'Để trống nếu không áp dụng'
                  }
                  disabled={loading}
                />
              </div>
              <small className="field-hint">
                {formData.metricScope === 'INTERFACE' && formData.oidKind === 'SINGLE'
                  ? 'Bắt buộc khi truy vấn một interface cụ thể.'
                  : 'Tùy chọn (nhập nếu cần chỉ định index interface).'}
              </small>
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
              Cho phép hệ thống sử dụng OID này khi thu thập dữ liệu
            </label>
          </div>

          <div className="create-oid-actions">
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
              disabled={loading || !deviceId}
            >
              {loading ? 'ĐANG LƯU...' : 'Tạo Cấu Hình OID'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateOidConfigPage;