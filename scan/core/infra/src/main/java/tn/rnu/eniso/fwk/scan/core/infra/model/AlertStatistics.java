package tn.rnu.eniso.fwk.scan.core.infra.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertStatistics {
    private long totalAlerts;
    private long criticalAlerts;
    private long highAlerts;
    private long mediumAlerts;
    private long lowAlerts;

    private Map<String, Long> alertsByCategory;
    private Map<String, Long> topSourceIps;
    private Map<String, Long> topDestIps;
    private Map<String, Long> topSignatures;

    private long alertsLastHour;
    private long alertsLast24Hours;
    private long alertsLast7Days;

    public long getTotalAlerts() {
        return totalAlerts;
    }

    public void setTotalAlerts(long totalAlerts) {
        this.totalAlerts = totalAlerts;
    }

    public long getCriticalAlerts() {
        return criticalAlerts;
    }

    public void setCriticalAlerts(long criticalAlerts) {
        this.criticalAlerts = criticalAlerts;
    }

    public long getHighAlerts() {
        return highAlerts;
    }

    public void setHighAlerts(long highAlerts) {
        this.highAlerts = highAlerts;
    }

    public long getMediumAlerts() {
        return mediumAlerts;
    }

    public void setMediumAlerts(long mediumAlerts) {
        this.mediumAlerts = mediumAlerts;
    }

    public long getLowAlerts() {
        return lowAlerts;
    }

    public void setLowAlerts(long lowAlerts) {
        this.lowAlerts = lowAlerts;
    }

    public Map<String, Long> getAlertsByCategory() {
        return alertsByCategory;
    }

    public void setAlertsByCategory(Map<String, Long> alertsByCategory) {
        this.alertsByCategory = alertsByCategory;
    }

    public Map<String, Long> getTopSourceIps() {
        return topSourceIps;
    }

    public void setTopSourceIps(Map<String, Long> topSourceIps) {
        this.topSourceIps = topSourceIps;
    }

    public Map<String, Long> getTopDestIps() {
        return topDestIps;
    }

    public void setTopDestIps(Map<String, Long> topDestIps) {
        this.topDestIps = topDestIps;
    }

    public Map<String, Long> getTopSignatures() {
        return topSignatures;
    }

    public void setTopSignatures(Map<String, Long> topSignatures) {
        this.topSignatures = topSignatures;
    }

    public long getAlertsLastHour() {
        return alertsLastHour;
    }

    public void setAlertsLastHour(long alertsLastHour) {
        this.alertsLastHour = alertsLastHour;
    }

    public long getAlertsLast24Hours() {
        return alertsLast24Hours;
    }

    public void setAlertsLast24Hours(long alertsLast24Hours) {
        this.alertsLast24Hours = alertsLast24Hours;
    }

    public long getAlertsLast7Days() {
        return alertsLast7Days;
    }

    public void setAlertsLast7Days(long alertsLast7Days) {
        this.alertsLast7Days = alertsLast7Days;
    }
}
