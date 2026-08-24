# pasta — Português

[Idiomas](https://github.com/MARVserver/pasta/wiki/Home) · [Primeiros passos](https://github.com/MARVserver/pasta/wiki/pt-Getting-Started) · [Arquitetura](https://github.com/MARVserver/pasta/wiki/pt-Architecture)

## O que é o pasta?

**pasta** (anteriormente Folia Phantom) adapta JARs compilados de plugins Bukkit ao modelo de execução por regiões do Folia. Ele reescreve bytecode com ASM sem exigir o código-fonte nem recompilar o plugin original.

## Formas de uso

- Browser: conversão local por arrastar e soltar
- GitHub Actions: artefatos Folia no CI
- CLI: automação e processamento em lote
- GUI: interface JavaFX
- Server plugin: conversão em ambiente Paper/Bukkit

## Requisitos e segurança

Use **JDK 21+** em builds e fluxos Java; Maven 3.8+ para compilar a partir do código-fonte. O pasta adapta padrões conhecidos, mas não prova que o estado compartilhado do plugin seja thread-safe. Valide em um servidor Folia de staging antes da produção.
