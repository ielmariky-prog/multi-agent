# Changelog

Toutes les modifications notables de ce projet seront documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-01-08

### ✨ Ajouté
- Configuration Kafka KRaft complète avec 3 nœuds combinés (broker + controller)
- StatefulSet Kubernetes avec services et PVC
- Script d'installation automatique (`install.sh`)
- Script de désinstallation (`uninstall.sh`)
- Suite de tests automatisée (`test-kafka.sh`)
- Script de monitoring (`monitor.sh`)
- Documentation complète:
  - `README.md` - Documentation exhaustive
  - `QUICK_START.md` - Guide de démarrage rapide
  - `ARCHITECTURE.md` - Architecture détaillée avec diagrammes
  - `INDEX.md` - Index et navigation des fichiers
- Configurations d'environnement:
  - `config-production.yaml` - Optimisations production
  - `config-development.yaml` - Configuration développement
- `values-kraft.yaml` - Référence de configuration Helm

### 🔧 Configuration
- **Version Kafka**: 7.7.0 (Confluent Platform)
- **Mode**: KRaft (sans Zookeeper)
- **Nœuds**: 3 combinés (broker + controller)
- **CPU**: 1-2 cores par nœud
- **Mémoire**: 2-4Gi par nœud
- **JVM Heap**: 1536m
- **Stockage**: 20Gi persistent par nœud
- **Réplication**: Factor 3, Min ISR 2
- **Rétention**: 7 jours / 1GB par partition
- **Network threads**: 8
- **I/O threads**: 8

### 🎯 Features
- Haute disponibilité avec réplication 3x
- Pod anti-affinity pour distribution sur les nœuds
- Pod Disruption Budget (max 1 unavailable)
- Health checks (liveness et readiness probes)
- Génération automatique de Cluster ID
- Support de PersistentVolumeClaims
- Configuration JVM optimisée (G1GC)

### 📚 Documentation
- Guide complet d'installation et d'utilisation
- Exemples de commandes Kafka
- Guide de troubleshooting
- Documentation de l'architecture
- Diagrammes de flux de données
- Guide de monitoring
- Procédures de scaling

### 🔐 Sécurité
- Configuration PLAINTEXT (à sécuriser pour production)
- Notes sur l'implémentation SSL/TLS et SASL

### 🧪 Tests
- Suite de tests automatisée
- Création/lecture/écriture de topics
- Validation du cluster

## [Unreleased]

### À venir
- [ ] Support SSL/TLS
- [ ] Support SASL authentication
- [ ] Network Policies
- [ ] Prometheus metrics export
- [ ] Grafana dashboards
- [ ] Automated backup scripts
- [ ] Migration scripts from Zookeeper
- [ ] Performance benchmarking tools
- [ ] Chaos engineering tests
- [ ] Multi-datacenter replication examples
- [ ] Schema Registry integration
- [ ] Connect workers deployment
- [ ] ksqlDB integration

## Notes de Migration

### De Zookeeper à KRaft
Si vous migrez d'une installation Kafka avec Zookeeper:
1. Sauvegarder vos données et configurations
2. Planifier une fenêtre de maintenance
3. Suivre le guide de migration officiel Kafka
4. Tester dans un environnement de développement d'abord

### Versions Précédentes
Ce dossier remplace l'ancienne configuration présente dans `values-kraft.yaml` qui utilisait:
- 1 replica (mode single-node)
- 10Gi de stockage
- Configuration basique

---

Pour toute question ou suggestion, veuillez créer une issue ou contacter l'équipe.
