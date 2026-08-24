# pasta — Español

[Idiomas](https://github.com/MARVserver/pasta/wiki/Home) · [Primeros pasos](https://github.com/MARVserver/pasta/wiki/es-Getting-Started) · [Arquitectura](https://github.com/MARVserver/pasta/wiki/es-Architecture)

## ¿Qué es pasta?

**pasta** (antes Folia Phantom) adapta JAR compilados de plugins Bukkit al modelo de ejecución por regiones de Folia. Reescribe bytecode con ASM sin necesitar el código fuente ni recompilar el plugin original.

## Formas de uso

- Browser: conversión local mediante arrastrar y soltar
- GitHub Actions: artefactos Folia en CI
- CLI: automatización y lotes
- GUI: interfaz JavaFX
- Server plugin: conversión desde Paper/Bukkit

## Requisitos y seguridad

Use **JDK 21+** para compilaciones y flujos Java; Maven 3.8+ para compilar desde fuente. pasta adapta patrones conocidos, pero no puede demostrar que el estado compartido propio del plugin sea thread-safe. Valide en un servidor Folia de staging antes de producción.
