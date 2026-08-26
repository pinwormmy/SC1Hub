# SC1Hub 관리자 콘텐츠 API

브라우저 세션 없이 게시판과 게시글을 조회·생성·수정·삭제하고, 이미지를 최적화해 업로드하며, 유튜브 영상이 포함된 글을 발행하는 관리자 전용 API다.

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

멀티파트 API는 이미지를 서버에서 가로 최대 700px, 400KB 이하로 최적화하고 최상단에 배치한다. 유튜브 주소는 개인정보 보호 강화 embed 주소로 변환해 본문 최하단에 반응형 16:9 플레이어와 정규화된 원본 링크로 삽입한다. 토큰 요청에서 작성자를 생략하면 `운영자`가 사용된다.

AI가 만드는 본문 HTML에는 상단 이미지나 하단 유튜브를 다시 넣지 않는다. 본문은 제목 아래의 실제 글 내용만 담고, 이미지와 유튜브는 `publish`의 별도 인자로 전달해야 서버가 순서와 모바일 레이아웃을 일관되게 보장한다. 게시 후 응답의 `postNum`을 `read`로 다시 조회해 이미지 → 본문 → 영상/원본 링크 순서를 확인한다.

게시물용 생성 이미지는 Git에 넣지 않는다. 원본·최적화본·중간 파일은 `.gitignore`에 등록된 `artifacts/post-images/`에서만 임시로 사용한다. 게시 후 API `read`와 공개 페이지에서 서버 이미지가 정상 노출되는 것을 확인하면 로컬 원본, 최적화본, 중간 파일과 이미지 생성 도구의 해당 출력까지 모두 삭제한다. 게시가 보류되거나 실패한 동안에는 재개에 필요한 최소 파일만 유지한다.

JSON HTML을 직접 게시하거나 이미지만 별도로 업로드하는 기존 API도 유지된다.

```text
POST /api/admin/content/boards/{boardTitle}/posts  application/json
POST /api/admin/content/images                    multipart/form-data
```

## 기존 게시글 수정

`update`는 게시글 전체를 교체하는 `PUT` 명령이다. 먼저 `read`로 현재 HTML을 확인하고, 수정할 본문 HTML 파일을 준비한 다음 실행한다.

```bash
SC1HUB_POST_IMAGE_ALT='23넥 아비터 운영 빌드 참고 이미지' \
SC1HUB_POST_IMAGE_CAPTION='23넥 아비터 운영을 표현한 전략게임 패러디 일러스트' \
scripts/sc1hub-content-api.sh update \
  pvstboard \
  2 \
  '쉽고 안정적인 정석. 23넥 아비터 운영 빌드' \
  /tmp/post-body.html \
  /tmp/post-image.jpg \
  'https://www.youtube.com/watch?v=csIPbJ719iw'
```

이미지와 유튜브를 전달하면 `publish`와 동일하게 이미지 → 본문 → 영상/원본 링크 순서로 재구성한다. 전달하지 않으면 HTML 파일의 본문만 저장한다. 기존 작성자는 요청값과 관계없이 보존하고, 제목·본문·공지 여부와 수정 시각을 갱신한다. 응답의 `postNum`을 다시 `read`해 최종 상태를 확인한다.

대응하는 API는 다음과 같다.

```text
PUT /api/admin/content/boards/{boardTitle}/posts/{postNum}  application/json
PUT /api/admin/content/boards/{boardTitle}/posts/{postNum}  multipart/form-data
```

## 게시글 삭제

삭제는 복구가 어려우므로 스크립트에서 정확한 게시판과 글 번호 뒤에 `--confirm`을 반드시 붙인다.

```bash
scripts/sc1hub-content-api.sh delete pvstboard 2 --confirm
```

서버는 게시글 존재 여부와 실제 삭제 행 수를 확인하고 성공 시 `204 No Content`를 반환한다.

```text
DELETE /api/admin/content/boards/{boardTitle}/posts/{postNum}
```

## 게시글 CRUD 요약

```text
Create  POST   /api/admin/content/boards/{boardTitle}/posts
Read    GET    /api/admin/content/boards/{boardTitle}/posts/{postNum}
Update  PUT    /api/admin/content/boards/{boardTitle}/posts/{postNum}
Delete  DELETE /api/admin/content/boards/{boardTitle}/posts/{postNum}
List    GET    /api/admin/content/boards/{boardTitle}/posts?limit=20
```
