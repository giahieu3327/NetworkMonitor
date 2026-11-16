import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../../api/axiosClient';
import './TabDeviceInterfaces.css';

const PAGE_SIZE = 10;

const INTERFACE_TYPE_MAP = {
  1: 'Khác (Other)',
  6: 'Ethernet',
  7: 'Ethernet (802.3)',
  22: 'Point-to-Point Serial',
  23: 'PPP',
  24: 'Loopback',
  32: 'Frame Relay',
  53: 'Virtual / VLAN',
  131: 'Tunnel',
  135: 'Layer 2 VLAN',
  136: 'Layer 3 IP VLAN',
  161: 'IEEE 802.3ad LAG (Port-Channel)',
};

const formatInterfaceType = (type) => {
  if (type === null || type === undefined || type === '') return 'UNKNOWN';
  return INTERFACE_TYPE_MAP[type] || `UNKNOWN (${type})`;
};

const TabDeviceInterfaces = ({ deviceId, device }) => {
  const navigate = useNavigate();

  const [interfaces, setInterfaces] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const [keyword, setKeyword] = useState('');

  const [searchParams, setSearchParams] = useState({
    keyword: '',
    page: 0,
    size: PAGE_SIZE,
    sortBy: 'interfaceIndex',
    direction: 'ASC',
  });

  const fetchInterfaces = useCallback(async () => {
    if (!deviceId) return;

    try {
      const res = await axiosClient.get(`/devices/${deviceId}/interfaces`, {
        params: {
          ...searchParams,
          size: PAGE_SIZE,
        },
      });

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

      setInterfaces(content);
      setTotalElements(total);
      setTotalPages(calculatedPages);
    } catch (err) {
      console.error('Lỗi tải danh sách interface:', err);
      setInterfaces([]);
      setTotalElements(0);
      setTotalPages(1);
    }
  }, [deviceId, searchParams]);

  useEffect(() => {
    fetchInterfaces();
  }, [fetchInterfaces]);

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

  const renderOperStatus = (operStatus) => {
    const normalized = String(operStatus || '').toUpperCase();

    if (normalized === 'UP') {
      return (
        <span className="status-active">
          UP
        </span>
      );
    }

    if (normalized === 'DOWN') {
      return (
        <span className="status-inactive">
          DOWN
        </span>
      );
    }

    return (
      <span className="status-unknown">
        {normalized || 'UNKNOWN'}
      </span>
    );
  };

  const startItem = totalElements === 0 ? 0 : searchParams.page * PAGE_SIZE + 1;
  const endItem = Math.min((searchParams.page + 1) * PAGE_SIZE, totalElements);

  return (
    <section className="dashboard-section interface-tab">
      <div className="user-toolbar">
        <div className="search-container">
          <input
            type="text"
            placeholder="Tìm kiếm tên interface, địa chỉ IP..."
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
      </div>

      <div className="user-table-wrapper">
        <table className="user-table">
          <thead>
            <tr>
              <th
                onClick={() => handleSort('interfaceIndex')}
                style={{ cursor: 'pointer', width: '110px' }}
              >
                Index
                {renderSortIcon('interfaceIndex')}
              </th>

              <th
                onClick={() => handleSort('interfaceName')}
                style={{ cursor: 'pointer' }}
              >
                Tên Interface
                {renderSortIcon('interfaceName')}
              </th>

              <th
                onClick={() => handleSort('interfaceType')}
                style={{ cursor: 'pointer' }}
              >
                Loại Interface
                {renderSortIcon('interfaceType')}
              </th>

              <th
                onClick={() => handleSort('ipAddress')}
                style={{ cursor: 'pointer' }}
              >
                Địa chỉ IP
                {renderSortIcon('ipAddress')}
              </th>

              <th
                onClick={() => handleSort('operStatus')}
                style={{ cursor: 'pointer' }}
              >
                Trạng thái
                {renderSortIcon('operStatus')}
              </th>
            </tr>
          </thead>

          <tbody>
            {interfaces.length > 0 ? (
              interfaces.map((item) => (
                <tr
                  key={item.interfaceIndex}
                  className="clickable-row"
                  onClick={() =>
                    navigate(
                      `/devices/${deviceId}/interfaces/${item.interfaceIndex}`,
                      { state: { device, interfaceItem: item } }
                    )
                  }
                >
                  <td>{item.interfaceIndex ?? '—'}</td>

                  <td>{item.interfaceName || '—'}</td>

                  <td>
                    <span className="interface-type-badge">
                      {formatInterfaceType(item.interfaceType)}
                    </span>
                  </td>

                  <td>{item.ipAddress || '—'}</td>

                  <td>{renderOperStatus(item.operStatus)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan="5"
                  className="empty-user-table"
                >
                  {searchParams.keyword
                    ? `Không tìm thấy interface nào khớp với "${searchParams.keyword}".`
                    : 'Chưa có interface nào cho thiết bị này.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {totalElements > 0 && (
        <div className="interface-pagination">
          <span className="pagination-info">
            Hiển thị <strong>{startItem}–{endItem}</strong> trong tổng số{' '}
            <strong>{totalElements}</strong> interface ({totalPages} trang)
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
    </section>
  );
};

export default TabDeviceInterfaces;