# Architecture

[Accueil](https://github.com/MARVserver/pasta/wiki/fr-Home) · [Bien démarrer](https://github.com/MARVserver/pasta/wiki/fr-Getting-Started)

```text
JAR d'entrée
  ↓
supprimer les signatures invalides + mettre à jour plugin.yml
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
intégrer le runtime bridge
  ↓
JAR patché
```

`core` contient les visiteurs ASM, la chaîne de transformation et le runtime bridge. `cli`, `gui`, `plugin` et `web` fournissent différents frontends. L'ordre des transformers est volontaire ; les étapes suivantes peuvent dépendre de la normalisation précédente. Les tests d'exécution restent nécessaires.
