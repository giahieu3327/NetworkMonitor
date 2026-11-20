package com.network_monitor.portal_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.mapper.ReportMapper;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DailyIncidentSummaryResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceMetricChartPoint;
import com.network_monitor.portal_service.model.dto.response.DeviceMetricChartSeriesPoint;
import com.network_monitor.portal_service.model.dto.response.IncidentChartGroup;
import com.network_monitor.portal_service.model.dto.response.IncidentChartItem;
import com.network_monitor.portal_service.model.dto.response.MetricAggregateResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.MetricAggregateService;
import com.network_monitor.portal_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;

    private final DeviceRepository deviceRepository;

    private final MetricAggregateService metricAggregateService;

    private final ObjectMapper objectMapper;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final String DAILY_INCIDENT_TEMPLATE = "reports/daily_incident_report.jrxml";

    private static final String DEVICE_METRIC_LINE_TEMPLATE = "reports/device_metric_line_chart.jrxml";

    private final Map<String, JasperReport> compiledReportCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public List<DailyIncidentSummaryResponse> getDailyIncidentSummary(Long deviceId, LocalDate date) {
        if (deviceId != null && !deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        LocalDate targetDate = date != null ? date : LocalDate.now(DEFAULT_ZONE);
        OffsetDateTime start = targetDate.atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();
        OffsetDateTime end = targetDate.plusDays(1).atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();

        List<DailyIncidentSummaryResponse> summaries =
                reportMapper.summarizeDailyIncidents(deviceId, start, end);

        for (DailyIncidentSummaryResponse summary : summaries) {
            if (summary.getAvgPeakValue() != null) {
                summary.setAvgPeakValue(Math.round(summary.getAvgPeakValue() * 100.0) / 100.0);
            }
        }

        return summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDailyIncidentReport(Long deviceId, LocalDate date, String format) {
        List<DailyIncidentSummaryResponse> summaries = getDailyIncidentSummary(deviceId, date);
        LocalDate targetDate = date != null ? date : LocalDate.now(DEFAULT_ZONE);

        List<IncidentChartGroup> chartGroups = buildIncidentChartGroups(summaries);

        try {
            JasperReport jasperReport = getCompiledReport(DAILY_INCIDENT_TEMPLATE);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("REPORT_DATE", targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            parameters.put("DEVICE_FILTER", deviceId != null ? "Device ID #" + deviceId : "All Devices");
            parameters.put("CHART_GROUPS_DATA_SOURCE", new JRBeanCollectionDataSource(chartGroups));

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(summaries);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            return exportByFormat(jasperPrint, format);
        } catch (Exception e) {
            log.error("Error exporting daily incident report", e);
            throw new RuntimeException("Failed to export daily incident report: " + e.getMessage(), e);
        }
    }

    private List<IncidentChartGroup> buildIncidentChartGroups(List<DailyIncidentSummaryResponse> summaries) {
        Map<String, List<IncidentChartItem>> groupedItems = new LinkedHashMap<>();

        for (DailyIncidentSummaryResponse summary : summaries) {
            String metricType = summary.getMetricType() != null ? summary.getMetricType() : "unknown";
            int dashIdx = metricType.indexOf('-');
            String baseMetric = dashIdx != -1 ? metricType.substring(0, dashIdx) : metricType;
            String suffix = dashIdx != -1 ? metricType.substring(dashIdx + 1) : "";

            String categoryLabel = suffix.isEmpty()
                    ? summary.getDeviceName()
                    : summary.getDeviceName() + " (" + suffix + ")";

            groupedItems.computeIfAbsent(baseMetric, k -> new ArrayList<>())
                    .add(IncidentChartItem.builder()
                            .categoryLabel(categoryLabel)
                            .warningCount(summary.getWarningCount() != null ? summary.getWarningCount() : 0)
                            .criticalCount(summary.getCriticalCount() != null ? summary.getCriticalCount() : 0)
                            .build());
        }

        List<IncidentChartGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<IncidentChartItem>> entry : groupedItems.entrySet()) {
            groups.add(IncidentChartGroup.builder()
                    .groupTitle(entry.getKey())
                    .itemsDataSource(new JRBeanCollectionDataSource(entry.getValue()))
                    .build());
        }

        return groups;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDeviceMetricLineChart(Long deviceId, LocalDate date, String format) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        LocalDate targetDate = date != null ? date : LocalDate.now(DEFAULT_ZONE);
        ApiResponse<List<MetricAggregateResponse>> metricResponse =
                metricAggregateService.get1DayChartMetrics(deviceId, targetDate);

        if (!metricResponse.isSuccess()) {
            throw new RuntimeException(metricResponse.getMessage());
        }

        List<MetricAggregateResponse> rawMetrics =
                metricResponse.getData() != null ? metricResponse.getData() : Collections.emptyList();

        List<MetricAggregateResponse> sortedMetrics = rawMetrics.stream()
                .sorted(Comparator.comparing(MetricAggregateResponse::getPeriodStart))
                .toList();

        List<DeviceMetricChartPoint> tableRows = new ArrayList<>();
        List<DeviceMetricChartSeriesPoint> chartSeriesPoints = new ArrayList<>();

        for (MetricAggregateResponse item : sortedMetrics) {
            Map<String, Object> aggregatedMap = toMap(item.getAggregatedData());
            Map<String, double[]> cpuSeriesMap = extractMetricEntries(aggregatedMap, "cpu_usage", "CPU");
            Map<String, double[]> memorySeriesMap = extractMetricEntries(aggregatedMap, "memory_usage", "RAM");

            OffsetDateTime startVn = item.getPeriodStart()
                    .atZoneSameInstant(DEFAULT_ZONE)
                    .toOffsetDateTime();
            OffsetDateTime endVn = item.getPeriodEnd() != null
                    ? item.getPeriodEnd().atZoneSameInstant(DEFAULT_ZONE).toOffsetDateTime()
                    : startVn.plusHours(1);

            String timeLabel = startVn.format(TIME_LABEL_FORMATTER)
                    + " - "
                    + endVn.format(TIME_LABEL_FORMATTER);

            Instant midpoint = startVn.toInstant()
                    .plus(Duration.between(startVn, endVn).dividedBy(2));
            Date midpointDate = Date.from(midpoint);

            for (Map.Entry<String, double[]> entry : cpuSeriesMap.entrySet()) {
                double avg = round2(entry.getValue()[0]);
                double max = round2(entry.getValue()[1]);

                chartSeriesPoints.add(DeviceMetricChartSeriesPoint.builder()
                        .seriesName(entry.getKey() + " Avg (%)")
                        .midpointDate(midpointDate)
                        .value(avg)
                        .build());

                tableRows.add(DeviceMetricChartPoint.builder()
                        .timeLabel(timeLabel)
                        .metricName(entry.getKey())
                        .avgValue(avg)
                        .maxValue(max)
                        .build());
            }

            for (Map.Entry<String, double[]> entry : memorySeriesMap.entrySet()) {
                double avg = round2(entry.getValue()[0]);
                double max = round2(entry.getValue()[1]);

                chartSeriesPoints.add(DeviceMetricChartSeriesPoint.builder()
                        .seriesName(entry.getKey() + " Avg (%)")
                        .midpointDate(midpointDate)
                        .value(avg)
                        .build());

                tableRows.add(DeviceMetricChartPoint.builder()
                        .timeLabel(timeLabel)
                        .metricName(entry.getKey())
                        .avgValue(avg)
                        .maxValue(max)
                        .build());
            }
        }

        try {
            JasperReport jasperReport = getCompiledReport(DEVICE_METRIC_LINE_TEMPLATE);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("DEVICE_NAME", device.getDeviceName() + " (#" + deviceId + ")");
            parameters.put("REPORT_DATE", targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            parameters.put("CHART_DATA_SOURCE", new JRBeanCollectionDataSource(chartSeriesPoints));
            parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getTimeZone(DEFAULT_ZONE));

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(tableRows);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            return exportByFormat(jasperPrint, format);
        } catch (Exception e) {
            log.error("Error exporting device metric line chart", e);
            throw new RuntimeException("Failed to export device metric line chart: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toMap(Object aggregatedData) {
        if (aggregatedData == null) {
            return Map.of();
        }
        if (aggregatedData instanceof String json) {
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Map.of();
            }
        }
        return objectMapper.convertValue(aggregatedData, new TypeReference<>() {});
    }

    private Map<String, double[]> extractMetricEntries(
            Map<String, Object> aggregatedMap,
            String baseMetricKey,
            String displayPrefix
    ) {
        Map<String, double[]> result = new TreeMap<>(
                Comparator.comparingInt(this::extractIndexOrder).thenComparing(String::compareTo)
        );
        if (aggregatedMap == null || aggregatedMap.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> entry : aggregatedMap.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(baseMetricKey) && !key.startsWith(baseMetricKey + "-")) {
                continue;
            }

            if (!(entry.getValue() instanceof Map<?, ?> map) || map.isEmpty()) {
                continue;
            }

            String metricLabel = key.equals(baseMetricKey)
                    ? displayPrefix
                    : displayPrefix + " #" + key.substring(baseMetricKey.length() + 1);

            if (map.containsKey("avg")) {
                result.put(metricLabel, new double[]{toDouble(map.get("avg")), toDouble(map.get("max"))});
            } else {
                double sumAvg = 0.0;
                double maxVal = 0.0;
                int count = 0;
                for (Object subValue : map.values()) {
                    if (subValue instanceof Map<?, ?> subMap && subMap.containsKey("avg")) {
                        sumAvg += toDouble(subMap.get("avg"));
                        maxVal = Math.max(maxVal, toDouble(subMap.get("max")));
                        count++;
                    }
                }
                if (count > 0) {
                    result.put(metricLabel, new double[]{sumAvg / count, maxVal});
                }
            }
        }

        return result;
    }

    private int extractIndexOrder(String metricLabel) {
        int hashIdx = metricLabel.indexOf('#');
        if (hashIdx == -1) {
            return -1;
        }
        try {
            return Integer.parseInt(metricLabel.substring(hashIdx + 1).trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private byte[] exportByFormat(JasperPrint jasperPrint, String format) throws Exception {
        if ("xlsx".equalsIgnoreCase(format)) {
            return exportToXlsx(jasperPrint);
        }
        return JasperExportManager.exportReportToPdf(jasperPrint);
    }

    private JasperReport getCompiledReport(String classpathLocation) throws Exception {
        JasperReport cached = compiledReportCache.get(classpathLocation);
        if (cached != null) {
            return cached;
        }
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            JasperReport compiled = JasperCompileManager.compileReport(inputStream);
            compiledReportCache.put(classpathLocation, compiled);
            return compiled;
        }
    }

    private byte[] exportToXlsx(JasperPrint jasperPrint) throws JRException, java.io.IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            JRXlsxExporter exporter = new JRXlsxExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

            SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
            configuration.setOnePagePerSheet(false);
            configuration.setDetectCellType(true);
            exporter.setConfiguration(configuration);

            exporter.exportReport();
            return out.toByteArray();
        }
    }
}