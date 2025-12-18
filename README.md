# SC1Hub

스타크래프트1 전문 커뮤니티 개발중...

## Terminal AI Assistant (Gemini)

터미널에서 `ask <질문>` 또는 `ai <질문>` 명령으로, 사이트 게시물 기반의 간단한 답변과 관련 게시물 링크를 제공합니다.

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

### RAG 로컬 테스트 흐름

1) `sc1hub.assistant.rag.enabled=true`로 켠 뒤 서버 실행
2) 관리자 계정으로 로그인
3) 브라우저 콘솔에서 인덱스 생성

```js
fetch('/api/assistant/rag/reindex', { method: 'POST' })
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
- 게시글 수정 시 `reg_date`가 갱신되므로, `update`는 수정된 글도 자동으로 재인덱싱합니다.
- `sc1hub.assistant.rag.autoUpdate.enabled=true`로 켜면 서버가 살아있는 동안 매일 지정된 cron 시간에 자동 `update`를 수행합니다.

