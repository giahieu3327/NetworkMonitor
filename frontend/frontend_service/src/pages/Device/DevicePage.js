import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { axiosClient } from '../../api/axiosClient';

import '../../styles/common.css';
import './DevicePage.css';

import TabDeviceInfo from './tabs/TabDeviceInfo';
import TabDeviceCredentials from './tabs/TabDeviceCredentials';
import TabDeviceOidConfig from './tabs/TabDeviceOidConfig';
import TabDeviceInterfaces from './tabs/TabDeviceInterfaces';
import TabDeviceArp from './tabs/TabDeviceArp';
import TabDeviceCpu from './tabs/TabDeviceCpu';
import TabDeviceRam from './tabs/TabDeviceRam';

const TABS = [
  { id: 'info', label: 'Thông tin', icon: 'ℹ️' },
  { id: 'credentials', label: 'Credentials', icon: '🔐' },
  { id: 'oidconfig', label: 'OID Config', icon: '⚙️' },
  { id: 'interfaces', label: 'Interfaces', icon: '🌐' },
  { id: 'arp', label: 'ARP Table', icon: '🔗' },
  { id: 'cpu', label: 'CPU', icon: '🖥️' },
  { id: 'ram', label: 'RAM', icon: '🧠' }
];

const DevicePage = () => {
  const { deviceId } = useParams();
  const navigate = useNavigate();

  const [device, setDevice] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('info');

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia(
      '(prefers-color-scheme: dark)'
    );

    const handleThemeChange = (event) => {
      setIsDarkMode(event.matches);
    };

    mediaQuery.addEventListener('change', handleThemeChange);

    return () => {
      mediaQuery.removeEventListener('change', handleThemeChange);
    };
  }, []);

  useEffect(() => {
    const fetchDevice = async () => {
      try {
        setLoading(true);

        const res = await axiosClient.get(`/devices/${deviceId}`);

        const data = res.data?.data ?? res.data;

        setDevice(data);
      } catch (err) {
        console.error('Lỗi tải thông tin thiết bị:', err);

        alert(
          err.response?.data?.message ||
          'Không tìm thấy thiết bị!'
        );

        navigate('/dashboard');
      } finally {
        setLoading(false);
      }
    };

    if (deviceId) {
      fetchDevice();
    }
  }, [deviceId, navigate]);

  const handleBack = () => {
    navigate('/dashboard');
  };

  const handleTabChange = (tabId) => {
    setActiveTab(tabId);
  };

  const renderTabContent = () => {
    const commonProps = {
      deviceId,
      device,
    };

    switch (activeTab) {
      case 'info':
        return <TabDeviceInfo {...commonProps} />;

      case 'credentials':
        return <TabDeviceCredentials {...commonProps} />;

      case 'oidconfig':
        return <TabDeviceOidConfig {...commonProps} />;

      case 'interfaces':
        return <TabDeviceInterfaces {...commonProps} />;

      case 'arp':
        return <TabDeviceArp {...commonProps} />;

      case 'cpu':
        return <TabDeviceCpu {...commonProps} />;

      case 'ram':
        return <TabDeviceRam {...commonProps} />;

      default:
        return <TabDeviceInfo {...commonProps} />;
    }
  };

  if (loading) {
    return (
      <div
        className={`device-page ${
          isDarkMode ? 'dark-mode' : 'light-mode'
        }`}
      >
        <div className="device-loading">
          <div className="device-loading-spinner" />
          <p>Đang tải thông tin thiết bị...</p>
        </div>
      </div>
    );
  }

  if (!device) {
    return null;
  }

  return (
    <div
      className={`device-page ${
        isDarkMode ? 'dark-mode' : 'light-mode'
      }`}
    >
      <header className="device-header">
        <div className="device-header-left">
          <button
            className="device-back-button"
            onClick={handleBack}
            title="Quay lại Dashboard"
          >
            ←
          </button>

          <div className="device-header-title">
            <h2>DEVICE MANAGEMENT</h2>
            <span>Quản lý chi tiết thiết bị</span>
          </div>
        </div>

        <div className="device-header-right">
          <div className="device-header-status">
            <span
              className={`device-status-dot ${
                device.status === 'UP' ? 'up' : 'down'
              }`}
            />

            {device.status || 'UNKNOWN'}
          </div>

          <button
            className="device-dashboard-button"
            onClick={handleBack}
          >
            🏠 Dashboard
          </button>
        </div>
      </header>

      <div className="device-summary">
        <div className="device-summary-icon">
          🌐
        </div>

        <div className="device-summary-info">
          <h3>{device.deviceName}</h3>

          <div className="device-summary-meta">
            <span>IP: {device.ipAddress}</span>
            <span>•</span>
            <span>{device.deviceType}</span>
            <span>•</span>
            <span>ID: {device.id}</span>
          </div>
        </div>
      </div>

      <div className="device-tab-container">
        <nav className="device-tab-nav">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              className={`device-tab-button ${
                activeTab === tab.id ? 'active' : ''
              }`}
              onClick={() => handleTabChange(tab.id)}
            >
              <span>{tab.icon}</span>
              <span>{tab.label}</span>
            </button>
          ))}
        </nav>
      </div>

      <main className="device-content">
        {renderTabContent()}
      </main>
    </div>
  );
};

export default DevicePage;