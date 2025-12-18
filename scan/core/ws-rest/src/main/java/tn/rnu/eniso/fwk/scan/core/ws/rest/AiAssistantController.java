package tn.rnu.eniso.fwk.scan.core.ws.rest;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.service.api.AiAssistantService;
import tn.rnu.eniso.fwk.scan.core.service.api.SuricataService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for AI Assistant features.
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiAssistantController {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final AiAssistantService aiAssistantService;
    private final SuricataService suricataService;
    public AiAssistantController(AiAssistantService aiAssistantService, SuricataService suricataService) {
        this.aiAssistantService = aiAssistantService;
        this.suricataService = suricataService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean available = aiAssistantService.isAvailable();
        return ResponseEntity.ok(Map.of(
                "status", available ? "UP" : "DOWN",
                "service", "Hugging Face Inference API",
                "timestamp", LocalDateTime.now()));
    }

    @PostMapping("/analyze/{alertId}")
    public ResponseEntity<Map<String, Object>> analyzeAlert(@PathVariable Long alertId) {
        log.info("Received request to analyze alert ID: {}", alertId);

        try {
            Alert alert = suricataService.getAlertById(alertId);
            if (alert == null) {
                return ResponseEntity.notFound().build();
            }

            String analysis = aiAssistantService.analyzeAlert(alert);

            return ResponseEntity.ok(Map.of(
                    "alertId", alertId,
                    "analysis", analysis,
                    "timestamp", LocalDateTime.now(),
                    "alert", alert));
        } catch (Exception e) {
            log.error("Error processing analysis request", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remediation/{alertId}")
    public ResponseEntity<Map<String, Object>> getRemediation(@PathVariable Long alertId) {
        log.info("Received request for remediation of alert ID: {}", alertId);

        try {
            String remediation = aiAssistantService.generateRemediation(alertId);

            return ResponseEntity.ok(Map.of(
                    "alertId", alertId,
                    "remediation", remediation,
                    "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Error processing remediation request", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze/batch")
    public ResponseEntity<List<String>> analyzeBatch(@RequestBody List<Long> alertIds) {
        // Implementation for batch analysis if needed
        // For now, just return empty or implement loop
        return ResponseEntity.ok(List.of("Batch analysis not yet fully implemented in controller"));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSecuritySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        LocalDateTime endTime = end != null ? end : LocalDateTime.now();
        LocalDateTime startTime = start != null ? start : endTime.minusDays(7);

        String summary = aiAssistantService.generateSecuritySummary(startTime, endTime);

        return ResponseEntity.ok(Map.of(
                "period", Map.of("start", startTime, "end", endTime),
                "summary", summary,
                "timestamp", LocalDateTime.now()));
    }
}
