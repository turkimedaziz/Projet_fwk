export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export const AlertSeverity = {
    LOW: 'LOW' as AlertSeverity,
    MEDIUM: 'MEDIUM' as AlertSeverity,
    HIGH: 'HIGH' as AlertSeverity,
    CRITICAL: 'CRITICAL' as AlertSeverity
};

export interface Alert {
    id: number;
    timestamp: string;
    sourceIp: string;
    destIp: string;
    sourcePort?: number;
    destPort?: number;
    protocol?: string;
    signature: string;
    category?: string;
    severity: AlertSeverity;
    signatureId?: number;
    payload?: string;
    action?: string;
}

export interface AlertStatistics {
    totalAlerts: number;
    criticalAlerts: number;
    highAlerts: number;
    mediumAlerts: number;
    lowAlerts: number;
    alertsByCategory: Record<string, number>;
    topSourceIps: Record<string, number>;
    topDestIps: Record<string, number>;
    topSignatures: Record<string, number>;
    alertsLastHour: number;
    alertsLast24Hours: number;
    alertsLast7Days: number;
}

export interface Device {
    id: number;
    ipAddress: string;
    macAddress?: string;
    hostname?: string;
    vendor?: string;
    state?: string;
}

export interface NmapPort {
    port: number;
    protocol: string;
    state: string; // open|closed|filtered
    service?: string;
    reason?: string;
}

export interface NmapScanResult {
    targetIp: string;
    scannedAt: string; // ISO timestamp
    ports: NmapPort[];
    raw?: string;
}

export interface Vulnerability {
    id?: string;
    name: string;
    description?: string;
    severity?: 'Low' | 'Medium' | 'High' | 'Critical';
    affectedPort?: number;
    cvss?: number;
    reference?: string;
}
