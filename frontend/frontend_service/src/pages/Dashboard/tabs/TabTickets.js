import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { axiosClient } from '../../../api/axiosClient';
import './TabTickets.css';

const ALLOWED_TICKET_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
]);

const TICKET_REVIEW_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
]);

const TICKET_MAKER_ROLES = new Set([
  'ROLE_SUPER_ADMIN',
  'ROLE_NETWORK_ADMIN',
  'ROLE_NOC_OPERATOR',
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

const STATUS_LABELS = {
  PENDING: { text: 'Chờ duyệt', className: 'ticket-badge-pending' },
  APPROVED: { text: 'Đã duyệt', className: 'ticket-badge-approved' },
  REJECTED: { text: 'Từ chối', className: 'ticket-badge-rejected' },
  CANCELLED: { text: 'Đã hủy', className: 'ticket-badge-cancelled' },
  FAILED: { text: 'Thực thi lỗi', className: 'ticket-badge-failed' },
};

const ENTITY_LABELS = {
  DEVICE: 'Thiết bị',
  DEVICE_CREDENTIAL: 'SNMP Credential',
  OID_CONFIG: 'OID Config',
  THRESHOLD_RULE: 'Ngưỡng cảnh báo',
};

const ACTION_LABELS = {
  CREATE: 'Thêm mới',
  UPDATE: 'Cập nhật',
  DELETE: 'Xóa',
  BULK_IMPORT: 'Import Excel',
  BULK_DELETE: 'Xóa hàng loạt',
};

const TabTickets = ({
  currentUser,
  canReviewTickets: canReviewTicketsProp,
  canManageDevices: canManageDevicesProp,
}) => {
  const resolvedUser = useMemo(
    () => currentUser || readStoredUser(),
    [currentUser]
  );

  const userRoles = useMemo(
    () => extractRoles(resolvedUser),
    [resolvedUser]
  );

  const hasAllowedTicketRole = useMemo(() => {
    if (!resolvedUser || userRoles.size === 0) return false;
    return Array.from(userRoles).some((role) =>
      ALLOWED_TICKET_ROLES.has(role)
    );
  }, [resolvedUser, userRoles]);

  const canReviewTickets = useMemo(() => {
    if (!hasAllowedTicketRole) return false;
    if (typeof canReviewTicketsProp === 'boolean') {
      return canReviewTicketsProp;
    }
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    return Array.from(userRoles).some((role) =>
      TICKET_REVIEW_ROLES.has(role)
    );
  }, [hasAllowedTicketRole, canReviewTicketsProp, canManageDevicesProp, userRoles]);

  const canCreateOrCancelTickets = useMemo(() => {
    if (!hasAllowedTicketRole) return false;
    if (typeof canManageDevicesProp === 'boolean') {
      return canManageDevicesProp;
    }
    return Array.from(userRoles).some((role) =>
      TICKET_MAKER_ROLES.has(role)
    );
  }, [hasAllowedTicketRole, canManageDevicesProp, userRoles]);

  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [bulkStatusText, setBulkStatusText] = useState('');

  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [entityFilter, setEntityFilter] = useState('');
  const [onlyMine, setOnlyMine] = useState(false);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [selectedTicket, setSelectedTicket] = useState(null);
  const [reviewComment, setReviewComment] = useState('');

  const fetchTickets = useCallback(async () => {
    if (!hasAllowedTicketRole) return;

    setLoading(true);
    try {
      const params = {
        page,
        size,
      };
      if (statusFilter) params.status = statusFilter;
      if (entityFilter) params.entityType = entityFilter;
      if (onlyMine && resolvedUser?.id) params.makerId = resolvedUser.id;
      if (keyword.trim()) params.keyword = keyword.trim();

      const res = await axiosClient.get('/tickets', { params });
      const apiRes = res.data;

      if (apiRes?.success === false) {
        alert(apiRes.message || 'Không thể tải danh sách ticket');
        return;
      }

      const pageData = apiRes?.data ?? apiRes;
      setTickets(pageData?.content || []);
      setTotalPages(pageData?.totalPages || 0);
      setTotalElements(pageData?.totalElements || 0);
    } catch (err) {
      alert(
        err.response?.data?.message ||
          err.message ||
          'Lỗi khi tải danh sách ticket'
      );
    } finally {
      setLoading(false);
    }
  }, [hasAllowedTicketRole, page, size, statusFilter, entityFilter, onlyMine, resolvedUser?.id, keyword]);

  useEffect(() => {
    fetchTickets();
  }, [fetchTickets]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (!hasAllowedTicketRole) return;
    setPage(0);
    setKeyword(keywordInput);
  };

  const handleOpenDetail = async (ticket) => {
    if (!hasAllowedTicketRole) return;

    setReviewComment('');
    setSelectedTicket(ticket);
    try {
      const res = await axiosClient.get(`/tickets/${ticket.id}`);
      const apiRes = res.data;
      if (apiRes?.success !== false && apiRes?.data) {
        setSelectedTicket(apiRes.data);
      }
    } catch {
      setSelectedTicket(ticket);
    }
  };

  const handleCloseModal = () => {
    if (submitting) return;
    setSelectedTicket(null);
    setReviewComment('');
  };

  const handleReview = async (ticketId, approved) => {
    if (!hasAllowedTicketRole || !canReviewTickets) return;

    const actionText = approved ? 'PHÊ DUYỆT' : 'TỪ CHỐI';
    if (!window.confirm(`Bạn có chắc chắn muốn ${actionText} Ticket #${ticketId}?`)) {
      return;
    }

    setSubmitting(true);
    try {
      const res = await axiosClient.post(`/tickets/${ticketId}/review`, {
        approved,
        comment: reviewComment.trim(),
      });

      const apiRes = res.data;
      if (apiRes?.success === false) {
        alert(apiRes.message || 'Xử lý ticket thất bại');
      } else {
        alert(apiRes?.message || `Đã ${actionText.toLowerCase()} ticket #${ticketId}`);
        setSelectedTicket(null);
        setReviewComment('');
        fetchTickets();
      }
    } catch (err) {
      alert(
        err.response?.data?.errorDetails ||
          err.response?.data?.message ||
          'Lỗi khi gửi quyết định phê duyệt'
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleBulkReview = async (approved) => {
    if (!hasAllowedTicketRole || !canReviewTickets) return;

    const actionText = approved ? 'PHÊ DUYỆT TOÀN BỘ' : 'TỪ CHỐI TOÀN BỘ';
    const defaultComment = approved ? 'Phê duyệt hàng loạt' : 'Từ chối hàng loạt';

    const inputComment = window.prompt(
      `Nhập ghi chú chung cho thao tác ${actionText} (nhấn Cancel để hủy):`,
      defaultComment
    );

    if (inputComment === null) {
      return;
    }

    setSubmitting(true);
    setBulkStatusText('Đang lấy danh sách ticket chờ duyệt...');

    try {
      const params = {
        status: 'PENDING',
        page: 0,
        size: 100,
      };
      if (entityFilter) params.entityType = entityFilter;
      if (keyword.trim()) params.keyword = keyword.trim();

      const listRes = await axiosClient.get('/tickets', { params });
      const listApiRes = listRes.data;

      if (listApiRes?.success === false) {
        alert(listApiRes.message || 'Không thể lấy danh sách ticket chờ duyệt');
        return;
      }

      const allPending = (listApiRes?.data ?? listApiRes)?.content || [];
      const eligibleTickets = allPending.filter(
        (t) => t.status === 'PENDING' && (!resolvedUser?.id || t.makerId !== resolvedUser.id)
      );

      if (eligibleTickets.length === 0) {
        alert('Không có ticket chờ duyệt nào hợp lệ (khác người tạo) để xử lý.');
        return;
      }

      const confirmed = window.confirm(
        `Tìm thấy ${eligibleTickets.length} ticket chờ duyệt hợp lệ.\n` +
          `Bạn có chắc chắn muốn ${actionText} ${eligibleTickets.length} ticket này?`
      );

      if (!confirmed) {
        return;
      }

      let successCount = 0;
      let failCount = 0;
      const failedDetails = [];

      for (let i = 0; i < eligibleTickets.length; i++) {
        const ticket = eligibleTickets[i];
        setBulkStatusText(
          `Đang xử lý (${i + 1}/${eligibleTickets.length}): Ticket #${ticket.id}...`
        );

        try {
          const res = await axiosClient.post(`/tickets/${ticket.id}/review`, {
            approved,
            comment: inputComment.trim(),
          });

          if (res.data?.success === false) {
            failCount++;
            failedDetails.push(`#${ticket.id}: ${res.data.message || 'Thất bại'}`);
          } else {
            successCount++;
          }
        } catch (err) {
          failCount++;
          const msg =
            err.response?.data?.errorDetails ||
            err.response?.data?.message ||
            err.message ||
            'Lỗi kết nối';
          failedDetails.push(`#${ticket.id}: ${msg}`);
        }
      }

      let summaryMsg =
        `Hoàn tất ${actionText}!\n` +
        `- Thành công: ${successCount}\n` +
        `- Thất bại: ${failCount}`;

      if (failedDetails.length > 0) {
        summaryMsg += `\n\nChi tiết lỗi:\n${failedDetails.slice(0, 5).join('\n')}`;
      }

      alert(summaryMsg);
      setPage(0);
      fetchTickets();
    } catch (err) {
      alert(
        err.response?.data?.message ||
          err.message ||
          'Lỗi khi xử lý hàng loạt ticket'
      );
    } finally {
      setSubmitting(false);
      setBulkStatusText('');
    }
  };

  const handleCancelTicket = async (ticketId) => {
    if (!hasAllowedTicketRole || !canCreateOrCancelTickets) return;

    if (!window.confirm(`Bạn có chắc chắn muốn HỦY Ticket #${ticketId}?`)) {
      return;
    }

    setSubmitting(true);
    try {
      const res = await axiosClient.post(`/tickets/${ticketId}/cancel`);
      const apiRes = res.data;

      if (apiRes?.success === false) {
        alert(apiRes.message || 'Không thể hủy ticket');
      } else {
        alert(apiRes?.message || `Đã hủy ticket #${ticketId}`);
        if (selectedTicket?.id === ticketId) {
          setSelectedTicket(null);
        }
        fetchTickets();
      }
    } catch (err) {
      alert(
        err.response?.data?.message || 'Lỗi khi hủy ticket'
      );
    } finally {
      setSubmitting(false);
    }
  };

  const formatDateTime = (isoString) => {
    if (!isoString) return '—';
    try {
      return new Date(isoString).toLocaleString('vi-VN');
    } catch {
      return isoString;
    }
  };

  const formatJson = (data) => {
    if (data === null || data === undefined) return 'Không có dữ liệu';
    try {
      return JSON.stringify(data, null, 2);
    } catch {
      return String(data);
    }
  };

  const isMakerOf = (ticket) =>
    Boolean(resolvedUser?.id && ticket?.makerId === resolvedUser.id);

  if (!hasAllowedTicketRole) {
    return (
      <section className="dashboard-section">
        <div className="permission-denied-box">
          <div className="permission-denied-icon">!</div>
          <h3 className="permission-denied-title">Không có quyền truy cập</h3>
          <p className="permission-message">
            Bạn không có quyền xem hoặc thao tác với danh sách yêu cầu thay đổi (Maker - Checker). Chỉ các vai trò SUPER_ADMIN, NETWORK_ADMIN và NOC_OPERATOR mới được phép truy cập mục này.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="dashboard-section">
      <div className="section-header">
        <div>
          <h3>Phê duyệt yêu cầu thay đổi (Maker - Checker)</h3>
          <span className="ticket-subtitle">
            Tổng số: <strong>{totalElements}</strong> ticket
          </span>
        </div>

        <form className="ticket-filter-bar" onSubmit={handleSearchSubmit}>
          <select
            className="ticket-filter-select"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
            disabled={submitting}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Chờ duyệt (PENDING)</option>
            <option value="APPROVED">Đã duyệt (APPROVED)</option>
            <option value="REJECTED">Từ chối (REJECTED)</option>
            <option value="CANCELLED">Đã hủy (CANCELLED)</option>
            <option value="FAILED">Thực thi lỗi (FAILED)</option>
          </select>

          <select
            className="ticket-filter-select"
            value={entityFilter}
            onChange={(e) => {
              setEntityFilter(e.target.value);
              setPage(0);
            }}
            disabled={submitting}
          >
            <option value="">Tất cả đối tượng</option>
            <option value="DEVICE">Thiết bị (DEVICE)</option>
            <option value="DEVICE_CREDENTIAL">Credential (SNMP)</option>
            <option value="OID_CONFIG">OID Config</option>
            <option value="THRESHOLD_RULE">Ngưỡng cảnh báo</option>
          </select>

          {canCreateOrCancelTickets && (
            <label className="ticket-mine-checkbox">
              <input
                type="checkbox"
                checked={onlyMine}
                onChange={(e) => {
                  setOnlyMine(e.target.checked);
                  setPage(0);
                }}
                disabled={submitting}
              />
              <span>Ticket của tôi</span>
            </label>
          )}

          <input
            type="text"
            className="user-search-input ticket-keyword-input"
            placeholder="Tìm theo nội dung yêu cầu..."
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            disabled={submitting}
          />

          <button type="submit" className="search-button" disabled={submitting}>
            Tìm kiếm
          </button>

          <button
            type="button"
            className="ticket-refresh-button"
            onClick={fetchTickets}
            disabled={loading || submitting}
          >
            ↻ Làm mới
          </button>
        </form>
      </div>

      {canReviewTickets && (
        <div className="ticket-bulk-bar">
          <div className="ticket-bulk-info">
            {bulkStatusText ? (
              <span className="ticket-bulk-progress">{bulkStatusText}</span>
            ) : (
              <span>
                Thao tác nhanh trên tất cả ticket đang chờ duyệt (khớp bộ lọc hiện tại, trừ ticket của bạn):
              </span>
            )}
          </div>

          <div className="ticket-bulk-actions">
            <button
              type="button"
              className="ticket-bulk-button ticket-btn-approve"
              onClick={() => handleBulkReview(true)}
              disabled={loading || submitting}
            >
              ✓ Phê duyệt toàn bộ
            </button>

            <button
              type="button"
              className="ticket-bulk-button ticket-btn-reject"
              onClick={() => handleBulkReview(false)}
              disabled={loading || submitting}
            >
              ✕ Từ chối toàn bộ
            </button>
          </div>
        </div>
      )}

      <div className="user-table-wrapper">
        <table className="user-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Đối tượng</th>
              <th>Hành động</th>
              <th>Nội dung yêu cầu</th>
              <th>Trạng thái</th>
              <th>Thời gian tạo</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="7" className="empty-user-table">
                  Đang tải dữ liệu ticket...
                </td>
              </tr>
            ) : tickets.length === 0 ? (
              <tr>
                <td colSpan="7" className="empty-user-table">
                  Không có ticket nào phù hợp với điều kiện lọc.
                </td>
              </tr>
            ) : (
              tickets.map((ticket) => {
                const statusInfo = STATUS_LABELS[ticket.status] || {
                  text: ticket.status,
                  className: 'ticket-badge-cancelled',
                };
                const mine = isMakerOf(ticket);

                return (
                  <tr
                    key={ticket.id}
                    className="clickable-row"
                    onClick={() => handleOpenDetail(ticket)}
                  >
                    <td>
                      <strong>#{ticket.id}</strong>
                    </td>
                    <td>{ENTITY_LABELS[ticket.entityType] || ticket.entityType}</td>
                    <td>
                      <span className="ticket-action-tag">
                        {ACTION_LABELS[ticket.actionType] || ticket.actionType}
                      </span>
                    </td>
                    <td>
                      <div className="ticket-summary-cell">
                        <span>{ticket.summary}</span>
                        {mine && (
                          <span className="ticket-mine-tag">Của tôi</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={`ticket-status-badge ${statusInfo.className}`}>
                        {statusInfo.text}
                      </span>
                    </td>
                    <td>{formatDateTime(ticket.createdAt)}</td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        className="user-action-button ticket-btn-view"
                        onClick={() => handleOpenDetail(ticket)}
                        disabled={submitting}
                      >
                        Chi tiết
                      </button>

                      {ticket.status === 'PENDING' && mine && canCreateOrCancelTickets && (
                        <button
                          type="button"
                          className="user-action-button delete-user-button"
                          onClick={() => handleCancelTicket(ticket.id)}
                          disabled={submitting}
                        >
                          Hủy
                        </button>
                      )}

                      {ticket.status === 'PENDING' && !mine && canReviewTickets && (
                        <button
                          type="button"
                          className="user-action-button unlock-button"
                          onClick={() => handleOpenDetail(ticket)}
                          disabled={submitting}
                        >
                          Xét duyệt
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="ticket-pagination">
          <button
            type="button"
            className="ticket-page-btn"
            disabled={page === 0 || loading || submitting}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            ← Trang trước
          </button>

          <span>
            Trang <strong>{page + 1}</strong> / {totalPages}
          </span>

          <button
            type="button"
            className="ticket-page-btn"
            disabled={page + 1 >= totalPages || loading || submitting}
            onClick={() => setPage((p) => p + 1)}
          >
            Trang sau →
          </button>
        </div>
      )}

      {selectedTicket && (
        <div className="device-modal-overlay" onClick={handleCloseModal}>
          <div
            className="device-create-modal ticket-detail-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="device-modal-header">
              <div>
                <h2>
                  Chi tiết Ticket #{selectedTicket.id} —{' '}
                  {ACTION_LABELS[selectedTicket.actionType] ||
                    selectedTicket.actionType}
                </h2>
                <p>{selectedTicket.summary}</p>
              </div>

              <button
                type="button"
                className="device-modal-close"
                onClick={handleCloseModal}
                disabled={submitting}
              >
                ×
              </button>
            </div>

            <div className="device-modal-body">
              <div className="ticket-meta-grid">
                <div className="ticket-meta-item">
                  <span className="ticket-meta-label">Trạng thái</span>
                  <span
                    className={`ticket-status-badge ${
                      (STATUS_LABELS[selectedTicket.status] || {}).className ||
                      ''
                    }`}
                  >
                    {(STATUS_LABELS[selectedTicket.status] || {}).text ||
                      selectedTicket.status}
                  </span>
                </div>

                <div className="ticket-meta-item">
                  <span className="ticket-meta-label">Loại đối tượng</span>
                  <strong>
                    {ENTITY_LABELS[selectedTicket.entityType] ||
                      selectedTicket.entityType}
                  </strong>
                </div>

                <div className="ticket-meta-item">
                  <span className="ticket-meta-label">Người tạo (Maker ID)</span>
                  <code>{selectedTicket.makerId}</code>
                </div>

                <div className="ticket-meta-item">
                  <span className="ticket-meta-label">Thời gian tạo</span>
                  <span>{formatDateTime(selectedTicket.createdAt)}</span>
                </div>

                <div className="ticket-meta-item">
                  <span className="ticket-meta-label">
                    Người duyệt (Checker ID)
                  </span>
                  <code>{selectedTicket.checkerId || 'Chưa có'}</code>
                </div>

                <div className="ticket-meta-item">
                  <span className="ticket-meta-label">Thời gian duyệt</span>
                  <span>{formatDateTime(selectedTicket.reviewedAt)}</span>
                </div>
              </div>

              {selectedTicket.checkerComment && (
                <div className="ticket-alert-box ticket-comment-box">
                  <strong>Ghi chú của Checker:</strong>{' '}
                  {selectedTicket.checkerComment}
                </div>
              )}

              {selectedTicket.executionError && (
                <div className="ticket-alert-box ticket-error-box">
                  <strong>Lỗi thực thi:</strong> {selectedTicket.executionError}
                </div>
              )}

              <div className="ticket-diff-grid">
                <div className="ticket-json-card">
                  <h4>Dữ liệu trước thay đổi (Before Data)</h4>
                  <pre className="ticket-json-pre">
                    {formatJson(selectedTicket.beforeData)}
                  </pre>
                </div>

                <div className="ticket-json-card">
                  <h4>Dữ liệu yêu cầu (New Payload)</h4>
                  <pre className="ticket-json-pre">
                    {formatJson(selectedTicket.payload)}
                  </pre>
                </div>
              </div>

              {selectedTicket.status === 'PENDING' &&
                !isMakerOf(selectedTicket) &&
                canReviewTickets && (
                  <div className="ticket-review-section">
                    <label htmlFor="checker-comment">
                      Ghi chú phê duyệt / Lý do từ chối
                    </label>
                    <textarea
                      id="checker-comment"
                      rows="3"
                      className="ticket-comment-textarea"
                      placeholder="Nhập nhận xét hoặc lý do từ chối để gửi email thông báo cho Maker..."
                      value={reviewComment}
                      onChange={(e) => setReviewComment(e.target.value)}
                      disabled={submitting}
                    />
                  </div>
                )}
            </div>

            <div className="device-modal-footer">
              <button
                type="button"
                className="device-cancel-button"
                onClick={handleCloseModal}
                disabled={submitting}
              >
                Đóng
              </button>

              {selectedTicket.status === 'PENDING' &&
                isMakerOf(selectedTicket) &&
                canCreateOrCancelTickets && (
                  <button
                    type="button"
                    className="device-submit-button ticket-btn-reject"
                    onClick={() => handleCancelTicket(selectedTicket.id)}
                    disabled={submitting}
                  >
                    {submitting ? 'Đang xử lý...' : 'Hủy yêu cầu'}
                  </button>
                )}

              {selectedTicket.status === 'PENDING' &&
                !isMakerOf(selectedTicket) &&
                canReviewTickets && (
                  <>
                    <button
                      type="button"
                      className="device-submit-button ticket-btn-reject"
                      onClick={() => handleReview(selectedTicket.id, false)}
                      disabled={submitting}
                    >
                      {submitting ? 'Đang xử lý...' : 'Từ chối'}
                    </button>

                    <button
                      type="button"
                      className="device-submit-button ticket-btn-approve"
                      onClick={() => handleReview(selectedTicket.id, true)}
                      disabled={submitting}
                    >
                      {submitting ? 'Đang xử lý...' : 'Phê duyệt'}
                    </button>
                  </>
                )}
            </div>
          </div>
        </div>
      )}
    </section>
  );
};

export default TabTickets;