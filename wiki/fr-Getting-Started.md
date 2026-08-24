# Bien démarrer

[Accueil](https://github.com/MARVserver/pasta/wiki/fr-Home) · [Architecture](https://github.com/MARVserver/pasta/wiki/fr-Architecture)

## Browser

1. Ouvrez l'application web pasta.
2. Ajoutez un ou plusieurs JAR de plugins.
3. Vérifiez les fichiers prêts et ignorés.
4. Convertissez puis téléchargez `patched-*.jar`.
5. Pour les lots, récupérez `pasta-report.csv` si nécessaire.

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

En CI de production, épinglez un tag de release ou un commit SHA immuable.

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

JDK 21+ et Maven 3.8+ sont requis.
