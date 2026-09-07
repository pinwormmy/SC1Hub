# SC1Hub 보안 강화 — 2026-09-07

2026-09-07 침해(무단 XSS·도배·권한 우회 시험) 대응의 후속으로, 로컬 재감사 보고서
(`.local-data/security-audit-20260907/security-audit-report.md`, 미추적)의 지적 사항을
코드로 반영했다. 아래는 커밋에 포함된 변경과, 코드 밖에서 별도로 진행해야 하는 운영 조치다.

전체 테스트 349개 통과(신규 10개 추가), `./gradlew clean build` 통과(JSP 프리컴파일 0 오류,
WAR 풋프린트 검증 통과). 임시 쓰기 제한(`sc1hub.security.public-writes-enabled=false`)은 유지한다.

## 코드로 반영한 조치

### F01 — 관리자·회원 화면 XSS (P1)
- 회원 목록·검색어·회원정보·별칭 사전 화면의 모든 동적 출력을 문맥에 맞게 이스케이프했다
  (`adminPage.jsp`, `modifyMemberByAdmin.jsp`, `myPage.jsp`, `modifyMyInfo.jsp`,
  `adminAliasDictionary.jsp`). 텍스트·속성은 `<c:out>`으로 감쌌다.
- 인라인 이벤트 핸들러에 EL을 끼워 넣던 부분(`onclick="...'${member.id}'..."` 등)은
  `data-*` 속성 + 위임 리스너로 바꿔 스크립트 삽입 경로를 제거했다.
- 서버측 입력 검증을 추가했다: 별명·이름·이메일·연락처의 길이 제한과 `<`, `>`·제어문자 금지
  (`MemberServiceImpl.validateProfileFields`), 관리자 회원수정의 등급 범위(1~3) 검증.
- 회원정보 수정(`/submitModifyMyInfo`)을 임시 쓰기 차단 대상에 포함시켜, 차단 중 일반 회원이
  이 경로로 마크업을 저장하지 못하게 했다.

### F02 — 보안 헤더·쿠키 보호 (P1, 일부는 앞단 프록시 몫)
- 모든 응답에 보안 헤더를 붙이는 `SecurityHeadersFilter`를 추가했다: `X-Content-Type-Options`,
  `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy`, `Content-Security-Policy`(frame-ancestors·
  base-uri·object-src·upgrade-insecure-requests), `Permissions-Policy`, `Strict-Transport-Security`.
  관리자·회원 전용 페이지에는 `Cache-Control: no-store`.
  - CSP는 인라인 스크립트와 애드센스·유튜브 임베드를 쓰는 사이트라 `script-src`를 걸지 않았다.
    nonce 기반 CSP로 스크립트까지 잠그는 것은 후속 과제.
- 세션 쿠키: `META-INF/context.xml`의 `<CookieProcessor sameSiteCookies="lax"/>`로 JSESSIONID 포함
  전 쿠키에 SameSite를 적용하고, `Secure`/`HttpOnly`는 `ServerConfig`의 `SessionCookieConfig`
  초기화로 컨테이너 수준에서 지정한다. `server.servlet.session.cookie.*` Boot 프로퍼티는 내장 Tomcat
  전용이라 카페24 외부 Tomcat 응답에는 반영되지 않는 것이 1차 배포에서 확인됐다(프로퍼티는
  로컬 평문 HTTP 에서 Secure 를 끄는 스위치로만 쓴다). 방문자·게시글 조회 쿠키에도
  `Secure`/`HttpOnly`를 적용했다.
- 로컬(평문 HTTP 8082)에서 로그인이 유지되도록 `run-local.sh`에서만 secure=false로 덮어쓴다.

### F04(일부) — BCrypt 72바이트 절단 (CVE-2025-22228) 완화
- 새/변경 비밀번호를 UTF-8 72바이트 이내로 제한했다(`MemberServiceImpl.validateNewPassword`).
  한글 등 멀티바이트가 많아 글자 수는 64자 이내여도 72바이트를 넘는 경우를 막아, 서로 다른 긴
  비밀번호가 같은 것으로 처리되는 문제를 신규 비밀번호에서 차단한다.

### F05 — 세션 고정·권한 회수
- 로그인·회원가입 성공 시 `request.changeSessionId()`로 세션 ID를 교체(세션 고정 방지).
- `MemberSessionRegistry`(회원 ID→세션) + `MemberSessionListener`를 추가해, 관리자의 회원 등급
  변경·탈퇴, 회원 본인 탈퇴, 비밀번호 변경 시 기존 세션을 즉시 회수한다(비밀번호 변경은 현재
  세션은 유지하고 다른 기기 세션만 회수). 리스너는 WAR에서도 확실히 등록되도록
  `ServerConfig`에서 `ServletListenerRegistrationBean`으로 명시 등록.

### F06 — 무인증 비밀번호 재설정·아이디 찾기 엔드포인트 제거
- UI에서 이미 제거된(메일 안내로 대체) `POST /findPassword`, `POST /findId` 서버 엔드포인트가
  살아 있어, 인증 없이 임의 계정의 비밀번호를 임시값으로 재설정하거나 메일을 반복 발송할 수
  있었다. 두 엔드포인트와 그 뒤의 서비스·매퍼 메서드, 사용처가 사라진 메일 발송 스택
  (`EmailService`/`EmailServiceImpl`/`EmailConfig`)을 함께 제거했다. GET 안내 페이지는 유지.

### F08 — 비회원 글 비밀번호 GET 오라클 제거
- `GET .../modifyPost`에서 `guestPassword` 쿼리로 정답/오답을 구분하던 경로를 없앴다. 이제
  비밀번호 검증은 `POST .../verifyGuestPostPassword`만 담당하고, 검증 성공이 서버 세션에 수정
  권한을 부여한다. 수정 화면은 세션 권한으로만 열리며 URL에 비밀번호를 싣지 않는다.
  POST 검증은 임시 쓰기 차단·분당 제한을 그대로 받는다.

### F09 — 로그인 실패 기록 메모리 상한
- `LoginAttemptGuard`가 상한(10,000) 도달 시 만료 항목 회수 후에도 가득 차면 가장 오래된 항목을
  밀어내 실제 크기를 상한 이내로 유지하도록 했다. 키 길이도 100자로 제한.

### F10 — 토큰 인증의 공유 세션 노출 차단
- 콘텐츠 API 토큰 인증(`ContentApiAdminSessionFilter`)이 기존(브라우저·공유) 세션에 관리자
  권한을 절대 싣지 않도록 바꿨다. 토큰 호출은 쿠키 없이 오므로, 요청 전용 임시 세션을 새로
  만들어 권한을 담고 요청이 끝나면 폐기한다. 같은 세션을 공유하는 동시 요청이 토큰 없이 관리자
  권한을 보던 경로가 사라진다.

### F11 — 이미지 디코딩 메모리
- 큰 이미지를 전체 해상도로 디코딩하던 것을 서브샘플링으로 축소해 읽도록 했다(디코딩 결과가
  최종 크기의 약 2배를 넘지 않음). 소스 픽셀 상한을 24MP로 낮추고, 동시 디코딩 수를 2로 제한.

### F12 — 내부 경로 노출 축소
- 비로그인 RAG/인덱스 상태 API가 내부 절대 경로(`indexPath`)를 반환하던 것을 응답에서 제외했다
  (`@JsonIgnore`). 나머지 상태 필드는 그대로 두어 공개 상태 확인·배포 검증에는 지장이 없다.

### F03 — 레거시(평문) 비밀번호 서버 내 일괄 승격 (2차 반영)
- `LegacyPasswordMigration`이 기동 직후(ApplicationReadyEvent) BCrypt 접두사가 아닌 행을 읽어 그 값을
  해시해 제자리에 저장한다. 평문 행의 저장값은 곧 사용자가 입력하는 비밀번호이므로 로그인 검증은 그대로
  통과하는 손실 없는 변환이다. 평문은 기록·전송하지 않는다.
- 안전장치: `$2` 행은 건드리지 않음, 읽은 값과 같을 때만 갱신하는 낙관적 UPDATE(동시 로그인 승격과
  충돌 없음), 72바이트 초과 평문은 건너뛰고 경고, 어떤 예외도 기동을 막지 않음. 모든 행이 승격되면
  이후 실행은 no-op. `sc1hub.security.legacy-password-migration-enabled=false`로 끌 수 있다.
- 관리자 API `GET /api/admin/security/status`(남은 레거시 수·마지막 실행 요약),
  `POST /api/admin/security/legacy-passwords/migrate`(재실행). 로그인 시 투명 승격 분기는 그대로 둔다.

### F07 — 해제된 별명을 통한 과거 글 관리 권한 취득 차단 (2차 반영)
- 불변 회원 ID 컬럼을 19개 게시판 테이블에 추가하고 과거 글을 매핑하는 스키마 이전은 매핑 불확실성이
  있어 이번에 하지 않았다. 대신 같은 보안 속성을 코드로 확보했다.
- `WriterNicknameGuard`: 가입·별명 변경·관리자 회원수정 시, 현재 회원 중 유일하지 않거나 과거 회원
  게시글 작성에 쓰인 별명(funboard 비회원 글 제외)은 다른 계정이 취득할 수 없다.
- 가입일 가드: 작성자 별명이 같아도 글보다 늦게 가입한 계정은 작성자로 보지 않는다(수정·삭제 경로 모두,
  가입일이 없는 옛 계정은 기존대로). 별명 재사용이 이미 일어난 과거 상태까지 방어한다.

### F02 최종 — 평문 HTTP 요청의 HTTPS 리다이렉트 (3차 반영)
- 카페24 JSP 호스팅 관리 화면(서버환경 변경·인증서관리·도메인 연결관리)에는 HTTPS 자동 전환 옵션이
  없다(인증서 페이지도 "소스 수정"으로 안내). 앱에서 구현해야 한다.
- 실측(임시 진단 엔드포인트, 확인 후 제거): 앞단 프록시는 `X-Forwarded-Proto`를 설정하지 않고 클라이언트
  헤더를 그대로 넘긴다. 다만 **평문 HTTP 요청에만 `X-Real-IP`가 붙고, TLS를 거친 HTTPS 요청에는
  `X-Forwarded-For`만 붙는다**(HTTP/1.0·1.1·2, UA 무관하게 동일).
- `PlainHttpRedirectFilter`: GET/HEAD + 스킴 http + `X-Real-IP` 존재 + 정식 호스트일 때만 `https://sc1hub.com`
  으로 301(쿼리 보존, `Cache-Control: no-store`). 루프 방어 세 겹: ① 10분 마커 쿠키(Secure/HttpOnly)가
  있으면 재리다이렉트 안 함 ② `X-Forwarded-Proto: https`가 오면 신뢰해 통과 ③ 분당 300회 초과 시 10분간
  중지+경고 로그. 내부 헬스체크(127.0.0.1)와 쓰기 요청은 대상이 아니다. `sc1hub.security.https-redirect-enabled`
  로 끌 수 있고 로컬은 `run-local.sh`가 끈다. HSTS는 이미 모든 응답에 나간다.

### 쓰기 재개와 도배 방지·자동 차단 (4차 반영, 커밋 bcca33e)
- 회원 쓰기를 재개했다(`public-writes-enabled=true`). 비회원 쓰기는 기본 차단(`guest-writes-enabled=false`)이며
  두 값 모두 관리자 보안 API/화면에서 재배포 없이 즉시 바꿀 수 있다(재시작 시 프로퍼티 기본값 복귀).
- 클라이언트 IP 는 프록시가 덧붙인 `X-Forwarded-For` 의 **가장 오른쪽 값**으로 판정한다(왼쪽은 위조 가능,
  직접 접속은 신뢰하지 않음). 이로써 IP별 제한·차단·조회수·방문자 집계가 프록시 공용 주소가 아니라
  이용자별로 동작한다. 사설·루프백 주소는 차단 대상에서 제외한다.
- 쓰기 관문(`PublicWriteSecurityInterceptor`): 출처 검사 → 평문 HTTP 쓰기·로그인 거부 → 제재 확인 →
  쓰기 스위치 → 회원 등급별 속도 제한(전체 10/분, 글 5/10분·신규 2, 댓글 20/10분·신규 6, 채팅 30/분, IP 20/분)
  → 가입 제한(IP 3/시간, 전체 30/시간). 모든 거부는 `OffenderTracker` 에 누적된다.
- 자동 제재: 10분 내 거부가 IP 30회면 1시간 IP 차단, 회원 20회면 1시간 뮤트, 24시간 내 재발 시 24시간.
  제재는 기존 `chat_sanction` 에 저장되어 재시작 후에도 유지되고 채팅뿐 아니라 모든 쓰기·로그인에 적용된다.
- 중복 내용 차단(`DuplicateContentGuard`): 같은 글 10분·댓글 5분(전역), 같은 채팅 60초(작성자별).
- 가입 보강: 숨김 필드(honeypot), ID 형식(`^[a-z][a-z0-9]{3,19}$`)·이메일 형식 서버 검증, IP별 로그인 실패 잠금.
- 관리자 보안 운영: `GET/POST /api/admin/security/{status,public-writes,guest-writes,sanctions,request-echo}` 와
  `/adminPage` 의 "보안 운영" 카드(스위치, 제재 추가/해제, 접속 헤더 확인). 대응 절차는
  [anti-spam-runbook.md](anti-spam-runbook.md).
- 게시판 권한 버그 수정: 관리자 전용 예외 목록에 대소문자 혼용 이름만 있어 소문자 정규 URL 에서는 회원이
  403 을 받던 `promotionboard`·`supportboard`·`videolinkboard` 를 회원 작성 가능하게 바로잡았다.

## 코드 밖에서 진행해야 하는 운영 조치 (미완)

1. **지원 종료 런타임 이전 (F04) — 보류 결정(2026-09-07)**: 운영 Tomcat 10.0.20은 지원 종료 라인이다.
   카페24 "Tomcat JSP호스팅 비즈니스"의 서버환경 변경 메뉴에는 Tomcat 10.0.x(JDK 17/11)와 8.5.x만 있어
   이 상품 안에서는 지원 라인으로 올릴 수 없다. 지원되는 런타임(Tomcat 10.1+/11)은 다른 상품군(가상서버 등)
   이나 다른 호스팅으로의 이전이 필요하며, Boot 3.2+/JSTL 3.0 URI 등 호환성 검증을 동반하는 별도 과제다.
   spring-security-crypto 6.1.9의 완전한 대응도 이 이전과 함께 최신 라인으로 올려야 한다.

## 완료된 운영 조치

- **사건 당일 대량 가입 계정 정리 (2026-09-07)**: 회원 530명 중 472명이 2026-09-06 하루에 가입했고
  (평소 월 1명 수준) 재감사의 실행 태그 패턴 회원 2건도 이 계정군에 속했다. 운영자 승인 후 공격
  시간대(12:04~23:31) 가입 471건을 관리자 삭제 API로 삭제했고, 공격 전 05:51 가입 1건은 일반
  이용자로 판단해 남겼다. 회원 총수 529→58. 원본 식별자·별명·가입시각과 건별 결과는 미추적 증거
  폴더에 보존했다.
- **회원 삭제 버그 수정**: `*_recommend.user_id`가 `member.id`를 삭제 연쇄 없이 참조해, 추천을 한 번이라도
  한 회원은 관리자 삭제·본인 탈퇴가 DB 오류로 실패했다(위 정리에서 10건이 이에 걸림). 회원 삭제 시
  전 게시판의 추천 기록을 지우고 추천 수를 재계산한 뒤 회원 행을 지우도록 고쳤다(단일 트랜잭션).
- **레거시 비밀번호 승격 결과**: 배포 직후 기동 시 53건 전부 승격, 실패 0, 남은 평문 0
  (`GET /api/admin/security/status`로 확인).

## 참고
- 미추적 `spring-boot-starter-mail` 의존성은 메일 스택 제거로 이제 사용되지 않는다(런타임 무해).
  풋프린트를 더 줄이려면 의존성과 `spring.autoconfigure.exclude`의 Mail 항목을 함께 제거하면 된다.
