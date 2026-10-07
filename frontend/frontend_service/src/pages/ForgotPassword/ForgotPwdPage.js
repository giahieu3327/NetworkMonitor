import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './ForgotPwdPage.css';

import StepEmail from './steps/StepEmail';
import StepOtp from './steps/StepOtp';
import StepResetPwd from './steps/StepResetPwd';

const ForgotPwdPage = () => {
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [resendCountdown, setResendCountdown] = useState(0);
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
    if (resendCountdown <= 0) return;
    const timer = setInterval(() => {
      setResendCountdown((prev) => (prev <= 1 ? (clearInterval(timer), 0) : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCountdown]);

  const clearMessages = () => {
    setErrorMsg('');
    setSuccessMsg('');
  };

  const handleConfirmEmail = async (e) => {
    e.preventDefault();
    clearMessages();
    const normalizedEmail = email.trim();
    if (!normalizedEmail) return setErrorMsg('Vui lòng nhập email.');

    setLoading(true);
    try {
      const res = await axiosClient.post('/auth/forgot-password', { email: normalizedEmail });
      setEmail(normalizedEmail);
      setOtp('');
      setStep(2);
      setResendCountdown(300);
      setSuccessMsg(res.data?.message || 'Mã OTP đã được gửi đến email của bạn.');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Không thể gửi mã OTP. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    clearMessages();
    if (resendCountdown > 0 || loading) return;

    setLoading(true);
    try {
      const res = await axiosClient.post('/auth/forgot-password', { email });
      setOtp('');
      setResendCountdown(300);
      setSuccessMsg(res.data?.message || 'Mã OTP mới đã được gửi đến email của bạn.');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Không thể gửi lại mã OTP.');
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmOtp = async (e) => {
    e.preventDefault();
    clearMessages();
    if (!otp.trim()) return setErrorMsg('Vui lòng nhập mã OTP.');
    if (!/^\d{6}$/.test(otp)) return setErrorMsg('OTP phải gồm 6 chữ số.');

    setLoading(true);
    try {
      const res = await axiosClient.post('/auth/verify-reset-password-otp', { email, otp });
      if (res.data?.data) setEmail(res.data.data);
      setOtp('');
      setStep(3);
      setSuccessMsg(res.data?.message || 'Mã OTP hợp lệ.');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Mã OTP không hợp lệ hoặc đã hết hạn.');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    clearMessages();
    if (!newPassword) return setErrorMsg('Vui lòng nhập mật khẩu mới.');
    if (newPassword !== confirmPassword) return setErrorMsg('Mật khẩu xác nhận không khớp.');

    setLoading(true);
    try {
      const res = await axiosClient.post('/auth/reset-password', { email, newPassword, confirmPassword });
      setSuccessMsg(res.data?.message || 'Đặt lại mật khẩu thành công.');
      setTimeout(() => navigate('/login'), 1500);
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Không thể đặt lại mật khẩu.');
    } finally {
      setLoading(false);
    }
  };

  const titles = ['Quên Mật Khẩu', 'Xác Thực OTP', 'Đặt Lại Mật Khẩu'];

  return (
    <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="auth-backdrop-glow" />

      <div className="auth-card">
        <div className="auth-header">
          <div className="brand-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
            </svg>
          </div>
          <h2 className="auth-title">{titles[step - 1]}</h2>
        </div>

        {/* Step Indicator */}
        <div className="step-indicator">
          {['Email', 'OTP', 'Mật khẩu'].map((label, idx) => (
            <React.Fragment key={idx}>
              <div className={`step-item ${step >= idx + 1 ? 'active' : ''}`}>
                <div className="step-number">{idx + 1}</div>
                <span className="step-label">{label}</span>
              </div>
              {idx < 2 && <div className={`step-line ${step >= idx + 2 ? 'active' : ''}`} />}
            </React.Fragment>
          ))}
        </div>

        {/* Alerts */}
        {errorMsg && (
          <div className="forgot-error">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <span>{errorMsg}</span>
          </div>
        )}

        {successMsg && (
          <div className="forgot-success">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
            <span>{successMsg}</span>
          </div>
        )}

        {/* Dynamic Steps */}
        {step === 1 && (
          <StepEmail
            email={email}
            setEmail={setEmail}
            onSubmit={handleConfirmEmail}
            loading={loading}
            onBack={() => navigate('/login')}
            clearMessages={clearMessages}
          />
        )}

        {step === 2 && (
          <StepOtp
            email={email}
            otp={otp}
            setOtp={setOtp}
            onSubmit={handleConfirmOtp}
            onResend={handleResendOtp}
            resendCountdown={resendCountdown}
            loading={loading}
            onBack={() => navigate('/login')}
            clearMessages={clearMessages}
          />
        )}

        {step === 3 && (
          <StepResetPwd
            email={email}
            newPassword={newPassword}
            setNewPassword={setNewPassword}
            confirmPassword={confirmPassword}
            setConfirmPassword={setConfirmPassword}
            onSubmit={handleResetPassword}
            loading={loading}
            onBack={() => navigate('/login')}
            clearMessages={clearMessages}
          />
        )}
      </div>
    </div>
  );
};

export default ForgotPwdPage;