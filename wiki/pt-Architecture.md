# Arquitetura

[Início](https://github.com/MARVserver/pasta/wiki/pt-Home) · [Primeiros passos](https://github.com/MARVserver/pasta/wiki/pt-Getting-Started)

```text
JAR de entrada
  ↓
remover assinaturas inválidas + atualizar plugin.yml
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
JAR corrigido
```

`core` contém os visitantes ASM, a cadeia de transformações e o runtime bridge. `cli`, `gui`, `plugin` e `web` oferecem frontends diferentes. A ordem dos transformers é intencional e etapas posteriores podem depender da normalização anterior. A validação em execução continua necessária.
