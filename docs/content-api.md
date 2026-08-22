# SC1Hub 관리자 콘텐츠 API

브라우저 세션 없이 게시판을 조회하고, 이미지를 최적화해 업로드하고, 유튜브 영상이 포함된 글을 게시하는 관리자 전용 API다.

## 인증

서버에는 `SC1HUB_CONTENT_API_TOKEN`을 설정한다. 클라이언트는 모든 요청에 다음 헤더를 보낸다.

```text
Authorization: Bearer <token>
```

토큰은 `/api/admin/content/**`에만 사용할 수 있으며 다른 관리자 API 권한은 갖지 않는다. 로컬에서는 저장소에 커밋되지 않는 `.content-api.env`를 사용한다.

```bash
SC1HUB_CONTENT_API_BASE_URL=https://sc1hub.com
SC1HUB_CONTENT_API_TOKEN=replace-with-a-long-random-token
```

macOS에서는 `.content-api.env` 대신 `sc1hub-content-api`라는 이름의 로그인 키체인 항목을 우선 사용할 수 있다. 호출 스크립트는 환경변수, `.content-api.env`, 키체인 순서로 토큰을 찾는다.

## 조회

```bash
scripts/sc1hub-content-api.sh boards
scripts/sc1hub-content-api.sh list teamplayguideboard 20
scripts/sc1hub-content-api.sh read teamplayguideboard 12
```

대응하는 API는 다음과 같다.

```text
GET /api/admin/content/boards
GET /api/admin/content/boards/{boardTitle}/posts?limit=20
GET /api/admin/content/boards/{boardTitle}/posts/{postNum}
```

목록 API는 최근 글 요약을 반환하고, 단일 글 API는 기존 HTML 본문 전체를 반환한다. 조회 개수는 1~100이다.

## 이미지와 유튜브를 포함해 한 번에 게시

본문 HTML 파일을 준비한 다음 실행한다.

```bash
SC1HUB_POST_IMAGE_CAPTION='합류냐 역공이냐, 3초 안에 결정해야 한다.' \
scripts/sc1hub-content-api.sh publish \
  teamplayguideboard \
  '헌터 팀플, 합류할까 역공할까?' \
  /tmp/post-body.html \
  /tmp/post-image.jpg \
  'https://www.youtube.com/watch?v=vi36jGm_cgw'
```

멀티파트 API는 이미지를 서버에서 가로 최대 700px, 400KB 이하로 최적화하고 최상단에 배치한다. 유튜브 주소는 개인정보 보호 강화 embed 주소로 변환해 본문 최하단에 너비 `100%`로 삽입한다. 토큰 요청에서 작성자를 생략하면 `운영자`가 사용된다.

JSON HTML을 직접 게시하거나 이미지만 별도로 업로드하는 기존 API도 유지된다.

```text
POST /api/admin/content/boards/{boardTitle}/posts  application/json
POST /api/admin/content/images                    multipart/form-data
```
