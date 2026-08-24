# Erste Schritte

[Start](https://github.com/MARVserver/pasta/wiki/de-Home) · [Architektur](https://github.com/MARVserver/pasta/wiki/de-Architecture)

## Browser

1. pasta-Web-App öffnen.
2. Ein oder mehrere Plugin-JARs hinzufügen.
3. Bereite und übersprungene Dateien prüfen.
4. Konvertieren und `patched-*.jar` herunterladen.
5. Bei Stapeln optional `pasta-report.csv` laden.

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

Für Produktions-CI einen Release-Tag oder unveränderlichen Commit-SHA pinnen.

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

JDK 21+ und Maven 3.8+ sind erforderlich.
