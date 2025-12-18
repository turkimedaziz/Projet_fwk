import { useState, useEffect } from 'react';
import StatisticsCards from './components/StatisticsCards';
import AlertList from './components/AlertList';
import Header from './components/Header';
import ChartsSection from './components/ChartsSection';
import { suricataApi, type TodayStatistics } from './services/api';
import { AlertSeverity } from './types';
import type { Alert } from './types';
import './App.css';

function App() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [statistics, setStatistics] = useState<TodayStatistics | null>(null);
  const [loading, setLoading] = useState(true);
  const [connected, setConnected] = useState(false);
  const [activeFilter, setActiveFilter] = useState<AlertSeverity | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [recentAlerts, stats] = await Promise.all([
          suricataApi.getRecentAlerts(1000),
          suricataApi.getTodayStatistics()
        ]);
        setAlerts(recentAlerts);
        setStatistics(stats);
        setConnected(true);
      } catch (error) {
        console.error('Error fetching initial data:', error);
        setConnected(false);
      } finally {
        setLoading(false);
      }
    };

    fetchData();

    // Setup SSE for alerts
    const alertSource = suricataApi.createAlertStream(
      (alert) => {
        setAlerts(prev => {
          // Filter incoming alerts based on active filter
          if (activeFilter && alert.severity !== activeFilter) {
            return prev;
          }
          return [alert, ...prev].slice(0, 1000);
        });
      },
      () => setConnected(false)
    );

    // Setup SSE for statistics
    const statsSource = suricataApi.createStatsStream(
      (stats) => setStatistics(stats),
      () => setConnected(false)
    );

    return () => {
      alertSource.close();
      statsSource.close();
    };
  }, [activeFilter]); // Re-run when filter changes

  const handleFilterChange = async (severity: AlertSeverity | null) => {
    setActiveFilter(severity);
    setLoading(true);
    try {
      if (severity) {
        // Pass limit=1000 to prevent crash
        const filteredAlerts = await suricataApi.getAlertsBySeverity(severity, 1000);
        setAlerts(filteredAlerts);
      } else {
        const recentAlerts = await suricataApi.getRecentAlerts(1000);
        setAlerts(recentAlerts);
      }
    } catch (error) {
      console.error('Error filtering alerts:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-container">
      <Header connected={connected} />

      <main className="main-content">
        <StatisticsCards
          statistics={statistics}
          loading={loading}
          onFilter={handleFilterChange}
          activeFilter={activeFilter}
        />

        <ChartsSection
          statistics={statistics}
          loading={loading}
        />

        <AlertList alerts={alerts} loading={loading} />
      </main>
    </div>
  );
}

export default App;
