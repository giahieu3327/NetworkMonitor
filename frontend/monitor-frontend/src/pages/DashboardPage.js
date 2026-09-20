import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../api/axiosClient';

const DashboardPage = () => {
  const [me, setMe] = useState(null);
  const [users, setUsers] = useState([]);
  
  // State quản lý ô Input và State thực sự dùng để Search
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  
  // State quản lý Tab & Sidebar
  const [activeTab, setActiveTab] = useState('devices'); // 'devices' hoặc 'users'
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isActionDropdownOpen, setIsActionDropdownOpen] = useState(false);

  // Mock dữ liệu thiết bị
  const [devices] = useState([]); 

  const navigate = useNavigate();

  // fetchUsers chỉ phụ thuộc vào searchKeyword (chỉ gọi API khi searchKeyword thay đổi)
  const fetchUsers = useCallback(async () => {
    try {
      const res = await axiosClient.get('/users', { params: { keyword: searchKeyword, page: 0, size: 20 } });
      setUsers(res.data.content || []);
    } catch (err) {
      console.error(err);
    }
  }, [searchKeyword]);

  useEffect(() => {
    const fetchMe = async () => {
      try {
        const res = await axiosClient.get('/auth/me');
        setMe(res.data);
      } catch (err) {
        navigate('/login');
      }
    };

    fetchMe();
    fetchUsers();
  }, [navigate, fetchUsers]);

  // Hàm xử lý khi bấm nút Tìm kiếm
  const handleSearch = () => {
    setSearchKeyword(keyword);
  };

  // Hàm xử lý khi nhấn phím Enter trong ô input
  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleToggleStatus = async (id, currentStatus) => {
    try {
      await axiosClient.put(`/users/${id}/status`, { isActive: !currentStatus });
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Cập nhật trạng thái thất bại!');
    }
  };

  const handleDeleteUser = async (id, username) => {
    if (!window.confirm(`Bạn có chắc muốn xóa tài khoản [${username}]?`)) return;
    try {
      await axiosClient.delete(`/users/${id}`);
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Xóa tài khoản thất bại!');
    }
  };

  const handleLogout = async () => {
    try {
      const refreshToken = localStorage.getItem('refresh_token');
      if (refreshToken) await axiosClient.post('/auth/logout', { refreshToken });
    } finally {
      localStorage.clear();
      navigate('/login');
    }
  };

  const isSuperAdmin = me?.roles?.includes('ROLE_SUPER_ADMIN');

  return (
    <div style={{ backgroundColor: '#0f172a', color: '#fff', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      
      {/* ==================== PHẦN TÊN/HEADER (2 BÊN) ==================== */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 24px', backgroundColor: '#1e293b', borderBottom: '1px solid #334155' }}>
        {/* Bên trái: Nút 3 gạch & Tiêu đề */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <button 
            onClick={() => setIsSidebarOpen(!isSidebarOpen)} 
            style={{ background: 'none', border: 'none', color: '#fff', fontSize: '24px', cursor: 'pointer', padding: '4px 8px', borderRadius: '4px', backgroundColor: '#334155' }}
            title="Mở menu điều hướng"
          >
            ☰
          </button>
          <h2 style={{ margin: 0, fontSize: '20px', color: '#38bdf8' }}>MONITOR SYSTEM DASHBOARD</h2>
        </div>

        {/* Bên phải: Nút Đăng xuất & Profile */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <span style={{ fontSize: '14px', color: '#94a3b8' }}>
            Xin chào: <strong style={{ color: '#fff' }}>{me?.fullName}</strong> ({me?.username})
          </span>
          <button
            onClick={handleLogout}
            style={{ padding: '8px 16px', backgroundColor: '#ef4444', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '14px' }}
          >
            Đăng Xuất
          </button>
        </div>
      </header>

      {/* ==================== SIDEBAR MENU (TRƯỢT TỪ TRÁI SANG) ==================== */}
      {isSidebarOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', zIndex: 1000, display: 'flex' }}>
          {/* Overlay nền tối */}
          <div onClick={() => setIsSidebarOpen(false)} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)' }} />

          {/* Menu Panel */}
          <div style={{ position: 'absolute', top: 0, left: 0, width: '260px', height: '100%', backgroundColor: '#1e293b', padding: '20px', boxShadow: '4px 0 10px rgba(0,0,0,0.5)', display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', paddingBottom: '12px', borderBottom: '1px solid #334155' }}>
              <h3 style={{ margin: 0, color: '#38bdf8', fontSize: '18px' }}>DANH MỤC MENU</h3>
              <button onClick={() => setIsSidebarOpen(false)} style={{ background: 'none', border: 'none', color: '#94a3b8', fontSize: '20px', cursor: 'pointer' }}>✕</button>
            </div>

            <nav style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <button
                onClick={() => { setActiveTab('devices'); setIsSidebarOpen(false); }}
                style={{ ...navButtonStyle, backgroundColor: activeTab === 'devices' ? '#3b82f6' : 'transparent' }}
              >
                🌐 Tab 1: Giám sát thiết bị
              </button>

              <button
                onClick={() => { setActiveTab('users'); setIsSidebarOpen(false); }}
                style={{ ...navButtonStyle, backgroundColor: activeTab === 'users' ? '#3b82f6' : 'transparent' }}
              >
                👥 Tab 2: Quản lý người dùng
              </button>
            </nav>
          </div>
        </div>
      )}

      {/* ==================== PHẦN THÂN (HIỂN THỊ NỘI DUNG THEO TAB) ==================== */}
      <main style={{ padding: '24px', flex: 1 }}>
        
        {/* ------------ TAB 1: GIÁM SÁT THIẾT BỊ ------------ */}
        {activeTab === 'devices' && (
          <div>
            {/* Thanh thao tác chứa Split Button */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h3 style={{ margin: 0, fontSize: '18px' }}>Danh Sách Thiết Bị Kết Nối</h3>

              {/* Split Button: Nút Thêm + Mũi tên xổ xuống */}
              <div style={{ position: 'relative', display: 'inline-flex' }}>
                <button
                  onClick={() => alert('Chức năng thêm thiết bị đang được mở...')}
                  style={{ padding: '10px 16px', backgroundColor: '#10b981', color: '#fff', border: 'none', borderTopLeftRadius: '6px', borderBottomLeftRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
                >
                  + Thêm Thiết Bị
                </button>
                <button
                  onClick={() => setIsActionDropdownOpen(!isActionDropdownOpen)}
                  style={{ padding: '10px 10px', backgroundColor: '#059669', color: '#fff', border: 'none', borderTopRightRadius: '6px', borderBottomRightRadius: '6px', cursor: 'pointer', borderLeft: '1px solid #047857' }}
                >
                  ▾
                </button>

                {/* Dropdown menu lựa chọn khác */}
                {isActionDropdownOpen && (
                  <div style={{ position: 'absolute', top: '100%', right: 0, marginTop: '4px', backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '6px', width: '180px', boxShadow: '0 4px 6px rgba(0,0,0,0.3)', zIndex: 10 }}>
                    <button
                      onClick={() => { alert('Chọn thiết bị cần xóa'); setIsActionDropdownOpen(false); }}
                      style={{ ...dropdownItemStyle, color: '#ef4444' }}
                    >
                      🗑️ Xóa thiết bị
                    </button>
                    <button
                      onClick={() => { alert('Xuất file cấu hình'); setIsActionDropdownOpen(false); }}
                      style={{ ...dropdownItemStyle, color: '#fff' }}
                    >
                      📄 Export cấu hình
                    </button>
                  </div>
                )}
              </div>
            </div>

            {/* Nội dung danh sách thiết bị */}
            {devices.length === 0 ? (
              <div style={{ padding: '40px', textAlign: 'center', backgroundColor: '#1e293b', borderRadius: '8px', border: '1px dashed #334155' }}>
                <p style={{ color: '#94a3b8', fontSize: '16px', marginBottom: '16px' }}>Chưa có thiết bị nào được kết nối tới hệ thống.</p>
                <button
                  onClick={() => alert('Chức năng thêm thiết bị đang được mở...')}
                  style={{ padding: '10px 20px', backgroundColor: '#10b981', color: '#fff', border: 'none', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
                >
                  + Thêm Thiết Bị Ngay
                </button>
              </div>
            ) : (
              <div>Hiển thị bảng thiết bị ở đây...</div>
            )}
          </div>
        )}

        {/* ------------ TAB 2: QUẢN LÝ NGƯỜI DÙNG ------------ */}
        {activeTab === 'users' && (
          <div>
            {isSuperAdmin ? (
              <div>
                {/* Thanh tìm kiếm và Nút Thêm Người Dùng */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                  <div style={{ display: 'flex', gap: '12px' }}>
                    <input
                      type="text"
                      placeholder="Tìm kiếm username, email, họ tên..."
                      value={keyword}
                      onChange={(e) => setKeyword(e.target.value)}
                      onKeyDown={handleKeyDown}
                      style={{ padding: '10px 14px', width: '320px', borderRadius: '6px', backgroundColor: '#1e293b', border: '1px solid #334155', color: '#fff', outline: 'none' }}
                    />
                    <button 
                      onClick={handleSearch} 
                      style={{ padding: '10px 18px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                      Tìm kiếm
                    </button>
                  </div>

                  <button
                    onClick={() => navigate('/register')}
                    style={{ padding: '10px 18px', backgroundColor: '#10b981', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}
                  >
                    + Thêm Người Dùng
                  </button>
                </div>

                {/* Bảng danh sách User */}
                <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#1e293b', borderRadius: '8px', overflow: 'hidden' }}>
                  <thead>
                    <tr style={{ backgroundColor: '#334155', textAlign: 'left' }}>
                      <th style={{ padding: '14px' }}>Username</th>
                      <th style={{ padding: '14px' }}>Họ và Tên</th>
                      <th style={{ padding: '14px' }}>Email</th>
                      <th style={{ padding: '14px' }}>Số Điện Thoại</th>
                      <th style={{ padding: '14px' }}>Trạng thái</th>
                      <th style={{ padding: '14px' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.map((u) => (
                      <tr key={u.id} style={{ borderBottom: '1px solid #334155' }}>
                        <td style={{ padding: '14px' }}>{u.username}</td>
                        <td style={{ padding: '14px' }}>{u.fullName}</td>
                        <td style={{ padding: '14px' }}>{u.email}</td>
                        <td style={{ padding: '14px' }}>{u.phoneNumber || 'N/A'}</td>
                        <td style={{ padding: '14px' }}>
                          <span style={{ color: u.isActive ? '#10b981' : '#ef4444', fontWeight: 'bold' }}>
                            {u.isActive ? 'HOẠT ĐỘNG' : 'ĐÃ KHÓA'}
                          </span>
                        </td>
                        <td style={{ padding: '14px' }}>
                          <button
                            onClick={() => handleToggleStatus(u.id, u.isActive)}
                            style={{ padding: '6px 14px', backgroundColor: u.isActive ? '#f59e0b' : '#10b981', color: '#fff', border: 'none', borderRadius: '4px', marginRight: '8px', cursor: 'pointer' }}
                          >
                            {u.isActive ? 'Khóa' : 'Mở khóa'}
                          </button>
                          {u.username !== 'admin' && (
                            <button
                              onClick={() => handleDeleteUser(u.id, u.username)}
                              style={{ padding: '6px 14px', backgroundColor: '#ef4444', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                            >
                              Xóa
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div style={{ padding: '20px', backgroundColor: '#1e293b', borderRadius: '8px' }}>
                Bạn đang đăng nhập với quyền User thông thường, không có quyền xem quản lý tài khoản.
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
};

const navButtonStyle = {
  width: '100%',
  padding: '12px 16px',
  textAlign: 'left',
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  cursor: 'pointer',
  fontSize: '14px',
  fontWeight: 'bold',
};

const dropdownItemStyle = {
  width: '100%',
  padding: '10px 14px',
  textAlign: 'left',
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: '13px',
};

export default DashboardPage;