# SC1Hub 도배·공격 대응 절차 (2026-09-07 수립)

일반 쓰기는 2026-09-07 재개됐다. 이 문서는 (1) 상시 자동으로 동작하는 방어, (2) 징후를 어떻게 보고,
(3) 도배·공격이 시작되면 무엇을 순서대로 하는지, (4) 정리와 복구 방법을 정리한다.
관리자 화면(`/adminPage` → 보안 운영 카드)과 관리자 API(`/api/admin/security/*`)가 도구다.
API 는 관리자 로그인 세션 또는 콘텐츠 API 토큰(`Authorization: Bearer`, 쿠키 없이)으로 호출한다.

## 1. 상시 자동 방어 (배포된 상태)

| 계층 | 규칙 | 위치 |
|---|---|---|
| 로그인 필수 | 글·댓글·채팅·이미지 업로드·한줄 공략은 회원만. 비회원은 가입만 가능(스위치로 허용 가능) | `PublicWriteSecurityInterceptor` |
| 출처 검사 | 로그인 세션의 쓰기는 같은 출처(Origin/Referer)만 | 〃 |
| 평문 HTTP 거부 | 프록시가 `X-Real-IP`를 붙이는 평문 요청의 쓰기·로그인은 403(열람은 HTTPS로 301) | 〃, `PlainHttpRedirectFilter` |
| 회원 속도 제한 | 전체 10회/분, 글 5회/10분(가입 24시간 미만 2회), 댓글 20회/10분(신규 6회), 채팅 30회/분 + 2초 간격 | 〃, `ChatRoomService` |
| IP 속도 제한 | 20회/분(프록시 헤더로 클라이언트를 확인한 요청만) | 〃 |
| 가입 제한 | IP당 3건/시간, 전체 30건/시간, 숨김 필드(honeypot) 채우면 거부, ID 형식·이메일 형식 서버 검증 | 〃, `MemberController`, `MemberServiceImpl` |
| 중복 내용 차단 | 같은 글 10분·같은 댓글 5분 안 재등록 거부(전역), 같은 채팅 60초(작성자별) | `DuplicateContentGuard` |
| 로그인 보호 | 계정 5회 실패 5분 잠금, IP 30회 실패 15분 잠금 | `LoginAttemptGuard` |
| 자동 제재 | 거부(속도 제한·비회원 쓰기·로그인 실패·봇 필드 등) 10분 내 IP 30회 → IP 차단 1시간, 회원 20회 → 뮤트 1시간. 24시간 내 재발 시 24시간 | `OffenderTracker` |
| 제재 효력 | 제재 대상은 채팅뿐 아니라 모든 쓰기·로그인이 막힌다. DB(`chat_sanction`)에 저장돼 재시작 후에도 유지 | `ChatModerationService` |
| 별명 보호 | 과거 글 작성자 별명은 다른 계정이 취득 불가, 글보다 늦게 가입한 계정은 작성자 아님 | `WriterNicknameGuard` |
| 콘텐츠 정화 | 본문 HTML 허용 목록, 회원정보 마크업 금지, 출력 이스케이프 | `PostContentSanitizer` 등 |

IP 는 프록시가 덧붙인 `X-Forwarded-For` 의 가장 오른쪽 값이다(왼쪽은 위조 가능). 헤더가 없는 직접 접속
(헬스체크 등)은 IP 기준 제한·차단 대상이 아니다. 사설·루프백 주소는 어떤 경로로도 차단하지 않는다.

## 2. 징후 확인

- 관리자 화면 보안 운영 카드: 회원/비회원 쓰기 상태, 활성 제재 수, **최근 10분 거부 횟수**, 기동 후 자동 차단 수.
  거부 횟수가 평소(0~수 회)와 달리 수십 회 이상이면 공격 중이다.
- `GET /api/admin/security/status` 로 같은 값을 JSON 으로 본다. `GET /api/admin/security/sanctions` 는 활성 제재 목록.
- 채팅 도배는 채팅창 관리자 명령(`/sanctions`)과 `/api/admin/chat/sanctions` 로도 본다.
- 운영 로그(`catalina.out`)의 `자동 제재 적용`, `HTTP→HTTPS 리다이렉트가 분당 … 넘어` 경고를 확인한다.

## 3. 공격 진행 중 대응 순서

1. **즉시 얼리기**: 보안 운영 카드의 "회원 쓰기 차단(긴급)" 또는
   `POST /api/admin/security/public-writes {"enabled":false}`. 글·댓글·채팅·이미지·가입·회원정보 수정이
   즉시 503 으로 막힌다(열람·로그인·관리자 기능은 유지). 재배포가 필요 없다.
2. **대상 차단**: 카드의 제재 추가로 IP 차단 또는 회원 뮤트(분 단위, 비우면 영구). 어떤 IP 인지 모르면
   "내 접속 정보"로 헤더 구조를 확인하고, 채팅이면 채팅 관리자 명령으로 닉네임 기준 제재.
   자동 제재가 이미 걸렸는지 활성 제재 목록에서 확인한다.
3. **증거 보존**: 정리 전에 대상 글·댓글·채팅·회원 식별자를 기록한다(콘텐츠 API `list`/`read`,
   관리자 회원 검색). 삭제한 항목은 복구되지 않는다.
4. **정리**: 글은 `scripts/sc1hub-content-api.sh delete BOARD POST_NUM --confirm`, 댓글·채팅은 관리자 화면·채팅
   관리자 명령, 계정은 관리자 회원 목록의 탈퇴(추천 기록까지 정리하고 세션을 끊는다). 대량이면
   `.local-data/incident-20260907/member-cleanup-*` 방식처럼 목록을 먼저 만들고 순차 실행한다.
5. **풀기**: "회원 쓰기 재개" 또는 `{"enabled":true}`. 제재는 필요한 만큼 유지한다.

## 4. 복구 후 점검

- 스위치는 **재시작하면 `application.properties` 기본값**(회원 쓰기 허용, 비회원 쓰기 차단)으로 돌아간다.
  장기간 차단이 필요하면 프로퍼티를 바꿔 배포한다.
- 잘못 걸린 제재는 활성 제재 목록에서 해제한다. 자동 제재는 `created_by=auto` 로 구분된다.
- 정상 이용자가 429 를 자주 본다면 임계치를 `PublicWriteSecurityInterceptor` 상수에서 조정하고 배포한다.

## 5. 환경 변화 시 재확인

- 카페24 프록시 헤더 구성이 바뀌면 IP 판정(`X-Forwarded-For` 가장 오른쪽)과 평문 HTTP 판정(`X-Real-IP`)이
  먼저 영향을 받는다. "내 접속 정보"(`request-echo`)로 `forwardedFor`, `realIp`, `resolvedClientIp`,
  `forwardedClientTrusted` 를 확인한다. `forwardedClientTrusted` 가 false 면 IP 제한·차단이 꺼진 상태다.
- HTTPS 리다이렉트가 분당 300회를 넘으면 10분간 자동 중지되고 경고가 남는다. 프록시 변경이 원인일 수 있다.
