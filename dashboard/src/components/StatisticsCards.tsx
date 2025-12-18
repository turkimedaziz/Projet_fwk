import React from 'react';
import type { TodayStatistics } from '../services/api';
import { AlertSeverity } from '../types';
import './StatisticsCards.css';

interface StatisticsCardsProps {
    statistics: TodayStatistics | null;
    loading?: boolean;
    activeFilter: AlertSeverity | null;
    onFilter: (severity: AlertSeverity | null) => void;
}

const StatisticsCards: React.FC<StatisticsCardsProps> = ({ statistics, loading, activeFilter, onFilter }) => {
    if (loading || !statistics) {
        return (
            <div className="statistics-grid">
                {[1, 2, 3, 4].map((i) => (
                    <div key={i} className="stat-card loading">
                        <div className="stat-skeleton"></div>
                    </div>
                ))}
            </div>
        );
    }

    const stats = [
        {
            label: 'Total Alerts (All Time)',
            value: statistics.globalTotal,
            color: '#007bff',
            icon: '📉',
            filter: null,
        },
        {
            label: 'Today\'s Traffic',
            value: statistics.totalAlerts,
            color: '#6c757d',
            icon: '📊',
            filter: null,
        },
        {
            label: 'Critical (Today)',
            value: statistics.criticalAlerts,
            color: '#dc3545',
            icon: '🔴',
            filter: AlertSeverity.CRITICAL,
        },
        {
            label: 'High (Today)',
            value: statistics.highAlerts,
            color: '#fd7e14',
            icon: '🟠',
            filter: AlertSeverity.HIGH,
        },
        {
            label: 'Medium (Today)',
            value: statistics.mediumAlerts,
            color: '#ffc107',
            icon: '🟡',
            filter: AlertSeverity.MEDIUM,
        },
        {
            label: 'Low (Today)',
            value: statistics.lowAlerts,
            color: '#28a745',
            icon: '🟢',
            filter: AlertSeverity.LOW,
        },
    ];

    return (
        <div className="statistics-grid">
            {stats.map((stat) => (
                <div
                    key={stat.label}
                    className={`stat-card ${activeFilter === stat.filter && stat.filter !== null ? 'active' : ''}`}
                    style={{ borderTopColor: stat.color }}
                    onClick={() => onFilter(stat.filter)}
                >
                    <div className="stat-icon">{stat.icon}</div>
                    <div className="stat-content">
                        <div className="stat-value">{stat.value.toLocaleString()}</div>
                        <div className="stat-label">{stat.label}</div>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default StatisticsCards;
