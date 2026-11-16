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

const TabDeviceCpu = ({ deviceId }) => {
  const [latestData, setLatestData] = useState(null);
  const [historyData, setHistoryData] = useState([]);
  const [selectedSlot, setSelectedSlot] = useState('');
  const [loading, setLoading] = useState(true);

  const fetchMetrics = useCallback(async (isInitial = false) => {
    if (!deviceId) return;
    if (isInitial) setLoading(true);

    try {
      const [latestRes, historyRes] = await Promise.all([
        axiosClient.get(`/metrics/${deviceId}/latest`),
        axiosClient.get(`/metrics/${deviceId}/cpu/history`),
      ]);

      const latest = latestRes.data?.data ?? latestRes.data ?? null;
      const history = historyRes.data?.data ?? historyRes.data ?? [];

      setLatestData(latest);
      setHistoryData(Array.isArray(history) ? history : []);
    } catch (err) {
      console.error('Lỗi tải dữ liệu CPU:', err);
      if (isInitial) {
        setLatestData(null);
        setHistoryData([]);
      }
    } finally {
      if (isInitial) setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
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
  }, [fetchMetrics]);

  const availableSlots = useMemo(() => {
    const cpuVal = latestData?.cpu_usage?.value;
    if (cpuVal && typeof cpuVal === 'object' && !Array.isArray(cpuVal)) {
      return Object.keys(cpuVal);
    }
    return [];
  }, [latestData]);

  useEffect(() => {
    if (availableSlots.length > 0 && !availableSlots.includes(selectedSlot)) {
      setSelectedSlot(availableSlots[0]);
    } else if (availableSlots.length === 0 && selectedSlot !== '') {
      setSelectedSlot('');
    }
  }, [availableSlots, selectedSlot]);

  const tableRows = useMemo(() => {
    if (!latestData) return [];

    const rows = [];
    const cpuVal = latestData.cpu_usage?.value;
    const coreCount = latestData.core_count?.value;
    const coreUsage = latestData.core_usage?.value;

    if (availableSlots.length > 0) {
      const activeSlot = selectedSlot || availableSlots[0];
      const slotCpuUsage = cpuVal?.[activeSlot];
      rows.push({
        key: `CPU Usage (Slot ${activeSlot})`,
        value: slotCpuUsage !== undefined ? `${slotCpuUsage}%` : '—',
      });

      if (coreCount !== undefined) {
        rows.push({
          key: 'Total Core Count',
          value: String(coreCount),
        });
      }

      const slotCores = coreUsage?.[activeSlot];
      if (slotCores && typeof slotCores === 'object') {
        Object.entries(slotCores).forEach(([coreIdx, val]) => {
          rows.push({
            key: `Core ${coreIdx}`,
            value: `${val}%`,
          });
        });
      }
    } else {
      if (cpuVal !== undefined && cpuVal !== null) {
        rows.push({
          key: 'CPU Usage',
          value: `${cpuVal}%`,
        });
      }

      if (coreCount !== undefined && coreCount !== null) {
        rows.push({
          key: 'Core Count',
          value: String(coreCount),
        });
      }

      if (coreUsage && typeof coreUsage === 'object') {
        Object.entries(coreUsage).forEach(([coreIdx, val]) => {
          rows.push({
            key: `Core ${coreIdx}`,
            value: `${val}%`,
          });
        });
      }
    }

    if (latestData.cpu_usage?.timestamp) {
      rows.push({
        key: 'Cập nhật lúc',
        value: new Date(latestData.cpu_usage.timestamp).toLocaleString('vi-VN'),
      });
    }

    return rows;
  }, [latestData, availableSlots, selectedSlot]);

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

      const rawCpu = item.metricData?.cpu_usage;
      let numericValue = 0;

      if (rawCpu && typeof rawCpu === 'object') {
        const slotKey = selectedSlot || Object.keys(rawCpu)[0];
        numericValue = Number(rawCpu[slotKey] ?? 0);
      } else {
        numericValue = Number(rawCpu ?? 0);
      }

      valueMap.set(bucket, Number.isNaN(numericValue) ? 0 : numericValue);
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
  }, [historyData, selectedSlot]);

  if (loading) {
    return (
      <div className="device-info-loading">
        Đang tải dữ liệu CPU...
      </div>
    );
  }

  return (
    <section className="metric-tab-container">
      {availableSlots.length > 0 && (
        <div className="metric-toolbar">
          <label htmlFor="cpu-slot-select" className="metric-slot-label">
            Slot:
          </label>
          <select
            id="cpu-slot-select"
            className="metric-slot-select"
            value={selectedSlot}
            onChange={(e) => setSelectedSlot(e.target.value)}
          >
            {availableSlots.map((slot) => (
              <option key={slot} value={slot}>
                {slot}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="metric-split-layout">
        <div className="metric-table-panel">
          <h4 className="metric-panel-title">Thông số CPU hiện tại</h4>
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
                    Chưa có dữ liệu CPU cho thiết bị này.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="metric-chart-panel">
          <h4 className="metric-panel-title">
            Biểu đồ lịch sử CPU (%)
            {availableSlots.length > 0 && selectedSlot ? ` - Slot ${selectedSlot}` : ''}
          </h4>

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
                    formatter={(val) => [`${val}%`, 'CPU Usage']}
                    labelFormatter={(_, payload) =>
                      payload?.[0]?.payload?.fullTime || ''
                    }
                  />
                  <Line
                    type="monotone"
                    dataKey="value"
                    stroke="#3b82f6"
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
              Chưa có dữ liệu lịch sử CPU.
            </div>
          )}
        </div>
      </div>
    </section>
  );
};

export default TabDeviceCpu;