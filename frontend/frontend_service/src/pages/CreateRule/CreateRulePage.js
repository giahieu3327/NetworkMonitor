import React, { useEffect, useState, useMemo } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './CreateRulePage.css';

const RULE_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
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
  metricType: '',
  warningLimit: '',
  criticalLimit: '',
  consecutiveOccurrences: '3',
  isEnabled: true,
};

const CreateRulePage = ({
  currentUser: currentUserProp,
  canManageDevices: canManageDevicesProp,
  canManageRules: canManageRulesProp,
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
  const [allowedMetrics, setAllowedMetrics] = useState([]);
  const [loadingMetrics, setLoadingMetrics] = useState(true);
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

  const canManageRules = useMemo(() => {
    if (typeof canManageRulesProp === 'boolean') {
      return canManageRulesProp;
    }
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    if (!resolvedUser || userRoles.size === 0) {
      return false;
    }
    return Array.from(userRoles).some((role) =>
      RULE_MANAGE_ROLES.has(role)
    );
  }, [
    canManageRulesProp,
    canManageDevicesProp,
    resolvedUser,
    userRoles,
  ]);

  useEffect(() => {
    if (!deviceId || !Number.isInteger(Number(deviceId)) || Number(deviceId) <= 0) {
      setErrorMsg('Không tìm thấy thiết bị cần tạo luật cảnh báo.');
    }
  }, [deviceId]);

  useEffect(() => {
    if (checkingPermission) return;

    if (!canManageRules) {
      setLoadingMetrics(false);
      return;
    }

    const fetchAllowedMetrics = async () => {
      if (!deviceId) return;

      try {
        setLoadingMetrics(true);
        const res = await axiosClient.get(`/threshold-rules/device/${deviceId}/allowed-metrics`);
        const list = res.data?.data ?? res.data ?? [];
        const validList = Array.isArray(list) ? list : [];

        setAllowedMetrics(validList);

        if (validList.length > 0) {
          setFormData((prev) => ({
            ...prev,
            metricType: prev.metricType || validList[0].metricType,
          }));
        }
      } catch (err) {
        setErrorMsg(
          err.response?.data?.message ||
            'Không thể tải danh sách Metric Type hợp lệ.'
        );
      } finally {
        setLoadingMetrics(false);
      }
    };

    fetchAllowedMetrics();
  }, [deviceId, checkingPermission, canManageRules]);

  const selectedMetricMeta = useMemo(
    () =>
      allowedMetrics.find((item) => item.metricType === formData.metricType) ||
      null,
    [allowedMetrics, formData.metricType]
  );

  const handleBack = () => {
    if (deviceId) {
      navigate(`/devices/${deviceId}`, {
        state: { activeTab: 'rules' },
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
    if (!formData.metricType) {
      return 'Vui lòng chọn Metric Type.';
    }

    const warningLimit = Number(formData.warningLimit);
    const criticalLimit = Number(formData.criticalLimit);

    if (formData.warningLimit === '' || !Number.isFinite(warningLimit)) {
      return 'Vui lòng nhập ngưỡng Warning hợp lệ.';
    }

    if (formData.criticalLimit === '' || !Number.isFinite(criticalLimit)) {
      return 'Vui lòng nhập ngưỡng Critical hợp lệ.';
    }

    if (selectedMetricMeta) {
      const { minValue, maxValue, conditionDirection, unit } = selectedMetricMeta;
      const unitSuffix = unit ? ` (${unit})` : '';

      if (minValue !== null && minValue !== undefined) {
        if (warningLimit < minValue || criticalLimit < minValue) {
          return `Giá trị ngưỡng phải lớn hơn hoặc bằng ${minValue}${unitSuffix}.`;
        }
      }

      if (maxValue !== null && maxValue !== undefined) {
        if (warningLimit > maxValue || criticalLimit > maxValue) {
          return `Giá trị ngưỡng phải nhỏ hơn hoặc bằng ${maxValue}${unitSuffix}.`;
        }
      }

      if (
        conditionDirection === 'GREATER_THAN' &&
        criticalLimit <= warningLimit
      ) {
        return 'Với điều kiện vượt ngưỡng (GREATER_THAN), ngưỡng Critical phải lớn hơn ngưỡng Warning.';
      }

      if (
        conditionDirection === 'LESS_THAN' &&
        criticalLimit >= warningLimit
      ) {
        return 'Với điều kiện giảm dưới ngưỡng (LESS_THAN), ngưỡng Critical phải nhỏ hơn ngưỡng Warning.';
      }
    }

    const occurrences = Number(formData.consecutiveOccurrences);
    if (!Number.isInteger(occurrences) || occurrences < 1) {
      return 'Số lần liên tiếp phải là số nguyên lớn hơn hoặc bằng 1.';
    }

    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (checkingPermission || !canManageRules) return;

    setErrorMsg('');

    const validationError = validateForm();
    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    const payload = {
      deviceId: Number(deviceId),
      metricType: formData.metricType,
      warningLimit: Number(formData.warningLimit),
      criticalLimit: Number(formData.criticalLimit),
      consecutiveOccurrences: Number(formData.consecutiveOccurrences),
      isEnabled: formData.isEnabled,
    };

    try {
      setLoading(true);
      await axiosClient.post('/threshold-rules', payload);
      alert('Tạo luật cảnh báo thành công.');
      handleBack();
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message ||
          'Không thể tạo luật cảnh báo. Vui lòng kiểm tra lại thông tin.'
      );
    } finally {
      setLoading(false);
    }
  };

  const deviceTitle =
    device?.deviceName || device?.name || `Mã thiết bị: ${deviceId}`;

  if (checkingPermission) {
    return (
      <div className={`create-rule-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
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

  if (!canManageRules) {
    return (
      <div className={`create-rule-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="create-rule-card">
          <div className="permission-denied-box">
            <div className="permission-denied-icon">!</div>
            <h2 className="permission-denied-title">Không có quyền truy cập</h2>
            <p className="permission-message">
              Bạn không có quyền tạo luật cảnh báo cho thiết bị này. Chỉ tài khoản có vai trò SUPER_ADMIN, NETWORK_ADMIN hoặc NOC_OPERATOR mới được phép truy cập trang này.
            </p>
          </div>
          <div className="create-rule-actions">
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
    <div className={`create-rule-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="create-rule-card">
        <h2 className="create-rule-title">THÊM LUẬT CẢNH BÁO</h2>
        <p className="create-rule-subtitle">
          {deviceTitle}
          {device?.ipAddress ? ` (${device.ipAddress})` : ''}
        </p>

        {errorMsg && (
          <div className="create-rule-alert error">
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="create-rule-form">
          <div className="form-grid">
            <div className="form-group full-width">
              <label htmlFor="metricType">Metric Type *</label>
              <div className="input-wrapper">
                <select
                  id="metricType"
                  name="metricType"
                  value={formData.metricType}
                  onChange={handleChange}
                  disabled={loading || loadingMetrics || allowedMetrics.length === 0}
                  className="rule-select"
                >
                  {loadingMetrics ? (
                    <option value="">Đang tải danh sách metric...</option>
                  ) : allowedMetrics.length === 0 ? (
                    <option value="">Không có metric khả dụng</option>
                  ) : (
                    allowedMetrics.map((metric) => (
                      <option key={metric.metricType} value={metric.metricType}>
                        {metric.metricType} {metric.unit ? `(${metric.unit})` : ''}
                      </option>
                    ))
                  )}
                </select>
              </div>
              {selectedMetricMeta && (
                <small className="field-hint">
                  Điều kiện:{' '}
                  <strong>
                    {selectedMetricMeta.conditionDirection === 'LESS_THAN'
                      ? 'Nhỏ hơn ngưỡng (<)'
                      : 'Lớn hơn ngưỡng (>)'}
                  </strong>
                  {selectedMetricMeta.minValue !== null ||
                  selectedMetricMeta.maxValue !== null
                    ? ` • Phạm vi: ${selectedMetricMeta.minValue ?? '0'} – ${
                        selectedMetricMeta.maxValue ?? '∞'
                      } ${selectedMetricMeta.unit || ''}`
                    : ''}
                </small>
              )}
            </div>

            <div className="form-group">
              <label htmlFor="warningLimit">
                Ngưỡng Warning {selectedMetricMeta?.unit ? `(${selectedMetricMeta.unit})` : ''} *
              </label>
              <div className="input-wrapper">
                <input
                  id="warningLimit"
                  name="warningLimit"
                  type="number"
                  min={selectedMetricMeta?.minValue ?? undefined}
                  max={selectedMetricMeta?.maxValue ?? undefined}
                  step="any"
                  value={formData.warningLimit}
                  onChange={handleChange}
                  placeholder="Ví dụ: 50"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="criticalLimit">
                Ngưỡng Critical {selectedMetricMeta?.unit ? `(${selectedMetricMeta.unit})` : ''} *
              </label>
              <div className="input-wrapper">
                <input
                  id="criticalLimit"
                  name="criticalLimit"
                  type="number"
                  min={selectedMetricMeta?.minValue ?? undefined}
                  max={selectedMetricMeta?.maxValue ?? undefined}
                  step="any"
                  value={formData.criticalLimit}
                  onChange={handleChange}
                  placeholder="Ví dụ: 80"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group full-width">
              <label htmlFor="consecutiveOccurrences">
                Số lần vi phạm liên tiếp *
              </label>
              <div className="input-wrapper">
                <input
                  id="consecutiveOccurrences"
                  name="consecutiveOccurrences"
                  type="number"
                  min="1"
                  step="1"
                  value={formData.consecutiveOccurrences}
                  onChange={handleChange}
                  placeholder="Ví dụ: 3"
                  required
                  disabled={loading}
                />
              </div>
              <small className="field-hint">
                Hệ thống chỉ phát cảnh báo khi chỉ số vượt ngưỡng liên tiếp đủ số chu kỳ này.
              </small>
            </div>
          </div>

          <div className="checkbox-group">
            <input
              type="checkbox"
              id="isEnabled"
              name="isEnabled"
              checked={formData.isEnabled}
              onChange={handleChange}
              disabled={loading}
            />
            <label htmlFor="isEnabled">
              Kích hoạt luật cảnh báo ngay sau khi tạo
            </label>
          </div>

          <div className="create-rule-actions">
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
              disabled={loading || loadingMetrics || !deviceId}
            >
              {loading ? 'ĐANG LƯU...' : 'Tạo Luật Cảnh Báo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateRulePage;