import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './RulePage.css';

const getResponseData = (response) => {
  return response?.data?.data ?? response?.data ?? null;
};

const formatDateTime = (isoString) => {
  if (!isoString) return 'N/A';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString('vi-VN');
};

const RulePage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

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
    } catch (err) {
      console.error('Lỗi tải danh sách allowed metrics:', err);
    }
  }, []);

  const fetchRule = useCallback(async () => {
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
  }, [id, fetchAllowedMetrics]);

  useEffect(() => {
    if (id) {
      fetchRule();
    }
  }, [id, fetchRule]);

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

  if (loading) {
    return (
      <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span
            className="spinner"
            style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }}
          />
          Đang tải luật cảnh báo...
        </div>
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
          </div>
        )}
      </main>

      {isEditModalOpen && (
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