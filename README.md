# SC1Hub

스타크래프트1 전문 공략사이트.

## Terminal AI Assistant (Gemini)

터미널에서 게시판 바로가기용 숫자 명령을 제외한 검색어 및 질문 프롬프트로, 사이트 게시물 기반의 간단한 답변과 관련 게시물 링크를 제공합니다.

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
