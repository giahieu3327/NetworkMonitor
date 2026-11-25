import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { axiosClient } from '../../api/axiosClient';

import '../../styles/common.css';
import './DevicePage.css';

import TabDeviceInfo from './tabs/TabDeviceInfo';
import TabDeviceCredentials from './tabs/TabDeviceCredentials';
import TabDeviceOidConfig from './tabs/TabDeviceOidConfig';
import TabDeviceRule from './tabs/TabDeviceRule';
import TabDeviceIncident from './tabs/TabDeviceIncident';
import TabDeviceInterfaces from './tabs/TabDeviceInterfaces';
import TabDeviceArp from './tabs/TabDeviceArp';
import TabDeviceCpu from './tabs/TabDeviceCpu';
import TabDeviceRam from './tabs/TabDeviceRam';

const TABS = [
  { id: 'info', label: 'Thông tin', icon: 'ℹ️' },
  { id: 'credentials', label: 'Credentials', icon: '🔐' },
  { id: 'oidconfig', label: 'OID Config', icon: '⚙️' },
  { id: 'rules', label: 'Threshold Rules', icon: '🚨' },
  { id: 'incidents', label: 'Incidents', icon: '⚠️' },
  { id: 'interfaces', label: 'Interfaces', icon: '🌐' },
  { id: 'arp', label: 'ARP Table', icon: '🔗' },
  { id: 'cpu', label: 'CPU', icon: '🖥️' },
  { id: 'ram', label: 'RAM', icon: '🧠' }
];

const DEVICE_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
]);

const INCIDENT_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
]);

const REPORT_EXPORT_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_REPORT_MANAGER',
]);

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  return trimmed.startsWith('ROLE_') ? trimmed : `ROLE_${trimmed}`;
};

const formatDateInput = (dateObj) => {
  const year = dateObj.getFullYear();
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const DevicePage = () => {
  const { deviceId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  const [me, setMe] = useState(null);
  const [device, setDevice] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState(
    location.state?.activeTab || 'info'
  );

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  const today = new Date();
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(today.getDate() - 6);

  const maxDateStr = formatDateInput(today);
  const minDateStr = formatDateInput(sevenDaysAgo);

  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [reportType, setReportType] = useState('incidents');
  const [reportDate, setReportDate] = useState(maxDateStr);
  const [reportFormat, setReportFormat] = useState('pdf');
  const [isExporting, setIsExporting] = useState(false);

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

  const canManageDevices = useMemo(
    () => Array.from(userRoles).some((role) => DEVICE_MANAGE_ROLES.has(role)),
    [userRoles]
  );

  const canManageIncidents = useMemo(
    () => Array.from(userRoles).some((role) => INCIDENT_MANAGE_ROLES.has(role)),
    [userRoles]
  );

  const canExportReports = useMemo(
    () => Array.from(userRoles).some((role) => REPORT_EXPORT_ROLES.has(role)),
    [userRoles]
  );

  const isGuestViewer = useMemo(
    () =>
      userRoles.size === 0 ||
      (userRoles.size === 1 && userRoles.has('ROLE_GUEST_VIEWER')),
    [userRoles]
  );

  useEffect(() => {
    if (location.state?.activeTab) {
      setActiveTab(location.state.activeTab);
    }
  }, [location.state?.activeTab]);

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

  const handleOpenExportModal = () => {
    if (!canExportReports) return;
    setReportDate(maxDateStr);
    setIsExportModalOpen(true);
  };

  const handleCloseExportModal = () => {
    if (!isExporting) {
      setIsExportModalOpen(false);
    }
  };

  const handleExportReport = async (e) => {
    e.preventDefault();

    if (!canExportReports) return;

    if (reportDate < minDateStr || reportDate > maxDateStr) {
      alert(`Vui lòng chọn ngày trong khoảng từ ${minDateStr} đến ${maxDateStr}.`);
      return;
    }

    const targetDeviceId = device?.id ?? deviceId;
    const endpoint =
      reportType === 'incidents'
        ? '/reports/incidents/daily/export'
        : '/reports/metrics/1d/export';

    try {
      setIsExporting(true);

      const response = await axiosClient.get(endpoint, {
        params: {
          deviceId: targetDeviceId,
          date: reportDate,
          format: reportFormat,
        },
        responseType: 'blob',
      });

      const contentDisposition = response.headers?.['content-disposition'];
      let fileName = `${
        reportType === 'incidents' ? 'incident-report' : 'cpu-memory-report'
      }-device-${targetDeviceId}-${reportDate}.${reportFormat}`;

      if (contentDisposition) {
        const fileNameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
        if (fileNameMatch && fileNameMatch[1]) {
          fileName = fileNameMatch[1].replace(/['"]/g, '');
        }
      }

      const blob = new Blob([response.data], {
        type:
          reportFormat === 'pdf'
            ? 'application/pdf'
            : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });

      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', fileName);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);

      setIsExportModalOpen(false);
    } catch (err) {
      console.error('Lỗi xuất báo cáo:', err);
      alert('Xuất báo cáo thất bại. Vui lòng thử lại!');
    } finally {
      setIsExporting(false);
    }
  };

  const renderTabContent = () => {
    const commonProps = {
      deviceId,
      device,
      currentUser: me,
      canManageDevices,
      canManageIncidents,
      canExportReports,
      isGuestViewer,
    };

    switch (activeTab) {
      case 'info':
        return <TabDeviceInfo {...commonProps} />;

      case 'credentials':
        return <TabDeviceCredentials {...commonProps} />;

      case 'oidconfig':
        return <TabDeviceOidConfig {...commonProps} />;

      case 'rules':
        return <TabDeviceRule {...commonProps} />;

      case 'incidents':
        return <TabDeviceIncident {...commonProps} />;

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
        <div className="device-summary-left">
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

        {canExportReports && (
          <div className="device-summary-actions">
            <button
              type="button"
              className="device-export-button"
              onClick={handleOpenExportModal}
            >
              📄 Xuất báo cáo
            </button>
          </div>
        )}
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

      {canExportReports && isExportModalOpen && (
        <div
          className="export-modal-overlay"
          onClick={handleCloseExportModal}
        >
          <div
            className="export-modal-content"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="export-modal-header">
              <h3>Xuất báo cáo thiết bị</h3>
              <button
                type="button"
                className="export-modal-close"
                onClick={handleCloseExportModal}
                disabled={isExporting}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleExportReport} className="export-modal-body">
              <div className="export-form-group">
                <label>Loại báo cáo</label>
                <div className="export-radio-cards">
                  <label
                    className={`export-radio-card ${
                      reportType === 'incidents' ? 'selected' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="reportType"
                      value="incidents"
                      checked={reportType === 'incidents'}
                      onChange={(e) => setReportType(e.target.value)}
                    />
                    <div className="export-radio-card-info">
                      <strong>DAILY INCIDENT SUMMARY REPORT</strong>
                      <span>
                        Date: {reportDate} | Scope: Device ID #{device.id}
                      </span>
                    </div>
                  </label>

                  <label
                    className={`export-radio-card ${
                      reportType === 'metrics' ? 'selected' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="reportType"
                      value="metrics"
                      checked={reportType === 'metrics'}
                      onChange={(e) => setReportType(e.target.value)}
                    />
                    <div className="export-radio-card-info">
                      <strong>DEVICE CPU &amp; MEMORY USAGE REPORT (1D)</strong>
                      <span>
                        Device: {device.deviceName} (#{device.id}) | Date: {reportDate}
                      </span>
                    </div>
                  </label>
                </div>
              </div>

              <div className="export-form-row">
                <div className="export-form-group">
                  <label htmlFor="report-date">
                    Ngày báo cáo (tối đa 7 ngày gần nhất)
                  </label>
                  <input
                    id="report-date"
                    type="date"
                    value={reportDate}
                    min={minDateStr}
                    max={maxDateStr}
                    onChange={(e) => setReportDate(e.target.value)}
                    required
                  />
                </div>

                <div className="export-form-group">
                  <label htmlFor="report-format">Định dạng tệp</label>
                  <select
                    id="report-format"
                    value={reportFormat}
                    onChange={(e) => setReportFormat(e.target.value)}
                  >
                    <option value="pdf">PDF (.pdf)</option>
                    <option value="xlsx">Excel (.xlsx)</option>
                  </select>
                </div>
              </div>

              <div className="export-modal-footer">
                <button
                  type="button"
                  className="export-btn-cancel"
                  onClick={handleCloseExportModal}
                  disabled={isExporting}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="export-btn-submit"
                  disabled={isExporting}
                >
                  {isExporting ? 'Đang xuất...' : 'Tải xuống báo cáo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default DevicePage;