package tn.rnu.eniso.fwk.scan.core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
@Component
public class HuggingFaceClient {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceClient.class);
    private final WebClient webClient;
    private final String apiToken;
    private final String apiUrl;
    private final boolean enabled;
    private final int maxTokens;
    private final double temperature;
    private final ObjectMapper objectMapper;

    public HuggingFaceClient(
            WebClient.Builder webClientBuilder,
            @Value("${huggingface.api.token:}") String apiToken,
            @Value("${huggingface.api.url:https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2}") String apiUrl,
            @Value("${huggingface.api.enabled:true}") boolean enabled,
            @Value("${huggingface.api.timeout:30000}") int timeout,
            @Value("${huggingface.api.max-tokens:500}") int maxTokens,
            @Value("${huggingface.api.temperature:0.7}") double temperature,
            ObjectMapper objectMapper) {

        this.apiToken = apiToken;
        this.apiUrl = apiUrl;
        this.enabled = enabled;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.objectMapper = objectMapper;

        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public boolean isAvailable() {
        return enabled && apiToken != null && !apiToken.isEmpty();
    }

    public String generateResponse(String prompt) {
        if (!enabled || apiToken == null || apiToken.isEmpty()) {
            log.warn("Hugging Face API is disabled or token is missing. Using fallback.");
            return generateFallbackResponse(prompt);
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "inputs", prompt,
                    "parameters", Map.of(
                            "max_new_tokens", maxTokens,
                            "temperature", temperature,
                            "return_full_text", false));

            String responseBody = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return parseResponse(responseBody);

        } catch (WebClientResponseException e) {
            log.error("Hugging Face API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return generateFallbackResponse(prompt);
        } catch (Exception e) {
            log.warn("Error calling Hugging Face API: {} - using fallback response", e.getMessage());
            return generateFallbackResponse(prompt);
        }
    }

    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isArray() && root.size() > 0) {
                return root.get(0).path("generated_text").asText();
            } else if (root.isObject() && root.has("generated_text")) {
                return root.get("generated_text").asText();
            }
            return responseBody;
        } catch (Exception e) {
            log.error("Error parsing AI response", e);
            return "Error parsing AI response";
        }
    }

    /**
     * Build a prompt for alert analysis
     */
    public String buildAlertAnalysisPrompt(String alertDetails) {
        return String.format("""
                You are an advanced Cybersecurity AI Assistant. Analyze the following intrusion detection alert.

                ALERT DATA:
                %s

                RESPONSE FORMAT (Markdown):
                ### 🛡️ Threat Analysis
                [Detailed technical explanation of what is happening]

                ### ⚠️ Severity & Impact
                [Assessment of severity and potential business impact]

                ### 🛠️ Recommended Remediation
                1. [Actionable step 1]
                2. [Actionable step 2]
                3. [Actionable step 3]

                ### 🔍 Investigation Commands
                ```bash
                [Provide 2-3 relevant Linux/Network commands to investigate this]
                ```

                Keep the tone professional, concise, and actionable.
                """, alertDetails);
    }

    /**
     * Build a prompt for security summary
     */
    public String buildSecuritySummaryPrompt(String alertsSummary) {
        return String.format(
                """
                        You are a CISO-level AI Security Consultant. Provide a strategic summary of the following security events.

                        EVENTS SUMMARY:
                        %s

                        RESPONSE FORMAT (Markdown):
                        ### 📊 Executive Summary
                        [High-level overview of the security posture]

                        ### 🚨 Top Security Concerns
                        * **[Concern 1]**: [Brief explanation]
                        * **[Concern 2]**: [Brief explanation]

                        ### 🎯 Strategic Recommendations
                        1. [Strategic recommendation 1]
                        2. [Strategic recommendation 2]

                        ### 🔮 Threat Intelligence
                        [Brief insight on observed patterns]
                        """,
                alertsSummary);
    }

    /**
     * Generate intelligent fallback response when Hugging Face API is unavailable
     * Enhanced with DDoS detection and personalized responses
     */
    private String generateFallbackResponse(String prompt) {
        log.info("Generating smart fallback AI response with enhanced detection");

        String promptLower = prompt.toLowerCase();
        StringBuilder response = new StringBuilder();

        // Extract ALL available details from the prompt for maximum personalization
        String sourceIp = extractValue(prompt, "Source IP: ([\\d\\.]+)");
        String destIp = extractValue(prompt, "Destination IP: ([\\d\\.]+)");
        String sourcePort = extractValue(prompt, "Source IP: .*\\(Port: (\\d+)\\)");
        String destPort = extractValue(prompt, "Destination IP: .*\\(Port: (\\d+)\\)");
        String protocol = extractValue(prompt, "Protocol: (\\w+)");
        String signature = extractValue(prompt, "Signature: (.+)");
        String category = extractValue(prompt, "Category: (.+)");
        String severity = extractValue(prompt, "Severity: (\\w+)");
        String signatureId = extractValue(prompt, "Signature ID: (\\d+)");
        String timestamp = extractValue(prompt, "Timestamp: (.+)");

        // Advanced threat detection with multiple indicators
        boolean isPortScan = promptLower.contains("port scan") || promptLower.contains("nmap")
                || promptLower.contains("scanning") || signature.toLowerCase().contains("scan");

        // Enhanced DDoS/Flood detection - check for multiple indicators
        boolean isDdos = promptLower.contains("flood") || promptLower.contains("dos")
                || promptLower.contains("denial") || promptLower.contains("ddos")
                || signature.toLowerCase().contains("flood")
                || signature.toLowerCase().contains("storm")
                || (isPortScan && (severity.equalsIgnoreCase("HIGH") || severity.equalsIgnoreCase("CRITICAL")));

        boolean isSqlInjection = promptLower.contains("sql") || promptLower.contains("injection")
                || promptLower.contains("union select");
        boolean isSsh = promptLower.contains("ssh") || promptLower.contains("login") || promptLower.contains("brute");
        boolean isWebAttack = promptLower.contains("http") || promptLower.contains("web")
                || destPort.equals("80") || destPort.equals("443") || destPort.equals("8080");

        response.append("### 🛡️ AI Security Analysis\\n\\n");

        // DDoS/Flood attacks - HIGHEST PRIORITY
        if (isDdos) {
            response.append(
                    "**🚨 CRITICAL THREAT**: Distributed Denial of Service (DDoS) / Network Flood Attack\\n\\n");
            response.append("**Attack Vector**: Massive traffic flood detected from **" + sourceIp + "** targeting **"
                    + destIp + "**");
            if (!destPort.equals("Unknown") && !destPort.equals("N/A")) {
                response.append(" on port **" + destPort + "**");
            }
            response.append(".\\n\\n");

            response.append("**Attack Characteristics**:\\n");
            response.append(
                    "* **Source**: " + sourceIp + (sourcePort.equals("Unknown") ? "" : ":" + sourcePort) + "\\n");
            response.append("* **Target**: " + destIp + (destPort.equals("Unknown") ? "" : ":" + destPort) + "\\n");
            response.append("* **Protocol**: " + protocol + "\\n");
            response.append("* **Signature**: " + signature + "\\n");
            response.append("* **Detection Time**: " + timestamp + "\\n\\n");

            response.append("This appears to be a **" + protocol + " flood attack** ");
            if (isPortScan) {
                response.append(
                        "combined with aggressive port scanning (likely **nmap** with aggressive timing options like `-T4` or `-T5`). ");
                response.append(
                        "The attacker is simultaneously mapping your network AND attempting to overwhelm it.\\n\\n");
            } else {
                response.append(
                        "designed to exhaust system resources (CPU, RAM, bandwidth) and cause service disruption.\\n\\n");
            }

            response.append("### ⚠️ Impact Assessment\\n");
            response.append("* **Severity**: **" + severity.toUpperCase() + "** - CRITICAL\\n");
            response.append(
                    "* **Immediate Risk**: Complete service unavailability, system crash, network saturation\\n");
            response.append("* **Target System**: " + destIp + " is under active attack\\n");
            response.append("* **Business Impact**: Service downtime, potential revenue loss, reputation damage\\n\\n");

            response.append("### 🛠️ IMMEDIATE Remediation Steps\\n");
            response.append("1. **URGENT - Block Attack Source**: Execute immediately:\\n");
            response.append("   ```bash\\n");
            response.append("   sudo iptables -A INPUT -s " + sourceIp + " -j DROP\\n");
            response.append("   sudo iptables-save > /etc/iptables/rules.v4\\n");
            response.append("   ```\\n");
            response.append("2. **Rate Limiting**: Implement aggressive rate limiting on " + protocol + " traffic:\\n");
            response.append("   ```bash\\n");
            response.append("   sudo iptables -A INPUT -p " + protocol.toLowerCase()
                    + " -m limit --limit 10/s --limit-burst 20 -j ACCEPT\\n");
            response.append("   sudo iptables -A INPUT -p " + protocol.toLowerCase() + " -j DROP\\n");
            response.append("   ```\\n");
            response.append("3. **Enable SYN Cookies** (if TCP flood):\\n");
            response.append("   ```bash\\n");
            response.append("   sudo sysctl -w net.ipv4.tcp_syncookies=1\\n");
            response.append("   ```\\n");
            response.append("4. **Contact ISP/DDoS Mitigation Service**: If traffic volume is saturating your link\\n");
            response.append("5. **Monitor System Resources**: Watch CPU, memory, and network utilization\\n\\n");

            response.append("### 🔍 Investigation & Forensics\\n");
            response.append("```bash\\n");
            response.append("# Monitor real-time attack traffic\\n");
            response.append("sudo tcpdump -i any -n src " + sourceIp + " | head -100\\n\\n");
            response.append("# Count packets from attacker\\n");
            response.append("sudo tcpdump -i any -n src " + sourceIp + " -c 1000 | wc -l\\n\\n");
            response.append("# Check current connection states\\n");
            response.append("netstat -an | grep " + sourceIp + " | wc -l\\n\\n");
            response.append("# Monitor system load\\n");
            response.append("top -b -n 1 | head -20\\n");
            response.append("```\\n");
            response.append(
                    "\\n**Note**: If you're experiencing an nmap flood, the attacker is using aggressive scan timing. ");
            response.append(
                    "Consider implementing port knocking or moving critical services to non-standard ports.\\n");

        } else if (isPortScan) {
            // Port Scan - personalized based on protocol and ports
            response.append("**Threat Detected**: Network Reconnaissance - Port Scanning Activity\\n\\n");
            response.append("**Scan Details**:\\n");
            response.append("* **Attacker**: " + sourceIp + "\\n");
            response.append("* **Target**: " + destIp);
            if (!destPort.equals("Unknown") && !destPort.equals("N/A")) {
                response.append(" (Port " + destPort + ")");
            }
            response.append("\\n");
            response.append("* **Protocol**: " + protocol + "\\n");
            response.append("* **Scan Type**: " + signature + "\\n");
            response.append("* **Time**: " + timestamp + "\\n\\n");

            response.append("The attacker **" + sourceIp + "** is systematically probing ");
            if (!destPort.equals("Unknown") && !destPort.equals("N/A")) {
                try {
                    int portNum = Integer.parseInt(destPort);
                    if (portNum < 1024) {
                        response.append("**well-known service ports** (port " + destPort + " - ");
                        response.append(getServiceName(destPort) + ") ");
                    } else {
                        response.append("port **" + destPort + "** ");
                    }
                } catch (NumberFormatException e) {
                    response.append("port **" + destPort + "** ");
                }
            } else {
                response.append("multiple ports ");
            }
            response.append("on **" + destIp + "** to identify running services and potential vulnerabilities.\\n\\n");

            response.append("### ⚠️ Impact Assessment\\n");
            response.append("* **Severity**: " + severity + "\\n");
            response.append("* **Risk Level**: This is reconnaissance - typically precedes a targeted attack\\n");
            response.append("* **Exposed System**: " + destIp + " is being mapped\\n");
            response.append("* **Next Expected**: Exploitation attempts on discovered open ports\\n\\n");

            response.append("### 🛠️ Remediation Steps\\n");
            response.append("1. **Block Scanning Source**:\\n");
            response.append("   ```bash\\n");
            response.append("   sudo iptables -A INPUT -s " + sourceIp + " -j DROP\\n");
            response.append("   ```\\n");
            response.append(
                    "2. **Review Firewall Rules**: Verify only essential ports are exposed on **" + destIp + "**\\n");
            response.append("3. **Enable IPS/IDS**: Configure Suricata to automatically drop scan packets\\n");
            response.append("4. **Port Hardening**: ");
            if (!destPort.equals("Unknown") && !destPort.equals("N/A")) {
                response.append(
                        "Consider moving service on port " + destPort + " to a non-standard port or behind VPN\\n");
            } else {
                response.append("Move critical services to non-standard ports or behind VPN\\n");
            }
            response.append(
                    "5. **Monitor for Follow-up**: Watch for exploitation attempts in the next 24-48 hours\\n\\n");

            response.append("### 🔍 Investigation\\n");
            response.append("```bash\\n");
            response.append("# Check what ports are actually open on target\\n");
            response.append("sudo netstat -tulpn | grep LISTEN\\n\\n");
            response.append("# Review recent connections from scanner\\n");
            response.append("sudo grep " + sourceIp + " /var/log/syslog | tail -50\\n\\n");
            response.append("# Check if scanner is still active\\n");
            response.append("sudo netstat -an | grep " + sourceIp + "\\n");
            response.append("```\\n");

        } else if (isSqlInjection) {
            response.append("**🔴 CRITICAL**: SQL Injection Attack Attempt\\n\\n");
            response.append("**Attack Details**:\\n");
            response.append("* **Attacker**: " + sourceIp + "\\n");
            response.append("* **Target Web Server**: " + destIp + ":" + destPort + "\\n");
            response.append("* **Attack Signature**: " + signature + "\\n");
            response.append("* **Time**: " + timestamp + "\\n\\n");

            response.append("Malicious SQL syntax detected in HTTP requests from **" + sourceIp
                    + "** targeting your web application on **" + destIp + "**. ");
            response.append(
                    "The attacker is attempting to manipulate database queries to extract sensitive data or bypass authentication.\\n\\n");

            response.append("### ⚠️ Impact Assessment\\n");
            response.append("* **Severity**: **CRITICAL**\\n");
            response.append(
                    "* **Risk**: Complete database compromise, data exfiltration, unauthorized admin access\\n");
            response.append("* **Affected System**: Web application on " + destIp + ":" + destPort + "\\n");
            response.append("* **Data at Risk**: User credentials, personal information, business data\\n\\n");

            response.append("### 🛠️ URGENT Remediation\\n");
            response.append("1. **Immediate Block**:\\n");
            response.append("   ```bash\\n");
            response.append("   sudo iptables -A INPUT -s " + sourceIp + " -p tcp --dport " + destPort + " -j DROP\\n");
            response.append("   ```\\n");
            response.append(
                    "2. **Enable WAF Rules**: Activate SQL injection protection in your Web Application Firewall\\n");
            response.append("3. **Code Review**: Audit application code for SQL injection vulnerabilities\\n");
            response.append("4. **Parameterized Queries**: Ensure all database queries use prepared statements\\n");
            response.append("5. **Database Audit**: Check logs for successful injections\\n\\n");

            response.append("### 🔍 Investigation\\n");
            response.append("```bash\\n");
            response.append("# Review web server access logs\\n");
            response.append("sudo grep " + sourceIp
                    + " /var/log/nginx/access.log | grep -i \"union\\|select\\|drop\\|insert\"\\n\\n");
            response.append("# Check database query logs\\n");
            response.append("sudo tail -100 /var/log/mysql/mysql.log\\n");
            response.append("```\\n");

        } else if (isSsh) {
            response.append("**Threat Detected**: SSH Brute-Force Attack\\n\\n");
            response.append("**Attack Profile**:\\n");
            response.append("* **Attacker**: " + sourceIp + "\\n");
            response.append("* **Target SSH Server**: " + destIp + ":22\\n");
            response.append("* **Attack Type**: " + signature + "\\n");
            response.append("* **Time**: " + timestamp + "\\n\\n");

            response.append(
                    "Repeated SSH login attempts detected from **" + sourceIp + "** targeting **" + destIp + "**. ");
            response.append(
                    "The attacker is systematically trying username/password combinations to gain unauthorized access.\\n\\n");

            response.append("### ⚠️ Impact Assessment\\n");
            response.append("* **Severity**: " + severity + "\\n");
            response.append("* **Risk**: Unauthorized root access, system compromise, data theft\\n");
            response.append("* **Target**: SSH service on " + destIp + "\\n\\n");

            response.append("### 🛠️ Remediation Steps\\n");
            response.append("1. **Block Attacker**:\\n");
            response.append("   ```bash\\n");
            response.append("   sudo iptables -A INPUT -s " + sourceIp + " -p tcp --dport 22 -j DROP\\n");
            response.append("   ```\\n");
            response.append("2. **Install fail2ban** (if not already installed):\\n");
            response.append("   ```bash\\n");
            response.append("   sudo apt-get install fail2ban\\n");
            response.append("   sudo systemctl enable fail2ban\\n");
            response.append("   ```\\n");
            response.append("3. **Disable Password Authentication**: Switch to key-based auth only\\n");
            response.append("4. **Change SSH Port**: Move SSH to non-standard port (e.g., 2222)\\n");
            response.append("5. **Review Successful Logins**: Check if attacker succeeded\\n\\n");

            response.append("### 🔍 Investigation\\n");
            response.append("```bash\\n");
            response.append("# Check failed login attempts\\n");
            response.append("sudo grep \"Failed password\" /var/log/auth.log | grep " + sourceIp + " | wc -l\\n\\n");
            response.append("# Check for successful logins (CRITICAL)\\n");
            response.append("sudo grep \"Accepted password\" /var/log/auth.log | grep " + sourceIp + "\\n\\n");
            response.append("# Current SSH sessions\\n");
            response.append("who | grep ssh\\n");
            response.append("```\\n");

        } else if (isWebAttack) {
            response.append("**Threat Detected**: Web Application Attack\\n\\n");
            response.append("**Attack Context**:\\n");
            response.append("* **Source**: " + sourceIp + "\\n");
            response.append("* **Target Web Server**: " + destIp + ":" + destPort + "\\n");
            response.append("* **Protocol**: " + protocol + "\\n");
            response.append("* **Signature**: " + signature + "\\n");
            response.append("* **Category**: " + category + "\\n\\n");

            response.append("Suspicious HTTP/HTTPS traffic detected targeting your web application. ");
            response.append(
                    "This could indicate various web-based attacks including XSS, directory traversal, or file inclusion attempts.\\n\\n");

            response.append("### ⚠️ Impact Assessment\\n");
            response.append("* **Severity**: " + severity + "\\n");
            response.append("* **Risk**: Web application compromise, data exposure\\n");
            response.append("* **Affected Service**: Web server on " + destIp + ":" + destPort + "\\n\\n");

            response.append("### 🛠️ Remediation Steps\\n");
            response.append("1. **Block Malicious IP**: `sudo iptables -A INPUT -s " + sourceIp + " -j DROP`\\n");
            response.append("2. **Enable WAF**: Configure Web Application Firewall rules\\n");
            response.append("3. **Update Application**: Ensure all web apps are patched\\n");
            response.append("4. **Input Validation**: Review and strengthen input sanitization\\n\\n");

            response.append("### 🔍 Investigation\\n");
            response.append("```bash\\n");
            response.append("# Review web access logs\\n");
            response.append("sudo grep " + sourceIp + " /var/log/nginx/access.log | tail -50\\n");
            response.append("```\\n");

        } else {
            // Generic response with ALL extracted details for maximum personalization
            response.append("**Threat Detected**: " + signature + "\\n\\n");
            response.append("**Alert Details**:\\n");
            response.append("* **Source**: " + sourceIp);
            if (!sourcePort.equals("Unknown") && !sourcePort.equals("N/A")) {
                response.append(":" + sourcePort);
            }
            response.append("\\n");
            response.append("* **Destination**: " + destIp);
            if (!destPort.equals("Unknown") && !destPort.equals("N/A")) {
                response.append(":" + destPort);
            }
            response.append("\\n");
            response.append("* **Protocol**: " + protocol + "\\n");
            response.append("* **Category**: " + category + "\\n");
            response.append("* **Severity**: " + severity + "\\n");
            response.append("* **Signature ID**: " + signatureId + "\\n");
            response.append("* **Timestamp**: " + timestamp + "\\n\\n");

            response.append("Anomalous network activity detected. The traffic pattern from **" + sourceIp + "** to **"
                    + destIp + "** ");
            response.append("deviates from normal baseline behavior and requires investigation.\\n\\n");

            response.append("### ⚠️ Impact Assessment\\n");
            response.append("* **Severity**: " + severity + "\\n");
            response.append("* **Risk**: Requires manual analysis to determine threat level\\n");
            response.append("* **Affected System**: " + destIp + "\\n\\n");

            response.append("### 🛠️ Recommended Actions\\n");
            response.append("1. **Investigate Source**: Research IP reputation for " + sourceIp + "\\n");
            response.append("2. **Packet Capture**: Analyze full packet data for this connection\\n");
            response.append("3. **Baseline Review**: Compare with normal traffic patterns\\n");
            response.append(
                    "4. **Temporary Isolation**: Consider quarantining " + destIp + " if behavior persists\\n\\n");

            response.append("### 🔍 Investigation\\n");
            response.append("```bash\\n");
            response.append("# Capture traffic from source\\n");
            response.append("sudo tcpdump -i any -n src " + sourceIp + " -w /tmp/capture.pcap -c 100\\n\\n");
            response.append("# Check IP reputation\\n");
            response.append("whois " + sourceIp + "\\n\\n");
            response.append("# Review recent activity\\n");
            response.append("sudo grep " + sourceIp + " /var/log/syslog | tail -20\\n");
            response.append("```\\n");
        }

        response.append("\\n---\\n");
        response.append("*AI Security Assistant (Smart Fallback Mode) - Analysis generated at " + timestamp + "*\\n");
        response.append("*Alert ID: " + signatureId + " | Severity: " + severity + "*");

        return response.toString();
    }

    /**
     * Get common service name for well-known ports
     */
    private String getServiceName(String port) {
        return switch (port) {
            case "20", "21" -> "FTP";
            case "22" -> "SSH";
            case "23" -> "Telnet";
            case "25" -> "SMTP";
            case "53" -> "DNS";
            case "80" -> "HTTP";
            case "110" -> "POP3";
            case "143" -> "IMAP";
            case "443" -> "HTTPS";
            case "3306" -> "MySQL";
            case "5432" -> "PostgreSQL";
            case "3389" -> "RDP";
            case "8080" -> "HTTP-Alt";
            default -> "Unknown Service";
        };
    }

    private String extractValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Unknown";
    }
}
