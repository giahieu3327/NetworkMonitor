import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './InterfacePage.css';

const ALLOWED_VIEW_ROLES = new Set([
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

const INTERFACE_TYPE_MAP = {
  1: 'Khác (Other)',
  6: 'Ethernet',
  7: 'Ethernet (802.3)',
  22: 'Point-to-Point Serial',
  23: 'PPP',
  24: 'Loopback',
  32: 'Frame Relay',
  53: 'Virtual / VLAN',
  131: 'Tunnel',
  135: 'Layer 2 VLAN',
  136: 'Layer 3 IP VLAN',
  161: 'IEEE 802.3ad LAG (Port-Channel)',
};

const formatInterfaceType = (type) => {
  if (type === null || type === undefined || type === '') return 'UNKNOWN';
  return INTERFACE_TYPE_MAP[type] || `UNKNOWN (${type})`;
};

const formatSpeedBps = (speedBps) => {
  if (speedBps === null || speedBps === undefined || speedBps === '') return '—';

  const speed = Number(speedBps);
  if (Number.isNaN(speed)) return String(speedBps);
  if (speed === 0) return '0 bps';

  const units = ['bps', 'Kbps', 'Mbps', 'Gbps', 'Tbps'];
  const unitIndex = Math.min(
    Math.floor(Math.log10(Math.abs(speed)) / 3),
    units.length - 1
  );
  const value = speed / Math.pow(1000, unitIndex);
  const formattedValue = Number.isInteger(value)
    ? value
    : Number(value.toFixed(2));

  return `${formattedValue} ${units[unitIndex]}`;
};

const formatDateTime = (isoString) => {
  if (!isoString) return '—';

  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;

  return date.toLocaleString('vi-VN');
};

const getResponseData = (response) => {
  return response?.data?.data ?? response?.data ?? {};
};

const InterfacePage = ({ currentUser }) => {
  const { deviceId, interfaceIndex } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const effectiveUser = currentUser || location.state?.currentUser || null;

  const userRoles = useMemo(() => {
    const roles = new Set();

    if (effectiveUser?.roleName) {
      roles.add(normalizeRole(effectiveUser.roleName));
    }

    if (Array.isArray(effectiveUser?.roles)) {
      effectiveUser.roles.forEach((role) => {
        const normalized = normalizeRole(role);
        if (normalized) roles.add(normalized);
      });
    }

    return roles;
  }, [effectiveUser]);

  const canViewInterface = useMemo(() => {
    if (!effectiveUser) return true;
    return Array.from(userRoles).some((role) => ALLOWED_VIEW_ROLES.has(role));
  }, [effectiveUser, userRoles]);

  const [interfaceData, setInterfaceData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  const fetchInterfaceDetail = useCallback(async () => {
    if (!canViewInterface) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setErrorMsg('');

      const response = await axiosClient.get(
        `/devices/${deviceId}/interfaces/${interfaceIndex}`
      );
      const data = getResponseData(response);

      setInterfaceData(data);
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Không thể tải thông tin interface!'
      );
    } finally {
      setLoading(false);
    }
  }, [deviceId, interfaceIndex, canViewInterface]);

  useEffect(() => {
    if (deviceId && interfaceIndex !== undefined) {
      fetchInterfaceDetail();
    }
  }, [deviceId, interfaceIndex, fetchInterfaceDetail]);

  const handleBack = () => {
    if (deviceId) {
      navigate(`/devices/${deviceId}`, {
        state: { activeTab: 'interfaces' }
      });
    } else {
      navigate(-1);
    }
  };

  const renderStatusBadge = (status) => {
    const normalized = String(status || '').toUpperCase();

    if (normalized === 'UP') {
      return (
        <div className="iface-status-badge up">
          ● Hoạt động (UP)
        </div>
      );
    }

    if (normalized === 'DOWN') {
      return (
        <div className="iface-status-badge down">
          ● Ngắt kết nối (DOWN)
        </div>
      );
    }

    return (
      <div className="iface-status-badge unknown">
        ● Không xác định ({normalized || 'UNKNOWN'})
      </div>
    );
  };

  if (!canViewInterface) {
    return (
      <div className={`iface-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <header className="iface-header">
          <div className="iface-header-left">
            <button
              type="button"
              className="iface-back-button"
              onClick={handleBack}
              title="Quay lại"
            >
              ←
            </button>
            <h2>CHI TIẾT NETWORK INTERFACE</h2>
          </div>
        </header>
        <main className="iface-main">
          <div className="iface-card">
            <div className="permission-message">
              Bạn không có quyền xem thông tin interface của thiết bị này.
            </div>
            <div className="iface-actions">
              <button
                type="button"
                className="iface-cancel-btn"
                onClick={handleBack}
              >
                Quay lại
              </button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (loading) {
    return (
      <div className={`auth-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="button-loading-state" style={{ color: 'var(--primary)' }}>
          <span
            className="spinner"
            style={{ borderColor: 'var(--primary)', borderTopColor: 'transparent' }}
          />
          Đang tải thông tin interface...
        </div>
      </div>
    );
  }

  return (
    <div className={`iface-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="iface-header">
        <div className="iface-header-left">
          <button
            type="button"
            className="iface-back-button"
            onClick={handleBack}
            title="Quay lại"
          >
            ←
          </button>
          <h2>CHI TIẾT NETWORK INTERFACE</h2>
        </div>
      </header>

      <main className="iface-main">
        {!interfaceData ? (
          <div className="iface-card">
            <div className="iface-alert error">
              <span>{errorMsg || 'Không tìm thấy thông tin interface.'}</span>
            </div>
            <div className="iface-actions">
              <button
                type="button"
                className="iface-cancel-btn"
                onClick={handleBack}
              >
                Quay lại
              </button>
            </div>
          </div>
        ) : (
          <div className="iface-card">
            <div className="iface-card-header">
              <div className="iface-avatar">
                #{interfaceData.interfaceIndex ?? interfaceIndex}
              </div>

              <div className="iface-header-info">
                <h3>
                  {interfaceData.interfaceName || `Interface #${interfaceIndex}`}
                </h3>
                <p>
                  Index: #{interfaceData.interfaceIndex ?? interfaceIndex} ·{' '}
                  {formatInterfaceType(interfaceData.interfaceType)}
                </p>
              </div>
            </div>

            {errorMsg && (
              <div className="iface-alert error">
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="iface-grid">
              <div className="iface-field">
                <label>Interface Index</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.interfaceIndex ?? '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Tên Interface</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.interfaceName || '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Mô tả / Alias</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.interfaceAlias || '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Loại Interface</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={formatInterfaceType(interfaceData.interfaceType)}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Địa chỉ IP</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.ipAddress || '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Subnet Mask</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.subnetMask || '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Địa chỉ MAC (Physical Address)</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.physAddress || '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Tốc độ băng thông</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={formatSpeedBps(interfaceData.speedBps)}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>MTU (Bytes)</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={interfaceData.mtu ?? '—'}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Cập nhật lần cuối</label>
                <input
                  type="text"
                  className="iface-input-box"
                  value={formatDateTime(interfaceData.updatedAt)}
                  readOnly
                />
              </div>

              <div className="iface-field">
                <label>Trạng thái quản trị (Admin Status)</label>
                {renderStatusBadge(interfaceData.adminStatus)}
              </div>

              <div className="iface-field">
                <label>Trạng thái vận hành (Oper Status)</label>
                {renderStatusBadge(interfaceData.operStatus)}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default InterfacePage;