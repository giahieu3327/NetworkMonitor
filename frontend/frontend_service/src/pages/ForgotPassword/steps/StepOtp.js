import React from 'react';

const StepOtp = ({
  email,
  otp,
  setOtp,
  onSubmit,
  onResend,
  resendCountdown,
  loading,
  onBack,
  clearMessages
}) => {
  return (
    <form onSubmit={onSubmit}>
      <p className="auth-description">
        Mã OTP đã được gửi đến email:
        <span className="email-display">{email}</span>
      </p>

      <div className="form-group">
        <label htmlFor="forgot-otp">Mã OTP (6 chữ số)</label>
        <div className="input-wrapper">
          <input
            id="forgot-otp"
            type="text"
            value={otp}
            onChange={(e) => {
              const val = e.target.value.replace(/\D/g, '');
              if (val.length <= 6) { setOtp(val); clearMessages(); }
            }}
            placeholder="• • • • • •"
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            required
            disabled={loading}
          />
        </div>
      </div>

      <button type="submit" className="primary-button" disabled={loading}>
        {loading ? (
          <span className="button-loading-state">
            <span className="spinner" /> Đang xác thực...
          </span>
        ) : ('XÁC NHẬN OTP')}
      </button>

      <div className="resend-otp-container">
        <span className="resend-text">Không nhận được mã?</span>
        {resendCountdown > 0 ? (
          <span className="resend-countdown">Gửi lại sau {resendCountdown}s</span>
        ) : (
          <button type="button" className="resend-otp-button" onClick={onResend} disabled={loading}>
            Gửi lại OTP
          </button>
        )}
      </div>

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

export default StepOtp;