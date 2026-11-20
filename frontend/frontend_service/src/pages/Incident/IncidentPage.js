import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import '../../styles/common.css';
import './IncidentPage.css';

const PAGE_SIZE = 10;

const formatMetricValue = (val) => {
  if (val === null || val === undefined || val === '') return '—';
  const num = Number(val);
  return Number.isFinite(num) ? Number(num.toFixed(2)) : '—';
};

const formatDateTime = (isoString) => {
  if (!isoString) return '—';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString('vi-VN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
};

const parseSamples = (samples) => {
  if (!samples) return [];
  if (Array.isArray(samples)) return samples;
  if (typeof samples === 'string') {
    try {
      const parsed = JSON.parse(samples);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
};

const IncidentPage = () => {
  const { deviceId, metricType } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  const device = location.state?.device || null;
  const metricSummary = location.state?.metricSummary || null;

  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  const [keywordInput, setKeywordInput] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [sortBy, setSortBy] = useState('createdAt');
  const [direction, setDirection] = useState('DESC');

  const [selectedIncident, setSelectedIncident] = useState(null);

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, []);

  useEffect(() => {
    setPage(0);
    setKeywordInput('');
    setAppliedKeyword('');
    setSortBy('createdAt');
    setDirection('DESC');
    setSelectedIncident(null);
  }, [deviceId, metricType]);

  const fetchIncidents = useCallback(async () => {
    if (!metricType) return;

    try {
      setLoading(true);
      setErrorMsg('');

      const params = {
        page,
        size: PAGE_SIZE,
        sortBy,
        direction,
      };

      if (deviceId) {
        params.deviceId = deviceId;
      }

      if (appliedKeyword) {
        params.keyword = appliedKeyword;
      }

      const response = await axiosClient.get(
        `/incidents/${encodeURIComponent(metricType)}`,
        { params }
      );

      const pageData = response?.data?.data ?? response?.data ?? {};
      setIncidents(Array.isArray(pageData.content) ? pageData.content : []);
      setTotalElements(pageData.totalElements ?? 0);
      setTotalPages(Math.max(1, pageData.totalPages ?? 1));
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message || 'Không thể tải danh sách sự cố!'
      );
      setIncidents([]);
      setTotalElements(0);
      setTotalPages(1);
    } finally {
      setLoading(false);
    }
  }, [deviceId, metricType, page, appliedKeyword, sortBy, direction]);

  useEffect(() => {
    fetchIncidents();
  }, [fetchIncidents]);

  useEffect(() => {
    if (!selectedIncident) return;
    const handleEsc = (e) => {
      if (e.key === 'Escape') {
        setSelectedIncident(null);
      }
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [selectedIncident]);

  const handleBack = () => {
    if (deviceId) {
      navigate(`/devices/${deviceId}`, {
        state: { activeTab: 'incidents' },
      });
    } else {
      navigate(-1);
    }
  };

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

  const getSeverityBadgeClass = (severity) => {
    const upper = String(severity || '').toUpperCase();
    if (upper === 'CRITICAL') return 'inc-badge inc-badge-critical';
    if (upper === 'WARNING') return 'inc-badge inc-badge-warning';
    return 'inc-badge inc-badge-default';
  };

  const getStatusBadgeClass = (status) => {
    const upper = String(status || '').toUpperCase();
    if (upper === 'OPEN') return 'inc-badge inc-status-open';
    if (upper === 'RESOLVED') return 'inc-badge inc-status-resolved';
    return 'inc-badge inc-badge-default';
  };

  const startItem = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
  const endItem = Math.min((page + 1) * PAGE_SIZE, totalElements);

  const deviceTitle =
    device?.deviceName ||
    incidents[0]?.deviceName ||
    (deviceId ? `Thiết bị #${deviceId}` : 'Tất cả thiết bị');

  return (
    <div className={`inc-page ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="inc-header">
        <div className="inc-header-left">
          <button
            type="button"
            className="inc-back-button"
            onClick={handleBack}
            title="Quay lại"
          >
            ←
          </button>
          <div>
            <h2>LỊCH SỬ SỰ CỐ: {metricType}</h2>
            <p className="inc-header-subtitle">
              Thiết bị: <strong>{deviceTitle}</strong>
              {metricSummary && (
                <>
                  {' '}
                  · Warning: <strong>{metricSummary.totalWarning ?? 0}</strong> ·
                  Critical: <strong>{metricSummary.totalCritical ?? 0}</strong>
                </>
              )}
            </p>
          </div>
        </div>
      </header>

      <main className="inc-main">
        <div className="inc-card">
          {errorMsg && (
            <div className="inc-alert error">
              <span>{errorMsg}</span>
            </div>
          )}

          <div className="inc-toolbar">
            <div className="inc-search-container">
              <input
                type="text"
                placeholder="Tìm kiếm tiêu đề, mô tả, mức độ, trạng thái..."
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                onKeyDown={handleKeyDown}
                className="inc-search-input"
              />

              {keywordInput && (
                <button
                  type="button"
                  onClick={handleClearSearch}
                  className="inc-clear-btn"
                  title="Xóa từ khóa"
                >
                  ✕
                </button>
              )}

              <button
                type="button"
                onClick={handleSearch}
                className="inc-search-btn"
              >
                Tìm kiếm
              </button>
            </div>
          </div>

          <div className="inc-table-wrapper">
            <table className="inc-table">
              <thead>
                <tr>
                  <th>Metric Type</th>

                  <th
                    onClick={() => handleSort('severity')}
                    style={{ cursor: 'pointer' }}
                  >
                    Severity
                    {renderSortIcon('severity')}
                  </th>

                  <th
                    onClick={() => handleSort('title')}
                    style={{ cursor: 'pointer' }}
                  >
                    Title
                    {renderSortIcon('title')}
                  </th>

                  <th>Description</th>

                  <th
                    onClick={() => handleSort('peakValue')}
                    style={{ cursor: 'pointer' }}
                  >
                    Peak Value
                    {renderSortIcon('peakValue')}
                  </th>

                  <th
                    onClick={() => handleSort('thresholdLimit')}
                    style={{ cursor: 'pointer' }}
                  >
                    Threshold Limit
                    {renderSortIcon('thresholdLimit')}
                  </th>

                  <th
                    onClick={() => handleSort('breachCount')}
                    style={{ cursor: 'pointer' }}
                  >
                    Breach Count
                    {renderSortIcon('breachCount')}
                  </th>

                  <th>Breach Samples</th>

                  <th
                    onClick={() => handleSort('status')}
                    style={{ cursor: 'pointer' }}
                  >
                    Status
                    {renderSortIcon('status')}
                  </th>
                </tr>
              </thead>

              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan="9" className="inc-empty-table">
                      Đang tải danh sách sự cố...
                    </td>
                  </tr>
                ) : incidents.length > 0 ? (
                  incidents.map((item) => {
                    const samples = parseSamples(item.breachSamples);
                    return (
                      <tr
                        key={item.id}
                        className="inc-clickable-row"
                        onClick={() => setSelectedIncident(item)}
                        title="Nhấn để xem chi tiết đầy đủ"
                      >
                        <td>
                          <code className="inc-metric-code">
                            {item.metricType || '—'}
                          </code>
                        </td>

                        <td>
                          <span className={getSeverityBadgeClass(item.severity)}>
                            {item.severity || '—'}
                          </span>
                        </td>

                        <td>
                          <div className="inc-cell-truncate inc-cell-title">
                            {item.title || '—'}
                          </div>
                        </td>

                        <td>
                          <div className="inc-cell-truncate inc-cell-desc">
                            {item.description || '—'}
                          </div>
                        </td>

                        <td>
                          <strong>{formatMetricValue(item.peakValue)}</strong>
                        </td>

                        <td>{formatMetricValue(item.thresholdLimit)}</td>

                        <td>{item.breachCount ?? 0}</td>

                        <td>
                          <span className="inc-sample-pill">
                            {samples.length} mẫu
                          </span>
                        </td>

                        <td>
                          <span className={getStatusBadgeClass(item.status)}>
                            {item.status || '—'}
                          </span>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan="9" className="inc-empty-table">
                      {appliedKeyword
                        ? `Không tìm thấy sự cố nào khớp với "${appliedKeyword}".`
                        : 'Chưa ghi nhận sự cố nào cho metric này.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {totalElements > 0 && (
            <div className="inc-pagination">
              <span className="inc-pagination-info">
                Hiển thị <strong>{startItem}–{endItem}</strong> trong tổng số{' '}
                <strong>{totalElements}</strong> sự cố ({totalPages} trang)
              </span>

              <div className="inc-pagination-controls">
                <button
                  type="button"
                  className="inc-page-btn"
                  disabled={page === 0}
                  onClick={() => handlePageChange(page - 1)}
                >
                  ‹ Trước
                </button>

                {paginationRange.map((pageItem, index) =>
                  pageItem === '...' ? (
                    <span key={`dots-${index}`} className="inc-page-dots">
                      ...
                    </span>
                  ) : (
                    <button
                      key={pageItem}
                      type="button"
                      className={`inc-page-btn ${
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
                  className="inc-page-btn"
                  disabled={page + 1 >= totalPages}
                  onClick={() => handlePageChange(page + 1)}
                >
                  Sau ›
                </button>
              </div>
            </div>
          )}
        </div>
      </main>

      {selectedIncident && (
        <div
          className="inc-modal-backdrop"
          onClick={() => setSelectedIncident(null)}
        >
          <div
            className="inc-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="inc-modal-header">
              <div>
                <h3>Chi tiết sự cố #{selectedIncident.id}</h3>
                <p>
                  {selectedIncident.deviceName || deviceTitle} ·{' '}
                  <code className="inc-metric-code">
                    {selectedIncident.metricType}
                  </code>
                </p>
              </div>

              <button
                type="button"
                className="inc-modal-close"
                onClick={() => setSelectedIncident(null)}
                title="Đóng"
              >
                ✕
              </button>
            </div>

            <div className="inc-modal-body">
              <div className="inc-detail-grid">
                <div className="inc-detail-item">
                  <span className="inc-detail-label">Metric Type</span>
                  <span className="inc-detail-value">
                    <code className="inc-metric-code">
                      {selectedIncident.metricType || '—'}
                    </code>
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Severity</span>
                  <span className="inc-detail-value">
                    <span className={getSeverityBadgeClass(selectedIncident.severity)}>
                      {selectedIncident.severity || '—'}
                    </span>
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Status</span>
                  <span className="inc-detail-value">
                    <span className={getStatusBadgeClass(selectedIncident.status)}>
                      {selectedIncident.status || '—'}
                    </span>
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Peak Value</span>
                  <span className="inc-detail-value">
                    <strong>{formatMetricValue(selectedIncident.peakValue)}</strong>
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Threshold Limit</span>
                  <span className="inc-detail-value">
                    {formatMetricValue(selectedIncident.thresholdLimit)}
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Breach Count</span>
                  <span className="inc-detail-value">
                    {selectedIncident.breachCount ?? 0}
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Thời điểm tạo</span>
                  <span className="inc-detail-value">
                    {formatDateTime(selectedIncident.createdAt)}
                  </span>
                </div>

                <div className="inc-detail-item">
                  <span className="inc-detail-label">Thời điểm khắc phục</span>
                  <span className="inc-detail-value">
                    {formatDateTime(selectedIncident.resolvedAt)}
                  </span>
                </div>
              </div>

              <div className="inc-detail-block">
                <span className="inc-detail-label">Title</span>
                <div className="inc-detail-text-box">
                  {selectedIncident.title || '—'}
                </div>
              </div>

              <div className="inc-detail-block">
                <span className="inc-detail-label">Description</span>
                <div className="inc-detail-text-box">
                  {selectedIncident.description || '—'}
                </div>
              </div>

              <div className="inc-detail-block">
                <span className="inc-detail-label">
                  Breach Samples ({parseSamples(selectedIncident.breachSamples).length})
                </span>

                {parseSamples(selectedIncident.breachSamples).length > 0 ? (
                  <div className="inc-samples-table-wrapper">
                    <table className="inc-samples-table">
                      <thead>
                        <tr>
                          <th>#</th>
                          <th>Timestamp</th>
                          <th>Severity</th>
                          <th>Value</th>
                        </tr>
                      </thead>
                      <tbody>
                        {parseSamples(selectedIncident.breachSamples).map(
                          (sample, idx) => (
                            <tr key={idx}>
                              <td>{idx + 1}</td>
                              <td>{formatDateTime(sample.timestamp)}</td>
                              <td>
                                <span
                                  className={getSeverityBadgeClass(
                                    sample.severity
                                  )}
                                >
                                  {sample.severity || '—'}
                                </span>
                              </td>
                              <td>
                                <strong>{formatMetricValue(sample.value)}</strong>
                              </td>
                            </tr>
                          )
                        )}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="inc-detail-text-box">
                    Không có dữ liệu breach samples.
                  </div>
                )}
              </div>
            </div>

            <div className="inc-modal-footer">
              <button
                type="button"
                className="inc-cancel-btn"
                onClick={() => setSelectedIncident(null)}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default IncidentPage;