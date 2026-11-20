
import axios from 'axios';

const BACKEND_HOST = process.env.REACT_APP_BACKEND_HOST || 'localhost';
const BACKEND_PORT = process.env.REACT_APP_BACKEND_PORT || '5000';

const BACKEND_URL = `http://${BACKEND_HOST}:${BACKEND_PORT}`;
const API_BACKEND_URL = `${BACKEND_URL}/api/v1`;

export const axiosClient = axios.create({
  baseURL: API_BACKEND_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor
axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token');

    // Thêm access token
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Nếu request gửi FormData thì bỏ Content-Type JSON
    // Để trình duyệt tự thiết lập multipart/form-data và boundary
    if (config.data instanceof FormData) {
      if (typeof config.headers.delete === 'function') {
        config.headers.delete('Content-Type');
      } else {
        delete config.headers['Content-Type'];
        delete config.headers['content-type'];
      }
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor
axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refresh_token');

        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        const res = await axios.post(
          `${API_BACKEND_URL}/auth/refresh`,
          { refreshToken }
        );

        // Đọc linh hoạt camelCase và snake_case
        const resData = res.data?.data ?? res.data;

        const newAccessToken =
          resData?.accessToken || resData?.access_token;

        const newRefreshToken =
          resData?.refreshToken || resData?.refresh_token;

        if (!newAccessToken) {
          throw new Error('Failed to refresh token');
        }

        // Lưu token mới
        localStorage.setItem('access_token', newAccessToken);

        if (newRefreshToken) {
          localStorage.setItem('refresh_token', newRefreshToken);
        }

        // Cập nhật token cho request cũ
        originalRequest.headers.Authorization =
          `Bearer ${newAccessToken}`;

        // Gửi lại request ban đầu
        return axiosClient(originalRequest);

      } catch (refreshErr) {
        localStorage.clear();
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);
