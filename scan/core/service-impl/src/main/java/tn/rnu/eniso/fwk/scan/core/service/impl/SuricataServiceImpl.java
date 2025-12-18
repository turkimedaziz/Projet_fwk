package tn.rnu.eniso.fwk.scan.core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.fwk.scan.core.dal.repository.AlertRepository;
import tn.rnu.eniso.fwk.scan.core.dal.repository.DeviceRepository;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertSeverity;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertStatistics;
import tn.rnu.eniso.fwk.scan.core.infra.model.Device;
import tn.rnu.eniso.fwk.scan.core.service.api.ElasticsearchService;
import tn.rnu.eniso.fwk.scan.core.service.api.SuricataService;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SuricataServiceImpl implements SuricataService {

    private static final Logger log = LoggerFactory.getLogger(SuricataServiceImpl.class);

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;
    private final ElasticsearchService elasticsearchService;
    private final ApplicationEventPublisher eventPublisher;
    private final DailyThreatService dailyThreatService;
    private final ObjectMapper objectMapper = createObjectMapper();

    public SuricataServiceImpl(AlertRepository alertRepository,
            DeviceRepository deviceRepository,
            ElasticsearchService elasticsearchService,
            ApplicationEventPublisher eventPublisher,
            DailyThreatService dailyThreatService) {
        this.alertRepository = alertRepository;
        this.deviceRepository = deviceRepository;
        this.elasticsearchService = elasticsearchService;
        this.eventPublisher = eventPublisher;
        this.dailyThreatService = dailyThreatService;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Override
    public List<Alert> getRecentAlerts(int limit) {
        return alertRepository.findByOrderByTimestampDesc(PageRequest.of(0, limit)).getContent();
    }

    @Override
    public Page<Alert> getAlerts(Pageable pageable) {
        return alertRepository.findByOrderByTimestampDesc(pageable);
    }

    @Override
    public List<Alert> getAlertsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return alertRepository.findByTimestampBetween(start, end);
    }

    @Override
    public List<Alert> getAlertsBySeverity(AlertSeverity severity) {
        return alertRepository.findBySeverityOrderByTimestampDesc(severity);
    }

    @Override
    public List<Alert> getAlertsBySeverity(AlertSeverity severity, int limit) {
        return alertRepository.findBySeverity(severity, PageRequest.of(0, limit));
    }

    @Override
    public List<Alert> getAlertsByIp(String ipAddress) {
        return alertRepository.findByIpAddress(ipAddress);
    }

    @Override
    public Alert getAlertById(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + id));
    }

    @Override
    public AlertStatistics getStatistics(LocalDateTime since) {
        AlertStatistics stats = new AlertStatistics();

        // Total alerts
        stats.setTotalAlerts(alertRepository.countByTimestampAfter(since));

        // Alerts by severity
        stats.setCriticalAlerts(alertRepository.countBySeverityAndTimestampAfter(AlertSeverity.CRITICAL, since));
        stats.setHighAlerts(alertRepository.countBySeverityAndTimestampAfter(AlertSeverity.HIGH, since));
        stats.setMediumAlerts(alertRepository.countBySeverityAndTimestampAfter(AlertSeverity.MEDIUM, since));
        stats.setLowAlerts(alertRepository.countBySeverityAndTimestampAfter(AlertSeverity.LOW, since));

        // Alerts by category
        Map<String, Long> categoryMap = new LinkedHashMap<>();
        alertRepository.countByCategory(since).forEach(row -> categoryMap.put((String) row[0], (Long) row[1]));
        stats.setAlertsByCategory(categoryMap);

        // Top source IPs
        Map<String, Long> sourceIpMap = new LinkedHashMap<>();
        alertRepository.countBySourceIp(since).stream().limit(10)
                .forEach(row -> sourceIpMap.put((String) row[0], (Long) row[1]));
        stats.setTopSourceIps(sourceIpMap);

        // Top destination IPs
        Map<String, Long> destIpMap = new LinkedHashMap<>();
        alertRepository.countByDestIp(since).stream().limit(10)
                .forEach(row -> destIpMap.put((String) row[0], (Long) row[1]));
        stats.setTopDestIps(destIpMap);

        // Top signatures
        Map<String, Long> signatureMap = new LinkedHashMap<>();
        alertRepository.countBySignature(since).stream().limit(10)
                .forEach(row -> signatureMap.put((String) row[0], (Long) row[1]));
        stats.setTopSignatures(signatureMap);

        // Time-based counts
        LocalDateTime now = LocalDateTime.now();
        stats.setAlertsLastHour(alertRepository.countByTimestampAfter(now.minusHours(1)));
        stats.setAlertsLast24Hours(alertRepository.countByTimestampAfter(now.minusDays(1)));
        stats.setAlertsLast7Days(alertRepository.countByTimestampAfter(now.minusDays(7)));

        return stats;
    }

    @Override
    @Transactional
    public void processEveLog(String jsonLog) {
        try {
            JsonNode root = objectMapper.readTree(jsonLog);

            // Check if this is an alert event
            String eventType = root.path("event_type").asText();
            if (!"alert".equals(eventType)) {
                return;
            }

            Alert alert = new Alert();

            // Parse timestamp
            String timestampStr = root.path("timestamp").asText();
            if (!timestampStr.isEmpty()) {
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
                    alert.setTimestamp(zdt.toLocalDateTime());
                } catch (Exception e) {
                    alert.setTimestamp(LocalDateTime.now());
                }
            } else {
                alert.setTimestamp(LocalDateTime.now());
            }

            // Parse source and destination
            alert.setSourceIp(root.path("src_ip").asText());
            alert.setDestIp(root.path("dest_ip").asText());
            alert.setSourcePort(root.path("src_port").asInt(0));
            alert.setDestPort(root.path("dest_port").asInt(0));
            alert.setProtocol(root.path("proto").asText());

            // Parse alert details
            JsonNode alertNode = root.path("alert");
            alert.setSignature(alertNode.path("signature").asText());
            alert.setCategory(alertNode.path("category").asText());
            alert.setSignatureId(alertNode.path("signature_id").asLong());
            alert.setGeneratorId(alertNode.path("gid").asLong());
            alert.setAction(alertNode.path("action").asText());

            // Determine severity based on signature or priority
            int severityLevel = alertNode.path("severity").asInt(2);
            String signature = alertNode.path("signature").asText();

            // Override severity for specific signatures
            if ("CUSTOM Rapid SYN Scan".equals(signature)
                    || "CUSTOM Port Scan Detected - Low Ports".equals(signature)) {
                alert.setSeverity(AlertSeverity.HIGH);
            } else {
                alert.setSeverity(mapSeverity(severityLevel));
            }

            // Parse payload if available
            if (root.has("payload")) {
                alert.setPayload(root.path("payload").asText());
            }

            // Try to correlate with existing devices
            Optional<Device> device = deviceRepository.findByIpAddress(alert.getDestIp());
            device.ifPresent(alert::setDevice);

            // Save to database
            Alert savedAlert = alertRepository.save(alert);
            log.info("Saved alert: {} from {} to {} (Severity: {})", savedAlert.getSignature(),
                    savedAlert.getSourceIp(), savedAlert.getDestIp(), savedAlert.getSeverity());

            // Index in Elasticsearch
            try {
                String esId = elasticsearchService.indexAlert(savedAlert);
                if (esId != null) {
                    savedAlert.setElasticsearchId(esId);
                    alertRepository.save(savedAlert);
                }
            } catch (Exception e) {
                log.warn("Failed to index alert in Elasticsearch", e);
            }

            // Publish event for real-time broadcasting via SSE
            eventPublisher.publishEvent(new AlertEvent(this, savedAlert));
            log.debug("Published AlertEvent for real-time broadcasting");

            // Cache to Redis for AI analysis and update stats
            dailyThreatService.cacheAlert(savedAlert);

            // Check for SYN Flood: Track individual SYN packets
            boolean isSynPacket = "CUSTOM SYN Packet Detected".equals(savedAlert.getSignature());
            if (isSynPacket) {
                // Increment the SYN flood counter for this source->dest pair
                boolean isFlooding = dailyThreatService.isSynFlood(savedAlert.getSourceIp(), savedAlert.getDestIp());

                if (isFlooding) {
                    // Generate critical alert after 10 SYN packets
                    String floodSignature = "Potential DDoS/Flooding: SYN Flood Detected";

                    if (dailyThreatService.shouldGenerateCriticalAlert(savedAlert.getSourceIp(), floodSignature)) {
                        Alert criticalAlert = new Alert();
                        criticalAlert.setTimestamp(LocalDateTime.now());
                        criticalAlert.setSourceIp(savedAlert.getSourceIp());
                        criticalAlert.setDestIp(savedAlert.getDestIp());
                        criticalAlert.setSourcePort(savedAlert.getSourcePort());
                        criticalAlert.setDestPort(savedAlert.getDestPort());
                        criticalAlert.setProtocol(savedAlert.getProtocol());
                        criticalAlert.setSignature(floodSignature);
                        criticalAlert.setCategory("Potential Denial of Service");
                        criticalAlert.setSeverity(AlertSeverity.CRITICAL);
                        criticalAlert.setAction("alert");

                        // Save, Index, Publish
                        Alert savedCritical = alertRepository.save(criticalAlert);

                        try {
                            String esId = elasticsearchService.indexAlert(savedCritical);
                            if (esId != null) {
                                savedCritical.setElasticsearchId(esId);
                                alertRepository.save(savedCritical);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to index critical alert in Elasticsearch", e);
                        }

                        eventPublisher.publishEvent(new AlertEvent(this, savedCritical));
                        dailyThreatService.cacheAlert(savedCritical);

                        log.warn("Generated CRITICAL alert for SYN flooding from {} to {}",
                                savedAlert.getSourceIp(), savedAlert.getDestIp());
                    }
                }
            }

            // Check for flooding/DDoS
            // "CUSTOM Rapid SYN Scan" is already aggregated by Suricata (count 20), so we
            // treat it as flooding immediately
            boolean isSynScan = "CUSTOM Rapid SYN Scan".equals(savedAlert.getSignature());

            // Also check for manual SYN flood detection (Source -> Dest > 10 packets)
            boolean isManualSynFlood = false;
            if (savedAlert.getProtocol() != null && "TCP".equalsIgnoreCase(savedAlert.getProtocol())) {
                // We don't have direct access to flags here easily without parsing payload or
                // flow,
                // but we can infer from signature or just count all TCP from src->dest for now
                // if signature is generic
                // Ideally we should check flags, but for now let's rely on the fact that these
                // are alerts.
                // Actually, let's trust the DailyThreatService to count.
                isManualSynFlood = dailyThreatService.isSynFlood(savedAlert.getSourceIp(), savedAlert.getDestIp());
            }

            if (isSynScan || isManualSynFlood || dailyThreatService.isFlooding(savedAlert)) {
                String floodSignature = "Potential DDoS/Flooding: " + savedAlert.getSignature();
                if (isManualSynFlood) {
                    floodSignature = "Potential DDoS/Flooding: SYN Flood Detected";
                }

                if (dailyThreatService.shouldGenerateCriticalAlert(savedAlert.getSourceIp(), floodSignature)) {
                    Alert criticalAlert = new Alert();
                    criticalAlert.setTimestamp(LocalDateTime.now());
                    criticalAlert.setSourceIp(savedAlert.getSourceIp());
                    criticalAlert.setDestIp(savedAlert.getDestIp());
                    criticalAlert.setSourcePort(savedAlert.getSourcePort());
                    criticalAlert.setDestPort(savedAlert.getDestPort());
                    criticalAlert.setProtocol(savedAlert.getProtocol());
                    criticalAlert.setSignature(floodSignature);
                    criticalAlert.setCategory("Potential Denial of Service");
                    criticalAlert.setSeverity(AlertSeverity.CRITICAL);
                    criticalAlert.setAction("alert");

                    // Save, Index, Publish
                    Alert savedCritical = alertRepository.save(criticalAlert);

                    try {
                        String esId = elasticsearchService.indexAlert(savedCritical);
                        if (esId != null) {
                            savedCritical.setElasticsearchId(esId);
                            alertRepository.save(savedCritical);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to index critical alert in Elasticsearch", e);
                    }

                    eventPublisher.publishEvent(new AlertEvent(this, savedCritical));
                    dailyThreatService.cacheAlert(savedCritical);

                    log.warn("Generated CRITICAL alert for flooding: {}", floodSignature);
                }
            }

        } catch (Exception e) {
            log.error("Error processing EVE log: {}", jsonLog, e);
        }
    }

    @Override
    @Transactional
    public void processEveLogs(List<String> jsonLogs) {
        jsonLogs.forEach(this::processEveLog);
    }

    private AlertSeverity mapSeverity(int suricataSeverity) {
        // Suricata severity: 1 = high, 2 = medium, 3 = low, 4 = very low
        return switch (suricataSeverity) {
            case 1 -> AlertSeverity.HIGH; // HIGH priority
            case 2 -> AlertSeverity.MEDIUM;
            case 3 -> AlertSeverity.LOW;
            case 4 -> AlertSeverity.LOW;
            default -> AlertSeverity.MEDIUM;
        };
    }
}
