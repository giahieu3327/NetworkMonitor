import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './OidConfigPage.css';

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

const getResponseData = (response) => {
  return response?.data?.data ?? response?.data ?? null;
};

const getScopeLabel = (scope) => {
  return (
    SCOPE_OPTIONS.find((opt) => opt.value === scope)?.label ||
    scope ||
    'DEVICE - Thông tin thiết bị'
  );
};

const OidConfigPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [oidConfig, setOidConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  const [editForm, setEditForm] = useState({
    metricScope: 'DEVICE',
    metricType: '',
    oidPattern: '',
    oidKind: 'SINGLE',
    dataType: 'INTEGER',
    multiplier: '1',
    interfaceIndex: '',
    isActive: true,
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

  const fetchOidConfig = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg('');

      const response = await axiosClient.get(`/oid-configs/${id}`);
      const data = getResponseData(response);

      setOidConfig(data);
      if (data) {
        setEditForm({
          metricScope: data.metricScope || 'DEVICE',
          metricType: data.metricType || '',
          oidPattern: data.oidPattern || '',
          oidKind: data.oidKind || 'SINGLE',
          dataType: data.dataType || 'INTEGER',
          multiplier: String(data.multiplier ?? 1),
          interfaceIndex:
            data.interfaceIndex !== null && data.interfaceIndex !== undefined
              ? String(data.interfaceIndex)
              : '',
          isActive: data.isActive ?? true,
        });
      }
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Không thể tải thông tin cấu hình OID!'
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) {
      fetchOidConfig();
    }
  }, [id, fetchOidConfig]);

  const handleBack = () => {
    if (oidConfig?.deviceId) {
      navigate(`/devices/${oidConfig.deviceId}`, {
        state: { activeTab: 'oidconfig' },
      });
    } else {
      navigate(-1);
    }
  };

  const handleEditOpen = () => {
    setErrorMsg('');
    setSuccessMsg('');
    setEditForm({
      metricScope: oidConfig?.metricScope || 'DEVICE',
      metricType: oidConfig?.metricType || '',
      oidPattern: oidConfig?.oidPattern || '',
      oidKind: oidConfig?.oidKind || 'SINGLE',
      dataType: oidConfig?.dataType || 'INTEGER',
      multiplier: String(oidConfig?.multiplier ?? 1),
      interfaceIndex:
        oidConfig?.interfaceIndex !== null &&
        oidConfig?.interfaceIndex !== undefined
          ? String(oidConfig.interfaceIndex)
          : '',
      isActive: oidConfig?.isActive ?? true,
    });
    setIsEditModalOpen(true);
  };

  const validateForm = () => {
    if (!editForm.metricScope) {
      return 'Vui lòng chọn Metric Scope!';
    }

    if (!editForm.metricType.trim()) {
      return 'Vui lòng nhập tên metric!';
    }

    if (!editForm.oidKind) {
      return 'Vui lòng chọn phương thức truy vấn!';
    }

    if (!editForm.oidPattern.trim()) {
      return 'Vui lòng nhập OID!';
    }

    if (!/^\d+(\.\d+)*$/.test(editForm.oidPattern.trim())) {
      return 'OID chỉ được chứa chữ số và dấu chấm, không được có dấu chấm ở đầu hoặc cuối!';
    }

    if (!DATA_TYPE_OPTIONS.includes(editForm.dataType)) {
      return 'Kiểu dữ liệu không hợp lệ!';
    }

    const multiplier = Number(editForm.multiplier);
    if (!Number.isFinite(multiplier) || multiplier <= 0) {
      return 'Hệ số nhân phải là số lớn hơn 0!';
    }

    if (editForm.interfaceIndex !== '' && editForm.interfaceIndex !== null) {
      const interfaceIndex = Number(editForm.interfaceIndex);
      if (!Number.isInteger(interfaceIndex) || interfaceIndex <= 0) {
        return 'Interface Index phải là số nguyên lớn hơn 0!';
      }
    }

    if (
      editForm.metricScope === 'INTERFACE' &&
      editForm.oidKind === 'SINGLE' &&
      !editForm.interfaceIndex
    ) {
      return 'Vui lòng nhập Interface Index cho OID INTERFACE dạng SINGLE!';
    }

    return '';
  };

  const handleUpdateOidConfig = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    const validationError = validateForm();
    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    const payload = {
      deviceId: oidConfig.deviceId,
      metricScope: editForm.metricScope,
      metricType: editForm.metricType.trim(),
      oidPattern: editForm.oidPattern.trim(),
      oidKind: editForm.oidKind,
      dataType: editForm.dataType,
      multiplier: Number(editForm.multiplier),
      interfaceIndex:
        editForm.interfaceIndex !== '' && editForm.interfaceIndex !== null
          ? Number(editForm.interfaceIndex)
          : null,
      isActive: editForm.isActive,
    };

    try {
      setSaving(true);
      await axiosClient.put(`/oid-configs/${id}`, payload);
      await fetchOidConfig();
      setSuccessMsg('Cập nhật cấu hình OID thành công!');
      setIsEditModalOpen(false);
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Cập nhật cấu hình OID thất bại!'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (
      !window.confirm(
        `Bạn có chắc chắn muốn xóa cấu hình OID [${oidConfig?.metricType || id}]?`
      )
    ) {
      return;
    }

    try {
      setDeleting(true);
      setErrorMsg('');

      await axiosClient.delete(`/oid-configs/${id}`);

      alert('Xóa cấu hình OID thành công!');
      handleBack();
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Xóa cấu hình OID thất bại!'
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
          Đang tải cấu hình OID...
        </div>
      </div>
    );
  }

  return (
    <div className={`oid-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="oid-header">
        <div className="oid-header-left">
          <button
            type="button"
            className="oid-back-button"
            onClick={handleBack}
            title="Quay lại"
          >
            ←
          </button>
          <h2>CHI TIẾT CẤU HÌNH OID</h2>
        </div>
      </header>

      <main className="oid-main">
        {!oidConfig ? (
          <div className="oid-card">
            <div className="oid-alert error">
              <span>{errorMsg || 'Không tìm thấy cấu hình OID.'}</span>
            </div>
            <div className="oid-actions">
              <button
                type="button"
                className="oid-cancel-btn"
                onClick={handleBack}
              >
                Quay lại
              </button>
            </div>
          </div>
        ) : (
          <div className="oid-card">
            <div className="oid-card-header">
              <div className="oid-avatar">
                {oidConfig.metricType ? oidConfig.metricType.charAt(0).toUpperCase() : 'O'}
              </div>

              <div className="oid-header-info">
                <h3>{oidConfig.metricType || `OID Config #${id}`}</h3>
                <p>
                  ID: #{oidConfig.id ?? id} ·{' '}
                  {oidConfig.deviceName || `Device ID: #${oidConfig.deviceId ?? 'N/A'}`}
                </p>
              </div>
            </div>

            {errorMsg && !isEditModalOpen && (
              <div className="oid-alert error">
                <span>{errorMsg}</span>
              </div>
            )}

            {successMsg && !isEditModalOpen && (
              <div className="oid-alert success">
                <span>{successMsg}</span>
              </div>
            )}

            <div className="oid-grid">
              <div className="oid-field">
                <label>Phạm vi metric</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={getScopeLabel(oidConfig.metricScope)}
                  readOnly
                />
              </div>

              <div className="oid-field">
                <label>Tên metric</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={oidConfig.metricType || ''}
                  readOnly
                />
              </div>

              <div className="oid-field full-width">
                <label>OID</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={oidConfig.oidPattern || ''}
                  readOnly
                />
              </div>

              <div className="oid-field">
                <label>Phương thức truy vấn</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={
                    oidConfig.oidKind === 'TABLE_COLUMN'
                      ? 'TABLE_COLUMN - SNMP WALK'
                      : 'SINGLE - SNMP GET'
                  }
                  readOnly
                />
              </div>

              <div className="oid-field">
                <label>Kiểu dữ liệu</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={oidConfig.dataType || 'INTEGER'}
                  readOnly
                />
              </div>

              <div className="oid-field">
                <label>Hệ số nhân</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={oidConfig.multiplier ?? 1}
                  readOnly
                />
              </div>

              <div className="oid-field">
                <label>Interface Index</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={
                    oidConfig.interfaceIndex !== null &&
                    oidConfig.interfaceIndex !== undefined
                      ? oidConfig.interfaceIndex
                      : 'Không áp dụng'
                  }
                  readOnly
                />
              </div>

              <div className="oid-field full-width">
                <label>Trạng thái cấu hình</label>
                <div className={`oid-status-badge ${oidConfig.isActive ? 'active' : 'inactive'}`}>
                  {oidConfig.isActive ? '● Đang hoạt động (Active)' : '● Đã tắt (Inactive)'}
                </div>
              </div>
            </div>

            <div className="oid-actions">
              <button
                type="button"
                className="btn-delete-oid"
                onClick={handleDelete}
                disabled={deleting || saving}
              >
                {deleting ? 'Đang xóa...' : 'Xóa Cấu Hình'}
              </button>

              <button
                type="button"
                className="btn-edit-oid"
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
        <div className="oid-modal-overlay">
          <div className="oid-modal-card">
            <div className="oid-modal-header">
              <h3>CHỈNH SỬA CẤU HÌNH OID</h3>
              <button
                type="button"
                className="oid-modal-close-btn"
                onClick={() => setIsEditModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="oid-alert error">{errorMsg}</div>}

            <form onSubmit={handleUpdateOidConfig}>
              <div className="oid-modal-row">
                <div className="oid-form-group">
                  <label>Phạm vi metric *</label>
                  <select
                    className="oid-select-box"
                    value={editForm.metricScope}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        metricScope: e.target.value,
                      }))
                    }
                    disabled={saving}
                  >
                    {SCOPE_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="oid-form-group">
                  <label>Tên metric *</label>
                  <input
                    type="text"
                    className="oid-input-box"
                    value={editForm.metricType}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        metricType: e.target.value,
                      }))
                    }
                    maxLength={150}
                    placeholder="Nhập tên metric"
                    required
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="oid-form-group">
                <label>OID *</label>
                <input
                  type="text"
                  className="oid-input-box"
                  value={editForm.oidPattern}
                  onChange={(e) =>
                    setEditForm((prev) => ({
                      ...prev,
                      oidPattern: e.target.value,
                    }))
                  }
                  placeholder="Ví dụ: 1.3.6.1.2.1.1.3.0"
                  required
                  disabled={saving}
                />
              </div>

              <div className="oid-modal-row">
                <div className="oid-form-group">
                  <label>Phương thức truy vấn *</label>
                  <select
                    className="oid-select-box"
                    value={editForm.oidKind}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        oidKind: e.target.value,
                      }))
                    }
                    disabled={saving}
                  >
                    <option value="SINGLE">SINGLE - SNMP GET</option>
                    <option value="TABLE_COLUMN">TABLE_COLUMN - SNMP WALK</option>
                  </select>
                </div>

                <div className="oid-form-group">
                  <label>Kiểu dữ liệu *</label>
                  <select
                    className="oid-select-box"
                    value={editForm.dataType}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        dataType: e.target.value,
                      }))
                    }
                    disabled={saving}
                  >
                    {DATA_TYPE_OPTIONS.map((type) => (
                      <option key={type} value={type}>
                        {type}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="oid-modal-row">
                <div className="oid-form-group">
                  <label>Hệ số nhân *</label>
                  <input
                    type="number"
                    className="oid-input-box"
                    min="0.000001"
                    step="any"
                    value={editForm.multiplier}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        multiplier: e.target.value,
                      }))
                    }
                    required
                    disabled={saving}
                  />
                </div>

                <div className="oid-form-group">
                  <label>
                    Interface Index{' '}
                    {editForm.metricScope === 'INTERFACE' &&
                    editForm.oidKind === 'SINGLE'
                      ? '*'
                      : ''}
                  </label>
                  <input
                    type="number"
                    className="oid-input-box"
                    min="1"
                    step="1"
                    value={editForm.interfaceIndex}
                    onChange={(e) =>
                      setEditForm((prev) => ({
                        ...prev,
                        interfaceIndex: e.target.value,
                      }))
                    }
                    placeholder={
                      editForm.metricScope === 'INTERFACE' &&
                      editForm.oidKind === 'SINGLE'
                        ? 'Ví dụ: 2'
                        : 'Để trống nếu không áp dụng'
                    }
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="oid-checkbox-group">
                <input
                  type="checkbox"
                  id="oidActiveCheck"
                  checked={editForm.isActive}
                  onChange={(e) =>
                    setEditForm((prev) => ({
                      ...prev,
                      isActive: e.target.checked,
                    }))
                  }
                  disabled={saving}
                />
                <label htmlFor="oidActiveCheck">
                  Kích hoạt cấu hình OID (Active)
                </label>
              </div>

              <div className="oid-modal-actions">
                <button
                  type="button"
                  className="oid-cancel-btn"
                  onClick={() => setIsEditModalOpen(false)}
                  disabled={saving}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="oid-save-btn"
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

export default OidConfigPage;