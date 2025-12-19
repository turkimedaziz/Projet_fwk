# Project Architecture Documentation

This document provides a comprehensive overview of the project's architecture, including the technology stack, pod communication, and class structure.

## 🏗️ System Architecture

The following diagram illustrates the high-level system architecture, showing the interaction between the different components and technologies.

```mermaid
graph TD
    subgraph "User Layer"
        User["🌐 Web Browser"]
    end

    subgraph "Frontend Layer (Dashboard Pod)"
        React["⚛️ React Frontend"]
        Nginx["⚙️ Nginx Proxy"]
    end

    subgraph "Backend Layer (Scan-App Pod)"
        SpringBoot["🍃 Spring Boot API"]
        SuricataMonitor["🔍 Suricata Log Monitor"]
    end

    subgraph "Security Layer (Suricata Pod)"
        Suricata["🛡️ Suricata IDS"]
        NetworkTraffic["📡 Network Traffic"]
    end

    subgraph "Data Layer"
        Postgres[("🐘 PostgreSQL\n(Relational Data)")]
        Redis[("🔴 Redis\n(Caching & Alerts)")]
        Elasticsearch[("🔍 Elasticsearch\n(Security Logs)")]
    end

    subgraph "External Services"
        HuggingFace["🤖 Hugging Face API\n(AI Analysis)"]
    end

    subgraph "Infrastructure"
        K3s["☸️ K3s Cluster"]
        PVC["💾 Shared Volume\n(suricata-logs)"]
    end

    %% Connections
    User -->|HTTPS| Nginx
    Nginx -->|Proxy| React
    React -->|REST API| SpringBoot
    
    Suricata -->|Capture| NetworkTraffic
    Suricata -->|Write logs| PVC
    SuricataMonitor -->|Read logs| PVC
    
    SpringBoot -->|JPA/Hibernate| Postgres
    SpringBoot -->|Jedis/Spring Data| Redis
    SpringBoot -->|REST Client| Elasticsearch
    SpringBoot -->|WebClient| HuggingFace
    
    DailyThreatService["DailyThreatService"] -.->|Alert Tracking| Redis
```

## 📊 Class Diagram (Backend)

The following class diagram represents the core logic of the `scan-app` backend, showing the relationships between controllers, services, repositories, and entities.

```mermaid
classDiagram
    class AiAssistantController {
        +analyzeAlert(Long id)
        +generateRemediation(Long id)
    }
    
    class SuricataController {
        +getAlerts()
        +getStats()
    }
    
    class NmapController {
        +startScan(String target)
    }

    class AiAssistantService {
        <<interface>>
        +analyzeAlert(Alert alert)
        +generateRemediation(Long alertId)
    }

    class AiAssistantServiceImpl {
        -HuggingFaceClient huggingFaceClient
        -AlertRepository alertRepository
        -DailyThreatService dailyThreatService
    }

    class SuricataService {
        <<interface>>
        +processAlerts()
        +getAlerts()
    }

    class SuricataServiceImpl {
        -AlertRepository alertRepository
        -DailyThreatService dailyThreatService
        -ApplicationEventPublisher eventPublisher
    }

    class DailyThreatService {
        -RedisTemplate redisTemplate
        +trackAlert(Alert alert)
        +getTodayAlerts()
    }

    class HuggingFaceClient {
        -WebClient webClient
        +generateResponse(String prompt)
    }

    class AlertRepository {
        <<interface>>
        +findByTimestampBetween()
    }

    class Alert {
        +Long id
        +String sourceIp
        +String destIp
        +String signature
        +LocalDateTime timestamp
    }

    %% Relationships
    AiAssistantController --> AiAssistantService
    SuricataController --> SuricataService
    NmapController --> NmapService

    AiAssistantServiceImpl ..|> AiAssistantService
    AiAssistantServiceImpl --> HuggingFaceClient
    AiAssistantServiceImpl --> AlertRepository
    AiAssistantServiceImpl --> DailyThreatService

    SuricataServiceImpl ..|> SuricataService
    SuricataServiceImpl --> AlertRepository
    SuricataServiceImpl --> DailyThreatService

    DailyThreatService --> RedisTemplate
    AlertRepository --> Alert
```

## 🛠️ Technology Stack

| Component | Technology |
| :--- | :--- |
| **Frontend** | React, Vite, TypeScript, Tailwind CSS, Recharts |
| **Backend** | Java 21, Spring Boot 3, Maven |
| **Database** | PostgreSQL (Relational), Redis (NoSQL/Cache) |
| **Search Engine** | Elasticsearch |
| **Security** | Suricata (IDS), Nmap (Scanner) |
| **AI** | Hugging Face (Mistral-7B) |
| **Infrastructure** | Docker, K3s (Kubernetes), Nginx |
| **OS** | Linux (Ubuntu/Debian) |
