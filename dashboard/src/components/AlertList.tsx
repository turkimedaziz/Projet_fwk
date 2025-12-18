import React, { useState } from 'react';
import AlertCard from './AlertCard';
import type { Alert } from '../types';
import './AlertList.css';

interface AlertListProps {
    alerts: Alert[];
    loading?: boolean;
    onAlertClick?: (alert: Alert) => void;
}

const AlertList: React.FC<AlertListProps> = ({ alerts, loading, onAlertClick }) => {
    const [filter, setFilter] = useState<string>('all');

    const safeAlerts = alerts || [];
    const filteredAlerts = safeAlerts.filter((alert) => {
        if (filter === 'all') return true;
        return alert.severity === filter.toUpperCase();
    });

    if (loading) {
        return (
            <div className="alert-list">
                <div className="alert-list-header">
                    <h2>Recent Alerts</h2>
                </div>
                <div className="loading-container">
                    <div className="spinner"></div>
                    <p>Loading alerts...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="alert-list">
            <div className="alert-list-header">
                <h2>Recent Alerts ({filteredAlerts.length})</h2>
                <div className="filter-buttons">
                    <button
                        className={filter === 'all' ? 'active' : ''}
                        onClick={() => setFilter('all')}
                    >
                        All
                    </button>
                    <button
                        className={filter === 'critical' ? 'active severity-critical' : 'severity-critical'}
                        onClick={() => setFilter('critical')}
                    >
                        Critical
                    </button>
                    <button
                        className={filter === 'high' ? 'active severity-high' : 'severity-high'}
                        onClick={() => setFilter('high')}
                    >
                        High
                    </button>
                    <button
                        className={filter === 'medium' ? 'active severity-medium' : 'severity-medium'}
                        onClick={() => setFilter('medium')}
                    >
                        Medium
                    </button>
                    <button
                        className={filter === 'low' ? 'active severity-low' : 'severity-low'}
                        onClick={() => setFilter('low')}
                    >
                        Low
                    </button>
                </div>
            </div>

            <div className="alerts-container">
                {filteredAlerts.length === 0 ? (
                    <div className="no-alerts">
                        <p>🎉 No alerts to display</p>
                        <small>Your network is secure</small>
                    </div>
                ) : (
                    filteredAlerts.map((alert) => (
                        <AlertCard
                            key={alert.id}
                            alert={alert}
                            onClick={() => onAlertClick?.(alert)}
                        />
                    ))
                )}
            </div>
        </div>
    );
};

export default AlertList;
