# 아키텍처

[홈](https://github.com/MARVserver/pasta/wiki/ko-Home) · [시작하기](https://github.com/MARVserver/pasta/wiki/ko-Getting-Started)

```text
입력 JAR
  ↓
무효화된 서명 제거 + plugin.yml 업데이트
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
runtime bridge 포함
  ↓
patched JAR
```

`core`는 ASM visitor, 변환 체인, runtime bridge를 담당하고 `cli`, `gui`, `plugin`, `web`은 서로 다른 frontend를 제공합니다. Transformer 순서는 의도적이며 뒤 단계가 앞 단계의 정규화에 의존할 수 있습니다. 런타임 검증은 여전히 필요합니다.
