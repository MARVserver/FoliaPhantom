# Architektur

[Start](https://github.com/MARVserver/pasta/wiki/de-Home) · [Erste Schritte](https://github.com/MARVserver/pasta/wiki/de-Getting-Started)

```text
Eingabe-JAR
  ↓
ungültige Signaturen entfernen + plugin.yml aktualisieren
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
runtime bridge bündeln
  ↓
gepatchtes JAR
```

`core` enthält ASM-Visitor, Transformationskette und Runtime-Bridge. `cli`, `gui`, `plugin` und `web` stellen verschiedene Frontends bereit. Die Transformer-Reihenfolge ist absichtlich festgelegt; spätere Schritte können von früherer Normalisierung abhängen. Laufzeittests bleiben erforderlich.
