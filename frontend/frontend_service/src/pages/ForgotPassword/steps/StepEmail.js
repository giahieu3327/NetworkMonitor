import React from 'react';

const StepEmail = ({ email, setEmail, onSubmit, loading, onBack, clearMessages }) => {
  return (
    <form onSubmit={onSubmit}>
      <p className="auth-description">Nhập email của bạn để nhận mã OTP đặt lại mật khẩu.</p>

      <div className="form-group">
        <label htmlFor="forgot-email">Email</label>
        <div className="input-wrapper">
          <input
            id="forgot-email"
            type="email"
            value={email}
            onChange={(e) => { setEmail(e.target.value); clearMessages(); }}
            placeholder="Nhập email tài khoản"
            autoComplete="email"
            required
            disabled={loading}
          />
        </div>
      </div>

      <button type="submit" className="primary-button" disabled={loading}>
        {loading ? (
          <span className="button-loading-state">
            <span className="spinner" /> Đang gửi OTP...
          </span>
        ) : ('GỬI MÃ OTP')}
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

export default StepEmail;