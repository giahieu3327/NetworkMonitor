package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jasperreports.engine.JRDataSource;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentChartGroup {
    private String groupTitle;
    private JRDataSource itemsDataSource;
}