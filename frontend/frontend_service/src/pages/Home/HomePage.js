import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './HomePage.css';

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

const HomePage = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [verifyingEmail, setVerifyingEmail] = useState(false);

  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isPwdModalOpen, setIsPwdModalOpen] = useState(false);

  const [editForm, setEditForm] = useState({
    fullName: '',
    phoneNumber: ''
  });

  const [pwdForm, setPwdForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });

  const [showCurrentPwd, setShowCurrentPwd] = useState(false);
  const [showNewPwd, setShowNewPwd] = useState(false);
  const [showConfirmPwd, setShowConfirmPwd] = useState(false);

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (event) => setIsDarkMode(event.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  useEffect(() => {
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    setLoading(true);
    setErrorMsg('');

    try {
      const response = await axiosClient.get('/profile/me');
      const data = response.data?.data ?? response.data;
      setMe(data);

      setEditForm({
        fullName: data?.fullName || '',
        phoneNumber: data?.phoneNumber || ''
      });
    } catch (error) {
      if (
        error.response?.status === 401 ||
        error.response?.status === 403
      ) {
        localStorage.clear();
        navigate('/login');
        return;
      }
      setErrorMsg(
        error.response?.data?.message || 'Không thể tải thông tin cá nhân.'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleSendVerifyEmail = async () => {
    if (!me?.email) {
      setErrorMsg('Không tìm thấy địa chỉ email.');
      return;
    }

    setVerifyingEmail(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      const response = await axiosClient.post('/auth/send-verification-email', {
        toEmail: me.email
      });
      setSuccessMsg(
        response.data?.message || 'Email xác thực đã được gửi thành công!'
      );
    } catch (error) {
      setErrorMsg(
        error.response?.data?.message || 'Không thể gửi email xác thực.'
      );
    } finally {
      setVerifyingEmail(false);
    }
  };

  const handleEditOpen = () => {
    if (me?.username === 'admin') return;
    setErrorMsg('');
    setSuccessMsg('');
    setEditForm({
      fullName: me?.fullName || '',
      phoneNumber: me?.phoneNumber || ''
    });
    setIsEditModalOpen(true);
  };

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    if (me?.username === 'admin') return;

    setErrorMsg('');
    setSuccessMsg('');

    if (!editForm.fullName.trim()) {
      setErrorMsg('Vui lòng nhập họ và tên.');
      return;
    }

    setSaving(true);

    try {
      const response = await axiosClient.put('/profile/me', {
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim()
      });

      const updatedData = response.data?.data ?? {
        ...me,
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim()
      };

      setMe(updatedData);
      setSuccessMsg('Cập nhật thông tin cá nhân thành công.');
      setIsEditModalOpen(false);
    } catch (error) {
      setErrorMsg(
        error.response?.data?.message || 'Cập nhật thông tin thất bại.'
      );
    } finally {
      setSaving(false);
    }
  };

  const handlePwdOpen = () => {
    setErrorMsg('');
    setSuccessMsg('');
    setPwdForm({
      currentPassword: '',
      newPassword: '',
      confirmPassword: ''
    });
    setIsPwdModalOpen(true);
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    if (!pwdForm.currentPassword) {
      setErrorMsg('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    if (!pwdForm.newPassword) {
      setErrorMsg('Vui lòng nhập mật khẩu mới.');
      return;
    }
    if (pwdForm.newPassword !== pwdForm.confirmPassword) {
      setErrorMsg('Mật khẩu xác nhận không khớp.');
      return;
    }

    setSaving(true);

    try {
      await axiosClient.put('/profile/me', {
        oldPassword: pwdForm.currentPassword,
        newPassword: pwdForm.newPassword,
        confirmNewPassword: pwdForm.confirmPassword
      });

      setSuccessMsg('Đổi mật khẩu thành công!');
      setIsPwdModalOpen(false);
    } catch (error) {
      setErrorMsg(
        error.response?.data?.message || 'Đổi mật khẩu thất bại.'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleLogout = async () => {
    try {
      const refreshToken = localStorage.getItem('refresh_token');
      if (refreshToken) {
        await axiosClient.post('/auth/logout', { refreshToken });
      }
    } finally {
      localStorage.clear();
      navigate('/login');
    }
  };

  const getRoleName = (roleName) => {
    if (!roleName) return 'N/A';
    return roleName.replace('ROLE_', '');
  };

  const isAdminAccount = me?.username === 'admin';

  if (loading) {
    return (
      <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span className="spinner" style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }} />
          Đang tải thông tin...
        </div>
      </div>
    );
  }

  return (
    <div className={`home-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="home-header">
        <div className="home-header-left">
          <button
            className="home-back-button"
            onClick={() => navigate('/dashboard')}
            title="Quay lại Dashboard"
          >
            ←
          </button>
          <h2>THÔNG TIN TÀI KHOẢN</h2>
        </div>

        <button className="home-logout-button" onClick={handleLogout}>
          Đăng xuất
        </button>
      </header>

      <main className="home-main">
        <div className="profile-card">
          <div className="profile-card-header">
            <div className="profile-avatar">
              {me?.username ? me.username.charAt(0).toUpperCase() : 'U'}
            </div>

            <div className="profile-header-info">
              <h3>{me?.fullName || me?.username}</h3>
              <p>@{me?.username || 'N/A'}</p>
            </div>
          </div>

          {/* Alert Messages */}
          {errorMsg && !isEditModalOpen && !isPwdModalOpen && (
            <div className="home-alert error">
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && !isEditModalOpen && !isPwdModalOpen && (
            <div className="home-alert success">
              <span>{successMsg}</span>
            </div>
          )}

          <div className="profile-form">
            <div className="profile-field">
              <label>Username</label>
              <input
                type="text"
                className="profile-input-box"
                value={me?.username || ''}
                readOnly
              />
            </div>

            <div className="profile-field">
              <label>Họ và tên</label>
              <input
                type="text"
                className="profile-input-box"
                value={me?.fullName || ''}
                readOnly
              />
            </div>

            <div className="profile-field">
              <label>
                Email {!me?.emailVerified && <span style={{ color: '#ef4444' }}>(*)</span>}
              </label>
              <div className="email-input-group">
                <input
                  type="text"
                  className="profile-input-box"
                  value={
                    me?.email
                      ? me.emailVerified
                        ? me.email
                        : `${me.email} (*)`
                      : ''
                  }
                  readOnly
                />
                {!me?.emailVerified && (
                  <button
                    className="verify-email-btn"
                    onClick={handleSendVerifyEmail}
                    disabled={verifyingEmail}
                  >
                    {verifyingEmail ? 'Đang gửi...' : 'Xác thực Email'}
                  </button>
                )}
              </div>
            </div>

            <div className="profile-field">
              <label>Số điện thoại</label>
              <input
                type="text"
                className="profile-input-box"
                value={me?.phoneNumber || 'Chưa cập nhật'}
                readOnly
              />
            </div>

            <div className="profile-field">
              <label>Vai trò</label>
              <input
                type="text"
                className="profile-input-box"
                value={getRoleName(me?.roleName)}
                readOnly
              />
            </div>
          </div>

          <div className="profile-actions">
            {!isAdminAccount && (
              <button className="edit-profile-btn" onClick={handleEditOpen}>
                ✎ Chỉnh sửa thông tin
              </button>
            )}
            <button className="change-password-btn" onClick={handlePwdOpen}>
              🔒 Đổi mật khẩu
            </button>
          </div>
        </div>
      </main>

      {/* Modal Chỉnh sửa Profile (Chỉ hiển thị khi không phải admin) */}
      {isEditModalOpen && !isAdminAccount && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-header">
              <h3>CHỈNH SỬA THÔNG TIN</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setIsEditModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="home-alert error">{errorMsg}</div>}

            <form onSubmit={handleUpdateProfile}>
              <div className="form-group">
                <label>Họ và tên</label>
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

              <div className="form-group">
                <label>Số điện thoại</label>
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
      {isPwdModalOpen && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-header">
              <h3>ĐỔI MẬT KHẨU</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setIsPwdModalOpen(false)}
                disabled={saving}
              >
                ✕
              </button>
            </div>

            {errorMsg && <div className="home-alert error">{errorMsg}</div>}

            <form onSubmit={handleChangePassword}>
              <div className="form-group password-group">
                <label>Mật khẩu hiện tại</label>
                <div className="input-wrapper">
                  <input
                    type={showCurrentPwd ? 'text' : 'password'}
                    value={pwdForm.currentPassword}
                    onChange={(e) =>
                      setPwdForm((prev) => ({
                        ...prev,
                        currentPassword: e.target.value
                      }))
                    }
                    placeholder="Nhập mật khẩu hiện tại"
                    required
                    disabled={saving}
                  />
                  <button
                    type="button"
                    className="toggle-password-button"
                    onClick={() => setShowCurrentPwd(!showCurrentPwd)}
                    tabIndex={-1}
                  >
                    <EyeIcon show={showCurrentPwd} />
                  </button>
                </div>
              </div>

              <div className="form-group password-group">
                <label>Mật khẩu mới</label>
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

              <div className="form-group password-group">
                <label>Xác nhận mật khẩu mới</label>
                <div className="input-wrapper">
                  <input
                    type={showConfirmPwd ? 'text' : 'password'}
                    value={pwdForm.confirmPassword}
                    onChange={(e) =>
                      setPwdForm((prev) => ({
                        ...prev,
                        confirmPassword: e.target.value
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

export default HomePage;