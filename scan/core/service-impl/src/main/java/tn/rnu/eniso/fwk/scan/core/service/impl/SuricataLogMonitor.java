package tn.rnu.eniso.fwk.scan.core.service.impl;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.rnu.eniso.fwk.scan.core.service.api.SuricataService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;

@Service
@ConditionalOnProperty(name = "suricata.log.monitor.enabled", havingValue = "true", matchIfMissing = false)
public class SuricataLogMonitor {

    private static final Logger log = LoggerFactory.getLogger(SuricataLogMonitor.class);

    @Value("${suricata.log.path:/var/log/suricata/eve.json}")
    private String logFilePath;

    @Value("${suricata.log.monitor.delay:1000}")
    private long monitorDelay;

    private final SuricataService suricataService;

    public SuricataLogMonitor(SuricataService suricataService) {
        this.suricataService = suricataService;
    }

    private Tailer tailer;

    @PostConstruct
    public void startMonitoring() {
        File logFile = new File(logFilePath);

        if (!logFile.exists()) {
            log.warn("Suricata log file does not exist: {}. Monitoring will start when file is created.", logFilePath);
        }

        TailerListenerAdapter listener = new TailerListenerAdapter() {
            @Override
            public void handle(String line) {
                if (line != null && !line.trim().isEmpty()) {
                    try {
                        // Process the EVE JSON log line
                        suricataService.processEveLog(line);
                        log.debug("Processed Suricata log line");
                    } catch (Exception e) {
                        log.error("Error processing log line: {}", line, e);
                    }
                }
            }

            @Override
            public void fileNotFound() {
                log.warn("Suricata log file not found: {}", logFilePath);
            }

            @Override
            public void fileRotated() {
                log.info("Suricata log file rotated: {}", logFilePath);
            }
        };

        // Create tailer that follows the file (like tail -f)
        // true = read from end of file (only new alerts)
        tailer = Tailer.create(logFile, listener, monitorDelay, true);

        log.info("Started monitoring Suricata log file: {}", logFilePath);
    }

    @PreDestroy
    public void stopMonitoring() {
        if (tailer != null) {
            tailer.stop();
            log.info("Stopped monitoring Suricata log file");
        }
    }
}
