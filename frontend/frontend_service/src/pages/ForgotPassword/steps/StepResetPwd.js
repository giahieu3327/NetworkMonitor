import React, { useState } from 'react';

const StepResetPwd = ({
  email,
  newPassword,
  setNewPassword,
  confirmPassword,
  setConfirmPassword,
  onSubmit,
  loading,
  onBack,
  clearMessages
}) => {
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

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

  return (
    <form onSubmit={onSubmit}>
      <p className="auth-description">
        Đặt lại mật khẩu mới cho tài khoản:
        <span className="email-display">{email}</span>
      </p>

      <div className="form-group password-group">
        <label htmlFor="new-password">Mật khẩu mới</label>
        <div className="input-wrapper">
          <input
            id="new-password"
            type={showNew ? 'text' : 'password'}
            value={newPassword}
            onChange={(e) => { setNewPassword(e.target.value); clearMessages(); }}
            placeholder="Nhập mật khẩu mới"
            autoComplete="new-password"
            required
            disabled={loading}
          />
          <button type="button" className="toggle-password-button" onClick={() => setShowNew(!showNew)} tabIndex={-1}>
            <EyeIcon show={showNew} />
          </button>
        </div>
      </div>

      <div className="form-group password-group">
        <label htmlFor="confirm-password">Xác nhận mật khẩu</label>
        <div className="input-wrapper">
          <input
            id="confirm-password"
            type={showConfirm ? 'text' : 'password'}
            value={confirmPassword}
            onChange={(e) => { setConfirmPassword(e.target.value); clearMessages(); }}
            placeholder="Nhập lại mật khẩu mới"
            autoComplete="new-password"
            required
            disabled={loading}
          />
          <button type="button" className="toggle-password-button" onClick={() => setShowConfirm(!showConfirm)} tabIndex={-1}>
            <EyeIcon show={showConfirm} />
          </button>
        </div>
      </div>

      <button type="submit" className="primary-button" disabled={loading}>
        {loading ? (
          <span className="button-loading-state">
            <span className="spinner" /> Đang cập nhật...
          </span>
        ) : ('ĐẶT LẠI MẬT KHẨU')}
      </button>

      <div className="back-login-container">
        <button type="button" className="back-login-button" onClick={onBack} disabled={loading}>
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M9.707 16.707a1 1 0 01-1.414 0l-6-6a1 1 0 010-1.414l6-6a1 1 0 011.414 1.414L5.414 9H17a1 1 0 110 2H5.414l4.293 4.293a1 1 0 010 1.414z" clipRule="evenodd" />
          </svg>
          Quay lại đăng nhập
        </button>
      </div>
    </form>
  );
};

export default StepResetPwd;