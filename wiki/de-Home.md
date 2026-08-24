# pasta — Deutsch

[Sprachen](https://github.com/MARVserver/pasta/wiki/Home) · [Erste Schritte](https://github.com/MARVserver/pasta/wiki/de-Getting-Started) · [Architektur](https://github.com/MARVserver/pasta/wiki/de-Architecture)

## Was ist pasta?

**pasta** (früher Folia Phantom) passt kompilierte Bukkit-Plugin-JARs an Folias regionenbasiertes Threading-Modell an. Der Bytecode wird mit ASM direkt umgeschrieben; Quellcode oder Neukompilierung des Plugins sind nicht erforderlich.

## Nutzung

- Browser: lokale Drag-and-drop-Konvertierung
- GitHub Actions: Folia-Artefakte im CI
- CLI: Automatisierung und Stapelverarbeitung
- GUI: JavaFX-Oberfläche
- Server plugin: Konvertierung in Paper/Bukkit

## Anforderungen und Sicherheit

Für Builds und Java-Workflows **JDK 21+**, für Quell-Builds Maven 3.8+. pasta kann bekannte Kompatibilitätsmuster anpassen, beweist aber nicht die Thread-Sicherheit plugin-eigener Zustände. Vor Produktion auf einem Folia-Staging-Server testen.
