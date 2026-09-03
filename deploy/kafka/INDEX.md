# 📦 Kafka KRaft Deployment - Index des Fichiers

## 🚀 Quick Start
Pour démarrer rapidement, consultez: **[QUICK_START.md](QUICK_START.md)**

---

## 📁 Structure du Dossier

```
kafka/
│
├── 📖 Documentation
│   ├── QUICK_START.md          → Guide de démarrage rapide
│   ├── README.md               → Documentation complète
│   ├── ARCHITECTURE.md         → Architecture détaillée et diagrammes
│   └── INDEX.md               → Ce fichier
│
├── 🔧 Configuration Kubernetes
│   ├── statefulset.yaml        → StatefulSet, Services et PVC
│   ├── config-production.yaml  → Overrides pour la production
│   └── config-development.yaml → Overrides pour le développement
│
├── 📝 Configuration Helm (référence)
│   └── values-kraft.yaml       → Valeurs Helm (référence seulement)
│
├── 🛠️ Scripts d'Installation
│   ├── install.sh             → Installation automatique ⭐
│   ├── uninstall.sh           → Désinstallation
│   └── node-id-mapper.sh      → Mapping Node ID (usage interne)
│
└── 🔍 Scripts d'Opération
    ├── test-kafka.sh          → Tests de validation ⭐
    └── monitor.sh             → Monitoring du cluster
```

---

## 📚 Guide de Lecture

### Pour Démarrer Rapidement
1. **[QUICK_START.md](QUICK_START.md)** - Commencez ici !
   - Installation en 1 commande
   - Commandes essentielles
   - Test rapide

### Pour Comprendre l'Architecture
2. **[ARCHITECTURE.md](ARCHITECTURE.md)** - Architecture détaillée
   - Diagrammes du cluster
   - Flux de données
   - Modèle de réplication
   - Performance et tuning

### Pour la Documentation Complète
3. **[README.md](README.md)** - Documentation exhaustive
   - Spécifications complètes
   - Configuration détaillée
   - Opérations avancées
   - Troubleshooting
   - Monitoring
   - Scaling

---

## 🎯 Workflows Courants

### 1️⃣ Installation Initiale

```bash
./install.sh
# Attend la fin (~2-3 minutes)
kubectl get pods -n agent-system -l app=kafka
```

### 2️⃣ Test et Validation

```bash
./test-kafka.sh
# Exécute une suite de tests complète
```

### 3️⃣ Monitoring Quotidien

```bash
./monitor.sh
# Affiche l'état du cluster
```

### 4️⃣ Opérations Kafka

Consultez [QUICK_START.md](QUICK_START.md) pour les commandes de base ou [README.md](README.md) pour les opérations avancées.

### 5️⃣ Désinstallation

```bash
./uninstall.sh
# Option de garder ou supprimer les données
```

---

## 🔑 Fichiers Clés

### Pour l'Installation
- **`install.sh`** ⭐ - Script principal d'installation
- **`statefulset.yaml`** - Définition Kubernetes complète

### Pour les Tests
- **`test-kafka.sh`** ⭐ - Suite de tests automatisée

### Pour les Opérations
- **`monitor.sh`** - Monitoring du cluster
- **`uninstall.sh`** - Désinstallation propre

### Pour la Configuration
- **`config-production.yaml`** - Configuration production
- **`config-development.yaml`** - Configuration développement
- **`values-kraft.yaml`** - Référence de configuration

---

## 📊 Spécifications du Cluster

| Composant | Valeur |
|-----------|--------|
| **Version** | Kafka 7.7.0 (CP) |
| **Mode** | KRaft (sans Zookeeper) |
| **Nœuds** | 3 (combined broker + controller) |
| **CPU** | 1-2 cores par nœud |
| **Mémoire** | 2-4Gi par nœud |
| **Heap JVM** | 1536m par nœud |
| **Stockage** | 20Gi par nœud (persistent) |
| **Réplication** | Factor 3, Min ISR 2 |
| **Rétention** | 7 jours / 1GB par partition |
| **Namespace** | agent-system |
| **Service** | kafka.agent-system.svc.cluster.local:9092 |

---

## 🎓 Niveaux d'Utilisation

### Débutant 🌱
1. Lisez [QUICK_START.md](QUICK_START.md)
2. Exécutez `./install.sh`
3. Testez avec `./test-kafka.sh`
4. Utilisez les commandes de base

### Intermédiaire 🌿
1. Lisez [README.md](README.md)
2. Comprenez les opérations Kafka
3. Utilisez `./monitor.sh`
4. Configurez vos applications

### Avancé 🌳
1. Lisez [ARCHITECTURE.md](ARCHITECTURE.md)
2. Comprenez le modèle de réplication
3. Optimisez les performances
4. Personnalisez la configuration
5. Utilisez config-production.yaml

---

## 🆘 Support Rapide

### Problème de démarrage
```bash
kubectl describe pod kafka-0 -n agent-system
kubectl logs kafka-0 -n agent-system
```

### Problème de connexion
```bash
kubectl get svc -n agent-system
./monitor.sh
```

### Reset complet
```bash
./uninstall.sh  # Répondre 'y' pour supprimer les PVC
./install.sh
```

### Plus d'aide
Consultez la section Troubleshooting dans [README.md](README.md)

---

## 📌 Notes Importantes

⚠️ **Sécurité**: Cette configuration utilise PLAINTEXT (pas de chiffrement). Pour la production, activez SSL/TLS et SASL.

💾 **Données**: Les PVC persistent après suppression du StatefulSet. Utilisez `./uninstall.sh` pour un nettoyage complet.

🔄 **Haute Disponibilité**: Le cluster tolère la perte d'1 nœud. Min ISR=2 garantit la durabilité des données.

📈 **Scaling**: Pour scaler, modifiez `replicas` dans statefulset.yaml et mettez à jour `KAFKA_CONTROLLER_QUORUM_VOTERS`.

---

## 🔗 Liens Utiles

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [KRaft Mode](https://kafka.apache.org/documentation/#kraft)
- [Confluent Platform Docs](https://docs.confluent.io/)
- [Kubernetes StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)

---

**Dernière mise à jour**: 8 janvier 2026
**Version**: 1.0.0
**Kafka Version**: 7.7.0 (Confluent Platform)
