# Primeros pasos

[Inicio](https://github.com/MARVserver/pasta/wiki/es-Home) · [Arquitectura](https://github.com/MARVserver/pasta/wiki/es-Architecture)

## Browser

1. Abra la aplicación web de pasta.
2. Añada uno o varios JAR de plugins.
3. Revise los archivos listos y omitidos.
4. Convierta y descargue `patched-*.jar`.
5. Para lotes, descargue `pasta-report.csv` si lo necesita.

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

En CI de producción, fije un release tag o un commit SHA inmutable.

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

Se requieren JDK 21+ y Maven 3.8+.
