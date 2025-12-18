import React from 'react';
import {
    PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
    AreaChart, Area, XAxis, YAxis, CartesianGrid
} from 'recharts';
import type { TodayStatistics } from '../services/api';
import './ChartsSection.css';

interface ChartsSectionProps {
    statistics: TodayStatistics | null;
    loading?: boolean;
}

const COLORS = {
    CRITICAL: '#dc3545',
    HIGH: '#fd7e14',
    MEDIUM: '#ffc107',
    LOW: '#28a745'
};

const ChartsSection: React.FC<ChartsSectionProps> = ({ statistics, loading }) => {
    if (loading || !statistics) {
        return <div className="charts-loading">Loading charts...</div>;
    }

    const pieData = [
        { name: 'Critical', value: statistics.criticalAlerts, color: COLORS.CRITICAL },
        { name: 'High', value: statistics.highAlerts, color: COLORS.HIGH },
        { name: 'Medium', value: statistics.mediumAlerts, color: COLORS.MEDIUM },
        { name: 'Low', value: statistics.lowAlerts, color: COLORS.LOW },
    ].filter(d => d.value > 0);

    // Mock trend data (since we don't have historical stats API yet)
    // In a real app, we'd fetch this from a new endpoint
    const trendData = [
        { time: '00:00', alerts: Math.floor(statistics.totalAlerts * 0.1) },
        { time: '04:00', alerts: Math.floor(statistics.totalAlerts * 0.05) },
        { time: '08:00', alerts: Math.floor(statistics.totalAlerts * 0.15) },
        { time: '12:00', alerts: Math.floor(statistics.totalAlerts * 0.3) },
        { time: '16:00', alerts: Math.floor(statistics.totalAlerts * 0.25) },
        { time: '20:00', alerts: Math.floor(statistics.totalAlerts * 0.15) },
    ];

    return (
        <div className="charts-grid">
            <div className="chart-card">
                <h3>Alert Severity Distribution</h3>
                <div className="chart-container">
                    <ResponsiveContainer width="100%" height={300}>
                        <PieChart>
                            <Pie
                                data={pieData}
                                cx="50%"
                                cy="50%"
                                innerRadius={60}
                                outerRadius={80}
                                paddingAngle={5}
                                dataKey="value"
                            >
                                {pieData.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={entry.color} />
                                ))}
                            </Pie>
                            <Tooltip />
                            <Legend />
                        </PieChart>
                    </ResponsiveContainer>
                </div>
            </div>

            <div className="chart-card">
                <h3>Alert Traffic Trend (Today)</h3>
                <div className="chart-container">
                    <ResponsiveContainer width="100%" height={300}>
                        <AreaChart data={trendData}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="time" />
                            <YAxis />
                            <Tooltip />
                            <Area type="monotone" dataKey="alerts" stroke="#007bff" fill="#007bff" fillOpacity={0.1} />
                        </AreaChart>
                    </ResponsiveContainer>
                </div>
            </div>
        </div>
    );
};

export default ChartsSection;
