package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricResponse;
import com.network_monitor.portal_service.model.dto.response.MetricValueResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.Metric;

import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.MetricRepository;
import com.network_monitor.portal_service.repository.projection.MetricValueProjection;
import com.network_monitor.portal_service.service.MetricService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricServiceImpl implements MetricService {

    private final MetricRepository metricRepository;

    private final DeviceRepository deviceRepository;

    /**
     * INSERT metric mới.
     *
     * Không UPDATE bản ghi cũ.
     */
    @Override
    @Transactional
    public ApiResponse<MetricResponse> insertMetric(MetricRequest request) {

        try {

            // Validate request
            if (request == null) {
                return ApiResponse.error("Metric request cannot be null");
            }

            if (request.getDeviceId() == null) {
                return ApiResponse.error("Device ID cannot be null");
            }

            if (request.getMetricData() == null ||
                request.getMetricData().isEmpty()) {

                return ApiResponse.error("Metric data cannot be empty");
            }

            // Kiểm tra thiết bị
            Device device = deviceRepository.findById(request.getDeviceId())
                .orElse(null);

            if (device == null) {
                return ApiResponse.error(
                    "Device not found with ID: " + request.getDeviceId()
                );
            }

            // Xác định thời điểm thu thập
            OffsetDateTime timestamp = request.getTimestamp() != null
                ? request.getTimestamp()
                : OffsetDateTime.now();

            // Tạo entity mới
            Metric metric = Metric.builder()
                .device(device)
                .metricData(request.getMetricData())
                .timestamp(timestamp)
                .build();

            // INSERT database
            Metric savedMetric = metricRepository.saveAndFlush(metric);

            // Chuyển Entity sang Response
            MetricResponse response = toResponse(savedMetric);

            log.info(
                "Metric inserted successfully: metricId={}, deviceId={}, timestamp={}",
                savedMetric.getId(),
                request.getDeviceId(),
                savedMetric.getTimestamp()
            );

            return ApiResponse.success(
                "Metric inserted successfully",
                response
            );

        } catch (Exception e) {

            log.error("Failed to insert metric", e);

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    /**
     * Lấy giá trị metric mới nhất theo deviceId và metricName.
     */
    @Override
    @Transactional(readOnly = true)
    public ApiResponse<MetricValueResponse> getLatestMetricValue(
        Long deviceId,
        String metricName
    ) {

        try {

            if (deviceId == null) {
                return ApiResponse.error("Device ID cannot be null");
            }

            if (metricName == null || metricName.isBlank()) {
                return ApiResponse.error("Metric name cannot be empty");
            }

            if (!deviceRepository.existsById(deviceId)) {
                return ApiResponse.error(
                    "Device not found with ID: " + deviceId
                );
            }

            List<MetricValueProjection> results =
                metricRepository.findLatestMetricValue(
                    deviceId,
                    metricName,
                    org.springframework.data.domain.PageRequest.of(0, 1)
                );

            if (results.isEmpty()) {
                return ApiResponse.error(
                    "No metric data found for: " + metricName
                );
            }

            MetricValueProjection result = results.get(0);

            MetricValueResponse response = MetricValueResponse.builder()
                .deviceId(result.getDeviceId())
                .metricName(metricName)
                .value(new java.math.BigDecimal(result.getMetricValue()))
                .timestamp(result.getTimestamp())
                .build();

            return ApiResponse.success(
                "Latest metric retrieved successfully",
                response
            );

        } catch (Exception e) {

            log.error("Failed to get latest metric value", e);

            return ApiResponse.error(getErrorMessage(e));
        }
    }


    /**
     * Lấy lịch sử metric theo deviceId và metricName.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<MetricValueResponse> getMetricHistory(
        Long deviceId,
        String metricName,
        Pageable pageable
    ) {

        if (deviceId == null) {
            throw new IllegalArgumentException("Device ID cannot be null");
        }

        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("Metric name cannot be empty");
        }

        if (!deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException(
                "Device not found with ID: " + deviceId
            );
        }

        Page<MetricValueProjection> results =
            metricRepository.findMetricHistory(
                deviceId,
                metricName,
                pageable
            );

        return results.map(result -> MetricValueResponse.builder()
            .deviceId(result.getDeviceId())
            .metricName(metricName)
            .value(new java.math.BigDecimal(result.getMetricValue()))
            .timestamp(result.getTimestamp())
            .build()
        );
    }

    /**
     * Tự động xóa metric đã quá 24 giờ.
     *
     * Chạy vào phút 0 của mỗi giờ.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredMetricsAutomatically() {

        try {

            OffsetDateTime cutoff = OffsetDateTime.now().minusHours(24);

            int deletedCount = metricRepository.deleteExpiredMetrics(cutoff);

            if (deletedCount > 0) {
                log.info(
                    "Automatically deleted {} expired metrics. Cutoff: {}",
                    deletedCount,
                    cutoff
                );
            }

        } catch (Exception e) {

            log.error("Failed to delete expired metrics", e);
        }
    }

    /**
     * Chuyển Entity sang Response.
     */
    private MetricResponse toResponse(Metric metric) {

        return MetricResponse.builder()
            .id(metric.getId())
            .deviceId(metric.getDevice().getId())
            .deviceName(metric.getDevice().getDeviceName())
            .metricData(metric.getMetricData())
            .timestamp(metric.getTimestamp())
            .build();
    }

    /**
     * Lấy thông báo lỗi chi tiết từ exception.
     */
    private String getErrorMessage(Throwable throwable) {

        Throwable cause = throwable;

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        return cause.getMessage() != null
            ? cause.getMessage()
            : "An unexpected error occurred";
    }

    
}