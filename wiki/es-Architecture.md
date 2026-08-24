# Arquitectura

[Inicio](https://github.com/MARVserver/pasta/wiki/es-Home) · [Primeros pasos](https://github.com/MARVserver/pasta/wiki/es-Getting-Started)

```text
JAR de entrada
  ↓
eliminar firmas inválidas + actualizar plugin.yml
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
incluir runtime bridge
  ↓
JAR parcheado
```

`core` contiene los visitantes ASM, la cadena de transformaciones y el runtime bridge. `cli`, `gui`, `plugin` y `web` ofrecen distintos frontends. El orden de los transformers es intencional y las fases posteriores pueden depender de la normalización previa. La validación en ejecución sigue siendo necesaria.
