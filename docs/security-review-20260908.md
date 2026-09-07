# SC1Hub 전체 보안 재검토 — 2026-09-08

09-07 강화([security-hardening-20260907.md](security-hardening-20260907.md)) 이후 사이트 전체를 다시 정적 감사했다.
다섯 갈래(라우팅·인증·세션 / 게시판·업로드·SQL / 채팅·AI·관리자 API / 뷰·JS·정화기 / 설정·시크릿·의존성·배포)로
나눠 전수 열람했고, 확인된 항목만 코드로 반영했다. 09-07 항목은 재검증만 했으며 깨진 곳은 없었다.

## 확인된 취약점과 조치

### V1 — 인터셉터 경로 패턴 우회 (High, 실증)
- 위치: `WebConfig.addInterceptors` 의 `/**/writePost`, `/**/submitPost`, `/**/submitModifyPost`, `/**/modifyPost/**`, `/**/deletePost/**`, `/**/movePost`.
- 원인: Spring 6 `PathPatternParser` 는 `**` 가 가운데 오는 패턴을 거부한다. `MappedInterceptor` 는 그 예외를 삼키고
  **원본(인코딩된) URI 기준 AntPathMatcher** 로 후퇴하는데, 핸들러 매핑은 디코딩된 경로로 열린다. 그래서
  `POST /boards/noticeboard/submit%50ost` 는 핸들러(`submitPost`)에 닿으면서 AdminInterceptor·BoardLvInterceptor 를 건너뛰었다
  (spring-webmvc 6.0.21 실물 jar 로 재현). `movePost` 만 별도 등록된 `/boards/*/movePost` 덕에 안전했다.
- 실제 영향: 컨트롤러 안의 2차 검사(`canWrite`, `hasManagePermission`, `isPostOwner`) 덕분에 익명 글쓰기까지는 막혔지만,
  관리자 전용 게시판의 편집 화면 노출, 회원이 관리자 전용 게시판에 옮겨진 자기 별명 글을 수정·삭제, 회원 전용 게시판의 로그인
  검사 무력화가 가능했다. 컨트롤러 검사 하나만 무너지면 곧바로 공개 쓰기가 되는 구조였다.
- 조치: 모든 패턴을 `/boards/*/…` 형태로 교체. `WebConfig.patterns()` 가 등록 시점에 `PathPatternParser` 로 해석되는지
  검증해 실패하면 **기동을 거부**한다. `SecurityRouteRegistrationTest.percentEncodedBoardWriteRoutesStayProtected` 와
  `WebConfigPatternTest` 가 인코딩 변형·매트릭스 파라미터·후퇴 금지를 고정한다.

### V2 — 봇 발행 글·댓글의 공유 비밀번호 (Medium)
- 위치: `AssistantBotService.buildPostForPublish`/댓글 발행, `AssistantBotProperties.publishGuestPassword`.
- 문제: 봇이 올린 모든 글·댓글이 하나의 비회원 비밀번호로 보호됐고, 코드 기본값은 `bot-approved`, 실제 값은 2026-03 에
  공개 GitHub 이력(`aff634b`)에 커밋된 적이 있다. 값을 아는 누구나 봇 글 전체를 수정·삭제할 수 있었다.
- 조치: 봇 글·댓글은 행마다 `GuestPasswordHasher.newRandomSecret()` 를 받아 해시로 저장한다(아무도 모르는 값 — 봇 글은
  관리자만 관리). 프로퍼티는 바인딩만 되고 무시된다(`@Deprecated`). 기존 봇 행은 `GuestPasswordMigration` 이 페르소나
  이름 기준으로 무작위 값으로 다시 잠근다. 외부 설정 파일의 옛 값은 지워도 된다.

### V3 — 회원 HIGH 제재 우회 (Medium)
- 위치: `OffenderTracker.attackDetected`.
- 문제: 회원이 스크립트 등 HIGH 내용을 올리면 회원만 24시간 뮤트되고 IP 는 기록되지 않았다. 로그아웃한 뒤 같은 주소에서
  비회원 글·댓글·채팅·신규 가입으로 그대로 이어 쓸 수 있었다.
- 조치: 회원 뮤트와 함께 신뢰할 수 있는 공인 주소를 24시간 `BLOCK_IP` 한다(비회원 경로와 동일). 사설·프록시 주소는 여전히
  차단하지 않는다. `OffenderTrackerTest` 갱신.

### V4 — 관리자 운영 화면의 AI 생성 제목 XSS 싱크 (Medium)
- 위치: `adminOps.jsp` 의 `${item.draftTitle}` 등 raw EL 28곳.
- 문제: 초안 제목은 LLM 출력이 그대로 `history.draftTitle` 에 저장되고(발행 경로만 이스케이프), 프롬프트에는 비회원이 쓸 수
  있는 funboard 글·댓글이 들어간다. 프롬프트 주입으로 제목에 마크업을 넣으면 관리자가 `/adminPage/ops` 를 열 때 실행된다
  (CSP 에 script-src 없음). 현재 설정에선 글 초안 경로가 Gemini 호출에서 실패해 잠재적이지만 싱크 자체는 실재했다.
- 조치: 해당 화면의 동적 출력 전부 `<c:out>`.

### V5 — `GET /logout` CSRF (Low)
- 문제: 세션 쿠키가 `SameSite=Lax` 라 외부 페이지의 최상위 이동으로 강제 로그아웃이 가능했다.
- 조치: `Sec-Fetch-Site: cross-site`(없으면 Referer 가 외부 호스트)면 세션을 끊지 않고 `/` 로만 보낸다. 사이트 링크는
  same-origin, 주소창 입력·북마크는 `none` 이라 정상 동작. 회원 뮤트가 걸린 이용자도 로그아웃할 수 있어야 하므로 POST 로
  바꾸지 않았다(쓰기 관문의 제재 검사와 충돌).

### V6 — 계정 존재 오라클 (Low)
- 문제: `/isUniqueId`(화면에서 미사용), `/checkUniqueId`, `/checkUniqueEmail`(미사용) 이 무인증 GET 으로 아이디·이메일
  가입 여부를 알려줬다. 로그인 실패 메시지는 일부러 뭉뚱그렸는데 이 경로가 같은 정보를 내줬다.
- 조치: `/isUniqueId`·`/checkUniqueEmail` 제거. `/checkUniqueId` 는 ID 형식(`^[a-z][a-z0-9]{3,19}$`)을 먼저 검사하고,
  `/checkUniqueId`·`/checkUniqueNickName` 은 IP당 30회/10분(초과 시 429 + 거부 누적). 가입 제출 단계의 중복 검사는 그대로.

### V7 — 비회원 글·댓글 비밀번호 평문 저장 (Low)
- 문제: `funboard.guest_password`, `*_comment.password` 가 평문이었고 `Objects.equals` 로 비교했다.
- 조치: `GuestPasswordHasher`(SHA-256 사전 해시 → BCrypt; 100자 입력에서도 72바이트 절단 없음). 저장은 `BoardServiceImpl.submitPost`/
  `addComment` 에서, 검증은 `matches` 로(BCrypt 형식이 아닌 옛 행은 상수 시간 비교로 계속 통과). `GuestPasswordMigration` 이
  기동 후 데몬 스레드에서 평문 행을 표당 1,000행씩 승격하고, 현황은 `GET /api/admin/security/status` 의 `guestPasswordMigration`,
  재실행은 `POST /api/admin/security/guest-passwords/migrate`.

### V8 — 콘텐츠 API 스크립트의 토큰 argv 노출 (Low)
- 조치: `scripts/sc1hub-content-api.sh` 가 토큰을 `curl -H` 인자가 아니라 0600 임시 헤더 파일(`-H @file`)로 넘기고 종료 시 지운다.

### V9 — 죽은 `searchForm.jspf` 의 따옴표 없는 반사 싱크 (Low)
- `<input name="keyword" value=${page.keyword}>` 가 포함되지 않은 조각에 남아 있었다. 파일 삭제.

### V10 — `sc-terminal.js` 의 약한 `sanitizeHtml` (Low)
- 시스템 메시지(관리자 인덱스 API 응답 본문 포함)를 script/style 만 떼고 `innerHTML` 에 넣었다. `escapeHtml` 로 교체.

## 예방적 보강(취약점 실증은 없으나 함께 반영)
- **읽기 시 본문 정화**: `BoardServiceImpl.enrichPostContent` 가 화면에 나가는 본문을 다시 `PostContentSanitizer` 에 통과시킨다.
  09-07 이전 침해 시기의 행이나 정화기를 거치지 않은 경로의 행을 막는다. 옛 글 서식이 깨지면
  `sc1hub.security.sanitize-post-content-on-read=false` 로 끄고 해당 글을 수정한다.
- **재인증**: 내 정보 수정(`/submitModifyMyInfo`)·탈퇴(`/deleteMyAccount`)는 현재 비밀번호(`currentPw`)를 확인한다. 실패는
  로그인 잠금 카운터에 누적. 탈취된 세션만으로 비밀번호·이메일을 바꿔 계정을 영구 장악하는 경로를 막는다.
- **관리자 판정 통일**: `BoardController`/`BoardServiceImpl` 의 `id == "admin"` 예외를 없애고 인터셉터와 같이 등급 3 하나로.

## 안전하다고 확인한 것(요약)
SQL 은 `${}` 가 게시판 테이블명뿐이고 모든 호출 경로가 `BoardTitleNormalizer` 허용 목록을 거친다. 정화기는 우회 페이로드를
찾지 못했다. 댓글·채팅·최신글은 `textContent`, 관리자·회원 화면은 `<c:out>`, JSON-LD 는 사전 이스케이프. 업로드는 ImageIO
재인코딩(JPEG/PNG 만)과 경로 고정. 토큰 필터는 기존 세션에 권한을 싣지 않고 상수 시간 비교. 세션 고정·회수·쿠키 속성 유지.
SSRF 대상 호스트 고정. 액추에이터 없음, CORS 없음, TLS 검증 해제 없음, 스택트레이스 노출 없음, 추적 파일에 시크릿 없음,
배포 스크립트는 비밀번호를 `MYSQL_PWD` 로만 다룬다.

## 의존성 권고(코드 변경 없음)
- Tomcat 10.0.20(EOL): CVE-2023-45648/46589(트레일러 파싱 → 요청 스머글링)가 미수정. 프록시가 덧붙이는 XFF 를 신뢰하는 구조라
  스머글링은 IP 제한·제재를 흔들 수 있다. 상품군/호스팅 이전은 보류 결정 유지.
- MySQL Connector/J 8.0.33: CVE-2023-22102(악의적·중간자 MySQL 서버). 같은 호스팅 안이라 낮음. 별도 배포 검증 후 8.4 로.
- spring-security-crypto 6.1.9: 회원 비밀번호는 72바이트 상한, 비회원은 사전 해시라 CVE-2025-22228 영향 없음.
- `spring-boot-starter-mail` 은 미사용. 제거 가능.

## 배포 후 검증
```bash
# 인코딩 우회: 예전엔 200(알림 페이지), 이제는 관리자 전용 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Origin: https://sc1hub.com' 'https://sc1hub.com/boards/noticeboard/submit%50ost'
# 외부 유도 로그아웃 무시: 302 + 세션 유지(로그인 상태 브라우저에서 확인)
curl -s -o /dev/null -w '%{http_code}\n' -H 'Sec-Fetch-Site: cross-site' https://sc1hub.com/logout
# 제거된 오라클: 404
curl -s -o /dev/null -w '%{http_code}\n' 'https://sc1hub.com/checkUniqueEmail?email=a@b.c'
```
관리자 로그인 후 `/api/admin/security/status` 에서 `guestPasswordMigration` 의 `upgraded`/`rekeyedBotRows`/`truncated` 를 본다.
`truncated=true` 면 `POST /api/admin/security/guest-passwords/migrate` 를 남은 행이 없을 때까지 반복한다.
비회원 글 수정·삭제와 댓글 삭제를 실기기에서 한 번 시험한다(옛 평문 행·새 해시 행 모두 통과해야 한다).

## 변경 파일
코드: `WebConfig`, `OffenderTracker`, `GuestPasswordHasher`(신규), `GuestPasswordMigration`(신규), `BoardMapper`(.java/.xml),
`BoardServiceImpl`, `BoardController`, `AssistantBotService`, `AssistantBotProperties`, `SecurityAdminController`, `MemberController`.
뷰·정적: `adminOps.jsp`, `modifyMyInfo.jsp`, `searchForm.jspf`(삭제), `sc-terminal.js`. 설정·문서·스크립트: `application.properties`,
`README.md`, `docs/anti-spam-runbook.md`, `scripts/sc1hub-content-api.sh`. 테스트 460건 통과(신규 `WebConfigPatternTest`,
`GuestPasswordHasherTest`, `GuestPasswordMigrationTest` 포함).
