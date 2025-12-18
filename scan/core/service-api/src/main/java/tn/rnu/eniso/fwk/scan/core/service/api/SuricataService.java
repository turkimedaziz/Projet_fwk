package tn.rnu.eniso.fwk.scan.core.service.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertSeverity;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertStatistics;

import java.time.LocalDateTime;
import java.util.List;

public interface SuricataService {

    /**
     * Get recent alerts with limit
     */
    List<Alert> getRecentAlerts(int limit);

    /**
     * Get alerts with pagination
     */
    Page<Alert> getAlerts(Pageable pageable);

    /**
     * Get alerts within a time range
     */
    List<Alert> getAlertsByTimeRange(LocalDateTime start, LocalDateTime end);

    /**
     * Get alerts by severity level
     */
    List<Alert> getAlertsBySeverity(AlertSeverity severity);

    /**
     * Get alerts by severity level with limit
     */
    List<Alert> getAlertsBySeverity(AlertSeverity severity, int limit);

    /**
     * Get alerts related to a specific IP address
     */
    List<Alert> getAlertsByIp(String ipAddress);

    /**
     * Get alert by ID
     */
    Alert getAlertById(Long id);

    /**
     * Get statistics for dashboard
     */
    AlertStatistics getStatistics(LocalDateTime since);

    /**
     * Process a Suricata EVE JSON log entry
     */
    void processEveLog(String jsonLog);

    /**
     * Process multiple EVE JSON log entries
     */
    void processEveLogs(List<String> jsonLogs);
}
