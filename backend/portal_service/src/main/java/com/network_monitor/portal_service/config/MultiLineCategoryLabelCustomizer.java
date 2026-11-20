package com.network_monitor.portal_service.config;

import net.sf.jasperreports.engine.JRChart;
import net.sf.jasperreports.engine.JRChartCustomizer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.plot.CategoryPlot;

public class MultiLineCategoryLabelCustomizer implements JRChartCustomizer {
    @Override
    public void customize(JFreeChart chart, JRChart jasperChart) {
        CategoryPlot plot = chart.getCategoryPlot();
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setMaximumCategoryLabelLines(4);
        domainAxis.setMaximumCategoryLabelWidthRatio(0.95f);
    }
}
