import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../../api/axiosClient';
import './TabDeviceRule.css';

const PAGE_SIZE = 10;

const ALLOWED_VIEW_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
  'ROLE_REPORT_MANAGER',
  'ROLE_GUEST_VIEWER',
]);

const RULE_MANAGE_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
]);

const RULE_EXPORT_ROLES = new Set([
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

const TabDeviceRule = ({
  deviceId,
  device,
  currentUser,
  canManageRules: canManageRulesProp,
  canExportRules: canExportRulesProp,
}) => {
  const navigate = useNavigate();

  const resolvedUser = useMemo(
    () => currentUser || readStoredUser(),
    [currentUser]
  );

  const userRoles = useMemo(
    () => extractRoles(resolvedUser),
    [resolvedUser]
  );

  const canViewRules = useMemo(() => {
    if (!resolvedUser || userRoles.size === 0) return false;
    return Array.from(userRoles).some((role) => ALLOWED_VIEW_ROLES.has(role));
  }, [resolvedUser, userRoles]);

  const canManageRules = useMemo(() => {
    if (typeof canManageRulesProp === 'boolean') {
      return canManageRulesProp;
    }
    return Array.from(userRoles).some((role) =>
      RULE_MANAGE_ROLES.has(role)
    );
  }, [canManageRulesProp, userRoles]);

  const canExportRules = useMemo(() => {
    if (typeof canExportRulesProp === 'boolean') {
      return canExportRulesProp;
    }
    return Array.from(userRoles).some((role) =>
      RULE_EXPORT_ROLES.has(role)
    );
  }, [canExportRulesProp, userRoles]);

  const [rules, setRules] = useState([]);
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

  const fetchRules = useCallback(async () => {
    if (!deviceId || !canViewRules) return;

    try {
      const res = await axiosClient.get(
        `/threshold-rules/device/${deviceId}`,
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

      setRules(content);
      setTotalElements(total);
      setTotalPages(calculatedPages);
      setSelectedIds([]);
    } catch (err) {
      console.error('Lỗi tải danh sách luật cảnh báo:', err);
      setRules([]);
      setTotalElements(0);
      setTotalPages(1);
      setSelectedIds([]);
    }
  }, [deviceId, canViewRules, searchParams]);

  useEffect(() => {
    fetchRules();
  }, [fetchRules]);

  const handleNavigateCreate = () => {
    if (!canManageRules) return;

    navigate(`/devices/${deviceId}/threshold-rules/create`, {
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
    if (!canManageRules) return;

    setSelectedIds(
      e.target.checked
        ? rules.map((rule) => rule.id)
        : []
    );
  };

  const handleSelectOne = (e, rule) => {
    e.stopPropagation();
    if (!canManageRules) return;

    setSelectedIds((prev) =>
      prev.includes(rule.id)
        ? prev.filter((id) => id !== rule.id)
        : [...prev, rule.id]
    );
  };

  const isAllSelected =
    rules.length > 0 &&
    rules.every((rule) =>
      selectedIds.includes(rule.id)
    );

  const handleBulkDelete = async () => {
    setIsDropdownOpen(false);
    if (!canManageRules) return;

    if (selectedIds.length === 0) {
      alert('Vui lòng chọn luật cảnh báo cần xóa!');
      return;
    }

    const confirmed = window.confirm(
      `Bạn có chắc chắn muốn xóa ${selectedIds.length} luật cảnh báo đã chọn?`
    );

    if (!confirmed) {
      return;
    }

    try {
      await Promise.all(
        selectedIds.map((id) =>
          axiosClient.delete(`/threshold-rules/${id}`)
        )
      );

      alert('Xóa luật cảnh báo thành công!');

      if (rules.length === selectedIds.length && searchParams.page > 0) {
        setSearchParams((prev) => ({
          ...prev,
          page: prev.page - 1,
        }));
      } else {
        await fetchRules();
      }
    } catch (err) {
      console.error('Lỗi xóa luật cảnh báo:', err);

      alert(
        err.response?.data?.message ||
          'Có lỗi xảy ra khi xóa luật cảnh báo!'
      );

      await fetchRules();
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
    if (!canExportRules) return;

    try {
      const res = await axiosClient.get(
        `/threshold-rules/device/${deviceId}/export`,
        {
          responseType: 'blob',
        }
      );

      downloadExcel(res.data, `threshold-rules-device-${deviceId}.xlsx`);
    } catch (err) {
      console.error('Lỗi export luật cảnh báo:', err);

      alert(
        err.response?.data?.message ||
          'Lỗi xuất file luật cảnh báo!'
      );
    }
  };

  const handleDownloadTemplate = async () => {
    if (!canManageRules) return;

    try {
      const res = await axiosClient.get(
        '/threshold-rules/import-template',
        {
          responseType: 'blob',
        }
      );

      downloadExcel(
        res.data,
        'threshold-rules-import-template.xlsx'
      );
    } catch (err) {
      console.error('Lỗi tải file mẫu:', err);

      alert(
        err.response?.data?.message ||
          'Lỗi tải file mẫu luật cảnh báo!'
      );
    }
  };

  const handleImport = async (e) => {
    e.preventDefault();
    if (!canManageRules) return;

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
        `/threshold-rules/device/${deviceId}/import`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      const responseData = res.data?.data ?? res.data ?? {};

      const message = [
        res.data?.message || 'Import luật cảnh báo hoàn tất!',
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
      console.error('Lỗi import luật cảnh báo:', err);

      alert(
        err.response?.data?.message ||
          'Import luật cảnh báo thất bại!'
      );
    } finally {
      setImporting(false);
    }
  };

  const renderStatus = (isEnabled) => {
    if (isEnabled === true) {
      return (
        <span className="status-active">
          HOẠT ĐỘNG
        </span>
      );
    }

    if (isEnabled === false) {
      return (
        <span className="status-inactive">
          ĐÃ TẮT
        </span>
      );
    }

    return <span>UNKNOWN</span>;
  };

  if (!canViewRules) {
    return (
      <section className="dashboard-section">
        <div className="permission-denied-box">
          <div className="permission-denied-icon">!</div>
          <h3 className="permission-denied-title">Không có quyền truy cập</h3>
          <p className="permission-message">
            Bạn không có quyền xem luật cảnh báo của thiết bị này.
          </p>
        </div>
      </section>
    );
  }

  const startItem = totalElements === 0 ? 0 : searchParams.page * PAGE_SIZE + 1;
  const endItem = Math.min((searchParams.page + 1) * PAGE_SIZE, totalElements);

  return (
    <section className="dashboard-section device-rule-tab">
      <div className="user-toolbar">
        <div className="search-container">
          <input
            type="text"
            placeholder="Tìm kiếm metric..."
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

        {canManageRules ? (
          <div className="device-actions">
            <button
              type="button"
              className="add-device-button"
              onClick={handleNavigateCreate}
            >
              + Thêm luật cảnh báo
            </button>

            <button
              type="button"
              className="device-action-dropdown-button"
              onClick={() =>
                setIsDropdownOpen((prev) => !prev)
              }
              title="Thao tác luật cảnh báo"
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
                  🗑️ Xóa luật
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
                  📥 Thêm luật từ file
                </button>

                {canExportRules && (
                  <button
                    type="button"
                    className="device-dropdown-item"
                    onClick={handleExport}
                  >
                    📤 Xuất file luật
                  </button>
                )}
              </div>
            )}
          </div>
        ) : (
          canExportRules && (
            <button
              type="button"
              className="search-button"
              onClick={handleExport}
            >
              📤 Xuất file luật
            </button>
          )
        )}
      </div>

      <div className="user-table-wrapper">
        <table className="user-table">
          <thead>
            <tr>
              {canManageRules && (
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
                onClick={() => handleSort('metricType')}
                style={{ cursor: 'pointer' }}
              >
                Tên Metric
                {renderSortIcon('metricType')}
              </th>

              <th
                onClick={() => handleSort('warningLimit')}
                style={{ cursor: 'pointer' }}
              >
                Ngưỡng Warning
                {renderSortIcon('warningLimit')}
              </th>

              <th
                onClick={() => handleSort('criticalLimit')}
                style={{ cursor: 'pointer' }}
              >
                Ngưỡng Critical
                {renderSortIcon('criticalLimit')}
              </th>

              <th
                onClick={() => handleSort('consecutiveOccurrences')}
                style={{ cursor: 'pointer' }}
              >
                Số lần liên tiếp
                {renderSortIcon('consecutiveOccurrences')}
              </th>

              <th
                onClick={() => handleSort('isEnabled')}
                style={{ cursor: 'pointer' }}
              >
                Trạng thái
                {renderSortIcon('isEnabled')}
              </th>
            </tr>
          </thead>

          <tbody>
            {rules.length > 0 ? (
              rules.map((rule) => (
                <tr
                  key={rule.id}
                  className="clickable-row"
                  onClick={() => navigate(`/threshold-rules/${rule.id}`)}
                >
                  {canManageRules && (
                    <td
                      style={{ textAlign: 'center' }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <input
                        type="checkbox"
                        checked={selectedIds.includes(rule.id)}
                        onChange={(e) =>
                          handleSelectOne(e, rule)
                        }
                      />
                    </td>
                  )}

                  <td>
                    <code className="rule-metric-code">
                      {rule.metricType || '—'}
                    </code>
                  </td>

                  <td>
                    {rule.warningLimit !== null && rule.warningLimit !== undefined ? (
                      <span className="rule-limit-badge rule-limit-warning">
                        {rule.warningLimit}
                      </span>
                    ) : (
                      '—'
                    )}
                  </td>

                  <td>
                    {rule.criticalLimit !== null && rule.criticalLimit !== undefined ? (
                      <span className="rule-limit-badge rule-limit-critical">
                        {rule.criticalLimit}
                      </span>
                    ) : (
                      '—'
                    )}
                  </td>

                  <td>{rule.consecutiveOccurrences ?? '—'}</td>

                  <td>
                    {renderStatus(rule.isEnabled)}
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan={canManageRules ? 6 : 5}
                  className="empty-user-table"
                >
                  {searchParams.keyword
                    ? `Không tìm thấy luật cảnh báo nào khớp với "${searchParams.keyword}".`
                    : 'Chưa có luật cảnh báo nào cho thiết bị này.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {totalElements > 0 && (
        <div className="device-rule-pagination">
          <span className="pagination-info">
            Hiển thị <strong>{startItem}–{endItem}</strong> trong tổng số{' '}
            <strong>{totalElements}</strong> luật cảnh báo ({totalPages} trang)
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

      {canManageRules && showImport && (
        <div className="import-modal-overlay">
          <div className="import-modal">
            <h3>Import danh sách luật cảnh báo</h3>

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

export default TabDeviceRule;