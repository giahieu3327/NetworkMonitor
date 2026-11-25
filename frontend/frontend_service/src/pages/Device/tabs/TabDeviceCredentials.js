import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../../api/axiosClient';
import './TabDeviceCredentials.css';

const PAGE_SIZE = 10;

const ALLOWED_VIEW_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_REPORT_MANAGER',
  'ROLE_GUEST_VIEWER',
]);

const CREDENTIAL_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
]);

const CREDENTIAL_EXPORT_ROLES = new Set([
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

const TabDeviceCredentials = ({
  deviceId,
  device,
  currentUser,
  canManageDevices: canManageDevicesProp,
  canExportReports: canExportReportsProp,
}) => {
  const navigate = useNavigate();

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

  const canViewCredentials = useMemo(() => {
    if (!currentUser) return true;
    return Array.from(userRoles).some((role) => ALLOWED_VIEW_ROLES.has(role));
  }, [currentUser, userRoles]);

  const canManageCredentials = useMemo(() => {
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    return Array.from(userRoles).some((role) =>
      CREDENTIAL_MANAGE_ROLES.has(role)
    );
  }, [canManageDevicesProp, userRoles]);

  const canExportCredentials = useMemo(() => {
    if (typeof canExportReportsProp === 'boolean') {
      return canExportReportsProp;
    }
    return Array.from(userRoles).some((role) =>
      CREDENTIAL_EXPORT_ROLES.has(role)
    );
  }, [canExportReportsProp, userRoles]);

  const [credentials, setCredentials] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [selectedIds, setSelectedIds] = useState([]);

  const [keyword, setKeyword] = useState('');

  const [searchParams, setSearchParams] = useState({
    keyword: '',
    page: 0,
    size: PAGE_SIZE,
    sortBy: 'createdAt',
    direction: 'DESC',
  });

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const [showImport, setShowImport] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);

  const fetchCredentials = useCallback(async () => {
    if (!deviceId || !canViewCredentials) return;

    try {
      const res = await axiosClient.get(
        `/device-credentials/device/${deviceId}`,
        {
          params: {
            ...searchParams,
            size: PAGE_SIZE,
          },
        }
      );

      const data = res.data?.data ?? res.data;

      const content = Array.isArray(data) ? data : data?.content ?? [];
      const total =
        typeof data?.totalElements === 'number'
          ? data.totalElements
          : content.length;

      const calculatedPages = Math.max(
        1,
        typeof data?.totalPages === 'number' && data.totalPages > 0
          ? data.totalPages
          : Math.ceil(total / PAGE_SIZE)
      );

      setCredentials(content);
      setTotalElements(total);
      setTotalPages(calculatedPages);
      setSelectedIds([]);
    } catch (err) {
      console.error('Lỗi tải danh sách thông tin xác thực:', err);

      alert(
        err.response?.data?.message ||
          'Không thể tải danh sách thông tin xác thực!'
      );
    }
  }, [deviceId, canViewCredentials, searchParams]);

  useEffect(() => {
    fetchCredentials();
  }, [fetchCredentials]);

  const handleNavigateCreate = () => {
    if (!canManageCredentials) return;

    navigate(`/devices/${deviceId}/credentials/create`, {
      state: { device },
    });
  };

  const handleSearch = () => {
    setSearchParams((prev) => ({
      ...prev,
      keyword: keyword.trim(),
      page: 0,
      size: PAGE_SIZE,
    }));
  };

  const handleClearSearch = () => {
    setKeyword('');
    setSearchParams((prev) => ({
      ...prev,
      keyword: '',
      page: 0,
      size: PAGE_SIZE,
    }));
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handlePageChange = (newPage) => {
    if (newPage < 0 || newPage >= totalPages || newPage === searchParams.page) {
      return;
    }

    setSearchParams((prev) => ({
      ...prev,
      page: newPage,
      size: PAGE_SIZE,
    }));
  };

  const paginationRange = useMemo(() => {
    const currentPage = searchParams.page + 1;
    const siblingCount = 1;
    const totalPageNumbers = siblingCount + 5;

    if (totalPages <= totalPageNumbers) {
      return Array.from({ length: totalPages }, (_, idx) => idx + 1);
    }

    const leftSiblingIndex = Math.max(currentPage - siblingCount, 1);
    const rightSiblingIndex = Math.min(currentPage + siblingCount, totalPages);

    const shouldShowLeftDots = leftSiblingIndex > 2;
    const shouldShowRightDots = rightSiblingIndex < totalPages - 1;

    if (!shouldShowLeftDots && shouldShowRightDots) {
      const leftItemCount = 3 + 2 * siblingCount;
      const leftRange = Array.from({ length: leftItemCount }, (_, i) => i + 1);
      return [...leftRange, '...', totalPages];
    }

    if (shouldShowLeftDots && !shouldShowRightDots) {
      const rightItemCount = 3 + 2 * siblingCount;
      const rightRange = Array.from(
        { length: rightItemCount },
        (_, i) => totalPages - rightItemCount + i + 1
      );
      return [1, '...', ...rightRange];
    }

    const middleRange = Array.from(
      { length: rightSiblingIndex - leftSiblingIndex + 1 },
      (_, i) => leftSiblingIndex + i
    );
    return [1, '...', ...middleRange, '...', totalPages];
  }, [searchParams.page, totalPages]);

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

    return searchParams.direction === 'ASC' ? (
      <span style={{ marginLeft: '4px' }}>↑</span>
    ) : (
      <span style={{ marginLeft: '4px' }}>↓</span>
    );
  };

  const handleSelectAll = (e) => {
    if (!canManageCredentials) return;

    setSelectedIds(
      e.target.checked
        ? credentials.map((credential) => credential.id)
        : []
    );
  };

  const handleSelectOne = (e, credential) => {
    e.stopPropagation();
    if (!canManageCredentials) return;

    setSelectedIds((prev) =>
      prev.includes(credential.id)
        ? prev.filter((id) => id !== credential.id)
        : [...prev, credential.id]
    );
  };

  const isAllSelected =
    credentials.length > 0 &&
    credentials.every((credential) =>
      selectedIds.includes(credential.id)
    );

  const handleBulkDelete = async () => {
    setIsDropdownOpen(false);
    if (!canManageCredentials) return;

    if (selectedIds.length === 0) {
      alert('Vui lòng chọn thông tin xác thực cần xóa!');
      return;
    }

    const confirmed = window.confirm(
      `Bạn có chắc chắn muốn xóa ${selectedIds.length} thông tin xác thực đã chọn?`
    );

    if (!confirmed) {
      return;
    }

    try {
      await Promise.all(
        selectedIds.map((id) =>
          axiosClient.delete(`/device-credentials/${id}`)
        )
      );

      alert('Xóa thông tin xác thực thành công!');

      if (credentials.length === selectedIds.length && searchParams.page > 0) {
        setSearchParams((prev) => ({
          ...prev,
          page: prev.page - 1,
        }));
      } else {
        await fetchCredentials();
      }
    } catch (err) {
      console.error('Lỗi xóa thông tin xác thực:', err);

      alert(
        err.response?.data?.message ||
          'Có lỗi xảy ra khi xóa thông tin xác thực!'
      );

      await fetchCredentials();
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
    if (!canExportCredentials) return;

    try {
      const res = await axiosClient.get('/device-credentials/export', {
        params: { deviceId },
        responseType: 'blob',
      });

      downloadExcel(res.data, `credentials-device-${deviceId}.xlsx`);
    } catch (err) {
      console.error('Lỗi export thông tin xác thực:', err);

      alert(
        err.response?.data?.message ||
          'Lỗi xuất file thông tin xác thực!'
      );
    }
  };

  const handleDownloadTemplate = async () => {
    if (!canManageCredentials) return;

    try {
      const res = await axiosClient.get(
        '/device-credentials/import/template',
        {
          responseType: 'blob',
        }
      );

      downloadExcel(
        res.data,
        'device-credentials-import-template.xlsx'
      );
    } catch (err) {
      console.error('Lỗi tải file mẫu:', err);

      alert(
        err.response?.data?.message ||
          'Lỗi tải file mẫu thông tin xác thực!'
      );
    }
  };

  const handleImport = async (e) => {
    e.preventDefault();
    if (!canManageCredentials) return;

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
        '/device-credentials/import',
        formData,
        {
          params: { deviceId },
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      const responseData = res.data?.data ?? res.data ?? {};

      const message = [
        res.data?.message || 'Import thông tin xác thực hoàn tất!',
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
      console.error('Lỗi import thông tin xác thực:', err);

      alert(
        err.response?.data?.message ||
          'Import thông tin xác thực thất bại!'
      );
    } finally {
      setImporting(false);
    }
  };

  const renderStatus = (isActive) => {
    if (isActive === true) {
      return (
        <span className="status-active">
          HOẠT ĐỘNG
        </span>
      );
    }

    if (isActive === false) {
      return (
        <span className="status-inactive">
          ĐÃ TẮT
        </span>
      );
    }

    return <span>UNKNOWN</span>;
  };

  if (!canViewCredentials) {
    return (
      <div className="permission-message">
        Bạn không có quyền xem thông tin xác thực của thiết bị này.
      </div>
    );
  }

  const startItem = totalElements === 0 ? 0 : searchParams.page * PAGE_SIZE + 1;
  const endItem = Math.min((searchParams.page + 1) * PAGE_SIZE, totalElements);

  return (
    <section className="dashboard-section credential-tab">
      <div className="user-toolbar">
        <div className="search-container">
          <input
            type="text"
            placeholder="Tìm kiếm tên credential, loại, giao thức..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={handleKeyDown}
            className="user-search-input"
          />

          {keyword && (
            <button
              type="button"
              onClick={handleClearSearch}
              className="clear-search-button"
              title="Xóa từ khóa"
            >
              ✕
            </button>
          )}

          <button
            type="button"
            onClick={handleSearch}
            className="search-button"
          >
            Tìm kiếm
          </button>
        </div>

        {canManageCredentials ? (
          <div className="device-actions">
            <button
              type="button"
              className="add-device-button"
              onClick={handleNavigateCreate}
            >
              + Thêm Credential
            </button>

            <button
              type="button"
              className="device-action-dropdown-button"
              onClick={() =>
                setIsDropdownOpen((prev) => !prev)
              }
              title="Thao tác thông tin xác thực"
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
                  🗑️ Xóa credential
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
                  📥 Thêm credential từ file
                </button>

                {canExportCredentials && (
                  <button
                    type="button"
                    className="device-dropdown-item"
                    onClick={handleExport}
                  >
                    📤 Xuất file credential
                  </button>
                )}
              </div>
            )}
          </div>
        ) : (
          canExportCredentials && (
            <button
              type="button"
              className="search-button"
              onClick={handleExport}
            >
              📤 Xuất file credential
            </button>
          )
        )}
      </div>

      <div className="user-table-wrapper">
        <table className="user-table">
          <thead>
            <tr>
              {canManageCredentials && (
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
                onClick={() => handleSort('credentialName')}
                style={{ cursor: 'pointer' }}
              >
                Tên Credential
                {renderSortIcon('credentialName')}
              </th>

              <th
                onClick={() => handleSort('credentialType')}
                style={{ cursor: 'pointer' }}
              >
                Loại Credential
                {renderSortIcon('credentialType')}
              </th>

              <th
                onClick={() => handleSort('protocolType')}
                style={{ cursor: 'pointer' }}
              >
                Giao thức
                {renderSortIcon('protocolType')}
              </th>

              <th>Community String</th>

              <th
                onClick={() => handleSort('port')}
                style={{ cursor: 'pointer' }}
              >
                Cổng
                {renderSortIcon('port')}
              </th>

              <th
                onClick={() => handleSort('isActive')}
                style={{ cursor: 'pointer' }}
              >
                Trạng thái
                {renderSortIcon('isActive')}
              </th>
            </tr>
          </thead>

          <tbody>
            {credentials.length > 0 ? (
              credentials.map((credential) => (
                <tr
                  key={credential.id}
                  className="clickable-row"
                  onClick={() => navigate(`/credentials/${credential.id}`)}
                >
                  {canManageCredentials && (
                    <td
                      style={{ textAlign: 'center' }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <input
                        type="checkbox"
                        checked={selectedIds.includes(credential.id)}
                        onChange={(e) =>
                          handleSelectOne(e, credential)
                        }
                      />
                    </td>
                  )}

                  <td>{credential.credentialName || '—'}</td>

                  <td>{credential.credentialType || '—'}</td>

                  <td>
                    <span className="credential-protocol">
                      {credential.protocolType || '—'}
                    </span>
                  </td>

                  <td>
                    {credential.communityString ? '••••••••' : '—'}
                  </td>

                  <td>{credential.port ?? '—'}</td>

                  <td>
                    {renderStatus(credential.isActive)}
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan={canManageCredentials ? 7 : 6}
                  className="empty-user-table"
                >
                  {searchParams.keyword
                    ? `Không tìm thấy thông tin xác thực nào khớp với "${searchParams.keyword}".`
                    : 'Chưa có thông tin xác thực nào cho thiết bị này.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {totalElements > 0 && (
        <div className="credential-pagination">
          <span className="pagination-info">
            Hiển thị <strong>{startItem}–{endItem}</strong> trong tổng số{' '}
            <strong>{totalElements}</strong> credential ({totalPages} trang)
          </span>

          <div className="pagination-controls">
            <button
              type="button"
              className="pagination-btn"
              disabled={searchParams.page === 0}
              onClick={() => handlePageChange(searchParams.page - 1)}
            >
              ‹ Trước
            </button>

            {paginationRange.map((pageItem, index) =>
              pageItem === '...' ? (
                <span key={`dots-${index}`} className="pagination-dots">
                  ...
                </span>
              ) : (
                <button
                  key={pageItem}
                  type="button"
                  className={`pagination-btn pagination-number ${
                    searchParams.page + 1 === pageItem ? 'active' : ''
                  }`}
                  onClick={() => handlePageChange(pageItem - 1)}
                >
                  {pageItem}
                </button>
              )
            )}

            <button
              type="button"
              className="pagination-btn"
              disabled={searchParams.page + 1 >= totalPages}
              onClick={() => handlePageChange(searchParams.page + 1)}
            >
              Sau ›
            </button>
          </div>
        </div>
      )}

      {canManageCredentials && showImport && (
        <div className="import-modal-overlay">
          <div className="import-modal">
            <h3>Import danh sách thông tin xác thực</h3>

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

export default TabDeviceCredentials;