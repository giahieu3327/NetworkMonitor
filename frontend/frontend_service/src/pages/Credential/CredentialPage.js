import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './CredentialPage.css';

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

const getResponseData = (response) => {
  return response?.data?.data ?? response?.data ?? {};
};

const CredentialPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [credential, setCredential] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [showCommunity, setShowCommunity] = useState(false);
  const [showEditCommunity, setShowEditCommunity] = useState(false);

  const [editForm, setEditForm] = useState({
    credentialName: '',
    credentialType: 'SNMP',
    protocolType: 'SNMPV2C',
    communityString: '',
    port: '161',
    isActive: true
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

  const fetchCredential = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg('');

      const response = await axiosClient.get(`/device-credentials/${id}`);
      const data = getResponseData(response);

      setCredential(data);
      setEditForm({
        credentialName: data.credentialName ?? '',
        credentialType: data.credentialType ?? 'SNMP',
        protocolType: data.protocolType ?? 'SNMPV2C',
        communityString: data.communityString ?? '',
        port: String(data.port ?? 161),
        isActive: data.isActive ?? true
      });
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Không thể tải thông tin xác thực!'
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) {
      fetchCredential();
    }
  }, [id, fetchCredential]);

  const handleBack = () => {
    if (credential?.deviceId) {
      navigate(`/devices/${credential.deviceId}`, {
        state: { activeTab: 'credentials' }
      });
    } else {
      navigate(-1);
    }
  };

  const handleEditOpen = () => {
    setErrorMsg('');
    setSuccessMsg('');
    setShowEditCommunity(false);
    setEditForm({
      credentialName: credential?.credentialName ?? '',
      credentialType: credential?.credentialType ?? 'SNMP',
      protocolType: credential?.protocolType ?? 'SNMPV2C',
      communityString: credential?.communityString ?? '',
      port: String(credential?.port ?? 161),
      isActive: credential?.isActive ?? true
    });
    setIsEditModalOpen(true);
  };

  const handleUpdateCredential = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    const port = Number(editForm.port);

    if (!editForm.credentialName.trim()) {
      setErrorMsg('Vui lòng nhập tên thông tin xác thực!');
      return;
    }

    if (!editForm.communityString.trim()) {
      setErrorMsg('Vui lòng nhập chuỗi Community!');
      return;
    }

    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      setErrorMsg('Cổng phải là số nguyên từ 1 đến 65535!');
      return;
    }

    const payload = {
      deviceId: credential.deviceId,
      credentialName: editForm.credentialName.trim(),
      credentialType: editForm.credentialType,
      protocolType: editForm.protocolType,
      communityString: editForm.communityString.trim(),
      port,
      isActive: editForm.isActive
    };

    try {
      setSaving(true);
      await axiosClient.put(`/device-credentials/${id}`, payload);
      await fetchCredential();
      setSuccessMsg('Cập nhật thông tin xác thực thành công!');
      setIsEditModalOpen(false);
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Cập nhật thông tin xác thực thất bại!'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (
      !window.confirm(
        `Bạn có chắc chắn muốn xóa thông tin xác thực [${credential?.credentialName || id}]?`
      )
    ) {
      return;
    }

    try {
      setDeleting(true);
      setErrorMsg('');

      await axiosClient.delete(`/device-credentials/${id}`);

      alert('Xóa thông tin xác thực thành công!');
      handleBack();
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Xóa thông tin xác thực thất bại!'
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
          Đang tải thông tin xác thực...
        </div>
      </div>
    );
  }

  return (
    <div className={`cred-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="cred-header">
        <div className="cred-header-left">
          <button
            type="button"
            className="cred-back-button"
            onClick={handleBack}
            title="Quay lại"
          >
            ←
          </button>
          <h2>CHI TIẾT THÔNG TIN XÁC THỰC</h2>
        </div>
      </header>

      <main className="cred-main">
        {!credential ? (
          <div className="cred-card">
            <div className="cred-alert error">
              <span>{errorMsg || 'Không tìm thấy thông tin xác thực.'}</span>
            </div>
            <div className="cred-actions">
              <button
                type="button"
                className="cred-cancel-btn"
                onClick={handleBack}
              >
                Quay lại
              </button>
            </div>
          </div>
        ) : (
          <div className="cred-card">
            <div className="cred-card-header">
              <div className="cred-avatar">
                {credential.credentialType ? credential.credentialType.charAt(0).toUpperCase() : 'C'}
              </div>

              <div className="cred-header-info">
                <h3>{credential.credentialName || `Credential #${id}`}</h3>
                <p>
                  ID: #{credential.id ?? id} · Device ID: #{credential.deviceId ?? 'N/A'}
                </p>
              </div>
            </div>

            {errorMsg && !isEditModalOpen && (
              <div className="cred-alert error">
                <span>{errorMsg}</span>
              </div>
            )}

            {successMsg && !isEditModalOpen && (
              <div className="cred-alert success">
                <span>{successMsg}</span>
              </div>
            )}

            <div className="cred-grid">
              <div className="cred-field">
                <label>Tên thông tin xác thực</label>
                <input
                  type="text"
                  className="cred-input-box"
                  value={credential.credentialName || ''}
                  readOnly
                />
              </div>

              <div className="cred-field">
                <label>Loại thông tin xác thực</label>
                <input
                  type="text"
                  className="cred-input-box"
                  value={credential.credentialType || 'SNMP'}
                  readOnly
                />
              </div>

              <div className="cred-field">
                <label>Phiên bản giao thức</label>
                <input
                  type="text"
                  className="cred-input-box"
                  value={credential.protocolType || 'SNMPV2C'}
                  readOnly
                />
              </div>

              <div className="cred-field">
                <label>Cổng kết nối</label>
                <input
                  type="text"
                  className="cred-input-box"
                  value={credential.port ?? 161}
                  readOnly
                />
              </div>

              <div className="cred-field">
                <label>Chuỗi Community</label>
                <div className="cred-secret-wrapper">
                  <input
                    type={showCommunity ? 'text' : 'password'}
                    className="cred-input-box cred-secret-input"
                    value={credential.communityString || ''}
                    readOnly
                  />
                  <button
                    type="button"
                    className="cred-eye-btn"
                    onClick={() => setShowCommunity((prev) => !prev)}
                    title={showCommunity ? 'Ẩn' : 'Hiển thị'}
                  >
                    <EyeIcon show={showCommunity} />
                  </button>
                </div>
              </div>

              <div className="cred-field">
                <label>Trạng thái cấu hình</label>
                <div className={`cred-status-badge ${credential.isActive ? 'active' : 'inactive'}`}>
                  {credential.isActive ? '● Đang hoạt động (Active)' : '● Đã tắt (Inactive)'}
                </div>
              </div>
            </div>

            <div className="cred-actions">
              <button
                type="button"
                className="btn-delete-cred"
                onClick={handleDelete}
                disabled={deleting || saving}
              >
                {deleting ? 'Đang xóa...' : 'Xóa Cấu Hình'}
              </button>

              <button
                type="button"
                className="btn-edit-cred"
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
        <div className="cred-modal-overlay">
          <div className="cred-modal-card">
            <div className="cred-modal-header">
              <h3>CHỈNH SỬA THÔNG TIN XÁC THỰC</h3>
              <button
                type="button"
                className="cred-modal-close-btn"
                onClick={() => setIsEditModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="cred-alert error">{errorMsg}</div>}

            <form onSubmit={handleUpdateCredential}>
              <div className="cred-form-group">
                <label>Tên thông tin xác thực *</label>
                <input
                  type="text"
                  className="cred-input-box"
                  value={editForm.credentialName}
                  onChange={(e) =>
                    setEditForm((prev) => ({ ...prev, credentialName: e.target.value }))
                  }
                  maxLength={100}
                  placeholder="Nhập tên thông tin xác thực"
                  required
                  disabled={saving}
                />
              </div>

              <div className="cred-modal-row">
                <div className="cred-form-group">
                  <label>Loại xác thực</label>
                  <select
                    className="cred-select-box"
                    value={editForm.credentialType}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, credentialType: e.target.value }))
                    }
                    disabled={saving}
                  >
                    <option value="SNMP">SNMP</option>
                  </select>
                </div>

                <div className="cred-form-group">
                  <label>Phiên bản giao thức</label>
                  <select
                    className="cred-select-box"
                    value={editForm.protocolType}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, protocolType: e.target.value }))
                    }
                    disabled={saving}
                  >
                    <option value="SNMPV1">SNMP V1</option>
                    <option value="SNMPV2C">SNMP V2C</option>
                  </select>
                </div>
              </div>

              <div className="cred-form-group">
                <label>Cổng kết nối *</label>
                <input
                  type="number"
                  className="cred-input-box"
                  min="1"
                  max="65535"
                  value={editForm.port}
                  onChange={(e) =>
                    setEditForm((prev) => ({ ...prev, port: e.target.value }))
                  }
                  placeholder="161"
                  required
                  disabled={saving}
                />
              </div>

              <div className="cred-form-group">
                <label>Chuỗi Community *</label>
                <div className="cred-secret-wrapper">
                  <input
                    type={showEditCommunity ? 'text' : 'password'}
                    className="cred-input-box cred-secret-input"
                    value={editForm.communityString}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, communityString: e.target.value }))
                    }
                    autoComplete="new-password"
                    placeholder="Nhập chuỗi Community"
                    required
                    disabled={saving}
                  />
                  <button
                    type="button"
                    className="cred-eye-btn"
                    onClick={() => setShowEditCommunity((prev) => !prev)}
                    tabIndex={-1}
                  >
                    <EyeIcon show={showEditCommunity} />
                  </button>
                </div>
              </div>

              <div className="cred-checkbox-group">
                <input
                  type="checkbox"
                  id="credActiveCheck"
                  checked={editForm.isActive}
                  onChange={(e) =>
                    setEditForm((prev) => ({ ...prev, isActive: e.target.checked }))
                  }
                  disabled={saving}
                />
                <label htmlFor="credActiveCheck">
                  Kích hoạt thông tin xác thực (Active)
                </label>
              </div>

              <div className="cred-modal-actions">
                <button
                  type="button"
                  className="cred-cancel-btn"
                  onClick={() => setIsEditModalOpen(false)}
                  disabled={saving}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="cred-save-btn"
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

export default CredentialPage;