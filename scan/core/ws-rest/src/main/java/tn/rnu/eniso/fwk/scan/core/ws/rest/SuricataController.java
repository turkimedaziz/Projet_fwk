package tn.rnu.eniso.fwk.scan.core.ws.rest;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertSeverity;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertStatistics;
import tn.rnu.eniso.fwk.scan.core.service.api.SuricataService;
import tn.rnu.eniso.fwk.scan.core.service.impl.AlertEvent;
import tn.rnu.eniso.fwk.scan.core.service.impl.DailyThreatService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/suricata")
@CrossOrigin(origins = "*")
public class SuricataController {

    private final SuricataService suricataService;
    private final DailyThreatService dailyThreatService;

    public SuricataController(SuricataService suricataService, DailyThreatService dailyThreatService) {
        this.suricataService = suricataService;
        this.dailyThreatService = dailyThreatService;
    }

    // Store SSE emitters for real-time alert streaming
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    // Store SSE emitters for real-time statistics streaming
    private final List<SseEmitter> statsEmitters = new CopyOnWriteArrayList<>();

    /**
     * Event listener for AlertEvent - broadcasts alerts to all connected SSE
     * clients
     */
    @EventListener
    public void handleAlertEvent(AlertEvent event) {
        broadcastAlert(event.getAlert());
        broadcastStatistics(dailyThreatService.getTodayStatistics());
    }

    @GetMapping("/alerts")
    public ResponseEntity<Page<Alert>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Alert> alerts = suricataService.getAlerts(PageRequest.of(page, size));
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/recent")
    public ResponseEntity<List<Alert>> getRecentAlerts(
            @RequestParam(defaultValue = "100") int limit) {
        List<Alert> alerts = suricataService.getRecentAlerts(limit);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<Alert> getAlertById(@PathVariable Long id) {
        Alert alert = suricataService.getAlertById(id);
        return ResponseEntity.ok(alert);
    }

    @GetMapping("/alerts/severity/{severity}")
    public ResponseEntity<List<Alert>> getAlertsBySeverity(
            @PathVariable AlertSeverity severity,
            @RequestParam(defaultValue = "1000") int limit) {
        List<Alert> alerts = suricataService.getAlertsBySeverity(severity, limit);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/ip/{ipAddress}")
    public ResponseEntity<List<Alert>> getAlertsByIp(@PathVariable String ipAddress) {
        List<Alert> alerts = suricataService.getAlertsByIp(ipAddress);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/timerange")
    public ResponseEntity<List<Alert>> getAlertsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<Alert> alerts = suricataService.getAlertsByTimeRange(start, end);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/statistics")
    public ResponseEntity<AlertStatistics> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        LocalDateTime sinceTime = since != null ? since : LocalDateTime.now().minusDays(7);
        AlertStatistics stats = suricataService.getStatistics(sinceTime);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get today's statistics from Redis
     */
    @GetMapping("/statistics/today")
    public ResponseEntity<DailyThreatService.TodayStatistics> getTodayStatistics() {
        DailyThreatService.TodayStatistics stats = dailyThreatService.getTodayStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    @GetMapping(value = "/statistics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatistics() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        statsEmitters.add(emitter);

        emitter.onCompletion(() -> statsEmitters.remove(emitter));
        emitter.onTimeout(() -> statsEmitters.remove(emitter));
        emitter.onError((e) -> statsEmitters.remove(emitter));

        // Send initial stats
        try {
            emitter.send(SseEmitter.event()
                    .name("stats")
                    .data(dailyThreatService.getTodayStatistics()));
        } catch (Exception e) {
            statsEmitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * This method can be called by the log monitor to broadcast new alerts
     */
    public void broadcastAlert(Alert alert) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("alert")
                        .data(alert));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });

        emitters.removeAll(deadEmitters);
    }

    public void broadcastStatistics(DailyThreatService.TodayStatistics stats) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        statsEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("stats")
                        .data(stats));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });

        statsEmitters.removeAll(deadEmitters);
    }
}
