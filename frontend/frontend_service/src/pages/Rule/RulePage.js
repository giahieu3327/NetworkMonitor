import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './RulePage.css';

const ALLOWED_VIEW_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_REPORT_MANAGER',
  'ROLE_GUEST_VIEWER',
]);

const RULE_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
]);

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';

  const trimmed = role.trim().toUpperCase();

  if (!trimmed) return '';

  return trimmed.startsWith('ROLE_')
    ? trimmed
    : `ROLE_${trimmed}`;
};

const getRoleValue = (role) => {
  if (typeof role === 'string') {
    return role;
  }

  if (role && typeof role === 'object') {
    return (
      role.authority ||
      role.roleName ||
      role.role_name ||
      role.name ||
      role.role ||
      ''
    );
  }

  return '';
};

const getUserRoles = (user) => {
  const roles = new Set();

  if (!user || typeof user !== 'object') {
    return roles;
  }

  const singleRoleFields = [
    user.roleName,
    user.role_name,
    user.role,
    user.authority,
  ];

  singleRoleFields.forEach((role) => {
    const normalized = normalizeRole(getRoleValue(role));

    if (normalized) {
      roles.add(normalized);
    }
  });

  const roleArrayFields = [
    user.roles,
    user.authorities,
    user.realmRoles,
    user.realm_roles,
    user.realm_access?.roles,
  ];

  roleArrayFields.forEach((roleArray) => {
    if (Array.isArray(roleArray)) {
      roleArray.forEach((role) => {
        const normalized = normalizeRole(getRoleValue(role));

        if (normalized) {
          roles.add(normalized);
        }
      });
    }
  });

  if (typeof user.scope === 'string') {
    user.scope
      .split(/[\s,]+/)
      .map((r) => normalizeRole(r))
      .filter(Boolean)
      .forEach((r) => roles.add(r));
  }

  return roles;
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

const getResponseData = (response) => {
  return response?.data?.data ?? response?.data ?? null;
};

const formatDateTime = (isoString) => {
  if (!isoString) return 'N/A';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString('vi-VN');
};

const RulePage = ({
  currentUser,
  canManageDevices: canManageDevicesProp,
  canManageRules: canManageRulesProp,
}) => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const passedUser =
    currentUser || location.state?.currentUser || null;

  const [profileUser, setProfileUser] = useState(
    () => passedUser || readStoredUser()
  );
  const [profileResolved, setProfileResolved] = useState(
    Boolean(passedUser)
  );
  const [profileError, setProfileError] = useState('');

  useEffect(() => {
    if (passedUser) {
      setProfileUser(passedUser);
      setProfileResolved(true);
      setProfileError('');
    }
  }, [passedUser]);

  useEffect(() => {
    if (passedUser) {
      return;
    }

    let cancelled = false;

    const fetchCurrentUser = async () => {
      const accessToken = localStorage.getItem('access_token');
      if (!accessToken) {
        if (!cancelled) {
          setProfileUser(null);
          setProfileResolved(true);
        }
        return;
      }

      try {
        setProfileResolved(false);
        setProfileError('');

        const response = await axiosClient.get('/profile/me');
        const data = getResponseData(response);

        if (!cancelled) {
          setProfileUser(data);
          setProfileResolved(true);
        }
      } catch (err) {
        if (!cancelled) {
          const fallbackUser = readStoredUser();
          setProfileUser(fallbackUser);
          if (!fallbackUser) {
            setProfileError(
              err.response?.data?.message ||
                'Không thể xác định quyền người dùng.'
            );
          }
          setProfileResolved(true);
        }
      }
    };

    fetchCurrentUser();

    return () => {
      cancelled = true;
    };
  }, [passedUser]);

  const effectiveUser = passedUser || profileUser;

  const userRoles = useMemo(() => {
    return getUserRoles(effectiveUser);
  }, [effectiveUser]);

  const canViewRule = useMemo(() => {
    return Array.from(userRoles).some((role) =>
      ALLOWED_VIEW_ROLES.has(role)
    );
  }, [userRoles]);

  const canManageRules = useMemo(() => {
    if (typeof canManageRulesProp === 'boolean') {
      return canManageRulesProp;
    }
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    return Array.from(userRoles).some((role) =>
      RULE_MANAGE_ROLES.has(role)
    );
  }, [canManageRulesProp, canManageDevicesProp, userRoles]);

  const [rule, setRule] = useState(null);
  const [allowedMetrics, setAllowedMetrics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  const [editForm, setEditForm] = useState({
    metricType: '',
    warningLimit: '',
    criticalLimit: '',
    consecutiveOccurrences: '3',
    isEnabled: true,
  });

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  const fetchAllowedMetrics = useCallback(async (targetDeviceId) => {
    if (!targetDeviceId) return;

    try {
      const res = await axiosClient.get(
        `/threshold-rules/device/${targetDeviceId}/allowed-metrics`
      );
      const list = getResponseData(res);
      setAllowedMetrics(Array.isArray(list) ? list : []);
    } catch {
    }
  }, []);

  const fetchRule = useCallback(async () => {
    if (!profileResolved) {
      return;
    }

    if (!canViewRule) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setErrorMsg('');

      const response = await axiosClient.get(`/threshold-rules/${id}`);
      const data = getResponseData(response);

      setRule(data);
      if (data) {
        setEditForm({
          metricType: data.metricType || '',
          warningLimit:
            data.warningLimit !== null && data.warningLimit !== undefined
              ? String(data.warningLimit)
              : '',
          criticalLimit:
            data.criticalLimit !== null && data.criticalLimit !== undefined
              ? String(data.criticalLimit)
              : '',
          consecutiveOccurrences: String(data.consecutiveOccurrences ?? 3),
          isEnabled: data.isEnabled ?? true,
        });

        if (data.deviceId) {
          await fetchAllowedMetrics(data.deviceId);
        }
      }
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Không thể tải thông tin luật cảnh báo!'
      );
    } finally {
      setLoading(false);
    }
  }, [id, canViewRule, profileResolved, fetchAllowedMetrics]);

  useEffect(() => {
    if (id && profileResolved) {
      fetchRule();
    }
  }, [id, profileResolved, fetchRule]);

  const currentMetricMeta = useMemo(
    () =>
      allowedMetrics.find((item) => item.metricType === rule?.metricType) ||
      null,
    [allowedMetrics, rule?.metricType]
  );

  const editMetricMeta = useMemo(
    () =>
      allowedMetrics.find((item) => item.metricType === editForm.metricType) ||
      null,
    [allowedMetrics, editForm.metricType]
  );

  const handleBack = () => {
    if (rule?.deviceId) {
      navigate(`/devices/${rule.deviceId}`, {
        state: { activeTab: 'rules' },
      });
    } else {
      navigate(-1);
    }
  };

  const handleEditOpen = () => {
    if (!canManageRules) return;

    setErrorMsg('');
    setSuccessMsg('');
    setEditForm({
      metricType: rule?.metricType || allowedMetrics[0]?.metricType || '',
      warningLimit:
        rule?.warningLimit !== null && rule?.warningLimit !== undefined
          ? String(rule.warningLimit)
          : '',
      criticalLimit:
        rule?.criticalLimit !== null && rule?.criticalLimit !== undefined
          ? String(rule.criticalLimit)
          : '',
      consecutiveOccurrences: String(rule?.consecutiveOccurrences ?? 3),
      isEnabled: rule?.isEnabled ?? true,
    });
    setIsEditModalOpen(true);
  };

  const validateForm = () => {
    if (!editForm.metricType) {
      return 'Vui lòng chọn Metric Type!';
    }

    const warningLimit = Number(editForm.warningLimit);
    const criticalLimit = Number(editForm.criticalLimit);

    if (editForm.warningLimit === '' || !Number.isFinite(warningLimit)) {
      return 'Vui lòng nhập ngưỡng Warning hợp lệ!';
    }

    if (editForm.criticalLimit === '' || !Number.isFinite(criticalLimit)) {
      return 'Vui lòng nhập ngưỡng Critical hợp lệ!';
    }

    if (editMetricMeta) {
      const { minValue, maxValue, conditionDirection, unit } = editMetricMeta;
      const unitSuffix = unit ? ` (${unit})` : '';

      if (minValue !== null && minValue !== undefined) {
        if (warningLimit < minValue || criticalLimit < minValue) {
          return `Giá trị ngưỡng phải lớn hơn hoặc bằng ${minValue}${unitSuffix}!`;
        }
      }

      if (maxValue !== null && maxValue !== undefined) {
        if (warningLimit > maxValue || criticalLimit > maxValue) {
          return `Giá trị ngưỡng phải nhỏ hơn hoặc bằng ${maxValue}${unitSuffix}!`;
        }
      }

      if (
        conditionDirection === 'GREATER_THAN' &&
        criticalLimit <= warningLimit
      ) {
        return 'Với điều kiện vượt ngưỡng (GREATER_THAN), ngưỡng Critical phải lớn hơn ngưỡng Warning!';
      }

      if (
        conditionDirection === 'LESS_THAN' &&
        criticalLimit >= warningLimit
      ) {
        return 'Với điều kiện giảm dưới ngưỡng (LESS_THAN), ngưỡng Critical phải nhỏ hơn ngưỡng Warning!';
      }
    }

    const occurrences = Number(editForm.consecutiveOccurrences);
    if (!Number.isInteger(occurrences) || occurrences < 1) {
      return 'Số lần vi phạm liên tiếp phải là số nguyên lớn hơn hoặc bằng 1!';
    }

    return '';
  };

  const handleUpdateRule = async (e) => {
    e.preventDefault();
    if (!canManageRules) return;

    setErrorMsg('');
    setSuccessMsg('');

    const validationError = validateForm();
    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    const payload = {
      deviceId: rule.deviceId,
      metricType: editForm.metricType,
      warningLimit: Number(editForm.warningLimit),
      criticalLimit: Number(editForm.criticalLimit),
      consecutiveOccurrences: Number(editForm.consecutiveOccurrences),
      isEnabled: editForm.isEnabled,
    };

    try {
      setSaving(true);
      await axiosClient.put(`/threshold-rules/${id}`, payload);
      await fetchRule();
      setSuccessMsg('Cập nhật luật cảnh báo thành công!');
      setIsEditModalOpen(false);
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Cập nhật luật cảnh báo thất bại!'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!canManageRules) return;

    if (
      !window.confirm(
        `Bạn có chắc chắn muốn xóa luật cảnh báo [${rule?.metricType || id}]?`
      )
    ) {
      return;
    }

    try {
      setDeleting(true);
      setErrorMsg('');

      await axiosClient.delete(`/threshold-rules/${id}`);

      alert('Xóa luật cảnh báo thành công!');
      handleBack();
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Xóa luật cảnh báo thất bại!'
      );
      setDeleting(false);
    }
  };

  if (!profileResolved || loading) {
    return (
      <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span
            className="spinner"
            style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }}
          />
          {!profileResolved
            ? 'Đang xác định quyền người dùng...'
            : 'Đang tải luật cảnh báo...'}
        </div>
      </div>
    );
  }

  if (!canViewRule) {
    return (
      <div className={`rule-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <header className="rule-header">
          <div className="rule-header-left">
            <button
              type="button"
              className="rule-back-button"
              onClick={handleBack}
              title="Quay lại"
            >
              ←
            </button>
            <h2>CHI TIẾT LUẬT CẢNH BÁO</h2>
          </div>
        </header>
        <main className="rule-main">
          <div className="rule-card">
            <div className="permission-denied-box">
              <div className="permission-denied-icon">!</div>
              <h2 className="permission-denied-title">Không có quyền truy cập</h2>
              <p className="permission-message">
                {profileError ||
                  'Bạn không có quyền xem luật cảnh báo của thiết bị này.'}
              </p>
            </div>
            <div className="rule-actions">
              <button
                type="button"
                className="rule-cancel-btn"
                onClick={handleBack}
              >
                Quay lại
              </button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className={`rule-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="rule-header">
        <div className="rule-header-left">
          <button
            type="button"
            className="rule-back-button"
            onClick={handleBack}
            title="Quay lại"
          >
            ←
          </button>
          <h2>CHI TIẾT LUẬT CẢNH BÁO</h2>
        </div>
      </header>

      <main className="rule-main">
        {!rule ? (
          <div className="rule-card">
            <div className="rule-alert error">
              <span>{errorMsg || 'Không tìm thấy luật cảnh báo.'}</span>
            </div>
            <div className="rule-actions">
              <button
                type="button"
                className="rule-cancel-btn"
                onClick={handleBack}
              >
                Quay lại
              </button>
            </div>
          </div>
        ) : (
          <div className="rule-card">
            <div className="rule-card-header">
              <div className="rule-avatar">
                {rule.metricType ? rule.metricType.charAt(0).toUpperCase() : 'R'}
              </div>

              <div className="rule-header-info">
                <h3>{rule.metricType || `Threshold Rule #${id}`}</h3>
                <p>
                  ID: #{rule.id ?? id} ·{' '}
                  {rule.deviceName || `Device ID: #${rule.deviceId ?? 'N/A'}`}
                </p>
              </div>
            </div>

            {errorMsg && !isEditModalOpen && (
              <div className="rule-alert error">
                <span>{errorMsg}</span>
              </div>
            )}

            {successMsg && !isEditModalOpen && (
              <div className="rule-alert success">
                <span>{successMsg}</span>
              </div>
            )}

            <div className="rule-grid">
              <div className="rule-field">
                <label>Metric Type</label>
                <input
                  type="text"
                  className="rule-input-box"
                  value={
                    currentMetricMeta?.unit
                      ? `${rule.metricType} (${currentMetricMeta.unit})`
                      : rule.metricType || ''
                  }
                  readOnly
                />
              </div>

              <div className="rule-field">
                <label>Số lần vi phạm liên tiếp</label>
                <input
                  type="text"
                  className="rule-input-box"
                  value={rule.consecutiveOccurrences ?? 1}
                  readOnly
                />
              </div>

              <div className="rule-field">
                <label>Ngưỡng Warning</label>
                <input
                  type="text"
                  className="rule-input-box"
                  value={
                    rule.warningLimit !== null && rule.warningLimit !== undefined
                      ? `${rule.warningLimit}${
                          currentMetricMeta?.unit ? ` ${currentMetricMeta.unit}` : ''
                        }`
                      : 'Không áp dụng'
                  }
                  readOnly
                />
              </div>

              <div className="rule-field">
                <label>Ngưỡng Critical</label>
                <input
                  type="text"
                  className="rule-input-box"
                  value={
                    rule.criticalLimit !== null && rule.criticalLimit !== undefined
                      ? `${rule.criticalLimit}${
                          currentMetricMeta?.unit ? ` ${currentMetricMeta.unit}` : ''
                        }`
                      : 'Không áp dụng'
                  }
                  readOnly
                />
              </div>

              <div className="rule-field">
                <label>Ngày tạo</label>
                <input
                  type="text"
                  className="rule-input-box"
                  value={formatDateTime(rule.createdAt)}
                  readOnly
                />
              </div>

              <div className="rule-field">
                <label>Cập nhật lần cuối</label>
                <input
                  type="text"
                  className="rule-input-box"
                  value={formatDateTime(rule.updatedAt)}
                  readOnly
                />
              </div>

              <div className="rule-field full-width">
                <label>Trạng thái luật cảnh báo</label>
                <div className={`rule-status-badge ${rule.isEnabled ? 'active' : 'inactive'}`}>
                  {rule.isEnabled ? '● Đang hoạt động (Enabled)' : '● Đã tắt (Disabled)'}
                </div>
              </div>
            </div>

            {canManageRules && (
              <div className="rule-actions">
                <button
                  type="button"
                  className="btn-delete-rule"
                  onClick={handleDelete}
                  disabled={deleting || saving}
                >
                  {deleting ? 'Đang xóa...' : 'Xóa Luật'}
                </button>

                <button
                  type="button"
                  className="btn-edit-rule"
                  onClick={handleEditOpen}
                  disabled={deleting || saving}
                >
                  Chỉnh Sửa Thông Tin
                </button>
              </div>
            )}
          </div>
        )}
      </main>

      {canManageRules && isEditModalOpen && (
        <div className="rule-modal-overlay">
          <div className="rule-modal-card">
            <div className="rule-modal-header">
              <h3>CHỈNH SỬA LUẬT CẢNH BÁO</h3>
              <button
                type="button"
                className="rule-modal-close-btn"
                onClick={() => setIsEditModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="rule-alert error">{errorMsg}</div>}

            <form onSubmit={handleUpdateRule}>
              <div className="rule-modal-row">
                <div className="rule-form-group">
                  <label>Metric Type *</label>
                  <select
                    className="rule-select-box"
                    value={editForm.metricType}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        metricType: e.target.value,
                      }))
                    }
                    disabled={saving || allowedMetrics.length === 0}
                  >
                    {allowedMetrics.length > 0 ? (
                      allowedMetrics.map((metric) => (
                        <option key={metric.metricType} value={metric.metricType}>
                          {metric.metricType} {metric.unit ? `(${metric.unit})` : ''}
                        </option>
                      ))
                    ) : (
                      <option value={editForm.metricType}>
                        {editForm.metricType}
                      </option>
                    )}
                  </select>
                </div>

                <div className="rule-form-group">
                  <label>Số lần vi phạm liên tiếp *</label>
                  <input
                    type="number"
                    className="rule-input-box"
                    min="1"
                    step="1"
                    value={editForm.consecutiveOccurrences}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        consecutiveOccurrences: e.target.value,
                      }))
                    }
                    required
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="rule-modal-row">
                <div className="rule-form-group">
                  <label>
                    Ngưỡng Warning {editMetricMeta?.unit ? `(${editMetricMeta.unit})` : ''} *
                  </label>
                  <input
                    type="number"
                    className="rule-input-box"
                    min={editMetricMeta?.minValue ?? undefined}
                    max={editMetricMeta?.maxValue ?? undefined}
                    step="any"
                    value={editForm.warningLimit}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        warningLimit: e.target.value,
                      }))
                    }
                    required
                    disabled={saving}
                  />
                </div>

                <div className="rule-form-group">
                  <label>
                    Ngưỡng Critical {editMetricMeta?.unit ? `(${editMetricMeta.unit})` : ''} *
                  </label>
                  <input
                    type="number"
                    className="rule-input-box"
                    min={editMetricMeta?.minValue ?? undefined}
                    max={editMetricMeta?.maxValue ?? undefined}
                    step="any"
                    value={editForm.criticalLimit}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        criticalLimit: e.target.value,
                      }))
                    }
                    required
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="rule-checkbox-group">
                <input
                  type="checkbox"
                  id="ruleEnabledCheck"
                  checked={editForm.isEnabled}
                  onChange={(e) =>
                    setEditForm((prev) => ({
                      ...prev,
                      isEnabled: e.target.checked,
                    }))
                  }
                  disabled={saving}
                />
                <label htmlFor="ruleEnabledCheck">
                  Kích hoạt luật cảnh báo (Enabled)
                </label>
              </div>

              <div className="rule-modal-actions">
                <button
                  type="button"
                  className="rule-cancel-btn"
                  onClick={() => setIsEditModalOpen(false)}
                  disabled={saving}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="rule-save-btn"
                  disabled={saving}
                >
                  {saving ? 'ĐANG LƯU...' : 'LƯU THAY ĐỔI'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default RulePage;