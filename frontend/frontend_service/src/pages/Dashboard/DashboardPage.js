import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './DashboardPage.css';

import TabDevices from './tabs/TabDevices';
import TabUsers from './tabs/TabUsers';
import TabTickets from './tabs/TabTickets';

const TICKET_ALLOWED_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_NETWORK_ADMIN',
]);

const USER_ALLOWED_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
]);

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  return trimmed.startsWith('ROLE_') ? trimmed : `ROLE_${trimmed}`;
};

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

  const userRoles = useMemo(() => {
    const roles = new Set();

    if (me?.roleName) {
      roles.add(normalizeRole(me.roleName));
    }

    if (Array.isArray(me?.roles)) {
      me.roles.forEach((role) => {
        const normalized = normalizeRole(role);
        if (normalized) roles.add(normalized);
      });
    }

    return roles;
  }, [me]);

  const canAccessTickets = useMemo(
    () => Array.from(userRoles).some((role) => TICKET_ALLOWED_ROLES.has(role)),
    [userRoles]
  );

  const canAccessUsers = useMemo(
    () => Array.from(userRoles).some((role) => USER_ALLOWED_ROLES.has(role)),
    [userRoles]
  );

  useEffect(() => {
    if (activeTab === 'tickets' && !canAccessTickets) {
      setActiveTab('devices');
    } else if (activeTab === 'users' && !canAccessUsers) {
      setActiveTab('devices');
    }
  }, [activeTab, canAccessTickets, canAccessUsers]);

  const handleProfileClick = () => {
    setIsSidebarOpen(false);
    navigate('/home');
  };

  const handleDevicesTab = () => {
    setActiveTab('devices');
    setIsSidebarOpen(false);
  };

  const handleTicketsTab = () => {
    if (!canAccessTickets) return;
    setActiveTab('tickets');
    setIsSidebarOpen(false);
  };

  const handleUsersTab = () => {
    if (!canAccessUsers) return;
    setActiveTab('users');
    setIsSidebarOpen(false);
  };

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

              {canAccessTickets && (
                <button
                  className={`sidebar-nav-button ${
                    activeTab === 'tickets' ? 'active' : ''
                  }`}
                  onClick={handleTicketsTab}
                >
                  📋 Phê duyệt Ticket
                </button>
              )}

              {canAccessUsers && (
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

      <main className="dashboard-main">
        {activeTab === 'devices' && <TabDevices currentUser={me} />}
        {activeTab === 'tickets' && canAccessTickets && (
          <TabTickets currentUser={me} />
        )}
        {activeTab === 'users' && canAccessUsers && (
          <TabUsers currentUser={me} />
        )}
      </main>
    </div>
  );
};

export default DashboardPage;