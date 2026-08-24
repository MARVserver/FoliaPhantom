# Primeiros passos

[Início](https://github.com/MARVserver/pasta/wiki/pt-Home) · [Arquitetura](https://github.com/MARVserver/pasta/wiki/pt-Architecture)

## Browser

1. Abra o aplicativo web do pasta.
2. Adicione um ou mais JARs de plugins.
3. Revise arquivos prontos e ignorados.
4. Converta e baixe `patched-*.jar`.
5. Em lotes, baixe `pasta-report.csv` quando necessário.

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

Em CI de produção, fixe um release tag ou commit SHA imutável.

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

São necessários JDK 21+ e Maven 3.8+.
