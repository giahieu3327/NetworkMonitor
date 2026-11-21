import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './UserPage.css';

const USER_ADMIN_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
]);

const PASSWORD_POLICY_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/;

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  if (!trimmed) return '';
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
      pushRole(val.roleName || val.role_name || val.name || val.authority || val.role);
    }
  };

  pushRole(source.roleName);
  pushRole(source.role_name);
  pushRole(source.role);
  pushRole(source.authority);
  pushRole(source.roles);
  pushRole(source.authorities);
  pushRole(source.realmRoles);
  pushRole(source.realm_roles);
  pushRole(source.realm_access?.roles);
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

const UserPage = ({
  currentUser: currentUserProp,
  canManageUsers: canManageUsersProp,
}) => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const initialUser =
    currentUserProp || location.state?.currentUser || readStoredUser();

  const [resolvedUser, setResolvedUser] = useState(initialUser);
  const [checkingPermission, setCheckingPermission] = useState(true);

  const [targetUser, setTargetUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isPwdModalOpen, setIsPwdModalOpen] = useState(false);

  const [editForm, setEditForm] = useState({
    username: '',
    fullName: '',
    email: '',
    phoneNumber: '',
    roleName: '',
    isActive: true,
  });

  const [pwdForm, setPwdForm] = useState({
    newPassword: '',
    confirmNewPassword: '',
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

  const currentUserRoles = useMemo(
    () => extractRoles(resolvedUser),
    [resolvedUser]
  );

  const canViewAndManageUsers = useMemo(() => {
    if (typeof canManageUsersProp === 'boolean') {
      return canManageUsersProp;
    }
    if (!resolvedUser || currentUserRoles.size === 0) {
      return false;
    }
    return Array.from(currentUserRoles).some((role) =>
      USER_ADMIN_ROLES.has(role)
    );
  }, [canManageUsersProp, resolvedUser, currentUserRoles]);

  useEffect(() => {
    if (checkingPermission) return;

    if (!canViewAndManageUsers) {
      setLoading(false);
      return;
    }

    const fetchData = async () => {
      setLoading(true);
      setErrorMsg('');

      try {
        const [targetRes, rolesRes] = await Promise.all([
          axiosClient.get(`/users/${id}`),
          axiosClient.get('/users/roles'),
        ]);

        const userData = targetRes.data?.data ?? targetRes.data;
        const rolesData = rolesRes.data?.data ?? rolesRes.data ?? [];

        setTargetUser(userData);
        setRoles(Array.isArray(rolesData) ? rolesData : []);

        setEditForm({
          username: userData?.username || '',
          fullName: userData?.fullName || '',
          email: userData?.email || '',
          phoneNumber: userData?.phoneNumber || '',
          roleName: normalizeRole(userData?.roleName) || '',
          isActive: userData?.isActive ?? true,
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
  }, [id, checkingPermission, canViewAndManageUsers]);

  const targetRoles = useMemo(
    () => extractRoles(targetUser),
    [targetUser]
  );

  const isTargetAdmin = targetUser?.username === 'admin';
  const isSelfAccount = Boolean(
    resolvedUser &&
      targetUser &&
      ((resolvedUser.id && targetUser.id && resolvedUser.id === targetUser.id) ||
        (resolvedUser.username &&
          targetUser.username &&
          resolvedUser.username === targetUser.username))
  );

  const isTargetSuperAdmin = targetRoles.has('ROLE_SUPER_ADMIN');
  const isCurrentAdmin = resolvedUser?.username === 'admin';

  const isForbidden =
    !canViewAndManageUsers ||
    isTargetAdmin ||
    isSelfAccount ||
    (isTargetSuperAdmin && !isCurrentAdmin);

  const getForbiddenReason = () => {
    if (!canViewAndManageUsers) {
      return 'Bạn không có quyền quản lý thông tin tài khoản người dùng. Chỉ tài khoản có vai trò SUPER_ADMIN mới được phép truy cập trang này.';
    }
    if (isTargetAdmin) {
      return 'Tài khoản admin mặc định không được phép chỉnh sửa hoặc xóa!';
    }
    if (isSelfAccount) {
      return 'Bạn không thể quản lý chính tài khoản của mình tại đây. Vui lòng truy cập trang Hồ sơ cá nhân!';
    }
    if (isTargetSuperAdmin && !isCurrentAdmin) {
      return 'Chỉ có duy nhất tài khoản admin mới có quyền thao tác trên người dùng có vai trò SUPER_ADMIN!';
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
      roleName: normalizeRole(targetUser?.roleName) || '',
      isActive: targetUser?.isActive ?? true,
    });
    setIsEditModalOpen(true);
  };

  const handleUpdateUser = async (e) => {
    e.preventDefault();
    if (checkingPermission || isForbidden) return;

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

    const normalizedRole = normalizeRole(editForm.roleName);
    if (!normalizedRole) {
      setErrorMsg('Vui lòng chọn vai trò hợp lệ!');
      return;
    }

    setSaving(true);

    try {
      const response = await axiosClient.put(`/users/${id}`, {
        username: editForm.username.trim(),
        email: editForm.email.trim(),
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        roleName: normalizedRole,
        isActive: editForm.isActive,
      });

      const updatedData = response.data?.data ?? {
        ...targetUser,
        username: editForm.username.trim(),
        fullName: editForm.fullName.trim(),
        email: editForm.email.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        roleName: normalizedRole,
        isActive: editForm.isActive,
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
      confirmNewPassword: '',
    });
    setIsPwdModalOpen(true);
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (checkingPermission || isForbidden) return;

    setErrorMsg('');
    setSuccessMsg('');

    if (!pwdForm.newPassword) {
      setErrorMsg('Vui lòng nhập mật khẩu mới!');
      return;
    }
    if (!PASSWORD_POLICY_REGEX.test(pwdForm.newPassword)) {
      setErrorMsg(
        'Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt!'
      );
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
        roleName: normalizeRole(targetUser.roleName),
        isActive: targetUser.isActive,
        newPassword: pwdForm.newPassword,
        confirmNewPassword: pwdForm.confirmNewPassword,
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
    if (checkingPermission || isForbidden) {
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
        data: [id],
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
    return normalizeRole(roleName).replace('ROLE_', '');
  };

  if (checkingPermission) {
    return (
      <div className={`user-page-denied ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span className="spinner" style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }} />
          Đang kiểm tra quyền truy cập...
        </div>
      </div>
    );
  }

  if (!canViewAndManageUsers) {
    return (
      <div className={`user-page-denied ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="user-denied-card">
          <div className="permission-denied-box">
            <div className="permission-denied-icon">!</div>
            <h2 className="permission-denied-title">Không có quyền truy cập</h2>
            <p className="permission-message">
              {getForbiddenReason()}
            </p>
          </div>
          <div className="user-denied-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={() => navigate('/dashboard')}
            >
              Quay lại
            </button>
          </div>
        </div>
      </div>
    );
  }

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
                      const rawRole = typeof r === 'string' ? r : r.name;
                      const roleVal = normalizeRole(rawRole);
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
                        newPassword: e.target.value,
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
                        confirmNewPassword: e.target.value,
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