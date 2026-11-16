
import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../../api/axiosClient';

const TabUsers = ({ currentUser }) => {
  const navigate = useNavigate();

  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [selectedIds, setSelectedIds] = useState([]);

  const [keyword, setKeyword] = useState('');
  const [searchParams, setSearchParams] = useState({
    keyword: '',
    page: 0,
    size: 10,
    sortBy: 'createdAt',
    direction: 'DESC'
  });

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const [showImport, setShowImport] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);

  // Fetch danh sách người dùng
  const fetchUsers = useCallback(async () => {
    try {
      const res = await axiosClient.get('/users', {
        params: searchParams
      });

      const data = res.data?.data ?? res.data;

      setUsers(data?.content ?? []);
      setTotalElements(data?.totalElements ?? 0);
      setTotalPages(data?.totalPages ?? 1);
      setSelectedIds([]);
    } catch (err) {
      console.error('Lỗi tải danh sách người dùng:', err);
    }
  }, [searchParams]);

  useEffect(() => {
    if (currentUser) {
      fetchUsers();
    }
  }, [currentUser, fetchUsers]);

  // Kiểm tra quyền thao tác user
  const canManageUser = (targetUser) => {
    if (!currentUser || !targetUser) return false;
    if (targetUser.username === 'admin') return false;
    if (targetUser.id === currentUser.id) return false;

    const isTargetSuperAdmin =
      targetUser.roleName === 'ROLE_SUPER_ADMIN' ||
      targetUser.roleName === 'SUPER_ADMIN';

    if (isTargetSuperAdmin && currentUser.username !== 'admin') {
      return false;
    }

    return true;
  };

  // Tìm kiếm
  const handleSearch = () => {
    setSearchParams((prev) => ({
      ...prev,
      keyword: keyword.trim(),
      page: 0
    }));
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleSearch();
  };

  // Sắp xếp
  const handleSort = (field) => {
    setSearchParams((prev) => ({
      ...prev,
      sortBy: field,
      direction:
        prev.sortBy === field && prev.direction === 'ASC'
          ? 'DESC'
          : 'ASC',
      page: 0
    }));
  };

  const renderSortIcon = (field) => {
    if (searchParams.sortBy !== field) {
      return <span style={{ opacity: 0.3, marginLeft: '4px' }}>↕</span>;
    }

    return searchParams.direction === 'ASC'
      ? <span style={{ marginLeft: '4px' }}>↑</span>
      : <span style={{ marginLeft: '4px' }}>↓</span>;
  };

  // Chọn user
  const getSelectableUsers = () =>
    users.filter((u) => canManageUser(u));

  const handleSelectAll = (e) => {
    setSelectedIds(
      e.target.checked
        ? getSelectableUsers().map((u) => u.id)
        : []
    );
  };

  const handleSelectOne = (e, user) => {
    e.stopPropagation();

    if (!canManageUser(user)) return;

    setSelectedIds((prev) =>
      prev.includes(user.id)
        ? prev.filter((id) => id !== user.id)
        : [...prev, user.id]
    );
  };

  // Lấy danh sách user hợp lệ được chọn
  const getTargetIds = () =>
    selectedIds.filter((id) => {
      const user = users.find((u) => u.id === id);
      return user && canManageUser(user);
    });

  // Xóa nhiều user
  const handleBulkDelete = async () => {
    setIsDropdownOpen(false);

    const targetIds = getTargetIds();

    if (targetIds.length === 0) {
      alert('Không có người dùng hợp lệ nào được chọn để xóa!');
      return;
    }

    if (!window.confirm(
      `Bạn có chắc chắn muốn xóa ${targetIds.length} người dùng đã chọn?`
    )) return;

    try {
      await axiosClient.delete('/users', {
        data: targetIds
      });

      await fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Có lỗi xảy ra khi xóa người dùng!');
    }
  };

  // Active / Deactive nhiều user
  const handleBulkStatusChange = async (isActive) => {
    setIsDropdownOpen(false);

    const targetIds = getTargetIds();

    if (targetIds.length === 0) {
      alert('Không có người dùng hợp lệ nào được chọn!');
      return;
    }

    const actionText = isActive ? 'kích hoạt' : 'vô hiệu hóa';

    if (!window.confirm(
      `Bạn có chắc chắn muốn ${actionText} ${targetIds.length} người dùng đã chọn?`
    )) return;

    try {
      await Promise.all(
        targetIds.map((id) =>
          axiosClient.put(`/users/${id}`, { isActive })
        )
      );

      await fetchUsers();
    } catch (err) {
      alert(
        err.response?.data?.message ||
        `Có lỗi xảy ra khi ${actionText} người dùng!`
      );
    }
  };

  // Tải file Excel
  const downloadExcel = (data, fileName) => {
    const url = window.URL.createObjectURL(
      new Blob([data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
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

  // Export user
  const handleExport = async () => {
    setIsDropdownOpen(false);

    try {
      const res = await axiosClient.get('/auth/export', {
        responseType: 'blob'
      });

      downloadExcel(res.data, 'users.xlsx');
    } catch (err) {
      console.error(err);
      alert('Lỗi xuất file người dùng!');
    }
  };

  // Tải file template
  const handleDownloadTemplate = async () => {
    try {
      const res = await axiosClient.get('/auth/import/template', {
        responseType: 'blob'
      });

      downloadExcel(res.data, 'users-import-template.xlsx');
    } catch (err) {
      console.error(err);
      alert('Lỗi tải file mẫu!');
    }
  };

  // Import user
  const handleImport = async (e) => {
    e.preventDefault();

    if (!importFile) {
      alert('Vui lòng chọn file Excel!');
      return;
    }

    const formData = new FormData();
    formData.append('file', importFile);
    

    try {
      setImporting(true);

      const res = await axiosClient.post('/auth/import', formData);
      const result = res.data?.data ?? {};

      const message = [
        res.data?.message || 'Import hoàn tất!',
        result.totalRows !== undefined && `Tổng số dòng: ${result.totalRows}`,
        result.successCount !== undefined && `Thành công: ${result.successCount}`,
        result.failedCount !== undefined && `Thất bại: ${result.failedCount}`,
        result.skippedCount !== undefined && `Bỏ qua: ${result.skippedCount}`
      ].filter(Boolean).join('\n');

      alert(message);

      setShowImport(false);
      setImportFile(null);
      await fetchUsers();
    } catch (err) {
      console.error('Lỗi import:', err);
      alert(err.response?.data?.message || 'Import người dùng thất bại!');
    } finally {
      setImporting(false);
    }
  };

  // Xem chi tiết user
  const handleRowClick = (userId) => {
    navigate(`/users/${userId}`);
  };

  const isSuperAdmin =
    currentUser?.roleName === 'ROLE_SUPER_ADMIN' ||
    currentUser?.roleName === 'SUPER_ADMIN';

  if (!isSuperAdmin) {
    return (
      <div className="permission-message">
        Bạn đang đăng nhập với quyền User thông thường,
        không có quyền xem quản lý tài khoản.
      </div>
    );
  }

  const selectableUsers = getSelectableUsers();

  const isAllSelected =
    selectableUsers.length > 0 &&
    selectableUsers.every((u) => selectedIds.includes(u.id));

  return (
    <section className="dashboard-section">

      {/* TOOLBAR */}
      <div className="user-toolbar">
        <div className="search-container">
          <input
            type="text"
            placeholder="Tìm kiếm username, email, họ tên..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={handleKeyDown}
            className="user-search-input"
          />

          <button onClick={handleSearch} className="search-button">
            Tìm kiếm
          </button>
        </div>

        <div className="device-actions">

          {/* Nút thêm người dùng riêng */}
          <button
            className="add-device-button"
            onClick={() => navigate('/register')}
          >
            + Thêm Người Dùng
          </button>

          {/* Dropdown thao tác hàng loạt */}
          <button
            className="device-action-dropdown-button"
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            title="Thao tác hàng loạt"
          >
            ▾
          </button>

          {isDropdownOpen && (
            <div className="device-dropdown">

              <button
                className="device-dropdown-item"
                onClick={() => handleBulkStatusChange(true)}
              >
                🔓 Active người dùng
                {selectedIds.length > 0 && ` (${selectedIds.length})`}
              </button>

              <button
                className="device-dropdown-item"
                onClick={() => handleBulkStatusChange(false)}
              >
                🔒 Deactive người dùng
                {selectedIds.length > 0 && ` (${selectedIds.length})`}
              </button>

              <button
                className="device-dropdown-item delete-item"
                onClick={handleBulkDelete}
              >
                🗑️ Xóa người dùng
                {selectedIds.length > 0 && ` (${selectedIds.length})`}
              </button>

              <button
                className="device-dropdown-item"
                onClick={() => {
                  setIsDropdownOpen(false);
                  setImportFile(null);
                  setShowImport(true);
                }}
              >
                📥 Thêm user từ file
              </button>

              <button
                className="device-dropdown-item"
                onClick={handleExport}
              >
                📤 Xuất file user
              </button>

            </div>
          )}
        </div>
      </div>

      {/* USER TABLE */}
      <div className="user-table-wrapper">
        <table className="user-table">
          <thead>
            <tr>
              <th style={{ width: '40px', textAlign: 'center' }}>
                <input
                  type="checkbox"
                  checked={isAllSelected}
                  onChange={handleSelectAll}
                />
              </th>

              <th onClick={() => handleSort('username')} style={{ cursor: 'pointer' }}>
                Username {renderSortIcon('username')}
              </th>

              <th onClick={() => handleSort('fullName')} style={{ cursor: 'pointer' }}>
                Họ và Tên {renderSortIcon('fullName')}
              </th>

              <th onClick={() => handleSort('email')} style={{ cursor: 'pointer' }}>
                Email {renderSortIcon('email')}
              </th>

              <th>Số Điện Thoại</th>

              <th onClick={() => handleSort('roleName')} style={{ cursor: 'pointer' }}>
                Vai trò {renderSortIcon('roleName')}
              </th>

              <th onClick={() => handleSort('isActive')} style={{ cursor: 'pointer' }}>
                Trạng thái {renderSortIcon('isActive')}
              </th>
            </tr>
          </thead>

          <tbody>
            {users.length > 0 ? (
              users.map((u) => {
                const isSelected = selectedIds.includes(u.id);
                const isManageable = canManageUser(u);

                return (
                  <tr
                    key={u.id}
                    className="clickable-row"
                    onClick={() => handleRowClick(u.id)}
                  >
                    <td
                      style={{ textAlign: 'center' }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <input
                        type="checkbox"
                        disabled={!isManageable}
                        checked={isSelected}
                        onChange={(e) => handleSelectOne(e, u)}
                      />
                    </td>

                    <td>{u.username}</td>
                    <td>{u.fullName}</td>

                    <td>
                      {u.email}
                      {!u.emailVerified && (
                        <span
                          style={{
                            color: '#ef4444',
                            fontWeight: 'bold',
                            marginLeft: '4px'
                          }}
                          title="Chưa xác thực email"
                        >
                          (*)
                        </span>
                      )}
                    </td>

                    <td>{u.phoneNumber || 'N/A'}</td>
                    <td>{u.roleName}</td>

                    <td>
                      <span className={u.isActive ? 'status-active' : 'status-inactive'}>
                        {u.isActive ? 'HOẠT ĐỘNG' : 'ĐÃ KHÓA'}
                      </span>
                    </td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td colSpan="7" className="empty-user-table">
                  Không tìm thấy người dùng nào.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* PAGINATION */}
      {totalPages > 1 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: '16px',
            fontSize: '13.5px'
          }}
        >
          <span>
            Hiển thị {users.length} / {totalElements} kết quả
          </span>

          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <button
              disabled={searchParams.page === 0}
              onClick={() =>
                setSearchParams((prev) => ({
                  ...prev,
                  page: prev.page - 1
                }))
              }
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border-light)',
                cursor: searchParams.page === 0 ? 'not-allowed' : 'pointer',
                opacity: searchParams.page === 0 ? 0.5 : 1
              }}
            >
              Trang trước
            </button>

            <span>
              Trang <strong>{searchParams.page + 1}</strong> / {totalPages}
            </span>

            <button
              disabled={searchParams.page + 1 >= totalPages}
              onClick={() =>
                setSearchParams((prev) => ({
                  ...prev,
                  page: prev.page + 1
                }))
              }
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border-light)',
                cursor: searchParams.page + 1 >= totalPages ? 'not-allowed' : 'pointer',
                opacity: searchParams.page + 1 >= totalPages ? 0.5 : 1
              }}
            >
              Trang sau
            </button>
          </div>
        </div>
      )}

      {/* IMPORT MODAL */}
      {showImport && (
        <div className="import-modal-overlay">
          <div className="import-modal">
            <h3>Import danh sách người dùng</h3>

            <form onSubmit={handleImport}>
              <input
                type="file"
                accept=".xlsx,.xls"
                onChange={(e) => setImportFile(e.target.files?.[0] || null)}
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
                  {importing ? 'Đang gửi...' : 'Xác nhận / Gửi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </section>
  );
};

export default TabUsers;
