import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { axiosClient } from '../../../api/axiosClient';
import './TabDeviceMetrics.css';

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

const formatTimeMinute = (dateObj) => {
  const hh = String(dateObj.getHours()).padStart(2, '0');
  const mm = String(dateObj.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
};

const getMinuteBucketMs = (isoString) => {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return null;
  date.setSeconds(0, 0);
  return date.getTime();
};

const getMsUntilNext15thSecond = () => {
  const now = new Date();
  const target = new Date(now);
  target.setSeconds(15, 0);
  if (now.getSeconds() >= 15) {
    target.setMinutes(target.getMinutes() + 1);
  }
  return target.getTime() - now.getTime();
};

const formatMemoryKB = (kbValue) => {
  if (kbValue === null || kbValue === undefined || Number.isNaN(Number(kbValue))) {
    return '—';
  }
  const num = Number(kbValue);
  const mb = num / 1024;
  if (mb >= 1024) {
    return `${(mb / 1024).toFixed(2)} GB (${num.toLocaleString('vi-VN')} KB)`;
  }
  return `${mb.toFixed(2)} MB (${num.toLocaleString('vi-VN')} KB)`;
};

const TabDeviceRam = ({ deviceId, currentUser }) => {
  const userRoles = useMemo(() => {
    const roles = new Set();

    if (currentUser?.roleName) {
      roles.add(normalizeRole(currentUser.roleName));
    }

    if (Array.isArray(currentUser?.roles)) {
      currentUser.roles.forEach((role) => {
        const normalized = normalizeRole(role);
        if (normalized) roles.add(normalized);
      });
    }

    return roles;
  }, [currentUser]);

  const canViewRam = useMemo(() => {
    if (!currentUser) return true;
    return Array.from(userRoles).some((role) => ALLOWED_VIEW_ROLES.has(role));
  }, [currentUser, userRoles]);

  const [latestData, setLatestData] = useState(null);
  const [historyData, setHistoryData] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchMetrics = useCallback(async (isInitial = false) => {
    if (!deviceId || !canViewRam) return;
    if (isInitial) setLoading(true);

    try {
      const [latestRes, historyRes] = await Promise.all([
        axiosClient.get(`/metrics/${deviceId}/latest`),
        axiosClient.get(`/metrics/${deviceId}/ram/history`),
      ]);

      const latest = latestRes.data?.data ?? latestRes.data ?? null;
      const history = historyRes.data?.data ?? historyRes.data ?? [];

      setLatestData(latest);
      setHistoryData(Array.isArray(history) ? history : []);
    } catch (err) {
      console.error('Lỗi tải dữ liệu RAM:', err);
      if (isInitial) {
        setLatestData(null);
        setHistoryData([]);
      }
    } finally {
      if (isInitial) setLoading(false);
    }
  }, [deviceId, canViewRam]);

  useEffect(() => {
    if (!canViewRam) {
      setLoading(false);
      return;
    }

    fetchMetrics(true);

    let intervalId = null;
    const timeoutId = setTimeout(() => {
      fetchMetrics(false);
      intervalId = setInterval(() => {
        fetchMetrics(false);
      }, 60 * 1000);
    }, getMsUntilNext15thSecond());

    return () => {
      clearTimeout(timeoutId);
      if (intervalId) clearInterval(intervalId);
    };
  }, [fetchMetrics, canViewRam]);

  const tableRows = useMemo(() => {
    if (!latestData) return [];

    const rows = [];

    if (latestData.ram_usage_percent?.value !== undefined) {
      rows.push({
        key: 'RAM Usage (%)',
        value: `${latestData.ram_usage_percent.value}%`,
      });
    }

    if (latestData.ram_capacity?.value !== undefined) {
      rows.push({
        key: 'RAM Capacity',
        value: formatMemoryKB(latestData.ram_capacity.value),
      });
    }

    if (latestData.ram_usage?.value !== undefined) {
      rows.push({
        key: 'RAM Used',
        value: formatMemoryKB(latestData.ram_usage.value),
      });
    }

    if (latestData.ram_free?.value !== undefined) {
      rows.push({
        key: 'RAM Free',
        value: formatMemoryKB(latestData.ram_free.value),
      });
    }

    if (latestData.ram_usage_percent?.timestamp) {
      rows.push({
        key: 'Cập nhật lúc',
        value: new Date(latestData.ram_usage_percent.timestamp).toLocaleString('vi-VN'),
      });
    }

    return rows;
  }, [latestData]);

  const chartData = useMemo(() => {
    if (!Array.isArray(historyData) || historyData.length === 0) return [];

    const valueMap = new Map();
    let minBucket = Infinity;
    let maxBucket = -Infinity;

    historyData.forEach((item) => {
      const bucket = getMinuteBucketMs(item.timestamp);
      if (bucket === null) return;

      if (bucket < minBucket) minBucket = bucket;
      if (bucket > maxBucket) maxBucket = bucket;

      const rawPercent = Number(item.metricData?.ram_usage_percent ?? 0);
      valueMap.set(bucket, Number.isNaN(rawPercent) ? 0 : rawPercent);
    });

    if (minBucket === Infinity || maxBucket === -Infinity) return [];

    const fifteenMinutesWindowStart = maxBucket - 14 * 60 * 1000;
    const startBucket = Math.min(minBucket, fifteenMinutesWindowStart);

    const filledPoints = [];
    for (let ts = startBucket; ts <= maxBucket; ts += 60 * 1000) {
      const dateObj = new Date(ts);
      filledPoints.push({
        timestamp: ts,
        time: formatTimeMinute(dateObj),
        fullTime: dateObj.toLocaleString('vi-VN'),
        value: valueMap.has(ts) ? valueMap.get(ts) : 0,
      });
    }

    return filledPoints;
  }, [historyData]);

  if (!canViewRam) {
    return (
      <div className="permission-message">
        Bạn không có quyền xem thông số RAM của thiết bị này.
      </div>
    );
  }

  if (loading) {
    return (
      <div className="device-info-loading">
        Đang tải dữ liệu RAM...
      </div>
    );
  }

  return (
    <section className="metric-tab-container">
      <div className="metric-split-layout">
        <div className="metric-table-panel">
          <h4 className="metric-panel-title">Thông số RAM hiện tại</h4>
          <table className="metric-kv-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {tableRows.length > 0 ? (
                tableRows.map((row) => (
                  <tr key={row.key}>
                    <td className="metric-key-cell">{row.key}</td>
                    <td className="metric-val-cell">{row.value}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="2" className="metric-empty-cell">
                    Chưa có dữ liệu RAM cho thiết bị này.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="metric-chart-panel">
          <h4 className="metric-panel-title">Biểu đồ lịch sử RAM (%)</h4>

          {chartData.length > 0 ? (
            <div className="metric-chart-wrapper">
              <ResponsiveContainer width="100%" height={340}>
                <LineChart
                  data={chartData}
                  margin={{ top: 12, right: 20, left: 0, bottom: 8 }}
                >
                  <CartesianGrid strokeDasharray="3 3" opacity={0.25} />
                  <XAxis
                    dataKey="time"
                    tick={{ fontSize: 12 }}
                    minTickGap={12}
                  />
                  <YAxis
                    domain={[0, 100]}
                    unit="%"
                    tick={{ fontSize: 12 }}
                    width={48}
                  />
                  <Tooltip
                    formatter={(val) => [`${val}%`, 'RAM Usage']}
                    labelFormatter={(_, payload) =>
                      payload?.[0]?.payload?.fullTime || ''
                    }
                  />
                  <Line
                    type="monotone"
                    dataKey="value"
                    stroke="#10b981"
                    strokeWidth={2.5}
                    dot={{ r: 3 }}
                    activeDot={{ r: 5 }}
                    isAnimationActive={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="metric-chart-empty">
              Chưa có dữ liệu lịch sử RAM.
            </div>
          )}
        </div>
      </div>
    </section>
  );
};

export default TabDeviceRam;