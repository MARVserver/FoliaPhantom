# 시작하기

[홈](https://github.com/MARVserver/pasta/wiki/ko-Home) · [아키텍처](https://github.com/MARVserver/pasta/wiki/ko-Architecture)

## Browser

1. pasta 웹 앱을 엽니다.
2. 하나 이상의 플러그인 JAR을 드롭합니다.
3. 변환 대상과 건너뛴 파일을 확인합니다.
4. 변환 후 `patched-*.jar`을 다운로드합니다.
5. 배치 작업에서는 필요하면 `pasta-report.csv`를 받습니다.

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

프로덕션 CI에서는 release tag 또는 불변 commit SHA를 고정하십시오.

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

JDK 21+와 Maven 3.8+가 필요합니다.
