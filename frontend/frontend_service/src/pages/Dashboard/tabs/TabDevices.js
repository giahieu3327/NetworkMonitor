import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../../api/axiosClient';

const DEVICE_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
]);

const DEVICE_EXPORT_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_REPORT_MANAGER',
]);

const normalizeRole = (role) => {
  if (!role || typeof role !== 'string') return '';
  const trimmed = role.trim().toUpperCase();
  if (!trimmed) return '';
  return trimmed.startsWith('ROLE_') ? trimmed : `ROLE_${trimmed}`;
};

const extractRoles = (source) => {
  const rolesSet = new Set();
  if (!source) return rolesSet;

  const pushRole = (val) => {
    if (!val) return;
    if (Array.isArray(val)) {
      val.forEach((item) => pushRole(item));
      return;
    }
    if (typeof val === 'string') {
      val
        .split(/[\s,]+/)
        .map((r) => normalizeRole(r))
        .filter(Boolean)
        .forEach((r) => rolesSet.add(r));
    } else if (typeof val === 'object') {
      pushRole(val.roleName || val.role_name || val.name || val.authority || val.role);
    }
  };

  pushRole(source.roleName);
  pushRole(source.role_name);
  pushRole(source.role);
  pushRole(source.authority);
  pushRole(source.roles);
  pushRole(source.authorities);
  pushRole(source.realmRoles);
  pushRole(source.realm_roles);
  pushRole(source.realm_access?.roles);
  pushRole(source.scope);

  return rolesSet;
};

const decodeJwtPayload = (token) => {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const jsonStr = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join('')
    );
    return JSON.parse(jsonStr);
  } catch {
    return null;
  }
};

const readStoredUser = () => {
  try {
    const rawUser = localStorage.getItem('current_user');
    if (rawUser) {
      return JSON.parse(rawUser);
    }
  } catch {
  }

  const storedRole = localStorage.getItem('user_role');
  let storedRoles = [];
  try {
    const rawRoles = localStorage.getItem('user_roles');
    if (rawRoles) {
      const parsed = JSON.parse(rawRoles);
      if (Array.isArray(parsed)) storedRoles = parsed;
    }
  } catch {
  }

  if (storedRole || storedRoles.length > 0) {
    return {
      roleName: storedRole || '',
      roles: storedRoles,
    };
  }

  const accessToken = localStorage.getItem('access_token');
  if (accessToken) {
    const claims = decodeJwtPayload(accessToken);
    if (claims) {
      return claims;
    }
  }

  return null;
};

const TabDevices = ({ currentUser }) => {
  const navigate = useNavigate();

  const resolvedUser = useMemo(
    () => currentUser || readStoredUser(),
    [currentUser]
  );

  const userRoles = useMemo(
    () => extractRoles(resolvedUser),
    [resolvedUser]
  );

  const canManageDevices = useMemo(
    () => Array.from(userRoles).some((role) => DEVICE_MANAGE_ROLES.has(role)),
    [userRoles]
  );

  const canExportDevices = useMemo(
    () => Array.from(userRoles).some((role) => DEVICE_EXPORT_ROLES.has(role)),
    [userRoles]
  );

  const [devices, setDevices] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [selectedIds, setSelectedIds] = useState([]);

  const [keyword, setKeyword] = useState('');

  const [searchParams, setSearchParams] = useState({
    keyword: '',
    page: 0,
    size: 10,
    sortBy: 'createdAt',
    direction: 'DESC',
  });

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const [showImport, setShowImport] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);

  const fetchDevices = useCallback(async () => {
    try {
      const res = await axiosClient.get('/devices', {
        params: searchParams,
      });

      const data = res.data?.data ?? res.data;

      setDevices(data?.content ?? []);
      setTotalElements(data?.totalElements ?? 0);
      setTotalPages(data?.totalPages ?? 1);
      setSelectedIds([]);
    } catch (err) {
      console.error('Lỗi tải danh sách thiết bị:', err);

      alert(
        err.response?.data?.message ||
        'Không thể tải danh sách thiết bị!'
      );
    }
  }, [searchParams]);

  useEffect(() => {
    fetchDevices();
  }, [fetchDevices]);

  const handleNavigateCreate = () => {
    if (!canManageDevices) return;
    navigate('/devices/create');
  };

  const handleSearch = () => {
    setSearchParams((prev) => ({
      ...prev,
      keyword: keyword.trim(),
      page: 0,
    }));
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleSort = (field) => {
    setSearchParams((prev) => ({
      ...prev,
      sortBy: field,
      direction:
        prev.sortBy === field && prev.direction === 'ASC'
          ? 'DESC'
          : 'ASC',
      page: 0,
    }));
  };

  const renderSortIcon = (field) => {
    if (searchParams.sortBy !== field) {
      return (
        <span style={{ opacity: 0.3, marginLeft: '4px' }}>
          ↕
        </span>
      );
    }

    return searchParams.direction === 'ASC'
      ? <span style={{ marginLeft: '4px' }}>↑</span>
      : <span style={{ marginLeft: '4px' }}>↓</span>;
  };

  const handleSelectAll = (e) => {
    if (!canManageDevices) return;
    setSelectedIds(
      e.target.checked
        ? devices.map((device) => device.id)
        : []
    );
  };

  const handleSelectOne = (e, device) => {
    e.stopPropagation();
    if (!canManageDevices) return;

    setSelectedIds((prev) =>
      prev.includes(device.id)
        ? prev.filter((id) => id !== device.id)
        : [...prev, device.id]
    );
  };

  const isAllSelected =
    devices.length > 0 &&
    devices.every((device) =>
      selectedIds.includes(device.id)
    );

  const handleBulkDelete = async () => {
    setIsDropdownOpen(false);
    if (!canManageDevices) return;

    if (selectedIds.length === 0) {
      alert('Vui lòng chọn thiết bị cần xóa!');
      return;
    }

    const confirmed = window.confirm(
      `Bạn có chắc chắn muốn xóa ${selectedIds.length} thiết bị đã chọn?`
    );

    if (!confirmed) {
      return;
    }

    try {
      await Promise.all(
        selectedIds.map((id) =>
          axiosClient.delete(`/devices/${id}`)
        )
      );

      alert('Xóa thiết bị thành công!');

      await fetchDevices();
    } catch (err) {
      console.error('Lỗi xóa thiết bị:', err);

      alert(
        err.response?.data?.message ||
        'Có lỗi xảy ra khi xóa thiết bị!'
      );

      await fetchDevices();
    }
  };

  const downloadExcel = (data, fileName) => {
    const url = window.URL.createObjectURL(
      new Blob([data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
    );

    const link = document.createElement('a');

    link.href = url;
    link.download = fileName;

    document.body.appendChild(link);
    link.click();
    link.remove();

    window.URL.revokeObjectURL(url);
  };

  const handleExport = async () => {
    setIsDropdownOpen(false);
    if (!canExportDevices) return;

    try {
      const res = await axiosClient.get('/devices/export', {
        responseType: 'blob',
      });

      downloadExcel(res.data, 'devices.xlsx');
    } catch (err) {
      console.error('Lỗi export thiết bị:', err);

      alert(
        err.response?.data?.message ||
        'Lỗi xuất file thiết bị!'
      );
    }
  };

  const handleDownloadTemplate = async () => {
    if (!canManageDevices) return;

    try {
      const res = await axiosClient.get(
        '/devices/import/template',
        {
          responseType: 'blob',
        }
      );

      downloadExcel(
        res.data,
        'devices-import-template.xlsx'
      );
    } catch (err) {
      console.error('Lỗi tải file mẫu:', err);

      alert(
        err.response?.data?.message ||
        'Lỗi tải file mẫu thiết bị!'
      );
    }
  };

  const handleImport = async (e) => {
    e.preventDefault();
    if (!canManageDevices) return;

    if (!importFile) {
      alert('Vui lòng chọn file Excel!');
      return;
    }

    const extension = importFile.name
      .split('.')
      .pop()
      .toLowerCase();

    if (!['xlsx', 'xls'].includes(extension)) {
      alert('Chỉ hỗ trợ file XLSX hoặc XLS!');
      return;
    }

    const formData = new FormData();

    formData.append('file', importFile);

    try {
      setImporting(true);

      const res = await axiosClient.post(
        '/devices/import',
        formData
      );

      const responseData =
        res.data?.data ?? res.data ?? {};

      const message = [
        res.data?.message || 'Import thiết bị hoàn tất!',
        responseData.totalRows !== undefined &&
          `Tổng số dòng: ${responseData.totalRows}`,
        responseData.successCount !== undefined &&
          `Thành công: ${responseData.successCount}`,
        responseData.failedCount !== undefined &&
          `Thất bại: ${responseData.failedCount}`,
        responseData.skippedCount !== undefined &&
          `Bỏ qua: ${responseData.skippedCount}`,
      ]
        .filter(Boolean)
        .join('\n');

      alert(message);

      setShowImport(false);
      setImportFile(null);

      setSearchParams((prev) => ({
        ...prev,
        page: 0,
      }));
    } catch (err) {
      console.error('Lỗi import thiết bị:', err);

      alert(
        err.response?.data?.message ||
        'Import thiết bị thất bại!'
      );
    } finally {
      setImporting(false);
    }
  };

  const renderStatus = (status) => {
    if (status === 'UP') {
      return (
        <span className="status-active">
          HOẠT ĐỘNG
        </span>
      );
    }

    if (status === 'DOWN') {
      return (
        <span className="status-inactive">
          KHÔNG HOẠT ĐỘNG
        </span>
      );
    }

    return (
      <span>
        {status || 'UNKNOWN'}
      </span>
    );
  };

  return (
    <section className="dashboard-section">

      <div className="user-toolbar">

        <div className="search-container">
          <input
            type="text"
            placeholder="Tìm kiếm tên thiết bị, IP, loại thiết bị..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={handleKeyDown}
            className="user-search-input"
          />

          <button
            onClick={handleSearch}
            className="search-button"
          >
            Tìm kiếm
          </button>
        </div>

        {canManageDevices ? (
          <div className="device-actions">

            <button
              type="button"
              className="add-device-button"
              onClick={handleNavigateCreate}
            >
              + Thêm Thiết Bị
            </button>

            <button
              type="button"
              className="device-action-dropdown-button"
              onClick={() =>
                setIsDropdownOpen((prev) => !prev)
              }
              title="Thao tác thiết bị"
            >
              ▾
            </button>

            {isDropdownOpen && (
              <div className="device-dropdown">

                <button
                  type="button"
                  className="device-dropdown-item delete-item"
                  onClick={handleBulkDelete}
                >
                  🗑️ Xóa thiết bị
                  {selectedIds.length > 0 &&
                    ` (${selectedIds.length})`}
                </button>

                <button
                  type="button"
                  className="device-dropdown-item"
                  onClick={() => {
                    setIsDropdownOpen(false);
                    setImportFile(null);
                    setShowImport(true);
                  }}
                >
                  📥 Thêm thiết bị từ file
                </button>

                <button
                  type="button"
                  className="device-dropdown-item"
                  onClick={handleExport}
                >
                  📤 Xuất file thiết bị
                </button>

              </div>
            )}

          </div>
        ) : (
          canExportDevices && (
            <button
              type="button"
              className="search-button"
              onClick={handleExport}
            >
              📤 Xuất file thiết bị
            </button>
          )
        )}
      </div>

      <div className="user-table-wrapper">
        <table className="user-table">

          <thead>
            <tr>

              {canManageDevices && (
                <th
                  style={{
                    width: '40px',
                    textAlign: 'center',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={isAllSelected}
                    onChange={handleSelectAll}
                  />
                </th>
              )}

              <th
                onClick={() => handleSort('deviceName')}
                style={{ cursor: 'pointer' }}
              >
                Tên thiết bị
                {renderSortIcon('deviceName')}
              </th>

              <th
                onClick={() => handleSort('ipAddress')}
                style={{ cursor: 'pointer' }}
              >
                Địa chỉ IP
                {renderSortIcon('ipAddress')}
              </th>

              <th
                onClick={() => handleSort('deviceType')}
                style={{ cursor: 'pointer' }}
              >
                Loại thiết bị
                {renderSortIcon('deviceType')}
              </th>

              <th
                onClick={() => handleSort('status')}
                style={{ cursor: 'pointer' }}
              >
                Trạng thái
                {renderSortIcon('status')}
              </th>

            </tr>
          </thead>

          <tbody>
            {devices.length > 0 ? (
              devices.map((device) => (
                <tr
                  key={device.id}
                  className="clickable-row"
                  onClick={() => navigate(`/devices/${device.id}`)}
                >

                  {canManageDevices && (
                    <td
                      style={{ textAlign: 'center' }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <input
                        type="checkbox"
                        checked={selectedIds.includes(device.id)}
                        onChange={(e) =>
                          handleSelectOne(e, device)
                        }
                      />
                    </td>
                  )}

                  <td>{device.deviceName}</td>

                  <td>{device.ipAddress}</td>

                  <td>{device.deviceType}</td>

                  <td>
                    {renderStatus(device.status)}
                  </td>

                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan={canManageDevices ? 5 : 4}
                  className="empty-user-table"
                >
                  Chưa có thiết bị nào được kết nối tới hệ thống.
                </td>
              </tr>
            )}
          </tbody>

        </table>
      </div>

      {totalPages > 1 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: '16px',
            fontSize: '13.5px',
          }}
        >

          <span>
            Hiển thị {devices.length} / {totalElements} thiết bị
          </span>

          <div
            style={{
              display: 'flex',
              gap: '8px',
              alignItems: 'center',
            }}
          >

            <button
              disabled={searchParams.page === 0}
              onClick={() =>
                setSearchParams((prev) => ({
                  ...prev,
                  page: prev.page - 1,
                }))
              }
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border-light)',
                cursor:
                  searchParams.page === 0
                    ? 'not-allowed'
                    : 'pointer',
                opacity:
                  searchParams.page === 0 ? 0.5 : 1,
              }}
            >
              Trang trước
            </button>

            <span>
              Trang{' '}
              <strong>{searchParams.page + 1}</strong>
              {' / '}
              {totalPages}
            </span>

            <button
              disabled={
                searchParams.page + 1 >= totalPages
              }
              onClick={() =>
                setSearchParams((prev) => ({
                  ...prev,
                  page: prev.page + 1,
                }))
              }
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border-light)',
                cursor:
                  searchParams.page + 1 >= totalPages
                    ? 'not-allowed'
                    : 'pointer',
                opacity:
                  searchParams.page + 1 >= totalPages
                    ? 0.5
                    : 1,
              }}
            >
              Trang sau
            </button>

          </div>
        </div>
      )}

      {canManageDevices && showImport && (
        <div className="import-modal-overlay">

          <div className="import-modal">

            <h3>Import danh sách thiết bị</h3>

            <form onSubmit={handleImport}>

              <input
                type="file"
                accept=".xlsx,.xls"
                onChange={(e) =>
                  setImportFile(
                    e.target.files?.[0] || null
                  )
                }
                disabled={importing}
              />

              <button
                type="button"
                onClick={handleDownloadTemplate}
                disabled={importing}
              >
                📥 Tải file import-template
              </button>

              <div className="import-modal-actions">

                <button
                  type="button"
                  onClick={() => {
                    setShowImport(false);
                    setImportFile(null);
                  }}
                  disabled={importing}
                >
                  Đóng
                </button>

                <button
                  type="submit"
                  disabled={importing || !importFile}
                >
                  {importing
                    ? 'Đang gửi...'
                    : 'Xác nhận / Gửi'}
                </button>

              </div>

            </form>

          </div>
        </div>
      )}

    </section>
  );
};

export default TabDevices;