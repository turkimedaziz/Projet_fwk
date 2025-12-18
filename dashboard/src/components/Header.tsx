import React from 'react';
import './Header.css';

interface HeaderProps {
    connected: boolean;
}

const Header: React.FC<HeaderProps> = ({ connected }) => {
    const [currentTime, setCurrentTime] = React.useState(new Date());

    React.useEffect(() => {
        const timer = setInterval(() => {
            setCurrentTime(new Date());
        }, 1000);

        return () => clearInterval(timer);
    }, []);

    return (
        <header className="app-header">
            <div className="header-content">
                <div className="header-left">
                    <h1>Suricata Dashboard</h1>
                    <p className="subtitle">Real-time Network Security Monitoring</p>
                </div>
                <div className="header-right">
                    <div className="current-time">
                        {currentTime.toLocaleTimeString()}
                    </div>
                    <div className={`connection-status ${connected ? 'connected' : 'disconnected'}`}>
                        <span className="status-dot"></span>
                        {connected ? 'Connected' : 'Disconnected'}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Header;
