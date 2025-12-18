package tn.rnu.eniso.fwk.scan.core.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tn.rnu.eniso.fwk.scan.core.dal.repository.DeviceRepository;
import tn.rnu.eniso.fwk.scan.core.dal.repository.ScanSessionRepository;
import tn.rnu.eniso.fwk.scan.core.infra.model.Device;
import tn.rnu.eniso.fwk.scan.core.infra.model.Port;
import tn.rnu.eniso.fwk.scan.core.infra.model.ScanSession;
import tn.rnu.eniso.fwk.scan.core.service.api.NmapService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NmapServiceImpl implements NmapService {

    private static final Logger log = LoggerFactory.getLogger(NmapServiceImpl.class);

    private final ScanSessionRepository scanSessionRepository;
    private final DeviceRepository deviceRepository;

    public NmapServiceImpl(ScanSessionRepository scanSessionRepository, DeviceRepository deviceRepository) {
        this.scanSessionRepository = scanSessionRepository;
        this.deviceRepository = deviceRepository;
    }

    @Override
    @Transactional
    public ScanSession scanNetwork(String target) {
        log.info("Starting Nmap scan for target: {}", target);

        ScanSession scanSession = new ScanSession();
        scanSession.setTimestamp(LocalDateTime.now());
        scanSession.setTarget(target);
        scanSession.setStatus("RUNNING");
        scanSession = scanSessionRepository.save(scanSession);

        try {
            // Execute nmap command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "nmap", "-oX", "-", target);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read XML output
            StringBuilder xmlOutput = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    xmlOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log.info("Nmap process exited with code: {}", exitCode);

            if (exitCode == 0) {
                // Parse XML and populate entities
                List<Device> devices = parseNmapXml(xmlOutput.toString(), scanSession);
                scanSession.setDevices(devices);
                scanSession.setStatus("COMPLETED");
            } else {
                scanSession.setStatus("FAILED");
                log.error("Nmap scan failed with exit code: {}", exitCode);
            }

        } catch (Exception e) {
            log.error("Error during Nmap scan", e);
            scanSession.setStatus("ERROR");
        }

        return scanSessionRepository.save(scanSession);
    }

    private List<Device> parseNmapXml(String xml, ScanSession scanSession) {
        List<Device> devices = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList hostNodes = doc.getElementsByTagName("host");

            for (int i = 0; i < hostNodes.getLength(); i++) {
                Element hostElement = (Element) hostNodes.item(i);

                Device device = new Device();
                device.setScanSession(scanSession);

                // Get host state
                NodeList statusNodes = hostElement.getElementsByTagName("status");
                if (statusNodes.getLength() > 0) {
                    Element statusElement = (Element) statusNodes.item(0);
                    device.setState(statusElement.getAttribute("state"));
                }

                // Get IP address
                NodeList addressNodes = hostElement.getElementsByTagName("address");
                for (int j = 0; j < addressNodes.getLength(); j++) {
                    Element addressElement = (Element) addressNodes.item(j);
                    String addrType = addressElement.getAttribute("addrtype");
                    String addr = addressElement.getAttribute("addr");

                    if ("ipv4".equals(addrType) || "ipv6".equals(addrType)) {
                        device.setIpAddress(addr);
                    } else if ("mac".equals(addrType)) {
                        device.setMacAddress(addr);
                        device.setVendor(addressElement.getAttribute("vendor"));
                    }
                }

                // Get hostname
                NodeList hostnameNodes = hostElement.getElementsByTagName("hostname");
                if (hostnameNodes.getLength() > 0) {
                    Element hostnameElement = (Element) hostnameNodes.item(0);
                    device.setHostname(hostnameElement.getAttribute("name"));
                }

                // Parse ports
                List<Port> ports = new ArrayList<>();
                NodeList portNodes = hostElement.getElementsByTagName("port");

                for (int j = 0; j < portNodes.getLength(); j++) {
                    Element portElement = (Element) portNodes.item(j);

                    Port port = new Port();
                    port.setDevice(device);
                    port.setPortNumber(Integer.parseInt(portElement.getAttribute("portid")));
                    port.setProtocol(portElement.getAttribute("protocol"));

                    // Get port state
                    NodeList stateNodes = portElement.getElementsByTagName("state");
                    if (stateNodes.getLength() > 0) {
                        Element stateElement = (Element) stateNodes.item(0);
                        port.setState(stateElement.getAttribute("state"));
                    }

                    // Get service info
                    NodeList serviceNodes = portElement.getElementsByTagName("service");
                    if (serviceNodes.getLength() > 0) {
                        Element serviceElement = (Element) serviceNodes.item(0);
                        port.setService(serviceElement.getAttribute("name"));
                        port.setVersion(serviceElement.getAttribute("product") + " " +
                                serviceElement.getAttribute("version"));
                    }

                    ports.add(port);
                }

                device.setPorts(ports);
                devices.add(device);
            }

        } catch (Exception e) {
            log.error("Error parsing Nmap XML", e);
        }

        return devices;
    }

    @Override
    public List<ScanSession> getAllScans() {
        return scanSessionRepository.findAll();
    }

    @Override
    public List<Device> getDevices(Long scanId) {
        return deviceRepository.findByScanSessionId(scanId);
    }

    @Override
    public ScanSession getScanById(Long scanId) {
        return scanSessionRepository.findById(scanId)
                .orElseThrow(() -> new RuntimeException("Scan not found with id: " + scanId));
    }
}
