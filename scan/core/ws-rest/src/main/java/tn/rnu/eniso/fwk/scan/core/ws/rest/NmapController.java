package tn.rnu.eniso.fwk.scan.core.ws.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rnu.eniso.fwk.scan.core.infra.model.Device;
import tn.rnu.eniso.fwk.scan.core.infra.model.ScanSession;
import tn.rnu.eniso.fwk.scan.core.service.api.NmapService;

import java.util.List;

@RestController
@RequestMapping("/api/nmap")
@CrossOrigin(origins = "*")
public class NmapController {

    private final NmapService nmapService;
    public NmapController(NmapService nmapService) {
        this.nmapService = nmapService;
    }

    @PostMapping("/scan")
    public ResponseEntity<ScanSession> scanNetwork(@RequestParam String target) {
        ScanSession scanSession = nmapService.scanNetwork(target);
        return ResponseEntity.ok(scanSession);
    }

    @GetMapping("/scans")
    public ResponseEntity<List<ScanSession>> getAllScans() {
        List<ScanSession> scans = nmapService.getAllScans();
        return ResponseEntity.ok(scans);
    }

    @GetMapping("/scans/{id}")
    public ResponseEntity<ScanSession> getScanById(@PathVariable Long id) {
        ScanSession scan = nmapService.getScanById(id);
        return ResponseEntity.ok(scan);
    }

    @GetMapping("/scans/{id}/devices")
    public ResponseEntity<List<Device>> getDevicesByScan(@PathVariable Long id) {
        List<Device> devices = nmapService.getDevices(id);
        return ResponseEntity.ok(devices);
    }
}
