# pasta — Français

[Langues](https://github.com/MARVserver/pasta/wiki/Home) · [Bien démarrer](https://github.com/MARVserver/pasta/wiki/fr-Getting-Started) · [Architecture](https://github.com/MARVserver/pasta/wiki/fr-Architecture)

## Qu'est-ce que pasta ?

**pasta** (anciennement Folia Phantom) adapte les JAR compilés de plugins Bukkit au modèle d'exécution régional de Folia. Il réécrit directement le bytecode avec ASM, sans nécessiter le code source ni la recompilation du plugin.

## Modes d'utilisation

- Browser : conversion locale par glisser-déposer
- GitHub Actions : artefacts Folia dans la CI
- CLI : automatisation et traitements par lots
- GUI : interface JavaFX
- Server plugin : conversion dans un environnement Paper/Bukkit

## Prérequis et sécurité

Utilisez **JDK 21+** pour les builds et workflows Java, Maven 3.8+ pour compiler depuis les sources. pasta adapte des motifs connus mais ne prouve pas que l'état partagé propre au plugin est thread-safe. Validez sur un serveur Folia de staging avant la production.
