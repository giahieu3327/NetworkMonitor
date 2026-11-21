import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './VerifyEmailPage.css';

const ALLOWED_VERIFY_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_REPORT_MANAGER',
  'ROLE_GUEST_VIEWER',
]);

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  return trimmed.startsWith('ROLE_') ? trimmed : `ROLE_${trimmed}`;
};

const extractUserRoles = (user) => {
  const rolesSet = new Set();
  if (!user) return rolesSet;

  if (user.roleName) {
    const normalized = normalizeRole(user.roleName);
    if (normalized) rolesSet.add(normalized);
  }

  if (Array.isArray(user.roles)) {
    user.roles.forEach((r) => {
      const normalized = normalizeRole(r);
      if (normalized) rolesSet.add(normalized);
    });
  }

  return rolesSet;
};

const VerifyEmailPage = ({ currentUser: currentUserProp }) => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const token = searchParams.get('token');

  const [currentUser, setCurrentUser] = useState(
    currentUserProp || location.state?.currentUser || null
  );
  const [roleChecked, setRoleChecked] = useState(
    Boolean(currentUserProp || location.state?.currentUser)
  );

  const [status, setStatus] = useState('loading'); // 'loading' | 'success' | 'error' | 'forbidden'
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
    let isMounted = true;

    const resolveCurrentUser = async () => {
      if (currentUserProp || location.state?.currentUser) {
        if (isMounted) {
          setCurrentUser(currentUserProp || location.state?.currentUser);
          setRoleChecked(true);
        }
        return;
      }

      const accessToken = localStorage.getItem('access_token');
      if (!accessToken) {
        if (isMounted) {
          setRoleChecked(true);
        }
        return;
      }

      try {
        const meRes = await axiosClient.get('/profile/me');
        if (isMounted) {
          setCurrentUser(meRes.data?.data ?? meRes.data ?? null);
        }
      } catch {
        // Cho phép luồng xác thực email công khai qua token nếu chưa có session hợp lệ
      } finally {
        if (isMounted) {
          setRoleChecked(true);
        }
      }
    };

    resolveCurrentUser();
    return () => {
      isMounted = false;
    };
  }, [currentUserProp, location.state?.currentUser]);

  const userRoles = useMemo(
    () => extractUserRoles(currentUser),
    [currentUser]
  );

  const canVerifyEmail = useMemo(() => {
    if (!currentUser) return true;
    if (userRoles.size === 0) return true;
    return Array.from(userRoles).some((role) =>
      ALLOWED_VERIFY_ROLES.has(role)
    );
  }, [currentUser, userRoles]);

  useEffect(() => {
    if (!roleChecked) return;

    if (!canVerifyEmail) {
      setStatus('forbidden');
      setErrorMessage(
        'Tài khoản của bạn không có quyền thực hiện xác thực email.'
      );
      return;
    }

    if (!token) {
      setStatus('error');
      setErrorMessage(
        'Mã xác thực (token) không tồn tại hoặc đường dẫn không hợp lệ.'
      );
      return;
    }

    let isMounted = true;

    const verifyToken = async () => {
      setStatus('loading');
      setErrorMessage('');
      try {
        await axiosClient.post('/auth/verify-email', { token });
        if (isMounted) {
          setStatus('success');
        }
      } catch (err) {
        if (isMounted) {
          setStatus('error');
          setErrorMessage(
            err.response?.data?.message ||
              'Xác thực thất bại hoặc liên kết đã hết hạn.'
          );
        }
      }
    };

    verifyToken();

    return () => {
      isMounted = false;
    };
  }, [roleChecked, canVerifyEmail, token]);

  return (
    <div className={`verify-email-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="verify-email-card">
        {status === 'loading' && (
          <div className="spinner-container">
            <div className="verify-spinner"></div>
            <h2 className="verify-title">Đang xác thực email...</h2>
            <p className="verify-desc">
              Hệ thống đang xử lý mã xác nhận của bạn. Vui lòng chờ trong giây lát.
            </p>
          </div>
        )}

        {status === 'success' && (
          <div>
            <div className="verify-icon success">✓</div>
            <h2 className="verify-title">Xác thực thành công!</h2>
            <p className="verify-desc">
              Địa chỉ email của bạn đã được xác nhận thành công. Bạn có thể đóng trang này lại.
            </p>
          </div>
        )}

        {status === 'forbidden' && (
          <div>
            <div className="verify-icon error">✕</div>
            <h2 className="verify-title">Không có quyền truy cập</h2>
            <p className="verify-desc">{errorMessage}</p>
            <button
              type="button"
              className="primary-button"
              onClick={() => navigate('/dashboard')}
            >
              Quay lại Dashboard
            </button>
          </div>
        )}

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