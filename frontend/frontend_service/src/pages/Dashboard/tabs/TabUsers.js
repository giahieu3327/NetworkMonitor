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

  // Fetch danh sách người dùng qua GET /api/v1/users
  const fetchUsers = useCallback(async () => {
    try {
      const res = await axiosClient.get('/users', {
        params: {
          keyword: searchParams.keyword,
          page: searchParams.page,
          size: searchParams.size,
          sortBy: searchParams.sortBy,
          direction: searchParams.direction
        }
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

  // Kiểm tra xem tài khoản hiện tại có quyền thao tác trên targetUser hay không
  const canManageUser = (targetUser) => {
    if (!currentUser || !targetUser) return false;

    // 1. Không thể thao tác với admin
    if (targetUser.username === 'admin') return false;

    // 2. Không thể tự thao tác với chính mình
    if (targetUser.id === currentUser.id) return false;

    // 3. Nếu targetUser là ROLE_SUPER_ADMIN -> Chỉ tài khoản admin gốc mới được thao tác
    const isTargetSuperAdmin =
      targetUser.roleName === 'ROLE_SUPER_ADMIN' || targetUser.roleName === 'SUPER_ADMIN';

    if (isTargetSuperAdmin && currentUser.username !== 'admin') {
      return false;
    }

    return true;
  };

  const handleSearch = () => {
    setSearchParams((prev) => ({
      ...prev,
      keyword: keyword.trim(),
      page: 0
    }));
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleSort = (field) => {
    setSearchParams((prev) => {
      const isSameField = prev.sortBy === field;
      const newDirection = isSameField && prev.direction === 'ASC' ? 'DESC' : 'ASC';
      return {
        ...prev,
        sortBy: field,
        direction: newDirection,
        page: 0
      };
    });
  };

  const renderSortIcon = (field) => {
    if (searchParams.sortBy !== field) return <span style={{ opacity: 0.3, marginLeft: '4px' }}>↕</span>;
    return searchParams.direction === 'ASC' ? (
      <span style={{ marginLeft: '4px' }}>↑</span>
    ) : (
      <span style={{ marginLeft: '4px' }}>↓</span>
    );
  };

  // Danh sách user đủ điều kiện để chọn checkbox
  const getSelectableUsers = () => users.filter((u) => canManageUser(u));

  const handleSelectAll = (e) => {
    if (e.target.checked) {
      const allSelectableIds = getSelectableUsers().map((u) => u.id);
      setSelectedIds(allSelectableIds);
    } else {
      setSelectedIds([]);
    }
  };

  const handleSelectOne = (e, user) => {
    e.stopPropagation();
    if (!canManageUser(user)) return;

    setSelectedIds((prev) =>
      prev.includes(user.id) ? prev.filter((id) => id !== user.id) : [...prev, user.id]
    );
  };

  // Gọi endpoint DELETE /api/v1/users xóa hàng loạt
  const handleBulkDelete = async () => {
    setIsDropdownOpen(false);
    const targetIds = selectedIds.filter((id) => {
      const u = users.find((item) => item.id === id);
      return u && canManageUser(u);
    });

    if (targetIds.length === 0) {
      alert('Không có người dùng hợp lệ nào được chọn để xóa!');
      return;
    }

    if (!window.confirm(`Bạn có chắc chắn muốn xóa ${targetIds.length} người dùng đã chọn?`)) {
      return;
    }

    try {
      await axiosClient.delete('/users', {
        data: targetIds
      });
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Có lỗi xảy ra khi xóa người dùng!');
    }
  };

  // Cập nhật trạng thái Active / Deactive hàng loạt qua PUT /api/v1/users/{id}
  const handleBulkStatusChange = async (isActive) => {
    setIsDropdownOpen(false);
    const targetIds = selectedIds.filter((id) => {
      const u = users.find((item) => item.id === id);
      return u && canManageUser(u);
    });

    if (targetIds.length === 0) {
      alert(`Không có người dùng hợp lệ nào được chọn để ${isActive ? 'kích hoạt' : 'vô hiệu hóa'}!`);
      return;
    }

    const actionText = isActive ? 'kích hoạt' : 'vô hiệu hóa';
    if (!window.confirm(`Bạn có chắc chắn muốn ${actionText} ${targetIds.length} người dùng đã chọn?`)) {
      return;
    }

    try {
      await Promise.all(
        targetIds.map((id) => axiosClient.put(`/users/${id}`, { isActive }))
      );
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || `Có lỗi xảy ra khi ${actionText} người dùng!`);
    }
  };

  const handleRowClick = (userId) => {
    navigate(`/users/${userId}`);
  };

  const isSuperAdmin =
    currentUser?.roleName === 'ROLE_SUPER_ADMIN' ||
    currentUser?.roleName === 'SUPER_ADMIN';

  if (!isSuperAdmin) {
    return (
      <div className="permission-message">
        Bạn đang đăng nhập với quyền User thông thường, không có quyền xem quản lý tài khoản.
      </div>
    );
  }

  const selectableUsers = getSelectableUsers();
  const isAllSelected =
    selectableUsers.length > 0 && selectableUsers.every((u) => selectedIds.includes(u.id));

  return (
    <section className="dashboard-section">
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
          <button className="add-device-button" onClick={() => navigate('/register')}>
            + Thêm Người Dùng
          </button>

          <button
            className="device-action-dropdown-button"
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            title="Thao tác hàng loạt"
          >
            ▾
          </button>

          {isDropdownOpen && (
            <div className="device-dropdown">
              <button className="device-dropdown-item" onClick={() => handleBulkStatusChange(true)}>
                🔓 Active người dùng {selectedIds.length > 0 && `(${selectedIds.length})`}
              </button>

              <button className="device-dropdown-item" onClick={() => handleBulkStatusChange(false)}>
                🔒 Deactive người dùng {selectedIds.length > 0 && `(${selectedIds.length})`}
              </button>

              <button className="device-dropdown-item delete-item" onClick={handleBulkDelete}>
                🗑️ Xóa người dùng {selectedIds.length > 0 && `(${selectedIds.length})`}
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="user-table-wrapper">
        <table className="user-table">
          <thead>
            <tr>
              <th style={{ width: '40px', textAlign: 'center' }}>
                <input type="checkbox" checked={isAllSelected} onChange={handleSelectAll} />
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
                    <td style={{ textAlign: 'center' }} onClick={(e) => e.stopPropagation()}>
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
                          style={{ color: '#ef4444', fontWeight: 'bold', marginLeft: '4px' }}
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
              onClick={() => setSearchParams((prev) => ({ ...prev, page: prev.page - 1 }))}
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
              onClick={() => setSearchParams((prev) => ({ ...prev, page: prev.page + 1 }))}
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
    </section>
  );
};

export default TabUsers;