package tn.rnu.eniso.fwk.scan.core.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.rnu.eniso.fwk.scan.core.dal.repository.AlertRepository;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.service.api.AiAssistantService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AI Assistant service for security alert analysis.
 * Uses Hugging Face API to provide intelligent recommendations.
 */
@Service
public class AiAssistantServiceImpl implements AiAssistantService {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);

    private final HuggingFaceClient huggingFaceClient;
    private final AlertRepository alertRepository;
    private final DailyThreatService dailyThreatService;

    public AiAssistantServiceImpl(HuggingFaceClient huggingFaceClient,
            AlertRepository alertRepository,
            DailyThreatService dailyThreatService) {
        this.huggingFaceClient = huggingFaceClient;
        this.alertRepository = alertRepository;
        this.dailyThreatService = dailyThreatService;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String analyzeAlert(Alert alert) {
        if (alert == null) {
            log.warn("Null alert provided for analysis");
            return "Invalid alert provided.";
        }

        log.info("Analyzing alert ID: {} with signature: {}", alert.getId(), alert.getSignature());

        try {
            // Build detailed alert information
            String alertDetails = formatAlertDetails(alert);

            // Create the prompt using the client's helper method
            String prompt = huggingFaceClient.buildAlertAnalysisPrompt(alertDetails);

            // Get AI analysis
            String analysis = huggingFaceClient.generateResponse(prompt);

            log.info("Successfully analyzed alert ID: {}", alert.getId());
            return analysis;

        } catch (Exception e) {
            log.error("Error analyzing alert ID: {}", alert.getId(), e);
            return "Error analyzing alert: " + e.getMessage();
        }
    }

    @Override
    public String generateRemediation(Long alertId) {
        if (alertId == null) {
            log.warn("Null alert ID provided for remediation");
            return "Invalid alert ID provided.";
        }

        log.info("Generating remediation for alert ID: {}", alertId);

        try {
            // Fetch the alert from database
            Alert alert = alertRepository.findById(alertId)
                    .orElseThrow(() -> new IllegalArgumentException("Alert not found with ID: " + alertId));

            // Build focused remediation prompt
            String alertDetails = formatAlertDetails(alert);

            String prompt = String.format("""
                    You are a cybersecurity expert. Provide ONLY specific remediation steps for this security alert.

                    Alert: %s

                    Provide 5-7 concrete, actionable steps to remediate this security issue.
                    Format as a numbered list. Be specific and technical.
                    """, alertDetails);

            String remediation = huggingFaceClient.generateResponse(prompt);

            log.info("Successfully generated remediation for alert ID: {}", alertId);
            return remediation;

        } catch (IllegalArgumentException e) {
            log.error("Alert not found: {}", alertId);
            return "Alert not found with ID: " + alertId;

        } catch (Exception e) {
            log.error("Error generating remediation for alert ID: {}", alertId, e);
            return "Error generating remediation: " + e.getMessage();
        }
    }

    @Override
    public List<String> analyzeAlerts(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            log.warn("Empty or null alert list provided for batch analysis");
            return List.of("No alerts provided for analysis.");
        }

        log.info("Analyzing batch of {} alerts", alerts.size());

        List<String> analyses = new ArrayList<>();

        for (Alert alert : alerts) {
            try {
                String analysis = analyzeAlert(alert);
                analyses.add(analysis);
            } catch (Exception e) {
                log.error("Error analyzing alert ID: {} in batch", alert.getId(), e);
                analyses.add("Error analyzing alert: " + e.getMessage());
            }
        }

        log.info("Completed batch analysis of {} alerts", alerts.size());
        return analyses;
    }

    @Override
    public String generateSecuritySummary(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            log.warn("Invalid time range provided for security summary");
            return "Invalid time range provided.";
        }

        log.info("Generating security summary for period: {} to {}", start, end);

        try {
            // Fetch alerts from Redis for today if the range is within today
            List<Alert> alerts;
            if (start.toLocalDate().equals(LocalDate.now()) && end.toLocalDate().equals(LocalDate.now())) {
                log.info("Fetching today's alerts from Redis for security summary");
                alerts = dailyThreatService.getTodayAlerts();
            } else {
                log.info("Fetching alerts from DB for security summary (range: {} to {})", start, end);
                alerts = alertRepository.findByTimestampBetween(start, end);
            }

            if (alerts.isEmpty()) {
                return "No security alerts found in the specified time period.";
            }

            // Build summary statistics
            String alertsSummary = buildAlertsSummary(alerts, start, end);

            // Create summary prompt
            String prompt = huggingFaceClient.buildSecuritySummaryPrompt(alertsSummary);

            // Get AI-generated summary
            String summary = huggingFaceClient.generateResponse(prompt);

            log.info("Successfully generated security summary for {} alerts", alerts.size());
            return summary;

        } catch (Exception e) {
            log.error("Error generating security summary", e);
            return "Error generating security summary: " + e.getMessage();
        }
    }

    @Override
    public boolean isAvailable() {
        return huggingFaceClient.isAvailable();
    }

    /**
     * Format alert details into a readable string for AI analysis
     */
    private String formatAlertDetails(Alert alert) {
        return String.format("""
                Timestamp: %s
                Source IP: %s (Port: %s)
                Destination IP: %s (Port: %s)
                Protocol: %s
                Signature: %s
                Category: %s
                Severity: %s
                Signature ID: %s
                Action: %s
                """,
                alert.getTimestamp() != null ? alert.getTimestamp().format(FORMATTER) : "Unknown",
                alert.getSourceIp() != null ? alert.getSourceIp() : "Unknown",
                alert.getSourcePort() != null ? alert.getSourcePort() : "N/A",
                alert.getDestIp() != null ? alert.getDestIp() : "Unknown",
                alert.getDestPort() != null ? alert.getDestPort() : "N/A",
                alert.getProtocol() != null ? alert.getProtocol() : "Unknown",
                alert.getSignature() != null ? alert.getSignature() : "Unknown",
                alert.getCategory() != null ? alert.getCategory() : "Unknown",
                alert.getSeverity() != null ? alert.getSeverity() : "Unknown",
                alert.getSignatureId() != null ? alert.getSignatureId() : "N/A",
                alert.getAction() != null ? alert.getAction() : "Unknown");
    }

    /**
     * Build a summary of alerts for a time period
     */
    private String buildAlertsSummary(List<Alert> alerts, LocalDateTime start, LocalDateTime end) {
        // Count by severity
        long critical = alerts.stream().filter(a -> "CRITICAL".equalsIgnoreCase(a.getSeverity().toString())).count();
        long high = alerts.stream().filter(a -> "HIGH".equalsIgnoreCase(a.getSeverity().toString())).count();
        long medium = alerts.stream().filter(a -> "MEDIUM".equalsIgnoreCase(a.getSeverity().toString())).count();
        long low = alerts.stream().filter(a -> "LOW".equalsIgnoreCase(a.getSeverity().toString())).count();

        // Get top categories
        String topCategories = alerts.stream()
                .map(Alert::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        // Get top source IPs
        String topSourceIPs = alerts.stream()
                .map(Alert::getSourceIp)
                .filter(ip -> ip != null && !ip.isEmpty())
                .collect(Collectors.groupingBy(ip -> ip, Collectors.counting()))
                .entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + " alerts)")
                .collect(Collectors.joining(", "));

        return String.format("""
                Time Period: %s to %s
                Total Alerts: %d

                Severity Distribution:
                - Critical: %d
                - High: %d
                - Medium: %d
                - Low: %d

                Top Alert Categories: %s

                Top Source IPs: %s
                """,
                start.format(FORMATTER),
                end.format(FORMATTER),
                alerts.size(),
                critical, high, medium, low,
                topCategories.isEmpty() ? "None" : topCategories,
                topSourceIPs.isEmpty() ? "None" : topSourceIPs);
    }
}
