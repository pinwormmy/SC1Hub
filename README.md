# SC1Hub

스타크래프트1 전문 공략사이트.

## 운영 디스크 정리

저용량 JSP/Tomcat 호스팅에서는 Tomcat 로그와 배포 WAR 백업이 누적되어 디스크를 채울 수 있습니다.
`scripts/cleanup-hosting-storage.sh`는 Tomcat 디렉터리의 디스크 사용률이 기본 90% 이상일 때만 아래 항목을 정리합니다.

- `$TOMCAT_DIR/logs`의 `*.log`, `*.out`, `*.txt` 파일 truncate
- `$TOMCAT_DIR/webapps`의 오래된 `*.war.bak.*` 백업 삭제, 기본 최신 2개 보관
- `$TOMCAT_DIR/temp`, `$TOMCAT_DIR/work` 하위 임시 파일 삭제

운영 서버에서 예행 실행:

```bash
DRY_RUN=true TOMCAT_DIR=/home/hosting_users/sc1hub/tomcat bash scripts/cleanup-hosting-storage.sh
```

cron 예시, 10분마다 확인하고 90% 이상일 때만 정리:

```cron
*/10 * * * * TOMCAT_DIR=/home/hosting_users/sc1hub/tomcat THRESHOLD_PERCENT=90 bash /home/hosting_users/sc1hub/scripts/cleanup-hosting-storage.sh >> /home/hosting_users/sc1hub/storage-cleanup.log 2>&1
```

## Terminal AI Assistant

터미널에서 게시판 바로가기용 숫자 명령을 제외한 검색어 및 질문 프롬프트로, 사이트 게시물 기반의 간단한 답변과 관련 게시물 링크를 제공합니다. 기본 검색 LLM 제공자는 Anthropic(`claude-haiku-5-5`)이며, 설정에 따라 OpenAI/Gemini로 전환할 수 있습니다. (RAG 임베딩은 Gemini 임베딩 모델을 사용합니다.)

## 로컬 실행 테스트

`run-local.sh`는 로컬 화면 확인과 스크린샷 촬영용 안전 실행 경로입니다. 공용 `application.properties`를 수정하지 않고 `local` 프로필을 명시하며, 마지막 오버라이드 파일로 LLM/임베딩 라이브 호출, assistant bot, 자동 발행, RAG 자동 업데이트를 강제로 끕니다.

1. 로컬 설정 파일을 만듭니다.

```bash
cp src/main/resources/application-local.example.properties src/main/resources/application-local.properties
```

2. `application-local.properties`의 `spring.datasource.*`를 로컬 MySQL DB로 수정합니다.

`run-local.sh`는 기본적으로 `localhost`, `127.0.0.1`, `[::1]`, `0.0.0.0` MySQL URL만 허용합니다. 외부 테스트 DB가 꼭 필요할 때만 `SC1HUB_ALLOW_NONLOCAL_DB=true`를 명시합니다.

3. 필요한 SQL을 로컬 DB에 먼저 적용한 뒤 실행합니다.

```bash
./run-local.sh
```

서버는 `http://localhost:8082`에서 실행됩니다.

운영(2026-11-04부터 AWS 라이트세일)은 `deploy/lightsail/provision.sh`가 만든 Tomcat `setenv.sh`가 `SPRING_PROFILES_ACTIVE=online`을 지정하고, 배포는 `deploy-lightsail.sh`로 합니다. `deploy.sh`는 은퇴한 카페24 호스트용입니다. 로컬/운영 전환을 위해 `application.properties` 마지막 줄을 수동으로 바꾸지 않습니다.

### 로컬 샘플 데이터

운영 DB 전체를 로컬 더미데이터로 복제하지 않습니다. 공략게시판 테스트 데이터가 필요하면 스키마를 먼저 맞춘 뒤 공개 공략게시판 게시글 샘플만 가져옵니다.

```bash
./sync-local-db-schema.sh
POST_LIMIT=20 ./sync-local-sample-data.sh
```

`sync-local-sample-data.sh` 기본 동작:

- 대상: 공략게시판 게시글 테이블, `teamplayguideboard`, `tipboard`, `board_list`
- 제외: `member`, 조회 IP 테이블, 추천 사용자 테이블, assistant bot history, 운영 설정
- 로컬 적재 후 게시글 `writer`는 `SC1Hub`로 치환
- 댓글은 기본 제외. 필요할 때만 `INCLUDE_COMMENTS=true`를 주며, 적재 후 `id`, `nickname`, `password`를 로컬용 값으로 익명화

### 설정

`application-local.properties` 또는 `application-online.properties`에 아래 값을 추가합니다.

```properties
# AI 검색 LLM 제공자 (anthropic 기본, openai/gemini 전환 가능)
sc1hub.assistant.searchProvider=anthropic
sc1hub.anthropic.apiKey=${ANTHROPIC_API_KEY:}
sc1hub.anthropic.searchModel=claude-haiku-5-5
sc1hub.anthropic.searchEffort=high
sc1hub.anthropic.allowLiveCalls=false

sc1hub.openai.apiKey=${OPENAI_API_KEY:}
sc1hub.openai.searchModel=gpt-6-luna
sc1hub.openai.searchReasoningEffort=max
sc1hub.openai.allowLiveCalls=false

sc1hub.gemini.apiKey=YOUR_GEMINI_API_KEY
sc1hub.gemini.model=gemini-3.7-flash
sc1hub.gemini.searchModel=gemini-3.5-flash-lite
sc1hub.gemini.searchThinkingLevel=minimal
sc1hub.gemini.embeddingModel=gemini-embedding-001
sc1hub.gemini.allowLiveCalls=false

# optional
sc1hub.assistant.enabled=true
sc1hub.assistant.requireLogin=false
sc1hub.assistant.maxRelatedPosts=3
sc1hub.assistant.anonymousDailyLimit=3
sc1hub.assistant.memberDailyLimit=10
sc1hub.assistant.adminUnlimited=true
sc1hub.assistant.adminId=admin
sc1hub.assistant.adminGrade=3

# AI bot (optional)
sc1hub.assistant.bot.enabled=true
sc1hub.assistant.bot.model=claude-haiku-5-5
sc1hub.assistant.bot.reasoningEffort=max
sc1hub.assistant.bot.personas[4].name=고수봇
sc1hub.assistant.bot.personas[4].boardTitle=funboard
sc1hub.assistant.bot.personas[4].maxOutputTokens=3000
# sc1hub.assistant.bot.publishGuestPassword 는 2026-09-08 부터 사용하지 않습니다(봇 글·댓글은 행마다 무작위 비밀번호, 해시 저장).
sc1hub.assistant.bot.autoPublishEnabled=false

# RAG (벡터 검색, 로컬 파일 인덱스)
sc1hub.assistant.rag.enabled=true
sc1hub.assistant.rag.indexPath=data/assistant/rag-index.json
sc1hub.assistant.rag.maxPostsPerBoard=1000
sc1hub.assistant.rag.chunkSizeChars=900
sc1hub.assistant.rag.chunkOverlapChars=150
sc1hub.assistant.rag.searchTopChunks=12
sc1hub.assistant.rag.autoUpdate.enabled=false
sc1hub.assistant.rag.autoUpdate.cron=0 0 5 * * *
# optional: empty = server timezone
sc1hub.assistant.rag.autoUpdate.zone=
```

### AI bot 자동 발행 운영 권장값

운영에서는 공용 `application.properties`를 직접 바꾸지 말고 `application-online.properties` 또는 환경 변수로만 켭니다. 현재 운영 봇 페르소나(프징징봇·테뻔뻔봇·저묵묵봇·건강봇·고수봇)는 모두 공개채팅방으로 라우팅됩니다.

```properties
sc1hub.assistant.bot.enabled=true
# sc1hub.assistant.bot.publishGuestPassword 는 2026-09-08 부터 사용하지 않습니다(봇 글·댓글은 행마다 무작위 비밀번호, 해시 저장).
sc1hub.assistant.bot.autoPublishEnabled=true
sc1hub.assistant.bot.autoPublishCron=0 * * * * *
sc1hub.assistant.bot.autoPublishZone=Asia/Seoul
sc1hub.assistant.bot.autoPublishCatchUpEnabled=true
sc1hub.assistant.bot.autoPublishChatDailyLimit=1
sc1hub.assistant.bot.autoPublishChatMinGapMinutes=20
sc1hub.assistant.bot.autoPublishChatMaxAttemptsPerDay=3
sc1hub.assistant.bot.autoPublishChatRetryCooldownMinutes=60
```

- 권장값 기준으로 매분 발행 가능 여부를 체크하고, 각 페르소나마다 하루 24시간 안에서 무작위 채팅 발행 슬롯이 잡히며, 전 페르소나 공통 `autoPublishChatMinGapMinutes=20` 간격으로 분산됩니다.
- 채팅은 성공한 발행만 `autoPublishChatDailyLimit`에 산입하고, 실패한 생성은 재시도 횟수와 쿨다운 범위에서 같은 날 다시 시도합니다. 페르소나에 같은 이름의 설정이 있으면 공통값보다 우선합니다.
- 고수봇(`personas[4]`)은 자신의 마지막 채팅 이후 올라온 최신 채팅 최대 3건을 읽어, 스타1 관련 대화가 있으면 가장 최근 관련 내용에 훈수를 두고, 없으면 독립적인 스타1 한 줄 공략을 게시합니다. 별도 provider/model 지정이 없으면 공통 봇 모델(`claude-haiku-5-5`, `reasoningEffort=max`)을 사용합니다.
- `publishGuestPassword`는 더 이상 사용하지 않습니다. 봇 글·댓글은 행마다 무작위 비밀번호를 받아 해시로 저장되므로 관리자만 수정·삭제할 수 있습니다.

배포 후 관리자 로그인 상태에서 현재 설정/슬롯 확인:

```js
fetch('/adminPage/ops/status', {
  credentials: 'include'
})
  .then(r => r.json())
  .then(console.log)
  .catch(console.error)
```

관리자 로그인 상태에서 수동 1회 점검:

```js
fetch('/api/admin/assistant-publisher/auto-publish/run', {
  method: 'POST',
  credentials: 'include'
})
  .then(r => r.json())
  .then(console.log)
  .catch(console.error)
```

전체 페르소나별 결과를 보려면 `/api/admin/assistant-publisher/auto-publish/run-all`을 같은 방식으로 호출합니다.

최근 생성 이력과 요약은 관리자 로그인 상태에서 `/adminPage/ops` 화면 또는 아래 URL로 직접 확인할 수 있습니다.

- `/adminPage/ops/history?days=3&limit=100`
- `/adminPage/ops/history/summary?days=3`

### 한줄 공략 (2026-09-07 제거)

한줄 공략 기능(`/strategy-tips`, `one_line_strategy*` 테이블)은 이용률이 낮고 도배에 쓰여 2026-09-07 에 통째로 제거했습니다.
공략은 종족별 공략게시판과 꿀팁보급고에서 다룹니다.

### RAG 적용 흐름

1) `sc1hub.assistant.rag.enabled=true`로 켠 뒤 서버 실행
2) 관리자 계정으로 로그인
3) 브라우저 콘솔에서 인덱스 생성 (기본: 비동기)

```js
fetch('/api/assistant/rag/reindex', { method: 'POST' })
  .then(r => r.json())
  .then(console.log)
```

동기 실행이 필요할 때:

```js
fetch('/api/assistant/rag/reindex?async=false', { method: 'POST' })
  .then(r => r.json())
  .then(console.log)
```

신규 게시물만 업데이트:

```js
fetch('/api/assistant/rag/update', { method: 'POST' })
  .then(r => r.json())
  .then(console.log)
```

상태 확인:

```js
fetch('/api/assistant/rag/status').then(r => r.json()).then(console.log)
```

- 인덱스는 기본적으로 `data/assistant/rag-index.json`에 저장되며 gitignore 처리되어 있습니다.
- 대상은 “일반 게시물(notice=0)”이며 댓글은 제외됩니다.
- 인덱싱 대상은 `*board`로 끝나는 일반 게시판입니다.
- `update`는 `GREATEST(reg_date, COALESCE(mod_date, reg_date))` 기준으로 신규/수정된 글을 증분 재인덱싱하고, 삭제된 게시물과 현재 보드 목록에 없는 보드의 기존 chunks를 자동으로 제거합니다.
- `sc1hub.assistant.rag.autoUpdate.enabled=true`로 켜면 서버가 살아있는 동안 매일 지정된 cron 시간에 RAG `update`를 자동 실행합니다. (`search_terms` 재인덱싱은 별칭 사전 등록/수정/삭제 시 자동 실행되거나 아래 수동 엔드포인트로 실행합니다.)
- `/api/assistant/rag/status` 응답의 `signatureAvailable`/`signatureMismatch`로 인덱스와 DB 불일치 여부를 확인할 수 있습니다.

### search_terms 재인덱싱 (alias_dictionary 반영)

alias_dictionary 등록/수정 후 기존 게시글의 `search_terms`를 갱신해야 관련 게시물/쿼리 확장에 반영됩니다.

1) 관리자 계정으로 로그인
2) 브라우저 콘솔에서 재인덱싱 실행 (별도 쿼리 파라미터 없음)

```js
fetch('/api/assistant/search-terms/reindex', {
  method: 'POST',
  credentials: 'include'
})
  .then(r => r.json())
  .then(console.log)
  .catch(console.error)
```
