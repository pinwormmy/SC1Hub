# SC1Hub SEO 점검 — 2026-09-08 KST

> 2026-09-27 정리: 아래 수치와 작업 상태는 2026-09-08 당시의 기록이다. 현재 색인 수나 배포 상태를 뜻하지 않으며, 이번 오류 수정 배포에서 Search Console을 재점검하지 않았다.

## 최신 글 색인 요청

현재 `/api/latest-posts`의 최근 활동 5개 URL 및 사이트맵에서 확인한 최근 저프전 글을 URL 검사했다. 최신 활동 목록은 댓글 시간도 반영하므로 신규 작성 순서와 같지 않다.

| URL | 확인 결과 |
| --- | --- |
| /boards/noticeboard/readPost?postNum=127 | Google에 알려지지 않은 URL → 색인 생성 요청됨, 우선순위 크롤링 대기열 추가 확인 |
| /boards/promotionboard/readPost?postNum=1 | 이미 Google 등록 |
| /boards/funboard/readPost?postNum=375 | 이미 Google 등록 |
| /boards/videolinkboard/readPost?postNum=1 | 이미 Google 등록 |
| /boards/funboard/readPost?postNum=100 | 이미 Google 등록 |
| /boards/zvspboard/readPost?postNum=15 | 이미 Google 등록 |

`https://sc1hub.com/sitemap.xml` 재제출 후 ‘사이트맵이 제출됨’ 확인. 요청 접수는 실제 색인 및 검색 노출 완료를 보장하지 않는다.

## 전체 공개 URL 점검

사이트맵 690개 URL을 동시 요청 2개로 전수 검사했다.

- HTTP 오류 0개, canonical 불일치 0개, 설명문 누락 0개, H1 개수 이상 0개.
- 사이트맵 URL의 noindex 0개.
- robots.txt는 공개 경로를 허용하고 관리자/API/업로드/편집 경로를 제한한다.
- HTTP → HTTPS 301 정상, 사이트맵 HTTP 200 및 10분 public 캐시 정상.
- 제목 중복 7그룹, 설명문 중복 1그룹은 꿀잼놀이터 글들에서 발견. 제목이 같다는 이유만으로 본문을 중복으로 판단하거나 삭제하지 않았다.
- 전체 렌더 HTML의 빈/누락 alt 이미지 968개 집계. 공통 장식 이미지 반복도 포함하므로 서로 다른 본문 이미지 968개를 뜻하지 않는다. 내용별 설명은 이미지 검수가 필요하다.
- 상세 페이지별 증거: `.local-data/seo-audit-20260908/pages.json` (로컬 비커밋).

## Search Console 상태

보고서 최종 업데이트는 2026-09-04다. 현재 실시간 페이지 검사 결과와 구분해야 한다.

- 색인 733, 미색인 927.
- 적절한 canonical 대체 페이지 387, 리디렉션 348, 404 27, noindex 8, 5xx 2, robots 차단 2, 기타 4xx 1, 크롤링됐으나 미색인 128, soft 404 1, Google이 다른 canonical 선택 12. 첫 화면 10개 사유만 열람했으며 합계와의 차이는 11번째 사유다.
- 코어 웹 바이탈: 모바일/데스크톱 각각 좋음 303, 개선 필요 0, 느림 0.
- 5xx 예시 두 URL을 실제 GET으로 확인: `/strategy-tips/recommend?tipNum=`는 404, `https://www.sc1hub.com/extendLogin`은 리디렉션 후 405. 현재 5xx가 아니므로 수정 결과 확인 요청. 서치 콘솔에서 ‘유효성 검사 상태: 시작됨’, 시작일 2026-09-08 확인.

## 코드 변경

- `SeoMetadataService`: 본문 첫 HTTP(S) 이미지를 Open Graph/Twitter/Article 대표 이미지로 공통 사용. 상대 URL을 절대 URL로 변환하고 inline/잘못된 URL/인증정보 포함 URL은 제외, 없으면 기존 기본 이미지 사용.
- Article headline에는 게시판/사이트명 접미사를 제외한 실제 글 제목 사용.
- 수정일을 따로 보관하지 않는 DTO에서 작성일을 수정일로 재사용하던 `dateModified` 제거.
- HTML 본문 설명을 Jsoup으로 추출해 script 텍스트를 제외하고 숫자 HTML 엔티티를 해석.
- 공통 head: 개별 이미지에 기본 배경 이미지의 1440×810 크기를 붙이지 않도록 수정, 글 제목을 미리보기 대체 설명에 반영.

근거: [Google Article 문서](https://developers.google.com/search/docs/appearance/structured-data/article), [사이트맵 문서](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap).

## 검증 및 배포 상태

- SEO 테스트 통과. 이미지 인증정보 URL에 대한 회귀 테스트 실패를 수정 후 재검증했다.
- `./gradlew clean build` 통과: 439 tests, 실패/오류/스킵 0, JSP 사전 컴파일 오류 0, WAR 생성 및 footprint 검사 통과.
- `git diff --check` 통과.
- `main`에서 작업. 기존 HEAD `e384fb928889544b9f2e16ad69755ac14a756ea1`.
- 원격 fetch 후 main은 origin/main보다 기존 커밋 4개 앞섬. 이번 작업의 커밋·푸시·배포는 수행하지 않았다. 기존 미추적 `docs/content-audit-20260905/` 보존.
