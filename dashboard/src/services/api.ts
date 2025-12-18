import axios from 'axios';
import type { Alert, AlertStatistics } from '../types';
import { AlertSeverity } from '../types';

export interface TodayStatistics {
    globalTotal: number;
    totalAlerts: number;
    criticalAlerts: number;
    highAlerts: number;
    mediumAlerts: number;
    lowAlerts: number;
}

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

export const suricataApi = {
    // Get recent alerts
    getRecentAlerts: async (limit: number = 100): Promise<Alert[]> => {
        const response = await api.get<Alert[]>(`/suricata/alerts/recent`, {
            params: { limit },
        });
        return response.data;
    },

    // Get paginated alerts
    getAlerts: async (page: number = 0, size: number = 20) => {
        const response = await api.get(`/suricata/alerts`, {
            params: { page, size },
        });
        return response.data;
    },

    // Get alert by ID
    getAlertById: async (id: number): Promise<Alert> => {
        const response = await api.get<Alert>(`/suricata/alerts/${id}`);
        return response.data;
    },

    // Get alerts by severity
    getAlertsBySeverity: async (severity: AlertSeverity, limit: number = 1000): Promise<Alert[]> => {
        const response = await api.get<Alert[]>(`/suricata/alerts/severity/${severity}`, {
            params: { limit },
        });
        return response.data;
    },

    // Get alerts by IP
    getAlertsByIp: async (ipAddress: string): Promise<Alert[]> => {
        const response = await api.get<Alert[]>(`/suricata/alerts/ip/${ipAddress}`);
        return response.data;
    },

    // Get statistics
    getStatistics: async (since?: string): Promise<AlertStatistics> => {
        const response = await api.get<AlertStatistics>('/suricata/statistics', {
            params: since ? { since } : {},
        });
        return response.data;
    },

    // Get today's statistics from Redis
    getTodayStatistics: async (): Promise<TodayStatistics> => {
        const response = await api.get<TodayStatistics>('/suricata/statistics/today');
        return response.data;
    },

    // Create SSE connection for real-time alerts
    createAlertStream: (onAlert: (alert: Alert) => void, onError?: (error: Event) => void) => {
        const eventSource = new EventSource(`${API_BASE_URL}/suricata/alerts/stream`);

        eventSource.addEventListener('alert', (event) => {
            try {
                const alert = JSON.parse(event.data) as Alert;
                onAlert(alert);
            } catch (error) {
                console.error('Error parsing alert:', error);
            }
        });

        eventSource.onerror = (error) => {
            console.error('SSE connection error:', error);
            if (onError) onError(error);
        };

        return eventSource;
    },

    // Create SSE connection for real-time statistics
    createStatsStream: (onStats: (stats: TodayStatistics) => void, onError?: (error: Event) => void) => {
        const eventSource = new EventSource(`${API_BASE_URL}/suricata/statistics/stream`);

        eventSource.addEventListener('stats', (event) => {
            try {
                const stats = JSON.parse(event.data) as TodayStatistics;
                onStats(stats);
            } catch (error) {
                console.error('Error parsing stats:', error);
            }
        });

        eventSource.onerror = (error) => {
            console.error('SSE stats connection error:', error);
            if (onError) onError(error);
        };

        return eventSource;
    },
    // Nmap scan endpoints
    runNmapScan: async (targetIp: string): Promise<any> => {
        const response = await api.post(`/nmap/scan`, null, { params: { target: targetIp } });
        return response.data;
    },

    getNmapScans: async (): Promise<any> => {
        const response = await api.get(`/nmap/scans`);
        return response.data;
    },

    getNmapScanById: async (id: number): Promise<any> => {
        const response = await api.get(`/nmap/scans/${id}`);
        return response.data;
    },

    getNmapDevices: async (scanId: number): Promise<any> => {
        const response = await api.get(`/nmap/scans/${scanId}/devices`);
        return response.data;
    },
};
