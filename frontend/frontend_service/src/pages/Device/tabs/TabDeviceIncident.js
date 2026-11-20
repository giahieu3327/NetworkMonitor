import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../../api/axiosClient';
import './TabDeviceIncident.css';

const PAGE_SIZE = 10;

const TabDeviceIncident = ({ deviceId, device }) => {
  const navigate = useNavigate();

  const [summaries, setSummaries] = useState([]);
  const [loading, setLoading] = useState(false);

  const [keywordInput, setKeywordInput] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [sortBy, setSortBy] = useState('metricType');
  const [direction, setDirection] = useState('ASC');

  useEffect(() => {
    setPage(0);
    setKeywordInput('');
    setAppliedKeyword('');
    setSortBy('metricType');
    setDirection('ASC');
  }, [deviceId]);

  const fetchSummaries = useCallback(async () => {
    if (!deviceId) return;

    try {
      setLoading(true);
      const params = {
        page,
        size: PAGE_SIZE,
        sortBy,
        direction,
      };

      if (appliedKeyword) {
        params.keyword = appliedKeyword;
      }

      const res = await axiosClient.get(`/incidents/device/${deviceId}`, {
        params,
      });

      const pageData = res.data?.data;
      setSummaries(Array.isArray(pageData?.content) ? pageData.content : []);
      setTotalElements(pageData?.totalElements ?? 0);
      setTotalPages(Math.max(1, pageData?.totalPages ?? 1));
    } catch (err) {
      console.error('Lỗi tải thống kê sự cố của thiết bị:', err);
      setSummaries([]);
      setTotalElements(0);
      setTotalPages(1);
    } finally {
      setLoading(false);
    }
  }, [deviceId, page, appliedKeyword, sortBy, direction]);

  useEffect(() => {
    fetchSummaries();
  }, [fetchSummaries]);

  const handleSearch = () => {
    setAppliedKeyword(keywordInput.trim());
    setPage(0);
  };

  const handleClearSearch = () => {
    setKeywordInput('');
    setAppliedKeyword('');
    setPage(0);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handlePageChange = (newPage) => {
    if (newPage < 0 || newPage >= totalPages || newPage === page) {
      return;
    }
    setPage(newPage);
  };

  const paginationRange = useMemo(() => {
    const currentPage = page + 1;
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
  }, [page, totalPages]);

  const handleSort = (field) => {
    setSortBy((prevField) => {
      if (prevField === field) {
        setDirection((prevDir) => (prevDir === 'ASC' ? 'DESC' : 'ASC'));
        return prevField;
      }
      setDirection('ASC');
      return field;
    });
    setPage(0);
  };

  const renderSortIcon = (field) => {
    if (sortBy !== field) {
      return (
        <span style={{ opacity: 0.3, marginLeft: '4px' }}>
          ↕
        </span>
      );
    }

    return direction === 'ASC' ? (
      <span style={{ marginLeft: '4px' }}>↑</span>
    ) : (
      <span style={{ marginLeft: '4px' }}>↓</span>
    );
  };

  const formatMetricValue = (val) => {
    if (val === null || val === undefined) return '—';
    const num = Number(val);
    return Number.isFinite(num) ? Number(num.toFixed(2)) : '—';
  };

  const startItem = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
  const endItem = Math.min((page + 1) * PAGE_SIZE, totalElements);

  return (
    <section className="dashboard-section device-incident-tab">
      <div className="user-toolbar">
        <div className="search-container">
          <input
            type="text"
            placeholder="Tìm kiếm loại metric..."
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onKeyDown={handleKeyDown}
            className="user-search-input"
          />

          {keywordInput && (
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
                onClick={() => handleSort('metricType')}
                style={{ cursor: 'pointer' }}
              >
                Metric Type
                {renderSortIcon('metricType')}
              </th>

              <th
                onClick={() => handleSort('totalWarning')}
                style={{ cursor: 'pointer' }}
              >
                Tổng Warning
                {renderSortIcon('totalWarning')}
              </th>

              <th
                onClick={() => handleSort('totalCritical')}
                style={{ cursor: 'pointer' }}
              >
                Tổng Critical
                {renderSortIcon('totalCritical')}
              </th>

              <th
                onClick={() => handleSort('maxPeakValue')}
                style={{ cursor: 'pointer' }}
              >
                Peak Cao Nhất
                {renderSortIcon('maxPeakValue')}
              </th>

              <th
                onClick={() => handleSort('minPeakValue')}
                style={{ cursor: 'pointer' }}
              >
                Peak Thấp Nhất
                {renderSortIcon('minPeakValue')}
              </th>

              <th
                onClick={() => handleSort('avgPeakValue')}
                style={{ cursor: 'pointer' }}
              >
                Peak Trung Bình
                {renderSortIcon('avgPeakValue')}
              </th>
            </tr>
          </thead>

          <tbody>
            {loading ? (
              <tr>
                <td colSpan="6" className="empty-user-table">
                  Đang tải thống kê sự cố...
                </td>
              </tr>
            ) : summaries.length > 0 ? (
              summaries.map((item) => (
                <tr
                  key={item.metricType}
                  className="clickable-row"
                  onClick={() =>
                    navigate(
                      `/devices/${deviceId}/incidents/${encodeURIComponent(item.metricType)}`,
                      { state: { device, metricSummary: item } }
                    )
                  }
                >
                  <td>
                    <code className="incident-metric-code">
                      {item.metricType || '—'}
                    </code>
                  </td>

                  <td>
                    <span className="incident-count-badge incident-count-warning">
                      {item.totalWarning ?? 0}
                    </span>
                  </td>

                  <td>
                    <span className="incident-count-badge incident-count-critical">
                      {item.totalCritical ?? 0}
                    </span>
                  </td>

                  <td>{formatMetricValue(item.maxPeakValue)}</td>

                  <td>{formatMetricValue(item.minPeakValue)}</td>

                  <td>{formatMetricValue(item.avgPeakValue)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="6" className="empty-user-table">
                  {appliedKeyword
                    ? `Không tìm thấy sự cố nào khớp với "${appliedKeyword}".`
                    : 'Chưa ghi nhận sự cố nào cho thiết bị này.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {totalElements > 0 && (
        <div className="incident-pagination">
          <span className="pagination-info">
            Hiển thị <strong>{startItem}–{endItem}</strong> trong tổng số{' '}
            <strong>{totalElements}</strong> loại metric ({totalPages} trang)
          </span>

          <div className="pagination-controls">
            <button
              type="button"
              className="pagination-btn"
              disabled={page === 0}
              onClick={() => handlePageChange(page - 1)}
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
                    page + 1 === pageItem ? 'active' : ''
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
              disabled={page + 1 >= totalPages}
              onClick={() => handlePageChange(page + 1)}
            >
              Sau ›
            </button>
          </div>
        </div>
      )}
    </section>
  );
};

export default TabDeviceIncident;