import React, { useState } from 'react';

const TabDevices = () => {
  const [isActionDropdownOpen, setIsActionDropdownOpen] = useState(false);
  const [devices] = useState([]);

  return (
    <section className="dashboard-section">
      <div className="section-header">
        <h3>Quản Lý Thiết Bị Kết Nối</h3>

        <div className="device-actions">
          <button
            className="add-device-button"
            onClick={() => alert('Chức năng thêm thiết bị đang được mở...')}
          >
            + Thêm Thiết Bị
          </button>

          <button
            className="device-action-dropdown-button"
            onClick={() => setIsActionDropdownOpen(!isActionDropdownOpen)}
          >
            ▾
          </button>

          {isActionDropdownOpen && (
            <div className="device-dropdown">
              <button
                className="device-dropdown-item delete-item"
                onClick={() => {
                  alert('Chọn thiết bị cần xóa');
                  setIsActionDropdownOpen(false);
                }}
              >
                🗑️ Xóa thiết bị
              </button>

              <button
                className="device-dropdown-item"
                onClick={() => {
                  alert('Xuất file cấu hình');
                  setIsActionDropdownOpen(false);
                }}
              >
                📄 Export cấu hình
              </button>
            </div>
          )}
        </div>
      </div>

      {devices.length === 0 ? (
        <div className="empty-device-container">
          <p>Chưa có thiết bị nào được kết nối tới hệ thống.</p>

          <button
            className="add-device-now-button"
            onClick={() => alert('Chức năng thêm thiết bị đang được mở...')}
          >
            + Thêm Thiết Bị Ngay
          </button>
        </div>
      ) : (
        <div className="device-table-placeholder">
          Hiển thị bảng thiết bị ở đây...
        </div>
      )}
    </section>
  );
};

export default TabDevices;