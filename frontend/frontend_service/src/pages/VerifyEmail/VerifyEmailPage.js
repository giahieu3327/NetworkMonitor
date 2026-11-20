import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './VerifyEmailPage.css';

const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState('loading'); // 'loading' | 'success' | 'error'
  const [errorMessage, setErrorMessage] = useState('');

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
    if (!token) {
      setStatus('error');
      setErrorMessage(
        'Mã xác thực (token) không tồn tại hoặc đường dẫn không hợp lệ.'
      );
      return;
    }

    const verifyToken = async () => {
      try {
        await axiosClient.post('/auth/verify-email', { token });
        setStatus('success');
      } catch (err) {
        setStatus('error');
        setErrorMessage(
          err.response?.data?.message ||
            'Xác thực thất bại hoặc liên kết đã hết hạn.'
        );
      }
    };

    verifyToken();
  }, [token]);

  return (
    <div className={`verify-email-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="verify-email-card">
        {/* Trạng thái 1: Đang xác thực */}
        {status === 'loading' && (
          <div className="spinner-container">
            <div className="verify-spinner"></div>
            <h2 className="verify-title">Đang xác thực email...</h2>
            <p className="verify-desc">
              Hệ thống đang xử lý mã xác nhận của bạn. Vui lòng chờ trong giây lát.
            </p>
          </div>
        )}

        {/* Trạng thái 2: Xác thực thành công */}
        {status === 'success' && (
          <div>
            <div className="verify-icon success">✓</div>
            <h2 className="verify-title">Xác thực thành công!</h2>
            <p className="verify-desc">
              Địa chỉ email của bạn đã được xác nhận thành công. Bạn có thể đóng trang này lại.
            </p>
          </div>
        )}

        {/* Trạng thái 3: Xác thực thất bại */}
        {status === 'error' && (
          <div>
            <div className="verify-icon error">✕</div>
            <h2 className="verify-title">Xác thực thất bại</h2>
            <p className="verify-desc">{errorMessage}</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default VerifyEmailPage;