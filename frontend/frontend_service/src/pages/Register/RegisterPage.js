import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './RegisterPage.css';

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

const RegisterPage = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    email: '',
    fullName: '',
    phoneNumber: '',
    roleName: '',
    sendEmailVerify: true
  });

  const [roles, setRoles] = useState([]);
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  // Lấy danh sách Roles từ GET /api/v1/users/roles
  useEffect(() => {
    axiosClient
      .get('/users/roles')
      .then((res) => {
        const data = res.data?.data ?? res.data ?? [];
        setRoles(data);
        if (data.length > 0) {
          const firstRole = typeof data[0] === 'string' ? data[0] : data[0].name;
          setFormData((prev) => ({ ...prev, roleName: firstRole }));
        }
      })
      .catch(() => setErrorMsg('Không thể tải danh sách Roles từ hệ thống!'));
  }, []);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    setErrorMsg('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');

    if (formData.password !== formData.confirmPassword) {
      setErrorMsg('Mật khẩu xác nhận không khớp!');
      return;
    }

    setLoading(true);

    try {
      await axiosClient.post('/auth/register', {
        username: formData.username.trim(),
        password: formData.password,
        confirmPassword: formData.confirmPassword,
        email: formData.email.trim(),
        fullName: formData.fullName.trim(),
        phoneNumber: formData.phoneNumber.trim(),
        roleName: formData.roleName,
        sendEmailVerify: formData.sendEmailVerify
      });

      alert('Tạo tài khoản mới thành công!');
      navigate('/dashboard');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Tạo tài khoản thất bại!');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={`register-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="register-card">
        <h2 className="register-title">TẠO TÀI KHOẢN MỚI</h2>

        {errorMsg && (
          <div className="register-alert error">
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="register-form">
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="username">Username *</label>
              <div className="input-wrapper">
                <input
                  id="username"
                  type="text"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  placeholder="Nhập username"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="email">Email *</label>
              <div className="input-wrapper">
                <input
                  id="email"
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="user@example.com"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group password-group">
              <label htmlFor="password">Mật khẩu *</label>
              <div className="input-wrapper">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="toggle-password-button"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  <EyeIcon show={showPassword} />
                </button>
              </div>
            </div>

            <div className="form-group password-group">
              <label htmlFor="confirmPassword">Xác nhận mật khẩu *</label>
              <div className="input-wrapper">
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="toggle-password-button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  tabIndex={-1}
                >
                  <EyeIcon show={showConfirmPassword} />
                </button>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="fullName">Họ và Tên *</label>
              <div className="input-wrapper">
                <input
                  id="fullName"
                  type="text"
                  name="fullName"
                  value={formData.fullName}
                  onChange={handleChange}
                  placeholder="Nhập họ và tên"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="phoneNumber">Số điện thoại</label>
              <div className="input-wrapper">
                <input
                  id="phoneNumber"
                  type="text"
                  name="phoneNumber"
                  value={formData.phoneNumber}
                  onChange={handleChange}
                  placeholder="0901234567"
                  disabled={loading}
                />
              </div>
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="roleName">Vai trò (Role) *</label>
            <div className="input-wrapper">
              <select
                id="roleName"
                name="roleName"
                value={formData.roleName}
                onChange={handleChange}
                disabled={loading}
                className="role-select"
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

          <div className="checkbox-group">
            <input
              type="checkbox"
              id="sendEmailVerify"
              name="sendEmailVerify"
              checked={formData.sendEmailVerify}
              onChange={handleChange}
              disabled={loading}
            />
            <label htmlFor="sendEmailVerify">
              Gửi email xác thực tài khoản sau khi tạo
            </label>
          </div>

          <div className="register-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={() => navigate('/dashboard')}
              disabled={loading}
            >
              Hủy
            </button>

            <button
              type="submit"
              className="submit-btn"
              disabled={loading}
            >
              {loading ? 'ĐANG TẠO...' : 'Tạo Tài Khoản'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default RegisterPage;