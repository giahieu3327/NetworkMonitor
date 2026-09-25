import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../api/axiosClient';

const LoginPage = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setLoading(true);

    try {
      const res = await axiosClient.post('/auth/login', { username, password });
      localStorage.setItem('access_token', res.data.access_token);
      localStorage.setItem('refresh_token', res.data.refresh_token);
      navigate('/dashboard');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Tên đăng nhập hoặc mật khẩu không chính xác!');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center', backgroundColor: '#0f172a', color: '#fff', padding: '16px' }}>
      <form 
        onSubmit={handleLogin} 
        style={{ 
          width: '90%', 
          maxWidth: '400px', 
          padding: '28px 24px', 
          backgroundColor: '#1e293b', 
          borderRadius: '12px', 
          boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.3)',
          boxSizing: 'border-box'
        }}
      >
        <h2 style={{ textAlign: 'center', marginBottom: '20px', fontSize: '20px', fontWeight: 'bold', color: '#38bdf8' }}>
          MONITOR SYSTEM
        </h2>

        {errorMsg && (
          <div style={{ color: '#ef4444', marginBottom: '16px', fontSize: '13px', textAlign: 'center', backgroundColor: 'rgba(239, 68, 68, 0.1)', padding: '8px', borderRadius: '6px' }}>
            {errorMsg}
          </div>
        )}

        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', marginBottom: '6px', fontSize: '13px' }}>Tên đăng nhập</label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            style={inputStyle}
          />
        </div>

        <div style={{ marginBottom: '24px' }}>
          <label style={{ display: 'block', marginBottom: '6px', fontSize: '13px' }}>Mật khẩu</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={inputStyle}
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          style={{ width: '100%', padding: '10px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer', fontSize: '14px' }}
        >
          {loading ? 'Đang đăng nhập...' : 'ĐĂNG NHẬP'}
        </button>
      </form>
    </div>
  );
};

const inputStyle = {
  width: '100%',
  padding: '8px 12px',
  borderRadius: '6px',
  border: '1px solid #334155',
  backgroundColor: '#0f172a',
  color: '#fff',
  boxSizing: 'border-box',
  outline: 'none',
  fontSize: '13px'
};

export default LoginPage;