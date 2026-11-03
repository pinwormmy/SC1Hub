# 카페24 → AWS 라이트세일 이전 계획

작성 2026-10-22. 목표는 **코드 변경 최소로 서버만 옮기고**, 런타임을 패치 가능한 최신 버전으로 올리는 것이다. Boot 업그레이드, Thymeleaf, 언어 변경은 이 이전이 안정된 뒤 별도로 진행한다.

## 1. 왜 옮기나

- 카페24 운영 JDK는 `17.0.4-internal`(2022-07 빌드)이다. 4년 치 분기 보안 패치와 2026-10 CVE-2026-91403(CA 인증서 탈취)이 빠져 있다.
- Tomcat 10.0은 2022-10에 지원이 끝났다. 2026년에 10.1에서 고친 Important 등급 취약점이 10.0에는 들어오지 않는다.
- MariaDB 10.1.13도 지원 종료 버전이다.
- 셋 다 카페24가 정하는 값이라 요금제를 올려도 바뀌지 않는다.

## 2. 마감일

| 날짜 | 무엇 | 비고 |
|---|---|---|
| 2026-10-30 | 도메인 이전 판단 시점 | 이때까지 Cloudflare 이전이 안 끝나면 카페24에서 1년 연장(23,500원) |
| **2026-11-02** | 도메인 sc1hub.com 만료 | 카페24 관리, 자동연장 꺼짐 |
| **2026-11-06** | 카페24 유료 SSL 만료 | **이 전에 전환을 끝내야** 현 사이트 HTTPS가 안 깨진다 |
| 2026-11-23 | 카페24 JSP 호스팅 만료 | 1개월 연장 완료. 전환 후 되돌리기용 대기 서버 |

## 3. 목표 구성

| 항목 | 지금 (카페24) | 이전 후 (라이트세일) |
|---|---|---|
| 서버 | 공유 JSP 호스팅 | Lightsail 서울(ap-northeast-2), $7 번들: 1GB / vCPU 2(버스트) / 40GB / 2TB, 고정 IP |
| OS | 카페24 관리 | Ubuntu 24.04 LTS, 시간대 Asia/Seoul |
| JDK | 17.0.4 (2022) | Temurin 또는 Ubuntu OpenJDK 17 최신 패치 |
| Tomcat | 10.0.20 (EOL) | Apache 공식 10.1.x 최신, systemd 서비스 |
| DB | MariaDB 10.1.13 | MariaDB 10.11 LTS (Ubuntu 기본 패키지), localhost 전용 |
| 앞단 | 카페24 프록시(openresty) | nginx: TLS 종단, 80→443 리다이렉트 |
| 인증서 | 카페24 유료 SSL | Let's Encrypt, Cloudflare DNS-01로 발급·자동 갱신 |
| DNS | 카페24 네임서버 | Cloudflare DNS (프록시 끔, 회색 구름) |
| 도메인 등록처 | 카페24 | Cloudflare Registrar |
| 백업 | 없음(수동) | 매일 DB 덤프 + 이미지/RAG 인덱스 → Google Drive |

**경로는 카페24와 똑같이 맞춘다.** `/home/hosting_users/sc1hub/{tomcat,config,scripts}` 구조를 그대로 만들면 `application-online.properties`의 업로드 경로(`path.upload.*`)와 `SPRING_CONFIG_ADDITIONAL_LOCATION`, RAG 인덱스 상대경로(`data/assistant/rag-index.json`, Tomcat 작업 디렉터리 기준)를 고치지 않아도 된다. DB도 같은 이름·계정으로 만들어 설정 파일을 그대로 복사한다.

## 4. 코드·빌드에서 바뀌는 것 (최소)

1. `build.gradle`의 `ext['tomcat.version'] = '10.0.27'` → 10.1.x. 미리 컴파일한 JSP 클래스가 Jasper 런타임 버전에 묶일 수 있어 리허설로 확인했고, 10.0 빌드와 10.1 빌드 모두 Tomcat 10.1에서 정상이었다(8-1절). Boot 3.1의 기본값이 원래 10.1이라 고정만 푸는 셈이다. 이 변경은 카페24(10.0)에서는 동작을 보장하지 않으므로 **전환이 안정된 뒤 일반 배포로 넣는다**(11/3 결정). 그때까지는 10.0 빌드 WAR를 Tomcat 10.1에서 그대로 쓴다(8-1절 A). 전환 새벽에 바뀌는 것이 하나 줄고, 카페24 되돌리기용 WAR를 따로 둘 필요가 없다.
2. `deploy.sh`: Tomcat 10.0 사전 점검을 10.1로 바꾸고, 대상 호스트·재시작 방식(systemd)을 새 서버에 맞춘다. 카페24 전용 단계(setenv.sh 덧붙이기, OOM 복구 스크립트)는 정리한다. **배포 게이트(`origin/main`에 있는 커밋만 배포)는 유지한다.**
3. 나머지 Java 코드와 JSP는 바꾸지 않는다.

## 5. 반드시 지켜야 할 함정

- **IP 헤더**: `IpService`는 `X-Forwarded-For`의 가장 오른쪽 값을 실제 방문자로 보고, `PlainHttpRedirectFilter`는 `X-Real-IP`가 붙은 요청을 평문 HTTP로 판단한다. nginx는 카페24 프록시와 똑같이 동작해야 한다.
  - 443: `X-Forwarded-For $proxy_add_x_forwarded_for`, `X-Forwarded-Proto https`, `Host $host`. **`X-Real-IP`는 보내지 않는다.** 보내면 모든 요청이 리다이렉트 대상으로 판정된다(필터의 루프 방어가 있긴 하지만 기대지 않는다).
  - 80: nginx에서 바로 301로 `https://sc1hub.com`에 보낸다. 앱까지 가지 않는다.
  - 검증: 관리자 IP 진단 API(`SecurityAdminController`)로 내 IP가 정확히 찍히는지, 127.0.0.1이나 서버 IP가 아닌지 확인한다.
- **시간대**: Lightsail 기본값은 UTC다. OS(`timedatectl`), JVM(`-Duser.timezone=Asia/Seoul`), MariaDB(`default_time_zone`) 모두 Asia/Seoul로 맞춘다. 봇 자동 게시 슬롯과 방문자 집계가 여기에 의존한다.
- **전환 전 새 서버의 자동 작업 차단**: 리허설 동안 새 서버에서 봇 자동 게시, RAG 자동 갱신, 외부 AI 호출이 돌면 API 비용이 나가고 DB 사본에 글이 쌓인다. 전환 전까지 `sc1hub.assistant.bot.enabled`, `autoPublishEnabled`, `rag.autoUpdate.enabled`, `*.allowLiveCalls`를 false로 둔다.
- **메모리 1GB**: 스왑 1GB를 만들고 JVM `-Xmx256m -XX:MaxMetaspaceSize=192m`, MariaDB `innodb_buffer_pool_size=128M`, `performance_schema=OFF`로 시작한다. 지금 운영 힙은 64MB로도 여유가 있어서 충분하다.
- **고정 IP**: 인스턴스에 연결된 동안만 무료다. 분리된 채로 두면 과금된다.
- **Lightsail은 정지해도 과금된다.** 과금을 멈추려면 삭제해야 한다.

## 6. 과금 방지 장치

- 루트 계정 MFA, 평소 루트 로그인 금지 (사용자)
- 예산 알림: 월 $10 기준, 실제 80%·100%와 예상 100%에 메일 (CLI로 설정)
- CLI용 IAM 사용자는 Lightsail과 비용 조회 권한만. 인스턴스를 만든 뒤에는 `CreateInstances` 권한을 빼서 큰 요금제를 실수로 만들 수 없게 한다.
- `scripts/aws-billing-check.sh`: 모든 리전의 Lightsail 인스턴스·고정 IP·스냅샷·디스크와 EC2 잔재, 이번 달 누적 금액을 한 번에 보여 준다. 월 1회 실행.
- 액세스 키는 저장소에 넣지 않는다. `aws configure`는 사용자가 직접 입력한다.

### 6-1. 준비해 둔 파일과 가입 후 순서 (10/27 작성, AWS 없이 스텁으로만 검증)

| 파일 | 내용 |
|---|---|
| `deploy/lightsail/aws/iam-policy-sc1hub-cli.json` | CLI 사용자 권한. 쓰기는 **서울 리전 Lightsail의 인스턴스·고정 IP·방화벽·키·스냅샷만**. 삭제 권한은 일부러 뺐다(삭제는 콘솔에서). 나머지는 과금 점검용 읽기와 `sc1hub-monthly` 예산 관리뿐. EC2·RDS·IAM 생성은 불가 |
| `deploy/lightsail/aws/iam-policy-lock-after-create.json` | 인스턴스를 만든 뒤 붙이는 Deny 정책. 인스턴스·고정 IP 추가 생성을 막는다 |
| `scripts/aws-budget-setup.sh <메일>` | 월 $10 예산 + 실제 80%·100%, 예상 100% 메일 알림. 이미 있으면 건드리지 않는다 |
| `scripts/aws-billing-check.sh` | 루트 MFA, 예산 대비 금액, 전 리전 Lightsail·EC2·RDS 잔재 점검. "서울 sc1hub 인스턴스 1개 + 붙어 있는 고정 IP 1개" 외에는 전부 경고, 경고가 있으면 종료 코드 2. 비용은 무료인 Budgets API로만 읽는다(Cost Explorer API는 요청당 $0.01) |

가입은 반드시 **"Sign up for AWS (advanced)"**(`signup?request_type=register`)로 한다. "Sign up for AWS (new)"나 구글 로그인으로 만든 계정은 주소가 한국이면 리전이 시드니로 고정되어 서울을 쓸 수 없다(10/30 확인).

가입 후 순서:
1. (사용자, 루트) 가입·카드 → 루트 MFA → IAM에서 사용자 `sc1hub-cli` 생성(콘솔 접근 없음) → 인라인 정책으로 `iam-policy-sc1hub-cli.json` 내용 붙여 넣기 → 액세스 키 발급 → Mac에서 `aws configure`(리전 `ap-northeast-2`). 루트 액세스 키는 만들지 않는다.
2. (나) `scripts/aws-budget-setup.sh <메일>` → 알림 구독 확인 메일이 오면 사용자가 승인. 권한 오류가 나면 루트 계정 설정의 "IAM 사용자의 결제 정보 액세스"를 켠다.
3. (나) `scripts/aws-billing-check.sh`로 빈 계정 상태 확인.
4. (나) 인스턴스·고정 IP 생성 후 (사용자) `iam-policy-lock-after-create.json`을 같은 사용자에 두 번째 인라인 정책으로 추가.
5. 이후 월 1회 `aws-billing-check.sh`.

## 7. 일정

### 1단계: 준비 (10/22~10/25)

| 할 일 | 담당 |
|---|---|
| Cloudflare 가입, sc1hub.com 추가(무료 플랜) | 사용자 |
| Cloudflare가 가져온 레코드 점검: A `@`, CNAME `www`, TXT SPF, **TXT google-site-verification(빠지면 서치콘솔 인증 해제)**, MX. 전부 회색 구름 | 나 |
| 카페24에서 네임서버를 Cloudflare 것으로 변경 (반영 최대 48시간) | 사용자 (화면은 내가 띄움) |
| AWS 가입, 카드 등록, 루트 MFA, IAM 사용자·키 발급, `aws configure` | 사용자 (순서는 내가 안내) |
| SSH 키 생성(ed25519). 공개키를 Lightsail 생성 때 등록 | 나 |
| 로컬 리허설 (아래 8절) | 나 |
| `deploy.sh` 개편, nginx 설정, 서버 구성 스크립트, 백업 스크립트, 과금 점검 스크립트 작성 | 나 |

### 2단계: 도메인 이전 (Cloudflare 활성화 직후 ~10/30)

| 할 일 | 담당 |
|---|---|
| 카페24에서 도메인 잠금 해제, Auth code 확인 | 사용자 |
| Cloudflare Registrar에서 이전 신청, 1년 결제(약 $11) | 사용자 |
| 카페24 이전 승인 메일·소유자 인증 처리 | 사용자 |
| 이전 완료 확인 (최대 5일). **10/30까지 안 끝나면 카페24에서 연장** | 나 |

### 3단계: 새 서버 구성 (10/26~10/31)

1. Lightsail 인스턴스 생성(서울, $7, Ubuntu 24.04), 고정 IP 연결, 방화벽은 22·80·443만 연다.
2. 서버 구성 스크립트 실행: 시간대, 스왑, 자동 보안 업데이트(unattended-upgrades), JDK 17, Tomcat 10.1(systemd), MariaDB 10.11, nginx, certbot + Cloudflare DNS 플러그인.
3. Let's Encrypt 인증서 발급 (DNS-01이라 도메인이 아직 카페24 서버를 가리켜도 발급된다). 네임서버 이전이 늦으면 카페24에서 받은 기존 인증서(11/6까지 유효)를 임시로 쓴다.
4. 운영 DB 덤프 → 새 서버 복원, `webapps/img`(46MB, 파일 수백 개)와 `data/assistant/rag-index.json`(15MB) 복사, `application-online.properties` 복사 후 자동 작업 플래그 끄기.
5. 새 deploy 스크립트로 WAR 배포.
6. **hosts 파일로 끝까지 시험** (내 Mac만 새 서버로 접속)
   - 홈, 게시판 목록, 글 읽기, `/sitemap.xml`, RSS
   - 로그인(BCrypt), 글·댓글 쓰기, 이미지 업로드
   - AI 검색 1회(이때만 allowLiveCalls를 잠깐 켠다)
   - 콘텐츠 API 스크립트 `boards`/`read`
   - http→https, www→루트 리다이렉트, HSTS, 세션 쿠키 Secure/SameSite
   - 관리자 IP 진단에서 내 IP가 정확히 나오는지
   - 메일 발송(인증 메일 경로) — Lightsail은 25번 포트만 막고 SMTP 465/587은 열려 있다
   - Metaspace·힙·전체 메모리 사용량
7. 백업 스크립트 첫 실행, Google Drive에서 확인.
8. 과금 점검 스크립트 실행, 예산 알림 설정 확인.

진행 기록 (11/3):
- 계정 `sc1hub`(171178671508): 루트 MFA, IAM `sc1hub-cli`에 인라인 정책 2개(쓰기 정책, 생성 후 잠금), 예산 `sc1hub-monthly` $10, 과금 점검 통과. 계정에서 켜지 않은 옵트인 리전 4곳은 모든 호출을 거부하므로 점검에서 건너뛴다.
- 인스턴스 `sc1hub`(`micro_3_0`, ap-northeast-2a), 고정 IP `sc1hub-ip` 3.34.22.112, 방화벽 22·80·443. SSH 별칭 `sc1hub-lightsail`(키 로그인만).
- `provision.sh` 실서버 실행 통과: OpenJDK 17.0.20, MariaDB 10.11.14, nginx 1.24, Tomcat 10.1.61.
- 데이터 리허설 복사: 설정 원본은 서버 `config/application-online.properties.cafe24`에 두고, 활성 설정은 봇 자동 발행·RAG 자동 갱신·AI 실제 호출을 끈다(전환 때 원본으로 되돌림). DB 덤프(utf8mb4) 복원 후 71개 테이블 행 수 일치, 이미지 356개, RAG 인덱스 해시 일치.
- Let's Encrypt 인증서(sc1hub.com, www.sc1hub.com) 발급, 만료 2027-02-01, 갱신 dry-run 통과.

### 4단계: 전환 (11/3~11/5 중 하루, 새벽 4시 전후)

1. 전날: Cloudflare에서 A 레코드 TTL을 300초로 낮춘다.
2. 카페24 Tomcat 정지 (점검 시작, 약 30분 예상).
3. 운영 DB 최종 덤프 → 새 서버 복원. 마지막 백업 이후 바뀐 이미지·RAG 인덱스 다시 복사.
4. 새 서버 설정에서 자동 작업 플래그를 운영 값으로 되돌리고 Tomcat 재시작.
5. Cloudflare A 레코드를 라이트세일 고정 IP로 변경.
6. 공개 URL 검증: 홈·게시판 200, `/api/assistant/rag/status`, 로그인, 관리자 IP 진단, 봇 이력 API(`/api/admin/assistant-bot/history`).
7. **되돌리기 기준**: 전환 후 1시간 안에 치명적 문제가 있으면 A 레코드를 카페24 IP로 되돌리고 카페24 Tomcat을 다시 켠다. 그 사이 새 서버에 쓰인 글·댓글은 덤프해서 따로 옮긴다(10.11 → 10.1 역방향이라 데이터만 INSERT로 옮긴다).

### 5단계: 안정화와 정리 (11/5~11/23)

- 매일: 서버 상태, 메모리, 봇 이력, 백업 파일 확인
- 서치콘솔 sc-domain 인증 유지 확인
- 11/20 전후: 카페24 홈 디렉터리 전체(`backup/`, `config/`, 로그) 최종 보관본을 로컬로 받고, 호스팅은 연장하지 않고 만료시킨다.
- 메모리·문서 갱신: 배포·DB 접근·런타임 관련 메모리를 새 서버 기준으로 고친다.

## 8. 로컬 리허설 (10/22~10/24)

로컬에서 새 서버 구성을 미리 재현해 위험한 지점을 먼저 확인한다. Docker가 없으므로 Homebrew로 구성한다.

1. MariaDB 10.11을 3307 포트로 띄운다(기존 MySQL 9.6은 3306 사용 중).
2. 운영 DB 덤프를 받아 10.11에 복원한다. 문자셋(utf8mb4 게시글 본문)과 정렬, 경고를 확인한다.
3. Apache Tomcat 10.1 최신 + 최신 JDK 17로 `.local-data/` 아래에 설치한다.
4. `tomcat.version`을 10.1로 바꾼 빌드(JSP 선컴파일 포함)로 WAR를 만들고 배포한다. `./gradlew clean build`가 통과하는지(테스트·WAR 풋프린트 검사 포함) 함께 본다.
5. 로컬 nginx로 카페24 프록시 헤더를 흉내 내 IP 판정과 리다이렉트를 확인한다.
6. 6번 hosts 시험 목록 중 로컬에서 가능한 항목을 돌린다.

리허설용 운영 DB 덤프와 설정 사본은 `.local-data/`(git 무시 경로)에만 두고, 끝나면 지운다.

### 8-1. 1차 리허설 결과 (2026-10-22)

작업 폴더 `.local-data/rehearsal-20261022/` (git 무시). 점검 스크립트 `check-routes.sh`.

| 항목 | 결과 |
|---|---|
| 운영 DB 덤프 | MariaDB 10.1.13에서 읽기 전용 `mysqldump --single-transaction`, 24.9MB, 71개 테이블 |
| MariaDB 10.11.19 복원 | 5초, 경고 0건. **71개 테이블 행 수가 운영과 전부 일치**. 한글 정상. 엔진 전부 InnoDB, 67개 utf8mb3 + 4개 utf8mb4 테이블 그대로 유지 |
| 런타임 | Apache Tomcat 10.1.61(SHA-512 확인) + OpenJDK 17.0.21 |
| **A. 지금 WAR(10.0.27로 빌드)를 Tomcat 10.1에서 실행** | 기동 1.6초, 경고 없음. 점검 40개 전부 통과 |
| **B. `tomcat.version`을 10.1.61로 바꿔 빌드** | 테스트 527개 통과, WAR 풋프린트 검사 통과. 같은 점검 40개 전부 통과. (`build.gradle`은 리허설 뒤 10.0.27로 되돌림) |
| 점검 범위 | 15개 게시판 목록·최신 글, 홈, `sitemap.xml`, `robots.txt`, 채팅 API, RAG·인덱스 상태, 업로드 이미지 서빙 |
| IP 판정 | nginx식 헤더(`X-Forwarded-For` + `X-Forwarded-Proto: https`, `X-Real-IP` 없음)에서 가장 오른쪽 IP를 방문자로 판정, 리다이렉트 없음. `X-Real-IP`만 있으면 301. 둘 다 있으면 https로 신뢰 |
| 관리자 API | 콘텐츠 API 토큰으로 보안 상태·봇 이력·request-echo 정상 |
| 글쓰기 | 콘텐츠 API로 이미지 포함 글 작성→읽기→삭제. 이미지 업로드 경로, 한글·이모지, 시간(DB·화면 KST) 정상. API JSON의 시간이 UTC로 나오는 건 운영과 같은 기존 동작 |
| RAG | 운영 인덱스 복사본(15MB) 로드, 373청크, 서명 일치 |
| 보안 헤더·쿠키 | HSTS, CSP, X-Frame-Options, nosniff 정상. 세션 쿠키 `Secure; HttpOnly; SameSite=Lax` (Tomcat 10.1에서도 context.xml CookieProcessor 동작) |
| 메모리 | JVM `-Xmx256m -XX:MaxMetaspaceSize=192m`에서 힙 약 90MB, Metaspace 59MB, 프로세스 RSS 약 200MB. 1GB 서버에 충분 |

**결론**: 지금 WAR 그대로도 Tomcat 10.1에서 동작하고, 10.1로 다시 빌드해도 문제없다. 전환은 10.0 빌드 WAR로 하고, `tomcat.version`은 전환이 안정된 뒤 10.1로 올린다(4절, 11/3 결정).

**남은 리허설**: nginx 설정 문법 검사(`nginx -t`)와 실제 TLS 경유 확인, 세션 로그인(브라우저), 메일 발송. 서버 구성 스크립트를 쓴 뒤 새 서버에서 진행한다.

### 8-2. 서버 구성 파일 (2026-10-22 작성)

| 파일 | 역할 |
|---|---|
| `deploy/lightsail/provision.sh` | 새 Ubuntu 24.04 서버를 한 번에 구성(다시 실행해도 안전). 시간대, 스왑 1GB, 자동 보안 업데이트(JDK 포함, 재부팅 없이 서비스만 재시작), JDK 17, Tomcat 10.1(SHA-512 확인), MariaDB 10.11, nginx, certbot, 카페24와 같은 폴더 구조, DB 계정 생성 |
| `deploy/lightsail/nginx-sc1hub.conf` | 80→443, www→루트 리다이렉트, HTTP/2, gzip. 카페24와 같은 모양의 프록시 헤더(`X-Forwarded-For` 덧붙이기, `X-Forwarded-Proto https`, `X-Real-IP` 제거) |
| `deploy/lightsail/server.xml` | Tomcat은 127.0.0.1:8645에서만 대기, 셧다운 포트 없음, 자동 배포 끔, 접근 로그 30일 |
| `deploy/lightsail/sc1hub-tomcat.service` | systemd. `sc1hub` 계정으로 실행, 메모리 부족으로 죽으면 10초 뒤 자동 재시작 |
| `deploy/lightsail/sc1hub-backup.sh` + `.service`/`.timer` | 매일 04:40에 DB 덤프·업로드 이미지·RAG 인덱스·설정을 서버에 14일 보관. 원격 복사는 rclone **암호화(crypt) 리모트만** 허용 |
| `deploy/lightsail/restore-db.sh` | 덤프를 새 DB로 복원하고 테이블별 행 수 출력. Tomcat이 켜져 있으면 거부 |
| `deploy-lightsail.sh` + `deploy/lightsail/remote-install.sh` | 새 배포. **깨끗하고 origin/main에 들어간 커밋만 배포**(리허설은 `ALLOW_UNRELEASED_COMMIT=true`), 전체 빌드·테스트, SQL 마이그레이션, 대표 경로 워밍업, nginx 경유 확인, Metaspace 점검, 실패 시 이전 WAR로 자동 복구 |

검증한 것:
- 스크립트 전부 `bash -n`, `shellcheck -S warning` 통과
- nginx 설정을 로컬 nginx로 `nginx -t` 통과하고, 리허설 Tomcat 앞에 실제로 띄워 확인: 클라이언트가 위조한 `X-Forwarded-For`·`X-Real-IP`가 무시되고 nginx가 본 주소로 판정됨, HTTP/2, www·http 리다이렉트, gzip, HSTS
- 가드 테스트 `LightsailDeployScriptTest`(5개)로 위 안전장치 문자열 고정. 전체 테스트 532개 통과

아직 검증하지 못한 것(서버에서 확인): `provision.sh` 실제 실행, systemd 유닛, 인증서 발급, 백업 스크립트와 rclone, `remote-install.sh`의 배포·복구 흐름.

## 9. 비용

| 항목 | 금액 |
|---|---|
| 라이트세일 $7 번들 | 약 10,200원/월 (부가세 포함 추정, 환율 1,325원 기준). 첫 달부터 과금: 과거 AWS 계정과 연결돼 신규 크레딧 대상이 아니고(10/30 가입 화면), 3개월 무료는 현재 공식 페이지에 없다 |
| 도메인 (Cloudflare) | 약 $11/년 |
| 인증서 | 0원 (Let's Encrypt) |
| 백업 | 0원 (Google Drive 기존 용량) |
| 카페24 연장분 | 5,500원 (11/23까지, 결제 완료) |

## 10. 정할 것

- 전환 날짜와 시각 (11/3~11/5 새벽 중)
- 점검 공지를 사이트에 미리 올릴지
- 백업 방식: 서버의 rclone에 **이 백업 폴더만 접근 가능한 권한**으로 Google Drive를 연결하는 안을 추천한다. 기존 `gdrive` 리모트는 드라이브 전체 권한이라 서버에 두지 않는다.

## 11. 이전 이후 (별도 작업)

1. Spring Boot 3.1 → 3.5 (Tomcat 10.1 위라 가능해짐)
2. JSP → Thymeleaf (33개 화면, 화면 단위 전환)
3. 필요하면 언어 변경 검토
