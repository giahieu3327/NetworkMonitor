package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricAggregateResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.Metric;
import com.network_monitor.portal_service.model.entity.MetricAggregate;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.MetricAggregateRepository;
import com.network_monitor.portal_service.repository.MetricRepository;
import com.network_monitor.portal_service.service.MetricAggregateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricAggregateServiceImpl implements MetricAggregateService {

    private final MetricRepository metricRepository;

    private final MetricAggregateRepository metricAggregateRepository;

    private final DeviceRepository deviceRepository;

    private static final String WINDOW_1H = "1H";

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    @Scheduled(cron = "0 2 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void aggregateLastHourMetrics() {
        OffsetDateTime currentHourStart = OffsetDateTime.now(DEFAULT_ZONE).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime previousHourStart = currentHourStart.minusHours(1);
        aggregateHourlyRange(previousHourStart, currentHourStart);
    }

    @Override
    @Transactional
    public ApiResponse<Integer> aggregateHourlyRange(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        try {
            if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
                return ApiResponse.error("Invalid aggregation period");
            }

            List<Device> devices = deviceRepository.findAll();
            int aggregatedDevicesCount = 0;

            for (Device device : devices) {
                List<Metric> metrics = metricRepository
                        .findByDeviceIdAndTimestampGreaterThanEqualAndTimestampLessThan(
                                device.getId(),
                                periodStart,
                                periodEnd
                        );

                if (metrics.isEmpty()) {
                    continue;
                }

                Map<String, Object> aggregatedData = buildAggregatedData(metrics);
                if (aggregatedData.isEmpty()) {
                    continue;
                }

                MetricAggregate aggregate = metricAggregateRepository
                        .findByDeviceIdAndAggregationWindowAndPeriodStart(
                                device.getId(),
                                WINDOW_1H,
                                periodStart
                        )
                        .orElseGet(() -> MetricAggregate.builder()
                                .device(device)
                                .aggregationWindow(WINDOW_1H)
                                .periodStart(periodStart)
                                .periodEnd(periodEnd)
                                .build()
                        );

                aggregate.setPeriodEnd(periodEnd);
                aggregate.setAggregatedData(aggregatedData);
                metricAggregateRepository.saveAndFlush(aggregate);

                aggregatedDevicesCount++;
            }

            log.info(
                    "Completed 1H metric aggregation for window [{} -> {}]. Devices aggregated: {}",
                    periodStart,
                    periodEnd,
                    aggregatedDevicesCount
            );

            return ApiResponse.success(
                    "Hourly metric aggregation completed successfully",
                    aggregatedDevicesCount
            );
        } catch (Exception e) {
            log.error("Failed to aggregate metrics for window [{} -> {}]", periodStart, periodEnd, e);
            return ApiResponse.error("Failed to aggregate metrics: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<MetricAggregateResponse>> get1DayChartMetrics(Long deviceId, LocalDate date) {
        try {
            if (deviceId != null && !deviceRepository.existsById(deviceId)) {
                return ApiResponse.error("Device not found: " + deviceId);
            }

            List<MetricAggregate> aggregates;

            if (date != null) {
                OffsetDateTime start = date.atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();
                OffsetDateTime end = date.plusDays(1).atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();

                aggregates = deviceId != null
                        ? metricAggregateRepository
                                .findByDeviceIdAndAggregationWindowAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
                                        deviceId,
                                        WINDOW_1H,
                                        start,
                                        end
                                )
                        : metricAggregateRepository
                                .findByAggregationWindowAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAscDeviceIdAsc(
                                        WINDOW_1H,
                                        start,
                                        end
                                );
            } else {
                OffsetDateTime since = OffsetDateTime.now(DEFAULT_ZONE)
                        .truncatedTo(ChronoUnit.HOURS)
                        .minusHours(24);

                aggregates = deviceId != null
                        ? metricAggregateRepository
                                .findByDeviceIdAndAggregationWindowAndPeriodStartGreaterThanEqualOrderByPeriodStartAsc(
                                        deviceId,
                                        WINDOW_1H,
                                        since
                                )
                        : metricAggregateRepository
                                .findByAggregationWindowAndPeriodStartGreaterThanEqualOrderByPeriodStartAscDeviceIdAsc(
                                        WINDOW_1H,
                                        since
                                );
            }

            List<MetricAggregateResponse> responses = aggregates.stream()
                    .map(this::toResponse)
                    .toList();

            return ApiResponse.success(
                    "1D chart metrics retrieved successfully",
                    responses
            );
        } catch (Exception e) {
            log.error("Failed to get 1D chart metrics for device ID: {}, date: {}", deviceId, date, e);
            return ApiResponse.error("Failed to get 1D chart metrics: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    @Scheduled(
            cron = "0 5 * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public ApiResponse<Integer> deleteExpiredAggregates() {
        try {
            OffsetDateTime cutoff = OffsetDateTime.now(DEFAULT_ZONE).minusDays(7);
            int deletedCount = metricAggregateRepository.deleteExpiredAggregates(cutoff);
            log.info("Deleted {} expired metric_aggregate records", deletedCount);
            return ApiResponse.success("Expired metric aggregates deleted successfully", deletedCount);
        } catch (Exception e) {
            log.error("Failed to delete expired metric aggregates", e);
            return ApiResponse.error("Failed to delete expired metric aggregates: " + e.getMessage());
        }
    }

    private Map<String, Object> buildAggregatedData(List<Metric> metrics) {
        Map<String, List<Double>> samplesByMetric = new LinkedHashMap<>();

        for (Metric metric : metrics) {
            Map<String, Object> data = metric.getMetricData();
            if (data == null || data.isEmpty()) {
                continue;
            }

            Object rawCpu = data.get("cpu_usage");
            collectNumericSamples("cpu_usage", rawCpu, samplesByMetric);

            Object rawRam = data.get("ram_usage_percent");
            collectNumericSamples("memory_usage", rawRam, samplesByMetric);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : samplesByMetric.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), computeStats(entry.getValue()));
            }
        }

        return result;
    }

    private void collectNumericSamples(
            String prefix,
            Object rawValue,
            Map<String, List<Double>> target
    ) {
        if (rawValue instanceof Number number) {
            target.computeIfAbsent(prefix, k -> new ArrayList<>())
                    .add(number.doubleValue());
        } else if (rawValue instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    String nextKey = prefix + "-" + String.valueOf(entry.getKey()).trim();
                    collectNumericSamples(nextKey, entry.getValue(), target);
                }
            }
        }
    }

    private Map<String, Double> computeStats(List<Double> values) {
        DoubleSummaryStatistics stats = values.stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        Map<String, Double> statMap = new LinkedHashMap<>();
        statMap.put("min", roundTwoDecimals(stats.getMin()));
        statMap.put("avg", roundTwoDecimals(stats.getAverage()));
        statMap.put("max", roundTwoDecimals(stats.getMax()));
        return statMap;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private MetricAggregateResponse toResponse(MetricAggregate aggregate) {
        return MetricAggregateResponse.builder()
                .id(aggregate.getId())
                .deviceId(aggregate.getDevice().getId())
                .deviceName(aggregate.getDevice().getDeviceName())
                .aggregationWindow(aggregate.getAggregationWindow())
                .periodStart(aggregate.getPeriodStart())
                .periodEnd(aggregate.getPeriodEnd())
                .aggregatedData(aggregate.getAggregatedData())
                .build();
    }
}