package com.network_monitor.portal_service.config;

import net.sf.jasperreports.engine.JRChart;
import net.sf.jasperreports.engine.JRChartCustomizer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.plot.XYPlot;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class TimeSeriesAxisCustomizer implements JRChartCustomizer {

    @Override
    public void customize(JFreeChart chart, JRChart jasperChart) {
        XYPlot plot = chart.getXYPlot();
        plot.setDomainGridlinesVisible(true);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        TimeZone vnZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(vnZone);

        domainAxis.setTimeZone(vnZone);
        domainAxis.setDateFormatOverride(sdf);
        domainAxis.setTickUnit(new DateTickUnit(DateTickUnitType.HOUR, 1));
        domainAxis.setVerticalTickLabels(true);
    }
}