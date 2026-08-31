# SC1Hub 플랫폼 업그레이드 계획 — Tomcat 10.0 / JDK 17 / Spring Boot 3.1

작성: 2026-08-31. 비즈니스 요금제 업그레이드(Metaspace 128MB) 완료를 전제로 한
버전 업그레이드 재시도 계획. 8/22 시도(f4d4c6d, 7655069로 리버트)의 복기를 반영한다.

## 1. 확정된 제약 (2026-08-31 실측)

- 카페24 "변경신청 → 서버환경 변경"에서 제공하는 조합은 정확히 셋:
  1. Tomcat 10.0.x / JSP 3.0 / Servlet 5.0 / **JDK 17** / MariaDB 10.1.x ← 목표
  2. Tomcat 10.0.x / JSP 3.0 / Servlet 5.0 / JDK 11 / MariaDB 10.1.x
  3. Tomcat 8.5.x / JSP 2.3 / Servlet 3.0 / JDK 8 / MariaDB 10.1.x ← 현재
- 변경 신청 후 **5분 이내 적용**, 하위 재변경(JDK 8 복귀) 가능 — 8/22에 실제로 왕복했다.
- 비즈니스 요금제의 JVM 옵션은 `-XX:MaxMetaspaceSize=128m -Xmx128m`
  (`/etc/userbashenv/sc1hub`, 2026-08-31 실측). 버전 변경은 상품 사양 변경이
  아니므로 유지된다는 담당자 답변 있음 — 단 컷오버 직후 `jinfo`로 재확인한다.
- MariaDB는 10.1로 고정(어느 조합이든 동일). DB는 버전 변경의 영향 밖이지만 백업은 한다.

## 2. 목표 스택과 버전 상한 근거

| 항목 | 현재 | 목표 | 근거 |
|---|---|---|---|
| JDK | 8 | **17** | 카페24 제공 조합 |
| Tomcat | 8.5.x | **10.0.x** (Servlet 5.0 / Jakarta EE 9) | 카페24 제공 조합 |
| Spring Boot | 2.7.2 | **3.1.12** (3.1 최종) | 아래 참조 |
| Spring Framework | 5.3.x | 6.0.x | Boot 3.1 종속 |
| Gradle | 7.5 | 8.x (8.14) | Boot 3.x 요건, f4d4c6d 재사용 |
| MyBatis starter | 2.2.0 | 3.0.x | Boot 3 대응 |
| JDBC 드라이버 | mysql-connector-java 8.0.x | **com.mysql:mysql-connector-j 유지** | §4 참조 |
| JSTL | javax.servlet:jstl 1.2 | glassfish jakarta.servlet.jsp.jstl **2.0** (EE 9) | §4 참조 |

**왜 Boot 3.5가 아니라 3.1인가 (8/22의 숨은 문제):**
Spring Framework 6.1+(= Boot 3.2+)는 런타임 기준선이 Jakarta EE 10
(Servlet 6.0, **Tomcat 10.1+**)이다. 카페24가 주는 건 Tomcat **10.0.x(Servlet 5.0)**
이므로, Servlet 5.0에서 구동 가능한 마지막 라인인 **Framework 6.0 = Boot 3.1.x**가
상한이다. 8/22의 f4d4c6d는 Boot **3.5.16**을 겨냥했는데 이는 Tomcat 10.0에서
스펙 밖 조합이었다(롤백 자체는 Metaspace로 촉발됐지만, 살아남았어도 위험했다).
Boot 3.1은 OSS 지원이 끝난 라인이라는 한계는 수용한다 — 현행 2.7.2도 마찬가지이고,
JDK 17 + Framework 6.0으로 옮겨두면 카페24가 Tomcat 10.1을 제공하는 시점에
Boot 3.2+로 가는 간극이 훨씬 작다. Phase 0의 로컬 Tomcat 10.0 기동 테스트가
이 호환성 판단을 실증한다.

## 3. Metaspace 예산 (이번엔 벽이 아님을 수치로)

- 현행 JDK 8/Boot 2.7: 워밍업 후 평탄 ~57MB. 64MB 시절 87~95%까지 갔던 값.
- Boot 3.1/JDK 17 예상 평탄: 65~85MB = 128MB의 **51~66%**. JDK 17은 Elastic
  Metaspace(JEP 387)로 반환·단편화 특성도 개선.
- 게이트는 전부 퍼센트 기준이라 그대로 유효: deploy.sh 경고 85%/거부 95%,
  앱 브레이커 92%(백그라운드)/96%(AI 전면). MetaspaceUsageLogger 10분 샘플링 유지.
- **Phase 0 로컬 게이트: 워밍업 후 평탄 사용량이 128MB의 75%(96MB) 이하**여야
  컷오버 진행. 초과 시 원인 분석 후 재계획.

## 4. Phase 0 — 코드 마이그레이션 + 로컬 검증 (서버 무접촉) — **완료 2026-08-31**

**결과 요약 (브랜치 `platform/boot31-jdk17`)**

- `./gradlew clean build` 통과(JDK 17), 테스트 전부 통과.
- **로컬 Apache Tomcat 10.0.27 + JDK 17 + `-Xmx128m -XX:MaxMetaspaceSize=128m`
  에서 기동 성공.** 대표 경로 17개 + 게시판/글읽기/사이트맵 전부 200, JSP는
  JSTL 2.0으로 정상 렌더(미해석 EL 없음), catalina 로그 에러 0건.
- **Metaspace 47.0MB / 128MB = 36.7%** (로드 클래스 9,337개). §3 게이트(≤75%)
  통과. 참고로 운영 JDK 8/Boot 2.7은 57MB·10,357클래스였다 — 업그레이드 후가
  오히려 클래스가 적고 여유는 8MB → 약 80MB로 늘어난다.

**마이그레이션 중 확인된 함정 4가지 (모두 조치 완료)**

1. **JSTL URI**: 체리픽이 가져온 `jakarta.tags.*`는 JSTL 3.0(EE 10) 문법이라
   Tomcat 10.0에서 해석 불가. JSTL 2.0 + `http://java.sun.com/jsp/jstl/*`로 복원.
2. **컨테이너 API 혼입**: JSTL 2.0이 서블릿/EL API를 전이 의존으로 끌고 오는데,
   Boot의 의존성 관리가 이를 **Servlet 6.0**으로 올려 WAR에 실었다. Tomcat 10.0이
   구현하지 않는 API를 앱이 들고 가는 상태였다. exclude로 제거하고,
   `verifyProductionWarFootprint`에 금지 규칙을 추가해 재발을 막았다.
3. **spring-test는 Servlet 6.0을 요구**: `MockHttpServletRequest`가 Servlet 6.0
   인터페이스를 구현하므로 테스트에는 6.0 API가 필요하다(`NoClassDefFoundError:
   jakarta/servlet/ServletConnection`). 테스트 스코프로만 추가 — WAR에는 불포함.
4. **JSP 바이트코드 조용한 강등**: Jasper가 딸려오는 ECJ 3.18은 release 17을
   몰라 경고만 남기고 12로 낮춰 컴파일한다. 구 ECJ를 배제해 JSP도 Java 17
   (major 61)로 컴파일되도록 했다.

**남은 확인 사항**: 관리자 JSP(adminPage/adminAliasDictionary)는 로컬에서 403이라
렌더되지 않았다. 과거 콜드 경로 트리거였으므로 컷오버 후 2시간 관찰에서 확인한다.
운영 실측에서 이 계단은 약 8MB였고, 현재 여유는 그 10배다.

---

### 원래 계획 (참고)

브랜치 `platform/boot31-jdk17`에서 진행. 기반 작업: `git cherry-pick -n f4d4c6d`
— 실측 결과 충돌은 5개 파일뿐(build.gradle, deploy.sh, runbook,
AssistantConfig.java, UploadController.java), 나머지 ~50개 파일(javax→jakarta,
JSP, 테스트)은 자동 병합된다.

1. **build.gradle**: Boot `3.1.12` / dep-mgmt 1.1.x / Gradle wrapper 8.14 /
   MyBatis starter 3.0.x / toolchain 17.
   - 드라이버는 `com.mysql:mysql-connector-j` **유지** (Boot 3.1 관리 8.0.33 —
     현행과 같은 계열, MariaDB 10.1에서 이미 검증된 조합). f4d4c6d의
     mariadb-java-client 전환은 **하지 않는다**: client 3.x는 서버 10.2+가
     최소 요건이고, 드라이버 교체는 이번 변경의 변수만 늘린다.
     (동봉됐던 `migrate-online-datasource-to-mariadb.sh`도 폐기.)
   - JSTL: Tomcat 10.0은 EE 9라 **JSTL 2.0** — glassfish
     `jakarta.servlet.jsp.jstl:2.0.0` + api 2.0. JSTL 2.0은 기존
     `http://java.sun.com/jsp/jstl/*` URI를 그대로 쓰므로 JSP taglib 선언
     변경 불필요(JSTL 3.0의 `jakarta.tags.*` URI로 바꾸면 안 됨).
   - JSP 선컴파일(jspCompiler)의 jasper는 **tomcat-embed-jasper 10.0.27로 핀**
     (Boot 3.1 BOM은 10.1 jasper를 주므로 런타임 10.0과 어긋난다).
2. **javax→jakarta 스윕**: 리버트 이후 9일간 추가된 코드(AI 검색, 봇 개편,
   관리자 콘텐츠 API 등)에 남은 `javax.servlet`(현재 27파일)을 일괄 전환.
   `javax.mail`→jakarta.mail, `javax.annotation`→jakarta.annotation 포함.
   `javax.imageio`는 JDK 내장이므로 그대로.
3. **검증(로컬)**:
   - `./gradlew clean build` — 전체 테스트 통과 (JAVA_HOME=openjdk@17).
   - 설정 가드 테스트(AssistantBotApplicationPropertiesTest) 갱신 여부 확인.
   - **Apache Tomcat 10.0.27을 로컬에 받아 WAR 배포**, JDK 17 +
     `-Xmx128m -XX:MaxMetaspaceSize=128m`으로 기동:
     a) 기동 성공 = Boot 3.1×Tomcat 10.0 호환 실증.
     b) deploy.sh의 대표 경로 워밍업 목록을 순회 + 관리자 JSP(adminPage,
        adminAliasDictionary — 과거 콜드 경로 트리거) 접근.
     c) `jstat -gc`로 평탄 사용량 측정 → §3 게이트 판정.
   - 로컬 실행 제약: 포트 8082/80 점유 때문에 로컬 인스턴스는 1개만.
4. **deploy.sh**: f4d4c6d의 런타임 프리플라이트(Tomcat 10.0.x + JVM 17 확인,
   불일치 시 배포 거부·구 WAR 복원 안내) 복원 + 현행 게이트(워밍업/Metaspace
   2회 검증/안정성 윈도)와 병합. WAR 검사 규칙(jakarta JSTL jar 존재 등) 갱신.

## 5. Phase 1 — 컷오버 준비 (서버 읽기만)

- 백업 3종: ① 현행 `ROOT.war` 사본(+SHA-256) ② DB 덤프(카페24 관리자
  DATA&DB백업 메뉴) ③ `config/` 외부 프로퍼티와 `tomcat/conf`, `setenv.sh` 사본.
- 버전 변경이 홈 디렉터리에 무엇을 만드는지 확인 준비: 8/22 경험상 재프로비저닝이
  `/etc/userbashenv/sc1hub`를 다시 쓴다. 새 tomcat 디렉터리 경로/포트(8645 유지
  여부)/`catalina.sh`의 JAVA_OPTS를 컷오버 직후 즉시 점검하는 체크리스트 준비.
- 컷오버 창: 저트래픽 시간대(새벽), 예상 소요 20~30분(환경 변경 5분 + 배포/검증).

## 6. Phase 2 — 컷오버 (사용자 승인 후 실행)

순서가 생명이다. **코드가 완성된 뒤에 서버 환경을 바꾼다.**

1. Phase 0 게이트 통과 + `main` 병합 + WAR 빌드 완료 상태에서 시작.
2. 카페24 변경신청 페이지에서 **서버환경 변경 → Tomcat 10.0/JDK 17** 제출
   (브라우저 작업은 Claude가 수행하되, 제출 직전 사용자 확인 필수.
   백업 확인 체크박스 포함).
3. ~5분 대기 후 서버 점검: 새 tomcat 디렉터리, `bin/version.sh`
   (Tomcat 10.0.x / JVM 17), `/etc/userbashenv/sc1hub`(128m 유지),
   포트, `deploy.sh`의 REMOTE_TOMCAT_DIR 경로 유효성.
   - 새 `setenv.sh`가 생기면 SPRING_PROFILES_ACTIVE / 외부 config 경로 /
     inflationThreshold 설정은 deploy.sh가 재주입한다. **`-Xmx64m`류의 힙 하향
     라인이 새 setenv.sh에 있으면 제거**(JAVA_OPTS의 -Xmx128m이 살도록).
4. `printf 'y\n' | ./deploy.sh` — 프리플라이트(10.0/17 확인) → WAR 설치 →
   기동 → 워밍업 → Metaspace 게이트 2회 → 30초 안정성 윈도.
5. 공개 검증: `https://sc1hub.com/` 및 `/boards/pvstboard` 200, 로그인/세션,
   이미지 업로드 표시, AI 검색 1회, 관리자 API 비인증 거부, catalina 로그의
   에러 스캔.
6. 이후 2시간 MetaspaceUsageLogger 곡선 관찰(과거 패턴: 기동 후 2시간 내 평탄).

## 7. 롤백 (8/22에 리허설 완료된 경로)

트리거: 기동 실패, 30초 안정성 실패, DB 연결 실패, JSP 렌더 실패, 공개 검증 실패,
Metaspace 게이트 거부.

1. Tomcat 중지, 실패 로그 보존(`catalina.YYYY-MM-DD.log` — deploy.sh가
   catalina.out을 로테이트하므로 일별 로그를 본다).
2. 카페24에서 서버환경을 **JDK 8 조합으로 재변경**(5분).
3. `ROOT.war.rollback` 복원 → 기동 → 공개 검증.
4. DB는 스키마 변경이 없으므로 원칙적으로 복원 불요(백업은 보험).

## 8. Phase 3 — 안정화/사후

- `docs/platform-upgrade-runbook.md` 전면 개정: "JDK 8 고정/Boot 3 스코프 밖"
  조항 삭제, 새 기준선(Tomcat 10.0/JDK 17/Boot 3.1/128MB) 반영.
- 메모리 파일 갱신(sc1hub-metaspace-*, deploy-verify).
- 1주 관찰 후 이상 없으면 구 tomcat 디렉터리 정리(카페24 안내대로 구버전
  디렉터리 삭제 가능 — 웹/스트리밍 하드 용량 회수).
- 후속 과제(별건): 카페24가 Tomcat 10.1 제공 시 Boot 3.2+ 재평가.

## 9. 리스크 요약

| 리스크 | 대응 |
|---|---|
| Boot 3.1이 Tomcat 10.0에서 미기동 | Phase 0 로컬 실증으로 컷오버 전 확인 |
| Metaspace가 128MB에서도 부족 | Phase 0 실측 게이트(≤75%) + deploy 게이트 95% |
| 버전 변경이 계정 구조를 예상과 다르게 변경 | Phase 2-3 점검 체크리스트, 안 맞으면 즉시 하위 재변경 |
| mysql-connector-j 8.0.33 × MariaDB 10.1 | 현행과 같은 8.0 계열이라 위험 낮음. Phase 0 로컬 DB 테스트 포함 |
| JSTL URI 불일치로 JSP 전면 오류 | JSTL 2.0(EE 9) 고정 + 로컬 JSP 렌더 확인 |
| 선컴파일 JSP와 런타임 jasper 버전 어긋남 | jspCompiler를 10.0.27로 핀 |
| 컷오버 중 장애 장기화 | 왕복 검증된 롤백 경로(§7), 저트래픽 창, 백업 3종 |
