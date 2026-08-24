# pasta — 한국어

[언어 선택](https://github.com/MARVserver/pasta/wiki/Home) · [시작하기](https://github.com/MARVserver/pasta/wiki/ko-Getting-Started) · [아키텍처](https://github.com/MARVserver/pasta/wiki/ko-Architecture)

## pasta란?

**pasta**(구 Folia Phantom)는 컴파일된 Bukkit 플러그인 JAR을 Folia의 지역 기반 스레딩 모델에 맞게 변환합니다. ASM으로 bytecode를 직접 다시 쓰므로 플러그인 소스 코드나 재컴파일이 필요하지 않습니다.

## 사용 방법

- Browser: 로컬 드래그 앤 드롭 변환
- GitHub Actions: CI에서 Folia용 산출물 생성
- CLI: 자동화 및 일괄 변환
- GUI: JavaFX 데스크톱 UI
- Server plugin: Paper/Bukkit 서버 환경에서 변환

## 요구 사항과 안전성

빌드 및 Java 워크플로에는 **JDK 21+**, 소스 빌드에는 Maven 3.8+가 필요합니다. pasta는 알려진 호환 패턴을 변환하지만 플러그인 자체 공유 상태의 thread-safety를 증명하지는 못합니다. 프로덕션 전에 Folia 스테이징 서버에서 검증하십시오.
