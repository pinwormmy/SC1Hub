# SC1Hub

스타크래프트1 전문 커뮤니티 개발중...

## Terminal AI Assistant (Gemini)

터미널에서 `ask <질문>` 또는 `ai <질문>` 명령으로, 사이트 게시물 기반의 간단한 답변과 관련 게시물 링크를 제공합니다.

### 설정

`application-local.properties` 또는 `application-online.properties`에 아래 값을 추가합니다.

```properties
sc1hub.gemini.apiKey=YOUR_GEMINI_API_KEY
sc1hub.gemini.model=gemini-1.5-flash

# optional
sc1hub.assistant.enabled=true
sc1hub.assistant.requireLogin=false
sc1hub.assistant.maxRelatedPosts=3
sc1hub.assistant.anonymousDailyLimit=3
sc1hub.assistant.memberDailyLimit=10
sc1hub.assistant.adminUnlimited=true
sc1hub.assistant.adminId=admin
sc1hub.assistant.adminGrade=3
```

