import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './DashboardPage.css';

import TabDevices from './tabs/TabDevices';
import TabUsers from './tabs/TabUsers';

const DashboardPage = () => {
  const [me, setMe] = useState(null);
  const [activeTab, setActiveTab] = useState('devices');
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  const navigate = useNavigate();

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (event) => setIsDarkMode(event.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  useEffect(() => {
    const fetchMe = async () => {
      try {
        const res = await axiosClient.get('/profile/me');
        const data = res.data?.data ?? res.data;
        setMe(data);
      } catch (err) {
        navigate('/login');
      }
    };

    fetchMe();
  }, [navigate]);

  const handleProfileClick = () => {
    setIsSidebarOpen(false);
    navigate('/home');
  };

  const handleDevicesTab = () => {
    setActiveTab('devices');
    setIsSidebarOpen(false);
  };

  const handleUsersTab = () => {
    setActiveTab('users');
    setIsSidebarOpen(false);
  };

  const isSuperAdmin =
    me?.roleName === 'ROLE_SUPER_ADMIN' ||
    me?.roleName === 'SUPER_ADMIN';

  const username = me?.username || 'User';

  return (
    <div className={`dashboard-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="dashboard-header">
        <div className="dashboard-header-left">
          <button
            className="menu-button"
            onClick={() => setIsSidebarOpen(true)}
            title="Mở menu điều hướng"
          >
            ☰
          </button>

          <h2 className="dashboard-title">MONITOR SYSTEM DASHBOARD</h2>
        </div>

        <div className="dashboard-header-right">
          <button
            className="profile-button"
            onClick={handleProfileClick}
            title="Xem thông tin tài khoản"
          >
            <span>👤</span>
            <span>{username}</span>
          </button>
        </div>
      </header>

      {/* Sidebar Navigation */}
      {isSidebarOpen && (
        <div className="sidebar-overlay">
          <div
            className="sidebar-overlay-background"
            onClick={() => setIsSidebarOpen(false)}
          />

          <aside className="sidebar">
            <div className="sidebar-header">
              <h3>DANH MỤC MENU</h3>

              <button
                className="sidebar-close-button"
                onClick={() => setIsSidebarOpen(false)}
              >
                ✕
              </button>
            </div>

            <nav className="sidebar-nav">
              <button
                className={`sidebar-nav-button ${
                  activeTab === 'devices' ? 'active' : ''
                }`}
                onClick={handleDevicesTab}
              >
                🌐 Quản lý thiết bị
              </button>

              {/* Chỉ hiển thị tab Quản lý người dùng cho ROLE_SUPER_ADMIN */}
              {isSuperAdmin && (
                <button
                  className={`sidebar-nav-button ${
                    activeTab === 'users' ? 'active' : ''
                  }`}
                  onClick={handleUsersTab}
                >
                  👥 Quản lý người dùng
                </button>
              )}
            </nav>
          </aside>
        </div>
      )}

      {/* Main Container Render Dynamic Tabs */}
      <main className="dashboard-main">
        {activeTab === 'devices' && <TabDevices />}
        {activeTab === 'users' && <TabUsers currentUser={me} />}
      </main>
    </div>
  );
};

export default DashboardPage;