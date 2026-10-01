import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [status, setStatus] = useState('loading'); // 'loading' | 'success' | 'error'
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setErrorMessage('Mã xác thực (token) không tồn tại hoặc đường dẫn không hợp lệ.');
      return;
    }

    const verifyToken = async () => {
      try {
        // Thay đường dẫn API backend của bạn vào đây
        const response = await fetch(`/api/auth/verify-email?token=${token}`, {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
          },
        });

        const data = await response.json();

        if (response.ok) {
          setStatus('success');
        } else {
          setStatus('error');
          setErrorMessage(data.message || 'Xác thực thất bại hoặc liên kết đã hết hạn.');
        }
      } catch (err) {
        setStatus('error');
        setErrorMessage('Không thể kết nối đến máy chủ. Vui lòng thử lại sau.');
      }
    };

    verifyToken();
  }, [token]);

  return (
    <div className="min-h-screen bg-slate-100 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-xl shadow-lg p-8 text-center border border-slate-200">
        
        {/* State 1: Đang xác thực */}
        {status === 'loading' && (
          <div className="space-y-4">
            <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
            <h2 className="text-xl font-bold text-slate-800">Đang xác thực email...</h2>
            <p className="text-sm text-slate-500">
              Hệ thống đang kiểm tra mã xác nhận của bạn. Vui lòng chờ trong giây lát.
            </p>
          </div>
        )}

        {/* State 2: Xác thực thành công */}
        {status === 'success' && (
          <div className="space-y-4">
            <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto text-3xl font-bold">
              ✓
            </div>
            <h2 className="text-2xl font-bold text-slate-800">Xác thực thành công!</h2>
            <p className="text-sm text-slate-600 leading-relaxed">
              Địa chỉ email của bạn đã được xác nhận thành công. Từ bây giờ bạn sẽ nhận được thông báo ngay khi có sự cố mạng xảy ra.
            </p>
            <div className="pt-4">
              <button
                onClick={() => navigate('/')}
                className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition duration-200 shadow-md"
              >
                Về trang chủ / Dashboard
              </button>
            </div>
          </div>
        )}

        {/* State 3: Xác thực thất bại */}
        {status === 'error' && (
          <div className="space-y-4">
            <div className="w-16 h-16 bg-red-100 text-red-600 rounded-full flex items-center justify-center mx-auto text-3xl font-bold">
              ✕
            </div>
            <h2 className="text-2xl font-bold text-slate-800">Xác thực thất bại</h2>
            <p className="text-sm text-slate-600 leading-relaxed">
              {errorMessage}
            </p>
            <div className="pt-4 space-y-2">
              <button
                onClick={() => navigate('/')}
                className="w-full py-3 px-4 bg-slate-800 hover:bg-slate-900 text-white font-medium rounded-lg transition duration-200 shadow-md"
              >
                Quay lại trang chủ
              </button>
              <p className="text-xs text-slate-400 pt-2">
                Bạn có thể đăng nhập vào hệ thống và bấm nút <strong>"Gửi lại email xác thực"</strong> ở thanh thông báo trên trang chủ.
              </p>
            </div>
          </div>
        )}

      </div>
    </div>
  );
};

export default VerifyEmailPage;