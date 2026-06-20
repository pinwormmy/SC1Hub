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

## Terminal AI Assistant (Gemini)

터미널에서 게시판 바로가기용 숫자 명령을 제외한 검색어 및 질문 프롬프트로, 사이트 게시물 기반의 간단한 답변과 관련 게시물 링크를 제공합니다.

## 로컬 실행 테스트

`run-local.sh`는 로컬 화면 확인과 스크린샷 촬영용 안전 실행 경로입니다. 공용 `application.properties`를 수정하지 않고 `local` 프로필을 명시하며, 마지막 오버라이드 파일로 Gemini 라이브 호출, assistant bot, 자동 발행, RAG 자동 업데이트를 강제로 끕니다.

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

운영 배포는 `deploy.sh`가 Tomcat `setenv.sh`에 `SPRING_PROFILES_ACTIVE=online` 기본값을 보장합니다. 로컬/운영 전환을 위해 `application.properties` 마지막 줄을 수동으로 바꾸지 않습니다.

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
sc1hub.gemini.apiKey=YOUR_GEMINI_API_KEY
sc1hub.gemini.model=gemini-1.5-flash
sc1hub.gemini.embeddingModel=text-embedding-004

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
sc1hub.assistant.bot.publishGuestPassword=CHANGE_ME
sc1hub.assistant.bot.autoPublishEnabled=false

# RAG (벡터 검색, 로컬 파일 인덱스)
sc1hub.assistant.rag.enabled=false
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

운영에서는 공용 `application.properties`를 직접 바꾸지 말고 `application-online.properties` 또는 환경 변수로만 켭니다.

```properties
sc1hub.assistant.bot.enabled=true
sc1hub.assistant.bot.publishGuestPassword=CHANGE_ME_TO_A_STRONG_VALUE
sc1hub.assistant.bot.autoPublishEnabled=true
sc1hub.assistant.bot.autoPublishCron=0 * * * * *
sc1hub.assistant.bot.autoPublishZone=Asia/Seoul
sc1hub.assistant.bot.autoPublishCatchUpEnabled=true
sc1hub.assistant.bot.autoPublishPostDailyLimit=5
sc1hub.assistant.bot.autoPublishCommentDailyLimit=10
sc1hub.assistant.bot.autoPublishCommentCandidatePosts=6
```

- 권장값 기준으로 매분 발행 가능 여부를 체크하고, 하루 전체 24시간 안에서 게시글 5회와 댓글 10회의 무작위 슬롯이 각각 잡힙니다.
- 운영시간 제한이나 최소 대기시간 없이, 일일 횟수 제한만 적용됩니다.
- `autoPublishCatchUpEnabled=true`이면 서버가 랜덤 슬롯을 놓친 경우 같은 날 남은 슬롯이나 복구 타이밍에 다시 시도합니다.
- `publishGuestPassword`는 운영 전용 강한 값으로 별도 관리해야 합니다.

배포 후 관리자 로그인 상태에서 현재 설정/슬롯 확인:

```js
fetch('/api/admin/assistant-publisher/status', {
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
- 인덱싱 대상 보드는 `*board`로 끝나는 보드만 포함됩니다.
- 게시글 수정 시 `reg_date`가 갱신되므로, `update`는 수정된 글도 자동으로 재인덱싱합니다.
- `update`는 현재 보드 목록에 없는 보드의 기존 chunks를 자동으로 제거합니다.
- `sc1hub.assistant.rag.autoUpdate.enabled=true`로 켜면 서버가 살아있는 동안 매일 지정된 cron 시간에 RAG `update` + `search_terms` 재인덱싱을 같이 수행합니다. (search_terms 기본 batchSize=200)
- `/api/assistant/rag/status` 응답의 `signatureAvailable`/`signatureMismatch`로 인덱스와 DB 불일치 여부를 확인할 수 있습니다.

### search_terms 재인덱싱 (alias_dictionary 반영)

alias_dictionary 등록/수정 후 기존 게시글의 `search_terms`를 갱신해야 관련 게시물/쿼리 확장에 반영됩니다.
(`sc1hub.assistant.rag.autoUpdate.enabled=true`면 매일 자동으로도 재인덱싱됩니다.)

1) 관리자 계정으로 로그인
2) 브라우저 콘솔에서 재인덱싱 실행

```js
fetch('/api/assistant/search-terms/reindex?batchSize=200', {
  method: 'POST',
  credentials: 'include'
})
  .then(r => r.json())
  .then(console.log)
  .catch(console.error)
```

- `batchSize`는 상황에 따라 100~500 사이로 조정 가능합니다.
