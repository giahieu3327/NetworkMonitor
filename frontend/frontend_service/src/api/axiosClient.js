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

axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshToken = localStorage.getItem('refresh_token');
        if (!refreshToken) throw new Error('No refresh token');

        const res = await axios.post(`${API_BACKEND_URL}/auth/refresh`, { refreshToken });
        const { access_token, refresh_token: newRefresh } = res.data;

        localStorage.setItem('access_token', access_token);
        if (newRefresh) localStorage.setItem('refresh_token', newRefresh);

        originalRequest.headers.Authorization = `Bearer ${access_token}`;
        return axiosClient(originalRequest);
      } catch (refreshErr) {
        localStorage.clear();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);