package tn.rnu.eniso.fwk.scan.core.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertSeverity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for caching Suricata alerts in Redis for daily analysis.
 *
 * Each day has its own Redis list with key: daily_threats:{yyyy-MM-dd}.
 * Values are stored as JSON thanks to
 * {@link tn.rnu.eniso.fwk.scan.core.service.impl.RedisConfig}.
 */
@Service
public class DailyThreatService {

    private static final Logger log = LoggerFactory.getLogger(DailyThreatService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String KEY_PREFIX = "daily_threats:";

    private final RedisTemplate<String, Alert> alertRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final tn.rnu.eniso.fwk.scan.core.dal.repository.AlertRepository alertRepository;

    public DailyThreatService(RedisTemplate<String, Alert> alertRedisTemplate,
            StringRedisTemplate stringRedisTemplate,
            tn.rnu.eniso.fwk.scan.core.dal.repository.AlertRepository alertRepository) {
        this.alertRedisTemplate = alertRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.alertRepository = alertRepository;
    }

    public void cacheAlert(Alert alert) {
        if (alert == null) {
            return;
        }

        try {
            String key = buildKey(alert);
            alertRedisTemplate.opsForList().rightPush(key, alert);

            // Increment daily statistics
            incrementDailyStats(alert);

            log.debug("Cached alert {} into Redis list {}", alert.getId(), key);
        } catch (Exception e) {
            // Redis must not break the main alert-processing pipeline
            log.warn("Failed to cache alert in Redis", e);
        }
    }

    private void incrementDailyStats(Alert alert) {
        try {
            String statsKey = "stats:" + DATE_FORMATTER.format(LocalDate.now());
            alertRedisTemplate.opsForHash().increment(statsKey, "total", 1);

            if (alert.getSeverity() != null) {
                switch (alert.getSeverity()) {
                    case CRITICAL -> alertRedisTemplate.opsForHash().increment(statsKey, "critical", 1);
                    case HIGH -> alertRedisTemplate.opsForHash().increment(statsKey, "high", 1);
                    case MEDIUM -> alertRedisTemplate.opsForHash().increment(statsKey, "medium", 1);
                    case LOW -> alertRedisTemplate.opsForHash().increment(statsKey, "low", 1);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to increment daily stats in Redis", e);
        }
    }

    /**
     * Check if there is a flooding attack (repeated threats)
     */
    public boolean isFlooding(Alert alert) {
        try {
            String key = "flood:" + alert.getSourceIp() + ":" + alert.getSignature();
            Long count = alertRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                alertRedisTemplate.expire(key, java.time.Duration.ofSeconds(10));
            }
            // Threshold: 20 alerts in 10 seconds
            return count != null && count > 20;
        } catch (Exception e) {
            log.warn("Failed to check flooding in Redis", e);
            return false;
        }
    }

    /**
     * Check for SYN Flooding: > 10 SYN packets from same Source to same Dest in 10
     * seconds
     */
    public boolean isSynFlood(String sourceIp, String destIp) {
        try {
            String key = "syn_flood:" + sourceIp + ":" + destIp;
            Long count = alertRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                alertRedisTemplate.expire(key, java.time.Duration.ofSeconds(10));
            }
            // Threshold: > 10 SYN packets
            return count != null && count > 10;
        } catch (Exception e) {
            log.warn("Failed to check SYN flooding in Redis", e);
            return false;
        }
    }

    /**
     * Check if we should generate a critical alert (rate limiting: max 5 times)
     */
    public boolean shouldGenerateCriticalAlert(String sourceIp, String signature) {
        try {
            String key = "critical_limit:" + sourceIp + ":" + signature;
            Long count = alertRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // Reset limit every hour
                alertRedisTemplate.expire(key, java.time.Duration.ofHours(1));
            }
            return count != null && count <= 5;
        } catch (Exception e) {
            log.warn("Failed to check critical alert limit in Redis", e);
            return true; // Default to true if Redis fails
        }
    }

    /**
     * Get all alerts for today from Redis
     */
    public List<Alert> getTodayAlerts() {
        try {
            String key = buildTodayKey();
            Long size = alertRedisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return List.of();
            }
            return alertRedisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            log.error("Failed to get today's alerts from Redis", e);
            return List.of();
        }
    }

    /**
     * Get statistics for today's alerts from Redis (Optimized)
     */
    public TodayStatistics getTodayStatistics() {
        try {
            String statsKey = "stats:" + DATE_FORMATTER.format(LocalDate.now());

            // Check if stats exist in Redis
            Boolean exists = stringRedisTemplate.hasKey(statsKey);
            if (Boolean.FALSE.equals(exists)) {
                backfillStatsFromDb(statsKey);
            }

            List<Object> values = stringRedisTemplate.opsForHash().multiGet(statsKey,
                    List.of("total", "critical", "high", "medium", "low"));

            long total = parseLong(values.get(0));
            long critical = parseLong(values.get(1));
            long high = parseLong(values.get(2));
            long medium = parseLong(values.get(3));
            long low = parseLong(values.get(4));

            long globalTotal = alertRepository.count();

            return new TodayStatistics(globalTotal, total, critical, high, medium, low);
        } catch (Exception e) {
            log.error("Failed to get today's statistics from Redis", e);
            return new TodayStatistics(0, 0, 0, 0, 0, 0);
        }
    }

    private void backfillStatsFromDb(String statsKey) {
        log.info("Backfilling daily stats from DB to Redis for key: {}", statsKey);
        try {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            List<Alert> todayAlerts = alertRepository.findByTimestampAfter(startOfDay);

            long total = todayAlerts.size();
            long critical = todayAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.CRITICAL).count();
            long high = todayAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.HIGH).count();
            long medium = todayAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.MEDIUM).count();
            long low = todayAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.LOW).count();

            Map<String, String> stats = new HashMap<>();
            stats.put("total", String.valueOf(total));
            stats.put("critical", String.valueOf(critical));
            stats.put("high", String.valueOf(high));
            stats.put("medium", String.valueOf(medium));
            stats.put("low", String.valueOf(low));

            stringRedisTemplate.opsForHash().putAll(statsKey, stats);
            stringRedisTemplate.expire(statsKey, java.time.Duration.ofDays(2)); // Keep for 2 days

        } catch (Exception e) {
            log.error("Failed to backfill stats from DB", e);
        }
    }

    private long parseLong(Object obj) {
        if (obj == null)
            return 0;
        if (obj instanceof Number)
            return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String buildKey(Alert alert) {
        LocalDate date = alert.getTimestamp() != null
                ? alert.getTimestamp().toLocalDate()
                : LocalDate.now();
        return KEY_PREFIX + DATE_FORMATTER.format(date);
    }

    private String buildTodayKey() {
        return KEY_PREFIX + DATE_FORMATTER.format(LocalDate.now());
    }

    /**
     * Statistics for today's alerts
     */
    public static class TodayStatistics {
        private long globalTotal;
        private long totalAlerts;
        private long criticalAlerts;
        private long highAlerts;
        private long mediumAlerts;
        private long lowAlerts;

        public TodayStatistics() {
        }

        public TodayStatistics(long globalTotal, long totalAlerts, long criticalAlerts,
                long highAlerts, long mediumAlerts, long lowAlerts) {
            this.globalTotal = globalTotal;
            this.totalAlerts = totalAlerts;
            this.criticalAlerts = criticalAlerts;
            this.highAlerts = highAlerts;
            this.mediumAlerts = mediumAlerts;
            this.lowAlerts = lowAlerts;
        }

        public long getGlobalTotal() {
            return globalTotal;
        }

        public void setGlobalTotal(long globalTotal) {
            this.globalTotal = globalTotal;
        }

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
    }
}
