import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './UserPage.css';

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

const UserPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [currentUser, setCurrentUser] = useState(null); // User đang đăng nhập
  const [targetUser, setTargetUser] = useState(null);   // User cần quản lý
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Modals state
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isPwdModalOpen, setIsPwdModalOpen] = useState(false);

  // Form Chỉnh sửa thông tin (Bao gồm username)
  const [editForm, setEditForm] = useState({
    username: '',
    fullName: '',
    email: '',
    phoneNumber: '',
    roleName: '',
    isActive: true
  });

  // Form Đổi Mật Khẩu
  const [pwdForm, setPwdForm] = useState({
    newPassword: '',
    confirmNewPassword: ''
  });

  const [showNewPwd, setShowNewPwd] = useState(false);
  const [showConfirmPwd, setShowConfirmPwd] = useState(false);

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
    const fetchData = async () => {
      setLoading(true);
      setErrorMsg('');

      try {
        const [meRes, targetRes, rolesRes] = await Promise.all([
          axiosClient.get('/profile/me').catch(() => null),
          axiosClient.get(`/users/${id}`),
          axiosClient.get('/users/roles')
        ]);

        if (meRes) {
          setCurrentUser(meRes.data?.data ?? meRes.data);
        }

        const userData = targetRes.data?.data ?? targetRes.data;
        const rolesData = rolesRes.data?.data ?? rolesRes.data ?? [];

        setTargetUser(userData);
        setRoles(rolesData);

        setEditForm({
          username: userData?.username || '',
          fullName: userData?.fullName || '',
          email: userData?.email || '',
          phoneNumber: userData?.phoneNumber || '',
          roleName: userData?.roleName || '',
          isActive: userData?.isActive ?? true
        });
      } catch (err) {
        setErrorMsg(
          err.response?.data?.message || 'Không thể tải thông tin người dùng!'
        );
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchData();
    }
  }, [id]);

  // LOGIC PHÂN QUYỀN
  const isTargetAdmin = targetUser?.username === 'admin';
  const isSelfAccount =
    currentUser &&
    (currentUser.id === targetUser?.id || currentUser.username === targetUser?.username);
  
  const isTargetSuperUser =
    targetUser?.roleName === 'ROLE_SUPER_USER' ||
    targetUser?.roleName === 'SUPER_USER' ||
    targetUser?.roleName === 'ROLE_SUPER_ADMIN' ||
    targetUser?.roleName === 'SUPER_ADMIN';

  const isCurrentAdmin = currentUser?.username === 'admin';

  const isForbidden =
    isTargetAdmin ||
    isSelfAccount ||
    (isTargetSuperUser && !isCurrentAdmin);

  const getForbiddenReason = () => {
    if (isTargetAdmin) return 'Tài khoản admin mặc định không được phép chỉnh sửa hoặc xóa!';
    if (isSelfAccount) return 'Bạn không thể quản lý chính tài khoản của mình tại đây. Vui lòng truy cập trang Hồ sơ cá nhân!';
    if (isTargetSuperUser && !isCurrentAdmin) {
      return 'Chỉ có duy nhất tài khoản admin mới có quyền thao tác trên người dùng có vai trò SUPER_USER!';
    }
    return '';
  };

  const handleEditOpen = () => {
    if (isForbidden) return;
    setErrorMsg('');
    setSuccessMsg('');
    setEditForm({
      username: targetUser?.username || '',
      fullName: targetUser?.fullName || '',
      email: targetUser?.email || '',
      phoneNumber: targetUser?.phoneNumber || '',
      roleName: targetUser?.roleName || '',
      isActive: targetUser?.isActive ?? true
    });
    setIsEditModalOpen(true);
  };

  // Cập nhật thông tin User (Có bao gồm username)
  const handleUpdateUser = async (e) => {
    e.preventDefault();
    if (isForbidden) return;

    setErrorMsg('');
    setSuccessMsg('');

    if (!editForm.username.trim()) {
      setErrorMsg('Vui lòng nhập username!');
      return;
    }
    if (!editForm.fullName.trim()) {
      setErrorMsg('Vui lòng nhập họ và tên!');
      return;
    }
    if (!editForm.email.trim()) {
      setErrorMsg('Vui lòng nhập email!');
      return;
    }

    setSaving(true);

    try {
      const response = await axiosClient.put(`/users/${id}`, {
        username: editForm.username.trim(),
        email: editForm.email.trim(),
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        roleName: editForm.roleName,
        isActive: editForm.isActive
      });

      const updatedData = response.data?.data ?? {
        ...targetUser,
        username: editForm.username.trim(),
        fullName: editForm.fullName.trim(),
        email: editForm.email.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        roleName: editForm.roleName,
        isActive: editForm.isActive
      };

      setTargetUser(updatedData);
      setSuccessMsg('Cập nhật thông tin người dùng thành công!');
      setIsEditModalOpen(false);
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Cập nhật người dùng thất bại!'
      );
    } finally {
      setSaving(false);
    }
  };

  const handlePwdOpen = () => {
    if (isForbidden) return;
    setErrorMsg('');
    setSuccessMsg('');
    setPwdForm({
      newPassword: '',
      confirmNewPassword: ''
    });
    setIsPwdModalOpen(true);
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (isForbidden) return;

    setErrorMsg('');
    setSuccessMsg('');

    if (!pwdForm.newPassword) {
      setErrorMsg('Vui lòng nhập mật khẩu mới!');
      return;
    }
    if (pwdForm.newPassword !== pwdForm.confirmNewPassword) {
      setErrorMsg('Mật khẩu xác nhận không khớp!');
      return;
    }

    setSaving(true);

    try {
      await axiosClient.put(`/users/${id}`, {
        username: targetUser.username,
        email: targetUser.email,
        fullName: targetUser.fullName,
        phoneNumber: targetUser.phoneNumber,
        roleName: targetUser.roleName,
        isActive: targetUser.isActive,
        newPassword: pwdForm.newPassword,
        confirmNewPassword: pwdForm.confirmNewPassword
      });

      setSuccessMsg('Đổi mật khẩu người dùng thành công!');
      setIsPwdModalOpen(false);
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Đổi mật khẩu thất bại!'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (isForbidden) {
      alert(getForbiddenReason());
      return;
    }

    if (
      !window.confirm(
        `Bạn có chắc chắn muốn xóa vĩnh viễn tài khoản [${targetUser?.username}]?`
      )
    ) {
      return;
    }

    setSaving(true);

    try {
      await axiosClient.delete('/users', {
        data: [id]
      });
      
      alert('Xóa người dùng thành công!');
      navigate('/dashboard');
    } catch (err) {
      alert(err.response?.data?.message || 'Xóa người dùng thất bại!');
      setSaving(false);
    }
  };

  const getRoleName = (roleName) => {
    if (!roleName) return 'N/A';
    return roleName.replace('ROLE_', '');
  };

  if (loading) {
    return (
      <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span className="spinner" style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }} />
          Đang tải thông tin người dùng...
        </div>
      </div>
    );
  }

  return (
    <div className={`user-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="user-header">
        <div className="user-header-left">
          <button
            className="user-back-button"
            onClick={() => navigate('/dashboard')}
            title="Quay lại Dashboard"
          >
            ←
          </button>
          <h2>CHI TIẾT NGƯỜI DÙNG</h2>
        </div>
      </header>

      <main className="user-main">
        <div className="user-card">
          <div className="user-card-header">
            <div className="user-avatar">
              {targetUser?.username ? targetUser.username.charAt(0).toUpperCase() : 'U'}
            </div>

            <div className="user-header-info">
              <h3>{targetUser?.fullName || targetUser?.username}</h3>
              <p>@{targetUser?.username || 'N/A'}</p>
            </div>
          </div>

          {/* Hiển thị lý do nếu bị cấm thao tác */}
          {isForbidden && (
            <div className="user-alert error">
              <span>{getForbiddenReason()}</span>
            </div>
          )}

          {errorMsg && !isEditModalOpen && !isPwdModalOpen && (
            <div className="user-alert error">
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && !isEditModalOpen && !isPwdModalOpen && (
            <div className="user-alert success">
              <span>{successMsg}</span>
            </div>
          )}

          <div className="user-grid">
            <div className="user-field">
              <label>Username</label>
              <input
                type="text"
                className="user-input-box"
                value={targetUser?.username || ''}
                readOnly
              />
            </div>

            <div className="user-field">
              <label>Họ và tên</label>
              <input
                type="text"
                className="user-input-box"
                value={targetUser?.fullName || ''}
                readOnly
              />
            </div>

            <div className="user-field">
              <label>
                Email {targetUser && !targetUser.emailVerified && <span style={{ color: '#ef4444' }}>(*)</span>}
              </label>
              <input
                type="text"
                className="user-input-box"
                value={
                  targetUser?.email
                    ? targetUser.emailVerified
                      ? targetUser.email
                      : `${targetUser.email} (*)`
                    : ''
                }
                readOnly
              />
            </div>

            <div className="user-field">
              <label>Số điện thoại</label>
              <input
                type="text"
                className="user-input-box"
                value={targetUser?.phoneNumber || 'Chưa cập nhật'}
                readOnly
              />
            </div>

            <div className="user-field">
              <label>Vai trò (Role)</label>
              <input
                type="text"
                className="user-input-box"
                value={getRoleName(targetUser?.roleName)}
                readOnly
              />
            </div>

            <div className="user-field">
              <label>Trạng thái tài khoản</label>
              <div className={`status-badge ${targetUser?.isActive ? 'active' : 'inactive'}`}>
                {targetUser?.isActive ? '● Đang hoạt động (Active)' : '● Đã khóa (Deactive)'}
              </div>
            </div>
          </div>

          {/* 3 Nút Thao Tác */}
          <div className="user-actions">
            <button
              type="button"
              className="btn-delete-user"
              onClick={handleDelete}
              disabled={isForbidden || saving}
            >
              🗑️ Xóa Người Dùng
            </button>

            <button
              type="button"
              className="btn-edit-user"
              onClick={handleEditOpen}
              disabled={isForbidden || saving}
            >
              ✎ Chỉnh Sửa Thông Tin
            </button>

            <button
              type="button"
              className="btn-password-user"
              onClick={handlePwdOpen}
              disabled={isForbidden || saving}
            >
              🔒 Đổi Mật Khẩu
            </button>
          </div>
        </div>
      </main>

      {/* Modal Chỉnh Sửa Thông Tin */}
      {isEditModalOpen && !isForbidden && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-header">
              <h3>CHỈNH SỬA NGƯỜI DÙNG</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setIsEditModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="user-alert error">{errorMsg}</div>}

            <form onSubmit={handleUpdateUser}>
              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Username *</label>
                <div className="input-wrapper">
                  <input
                    type="text"
                    value={editForm.username}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, username: e.target.value }))
                    }
                    placeholder="Nhập username"
                    required
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Họ và tên *</label>
                <div className="input-wrapper">
                  <input
                    type="text"
                    value={editForm.fullName}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, fullName: e.target.value }))
                    }
                    placeholder="Nhập họ và tên"
                    required
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Email *</label>
                <div className="input-wrapper">
                  <input
                    type="email"
                    value={editForm.email}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, email: e.target.value }))
                    }
                    placeholder="user@example.com"
                    required
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Số điện thoại</label>
                <div className="input-wrapper">
                  <input
                    type="text"
                    value={editForm.phoneNumber}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, phoneNumber: e.target.value }))
                    }
                    placeholder="Nhập số điện thoại"
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Vai trò (Role) *</label>
                <div className="input-wrapper">
                  <select
                    value={editForm.roleName}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, roleName: e.target.value }))
                    }
                    disabled={saving}
                    className="user-select-box"
                  >
                    {roles.map((r) => {
                      const roleVal = typeof r === 'string' ? r : r.name;
                      return (
                        <option key={roleVal} value={roleVal}>
                          {roleVal.replace('ROLE_', '')}
                        </option>
                      );
                    })}
                  </select>
                </div>
              </div>

              <div className="checkbox-group" style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '8px' }}>
                <input
                  type="checkbox"
                  id="userActiveCheck"
                  checked={editForm.isActive}
                  onChange={(e) =>
                    setEditForm((prev) => ({ ...prev, isActive: e.target.checked }))
                  }
                  disabled={saving}
                  style={{ width: '18px', height: '18px', accentColor: 'var(--primary)', cursor: 'pointer' }}
                />
                <label htmlFor="userActiveCheck" style={{ cursor: 'pointer', fontSize: '13.5px', userSelect: 'none' }}>
                  Kích hoạt tài khoản (Active)
                </label>
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="cancel-btn"
                  onClick={() => setIsEditModalOpen(false)}
                  disabled={saving}
                >
                  Hủy
                </button>
                <button type="submit" className="save-btn" disabled={saving}>
                  {saving ? 'ĐANG LƯU...' : 'LƯU THAY ĐỔI'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Đổi Mật Khẩu */}
      {isPwdModalOpen && !isForbidden && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-header">
              <h3>ĐỔI MẬT KHẨU NGƯỜI DÙNG</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setIsPwdModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="user-alert error">{errorMsg}</div>}

            <form onSubmit={handleChangePassword}>
              <div className="form-group password-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Mật khẩu mới *</label>
                <div className="input-wrapper">
                  <input
                    type={showNewPwd ? 'text' : 'password'}
                    value={pwdForm.newPassword}
                    onChange={(e) =>
                      setPwdForm((prev) => ({
                        ...prev,
                        newPassword: e.target.value
                      }))
                    }
                    placeholder="Nhập mật khẩu mới"
                    required
                    disabled={saving}
                  />
                  <button
                    type="button"
                    className="toggle-password-button"
                    onClick={() => setShowNewPwd(!showNewPwd)}
                    tabIndex={-1}
                  >
                    <EyeIcon show={showNewPwd} />
                  </button>
                </div>
              </div>

              <div className="form-group password-group" style={{ marginBottom: '16px' }}>
                <label style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', display: 'block' }}>Xác nhận mật khẩu mới *</label>
                <div className="input-wrapper">
                  <input
                    type={showConfirmPwd ? 'text' : 'password'}
                    value={pwdForm.confirmNewPassword}
                    onChange={(e) =>
                      setPwdForm((prev) => ({
                        ...prev,
                        confirmNewPassword: e.target.value
                      }))
                    }
                    placeholder="Nhập lại mật khẩu mới"
                    required
                    disabled={saving}
                  />
                  <button
                    type="button"
                    className="toggle-password-button"
                    onClick={() => setShowConfirmPwd(!showConfirmPwd)}
                    tabIndex={-1}
                  >
                    <EyeIcon show={showConfirmPwd} />
                  </button>
                </div>
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="cancel-btn"
                  onClick={() => setIsPwdModalOpen(false)}
                  disabled={saving}
                >
                  Hủy
                </button>
                <button type="submit" className="save-btn" disabled={saving}>
                  {saving ? 'ĐANG CẬP NHẬT...' : 'ĐỔI MẬT KHẨU'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserPage;