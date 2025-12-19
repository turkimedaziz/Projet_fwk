# 🧠 Documentation Technique Complète : Module AI Security Assistant

Ce document explique comment ton IA gère **tous les types de menaces**, comment elle est codée, et pourquoi tu as fait ces choix d'architecture.

---

## 1. ⚙️ Le Cœur du Système : Comment mon IA fonctionne ?

Mon IA repose sur une architecture **hybride et adaptative**. Elle ne se contente pas de "deviner", elle analyse selon un processus strict en 3 phases :

### Phase 1 : L'Ingestion et le Contextualisation
*   **Le Problème :** Une alerte brute Suricata (`{"src_ip": "1.2.3.4", "alert": "ET SCAN..."}`) est incompréhensible pour un humain novice.
*   **Ma Solution :** Dans `AiAssistantServiceImpl.java`, je transforme cette donnée brute en un "Prompt Enrichi". J'ajoute du contexte (ex: je traduis le port 80 en "Service HTTP") pour préparer le travail de l'IA.

### Phase 2 : Le Moteur de Décision (Le "Cerveau")
C'est ici que se trouve l'intelligence. J'ai codé une logique capable de gérer **n'importe quel type d'attaque** via deux modes :

1.  **Mode Cloud (Mistral-7B) :** Pour les analyses complexes et nuancées quand internet est disponible.
2.  **Mode Local (Mon Algorithme Expert) :** C'est mon innovation majeure. J'ai développé un moteur de règles dans `HuggingFaceClient.java` qui identifie précisément le type d'attaque grâce à des signatures spécifiques.

### Phase 3 : La Classification Multi-Menaces (Ce que je détecte)

Mon algorithme ne détecte pas juste les DDoS. Il classifie intelligemment chaque menace pour adapter la réponse :

*   **1. Attaques DDoS / Flood :**
    *   *Détection :* Mots-clés "flood", "storm" OU (Scan de port + Sévérité HAUTE).
    *   *Réaction :* Priorité CRITIQUE. Commandes de blocage immédiat (`iptables DROP`), activation des `syn_cookies`.

*   **2. Reconnaissance (Nmap / Port Scanning) :**
    *   *Détection :* Mots-clés "scan", "nmap", "potential ssh scan".
    *   *Réaction :* Analyse de la phase de reconnaissance. Je donne les commandes pour voir quels ports sont exposés (`netstat`) et je conseille le "Port Hardening".

*   **3. Injections SQL (Web Attacks) :**
    *   *Détection :* Patterns "union select", "sql", "injection" dans le payload ou la signature.
    *   *Réaction :* Focus sur la couche applicative. Je recommande de vérifier les logs du serveur Web (Nginx/Apache) et d'activer le WAF (Web Application Firewall).

*   **4. Brute-Force SSH :**
    *   *Détection :* Mots-clés "ssh", "login", "brute".
    *   *Réaction :* Je suggère l'installation de `fail2ban` et la désactivation de l'authentification par mot de passe.

*   **5. Menaces Génériques :**
    *   *Détection :* Tout ce qui ne rentre pas dans les cases ci-dessus.
    *   *Réaction :* Analyse comportementale standard (réputation IP, capture de paquets).

---

## 2. � Deep Dive : La Mécanique Interne (Pour les experts)

Voici comment l'IA "réfléchit" étape par étape :

### A. Le Prompt Engineering (L'Art de la Question)
Je ne pose pas une question simple. Je construis un **Prompt Système** complexe qui force l'IA à adopter un rôle précis.
*   **Role :** "You are an advanced Cybersecurity AI Assistant."
*   **Context :** J'injecte les données JSON brutes.
*   **Constraint :** "RESPONSE FORMAT (Markdown)". Je force l'IA à répondre avec des titres spécifiques (`### Threat Analysis`, `### Remediation`).
*   **Pourquoi ?** Sans cela, le LLM pourrait répondre par un poème ou du code Python inutile. Je le "bride" pour qu'il soit utile.

### B. L'Appel LLM (Large Language Model)
*   **Modèle :** Mistral-7B-Instruct-v0.2.
*   **Paramètres :**
    *   `Temperature: 0.7` : Assez bas pour être factuel, assez haut pour ne pas être robotique.
    *   `Max Tokens: 500` : Pour avoir une réponse concise et rapide.
*   **Flux :** L'appel est encapsulé dans un `WebClient` asynchrone. Si la réponse met 5 secondes, le serveur ne plante pas, il attend en tâche de fond.

### C. Le "Smart Fallback" (L'IA Locale)
Si le Cloud ne répond pas, mon code Java prend le relais. Il simule le raisonnement d'une IA :
1.  **Extraction d'Entités (NER) :** J'utilise des Regex (`Pattern.compile`) pour extraire l'IP (`\d+\.\d+\.\d+\.\d+`), le Port, et la Sévérité.
2.  **Arbre de Décision :** Le code parcourt une série de `if/else` basés sur des signatures d'attaques connues (DDoS, SQLi, etc.).
3.  **Génération de Template :** Il remplit des modèles de réponse pré-rédigés avec les données extraites. C'est ce qui donne l'impression que l'IA "parle" de l'attaque spécifique.

---

## 3. �💡 Pourquoi ces choix techniques ? (Justifications)

### Choix 1 : L'Architecture Hybride (Cloud + Local)
*   **Pourquoi ?** La sécurité ne doit pas avoir de "Single Point of Failure" (Point unique de défaillance).
*   **L'avantage :** Si l'API Hugging Face est en panne ou si le réseau est coupé (ce qui arrive souvent lors d'une cyberattaque !), mon moteur local prend le relais instantanément. C'est du **"Secure by Design"**.

### Choix 2 : Le Modèle Mistral-7B-Instruct
*   **Pourquoi pas GPT-4 ?** Mistral est un modèle "Open Weight" (ouvert) très performant pour le code et la technique. Il est plus léger, moins cher, et respecte mieux la confidentialité des données que d'envoyer des logs sensibles à OpenAI.
*   **Pourquoi "Instruct" ?** J'ai besoin d'un modèle qui obéit à mes consignes de formatage (Markdown), pas d'un modèle qui "chatte".

### Choix 3 : Spring WebFlux (Programmation Réactive)
*   **Pourquoi ?** L'analyse IA prend du temps (1-2 secondes). Avec une architecture classique (bloquante), 100 alertes simultanées bloqueraient tout le serveur.
*   **L'avantage :** Avec `WebClient` (non-bloquant), mon serveur peut lancer 1000 analyses IA en parallèle sans consommer 1000 threads. C'est crucial pour la scalabilité.

### Choix 4 : Regex & Logique Déterministe (Pour le local)
*   **Pourquoi ?** Les Regex sont extrêmement rapides (quelques microsecondes).
*   **L'avantage :** Cela permet une analyse en temps réel (Real-Time) sans latence perceptible pour l'utilisateur, même sur un petit serveur.

---

## 4. 📂 La Structure du Code (Où regarder ?)

*   **`HuggingFaceClient.java`** : C'est le fichier le plus important. Montre la méthode `generateFallbackResponse`. C'est là que réside toute la logique de classification (les `if (isDdos) ... else if (isSql) ...`).
*   **`AiAssistantServiceImpl.java`** : Montre comment tu construis le prompt. C'est là que tu injectes l'intelligence contextuelle.

---

## 📝 Conclusion pour ta soutenance

"Mon IA n'est pas un gadget. C'est un système de défense complet qui couvre tout le spectre des cybermenaces (DDoS, Web, Brute-force). J'ai fait des choix d'architecture forts (Hybride, Réactif, Open Source) pour garantir que le système reste rapide, résilient et pertinent, quelle que soit la situation."
