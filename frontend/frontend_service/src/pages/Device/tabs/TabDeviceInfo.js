import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { axiosClient } from '../../../api/axiosClient';

import './TabDeviceInfo.css';

const parseUptimeToSeconds = (uptime) => {
    if (!uptime || typeof uptime !== 'string') return 0;

    const match = uptime.match(
        /(?:(\d+)\s+days?,?\s*)?(\d+):(\d+):(\d+(?:\.\d+)?)/
    );

    if (!match) return 0;

    const days = Number(match[1] || 0);
    const hours = Number(match[2] || 0);
    const minutes = Number(match[3] || 0);
    const seconds = Number(match[4] || 0);

    return days * 86400 + hours * 3600 + minutes * 60 + seconds;
};

const formatUptime = (totalSeconds) => {
    const total = Math.max(0, Math.floor(totalSeconds || 0));

    const days = Math.floor(total / 86400);
    const hours = Math.floor((total % 86400) / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;

    return `${days} ngày, ${String(hours).padStart(2, '0')}:${String(
        minutes
    ).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
};

const formatDate = (value) => {
    if (!value) return 'N/A';

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) return value;

    return date.toLocaleString('vi-VN');
};

const formatLabel = (value) => {
    return String(value)
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/^./, (char) => char.toUpperCase());
};

const getDelayToNextMinuteAtSecond15 = () => {
    const now = new Date();

    const next = new Date(now);
    next.setSeconds(15, 0);

    if (next.getTime() <= now.getTime()) {
        next.setMinutes(next.getMinutes() + 1);
    }

    return next.getTime() - now.getTime();
};

const TabDeviceInfo = ({ deviceId, device }) => {
    const navigate = useNavigate();

    const [deviceInfo, setDeviceInfo] = useState(device || null);
    const [loading, setLoading] = useState(true);
    const [basicError, setBasicError] = useState('');

    const [snmpInfo, setSnmpInfo] = useState(null);
    const [snmpAvailable, setSnmpAvailable] = useState(false);

    const [uptimeBase, setUptimeBase] = useState(0);
    const [uptimeReceivedAt, setUptimeReceivedAt] = useState(null);
    const [currentUptime, setCurrentUptime] = useState(0);

    const [updating, setUpdating] = useState(false);
    const [deleting, setDeleting] = useState(false);

    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const [showEditModal, setShowEditModal] = useState(false);

    const [formData, setFormData] = useState({
        deviceName: '',
        ipAddress: '',
        deviceType: '',
    });

    const fetchDevice = useCallback(async (signal) => {
        if (!deviceId) return;

        try {
            setBasicError('');

            const response = await axiosClient.get(
                `/devices/${deviceId}`,
                { signal }
            );

            const data = response.data?.data;

            if (!response.data?.success || !data) {
                throw new Error(
                    response.data?.message ||
                    'Không thể lấy thông tin thiết bị.'
                );
            }

            setDeviceInfo(data);
        } catch (err) {
            if (
                err.name === 'CanceledError' ||
                err.name === 'AbortError'
            ) {
                return;
            }

            console.error('Fetch device error:', err);

            setBasicError(
                err.response?.data?.message ||
                err.message ||
                'Không thể tải thông tin thiết bị.'
            );
        } finally {
            setLoading(false);
        }
    }, [deviceId]);

    const fetchSnmpInfo = useCallback(async (signal) => {
        if (!deviceId) return;

        try {
            const response = await axiosClient.get(
                `/devices/${deviceId}/info`,
                { signal }
            );

            const responseBody = response.data;

            if (
                responseBody?.success !== true ||
                !responseBody?.data
            ) {
                setSnmpAvailable(false);
                setSnmpInfo(null);
                setUptimeReceivedAt(null);
                return;
            }

            const data = responseBody.data;
            const snmp = data.snmpInfo ?? data;

            if (
                !snmp ||
                typeof snmp !== 'object' ||
                Array.isArray(snmp) ||
                Object.keys(snmp).length === 0
            ) {
                setSnmpAvailable(false);
                setSnmpInfo(null);
                setUptimeReceivedAt(null);
                return;
            }

            setSnmpInfo(snmp);
            setSnmpAvailable(true);

            const uptimeValue =
                snmp['System Uptime'] ??
                snmp.systemUptime ??
                '';

            if (uptimeValue) {
                const uptimeSeconds = parseUptimeToSeconds(
                    uptimeValue
                );

                setUptimeBase(uptimeSeconds);
                setUptimeReceivedAt(Date.now());
                setCurrentUptime(uptimeSeconds);
            } else {
                setUptimeBase(0);
                setUptimeReceivedAt(null);
                setCurrentUptime(0);
            }
        } catch (err) {
            if (
                err.name === 'CanceledError' ||
                err.name === 'AbortError'
            ) {
                return;
            }

            console.warn('Fetch SNMP info error:', err);

            setSnmpAvailable(false);
            setSnmpInfo(null);
            setUptimeReceivedAt(null);
        }
    }, [deviceId]);

    useEffect(() => {
        if (!deviceId) return;

        let isMounted = true;
        let timeoutId;
        let controller;

        const requestAndSchedule = async () => {
            if (!isMounted) return;

            controller = new AbortController();

            await Promise.all([
                fetchDevice(controller.signal),
                fetchSnmpInfo(controller.signal),
            ]);

            if (!isMounted) return;

            const delay = getDelayToNextMinuteAtSecond15();

            timeoutId = setTimeout(
                requestAndSchedule,
                delay
            );
        };

        requestAndSchedule();

        return () => {
            isMounted = false;

            clearTimeout(timeoutId);
            controller?.abort();
        };
    }, [deviceId, fetchDevice, fetchSnmpInfo]);

    useEffect(() => {
        if (uptimeReceivedAt === null) return;

        const intervalId = setInterval(() => {
            const elapsedSeconds = Math.floor(
                (Date.now() - uptimeReceivedAt) / 1000
            );

            setCurrentUptime(
                uptimeBase + elapsedSeconds
            );
        }, 1000);

        return () => clearInterval(intervalId);
    }, [uptimeBase, uptimeReceivedAt]);

    const handleOpenEdit = () => {
        if (!deviceInfo) return;

        setFormData({
            deviceName: deviceInfo.deviceName || '',
            ipAddress: deviceInfo.ipAddress || '',
            deviceType: deviceInfo.deviceType || '',
        });

        setError('');
        setSuccess('');
        setShowEditModal(true);
    };

    const handleFormChange = (e) => {
        const { name, value } = e.target;

        setFormData((prev) => ({
            ...prev,
            [name]: value,
        }));
    };

    const handleUpdate = async (e) => {
        e.preventDefault();

        if (!formData.deviceName.trim()) {
            setError('Vui lòng nhập tên thiết bị.');
            return;
        }

        if (!formData.ipAddress.trim()) {
            setError('Vui lòng nhập địa chỉ IP.');
            return;
        }

        if (!formData.deviceType.trim()) {
            setError('Vui lòng nhập loại thiết bị.');
            return;
        }

        try {
            setUpdating(true);
            setError('');
            setSuccess('');

            const payload = {
                deviceName: formData.deviceName.trim(),
                ipAddress: formData.ipAddress.trim(),
                deviceType: formData.deviceType.trim(),
            };

            const response = await axiosClient.put(
                `/devices/${deviceId}`,
                payload
            );

            if (response.data?.success === false) {
                throw new Error(
                    response.data?.message ||
                    'Cập nhật thiết bị thất bại.'
                );
            }

            setShowEditModal(false);

            await Promise.all([
                fetchDevice(),
                fetchSnmpInfo(),
            ]);

            setSuccess('Cập nhật thông tin thiết bị thành công.');
        } catch (err) {
            console.error('Update device error:', err);

            setError(
                err.response?.data?.message ||
                err.message ||
                'Không thể cập nhật thông tin thiết bị.'
            );
        } finally {
            setUpdating(false);
        }
    };

    const handleDelete = async () => {
        const confirmed = window.confirm(
            `Bạn có chắc chắn muốn xóa thiết bị "${deviceInfo?.deviceName || ''}" không?\n\nThao tác này không thể hoàn tác.`
        );

        if (!confirmed) return;

        try {
            setDeleting(true);
            setError('');
            setSuccess('');

            const response = await axiosClient.delete(
                `/devices/${deviceId}`
            );

            if (response.data?.success === false) {
                throw new Error(
                    response.data?.message ||
                    'Xóa thiết bị thất bại.'
                );
            }

            navigate('/dashboard');
        } catch (err) {
            console.error('Delete device error:', err);

            setError(
                err.response?.data?.message ||
                err.message ||
                'Không thể xóa thiết bị.'
            );
        } finally {
            setDeleting(false);
        }
    };

    const basicFields = [
        ['Device ID', deviceInfo?.id],
        ['Tên thiết bị', deviceInfo?.deviceName],
        ['Địa chỉ IP', deviceInfo?.ipAddress],
        ['Loại thiết bị', deviceInfo?.deviceType],
        ['Trạng thái', deviceInfo?.status],
        ['Ngày tạo', formatDate(deviceInfo?.createdAt)],
        ['Ngày cập nhật', formatDate(deviceInfo?.updatedAt)],
    ];

    const getSnmpFields = () => {
        if (!snmpInfo) return [];

        const entries = Object.entries(snmpInfo);

        return entries.map(([key, value]) => {
            let displayValue = value;

            if (
                key === 'System Uptime' ||
                key === 'systemUptime'
            ) {
                displayValue = formatUptime(currentUptime);
            } else if (
                value === null ||
                value === undefined ||
                value === ''
            ) {
                displayValue = 'N/A';
            } else if (typeof value === 'object') {
                displayValue = JSON.stringify(value);
            }

            return [
                key,
                displayValue,
            ];
        });
    };

    if (loading) {
        return (
            <section className="dashboard-section device-tab-content device-info-tab">
                <div className="device-info-loading">
                    <span className="device-info-spinner" />
                    Đang tải thông tin thiết bị...
                </div>
            </section>
        );
    }

    return (
        <section className="dashboard-section device-tab-content device-info-tab">
            {error && (
                <div className="device-info-message device-info-message-error">
                    <span>{error}</span>

                    <button
                        type="button"
                        onClick={() => setError('')}
                    >
                        ×
                    </button>
                </div>
            )}

            {success && (
                <div className="device-info-message device-info-message-success">
                    <span>{success}</span>

                    <button
                        type="button"
                        onClick={() => setSuccess('')}
                    >
                        ×
                    </button>
                </div>
            )}

            <div className="device-info-columns">
                <div className="device-info-panel">
                    <div className="device-info-panel-header">
                        <div>
                            <h3>Thông tin thiết bị</h3>
                            <p>Thông tin được lưu trữ trong hệ thống.</p>
                        </div>

                        <span className="device-info-source-badge">
                            DATABASE
                        </span>
                    </div>

                    {basicError && (
                        <div className="device-info-inline-error">
                            {basicError}
                        </div>
                    )}

                    {deviceInfo && (
                        <>
                            <div className="device-info-grid">
                                {basicFields.map(([label, value]) => (
                                    <div
                                        className="device-info-card"
                                        key={label}
                                    >
                                        <span>{label}</span>

                                        <strong
                                            className={
                                                label === 'Trạng thái'
                                                    ? `device-info-status ${
                                                        value === 'UP'
                                                            ? 'status-up'
                                                            : 'status-down'
                                                    }`
                                                    : ''
                                            }
                                        >
                                            {value !== undefined &&
                                            value !== null &&
                                            value !== ''
                                                ? value
                                                : 'N/A'}
                                        </strong>
                                    </div>
                                ))}
                            </div>

                            <div className="device-info-actions">
                                <button
                                    type="button"
                                    className="device-info-edit-btn"
                                    onClick={handleOpenEdit}
                                    disabled={deleting}
                                >
                                    ✎ Chỉnh sửa
                                </button>

                                <button
                                    type="button"
                                    className="device-info-delete-btn"
                                    onClick={handleDelete}
                                    disabled={deleting}
                                >
                                    {deleting
                                        ? 'Đang xóa...'
                                        : '✕ Xóa thiết bị'}
                                </button>
                            </div>
                        </>
                    )}
                </div>

                {snmpAvailable && snmpInfo && (
                    <div className="device-info-panel">
                        <div className="device-info-panel-header">
                            <div>
                                <h3>Thông tin SNMP</h3>
                                <p>Thông tin được truy vấn trực tiếp từ thiết bị.</p>
                            </div>

                            <span className="device-info-snmp-badge">
                                SNMP
                            </span>
                        </div>

                        <div className="device-info-grid">
                            {getSnmpFields().map(([label, value]) => (
                                <div
                                    className={`device-info-card ${
                                        label === 'System Description' ||
                                        label === 'systemDescription'
                                            ? 'device-info-card-wide'
                                            : ''
                                    }`}
                                    key={label}
                                >
                                    <span>{formatLabel(label)}</span>

                                    <strong>{value}</strong>
                                </div>
                            ))}
                        </div>

                        <div className="device-info-live-footer">
                            <span>
                                <span className="device-info-live-dot" />
                                Đang nhận dữ liệu SNMP
                            </span>

                            <span>Tự động cập nhật mỗi phút</span>
                        </div>
                    </div>
                )}
            </div>

            {showEditModal && (
                <div
                    className="device-info-modal-overlay"
                    onClick={() => {
                        if (!updating) {
                            setShowEditModal(false);
                            setError('');
                        }
                    }}
                >
                    <div
                        className="device-info-modal"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="device-info-modal-header">
                            <div>
                                <h3>Chỉnh sửa thiết bị</h3>
                                <p>
                                    Cập nhật thông tin cơ bản của thiết bị.
                                </p>
                            </div>

                            <button
                                type="button"
                                onClick={() => setShowEditModal(false)}
                                disabled={updating}
                            >
                                ×
                            </button>
                        </div>

                        <form onSubmit={handleUpdate}>
                            <div className="device-info-form-group">
                                <label htmlFor="deviceName">
                                    Tên thiết bị
                                </label>

                                <input
                                    id="deviceName"
                                    name="deviceName"
                                    type="text"
                                    value={formData.deviceName}
                                    onChange={handleFormChange}
                                    placeholder="Nhập tên thiết bị"
                                    required
                                    maxLength={100}
                                />
                            </div>

                            <div className="device-info-form-group">
                                <label htmlFor="ipAddress">
                                    Địa chỉ IP
                                </label>

                                <input
                                    id="ipAddress"
                                    name="ipAddress"
                                    type="text"
                                    value={formData.ipAddress}
                                    onChange={handleFormChange}
                                    placeholder="Ví dụ: 172.16.200.66"
                                    required
                                />
                            </div>

                            <div className="device-info-form-group">
                                <label htmlFor="deviceType">
                                    Loại thiết bị
                                </label>

                                <input
                                    id="deviceType"
                                    name="deviceType"
                                    type="text"
                                    value={formData.deviceType}
                                    onChange={handleFormChange}
                                    placeholder="Ví dụ: Router, Switch, Firewall"
                                    required
                                    maxLength={100}
                                />
                            </div>

                            <div className="device-info-modal-actions">
                                <button
                                    type="button"
                                    className="device-info-cancel-btn"
                                    onClick={() => setShowEditModal(false)}
                                    disabled={updating}
                                >
                                    Hủy
                                </button>

                                <button
                                    type="submit"
                                    className="device-info-save-btn"
                                    disabled={updating}
                                >
                                    {updating
                                        ? 'Đang cập nhật...'
                                        : 'Lưu thay đổi'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </section>
    );
};

export default TabDeviceInfo;