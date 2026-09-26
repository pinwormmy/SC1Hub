# SC1Hub 공략 전수 검수 — 2026-09-05

> 2026-09-27 정리: 이 보고서와 같은 폴더의 JSON/HTML/수정 내역은 당시 감사 기록이다. 현재 게시글 상태를 재검증한 자료가 아니며, 과거 해외 근거를 포함한다. 새 공략 작성·수정에는 현행 AGENTS.md의 한국 자료 한정 규칙을 적용한다.

> **수정게시 업데이트:** P1 18개와 연결 공략 3개, 총 21개 글의 핵심 오류 수정게시 및 공개 검증을 마쳤다. [글별 수정 내역](corrections.md). 아래 검수 의견·우선순위는 수정 전 기록이며, 추가 보강까지 전부 끝났다는 뜻은 아니다.

**기존 글을 먼저 보완하는 것을 권장한다.** 공략·꿀팁 176개 본문을 모두 읽고 글별 수정 범위와 우선순위를 기록했다. 오래된 짧은 글은 실행 절차가 부족하고, 보강된 긴 글에는 단정적 판단·출처 없는 정밀 수치·반복 설명이 남아 있다.

## 범위와 검증 수준

- 종족전 9개 게시판 137개 + 팀플 15개 + 꿀팁 24개 = 일반 글 176개. 게시판 소개 공지 11개는 별도 제외.
- 한줄 공략 15개도 전부 검수. 기타 게시판에서는 제목으로 후보를 추리고 관련 본문을 확인해 보조 자료 2개를 별도 기록.
- API·사이트맵에서 확보한 673개 게시글은 재고 확인 범위다. 잡담·대회 영상·홍보·공지까지 모두 전략 사실검증을 했다는 뜻은 아니다.
- 전수 수행: 본문 읽기, 빌드/운영/역사/자료 유형 구분, 누락·내부 모순·단정·문체 검토, 이미지/영상 태그와 링크 접근 검사.
- 선택 수행: 주요 의심 주장에 공식 설명·BWAPI·원 통계·맵 배포 자료 대조. 외부 근거는 해당 주장만 뒷받침하며 글 전체를 인증하지 않는다.
- **전체 영상 재생, 리플레이 실측, 모든 빌드의 인게임 재현은 하지 않았다.** 게임 속도·맵·스폰별 정확한 타이밍, 장비 현행 가격, 역사적 최초 주장 등은 글별로 추가 검증 필요로 표시했다. 이미지 전체의 시각적 정확성과 공개 화면의 배치도 전수 확인 범위에 포함하지 않는다.
- 운영 글·상성 자료·역사 글에는 고정 빌드를 억지로 넣지 않는다. 제목이 빌드/실행 공략인 글에 재현 가능한 순서를 요구했다.
- 최초 전수 검수 단계에서는 읽기만 수행했다. 이후 사용자의 수정게시 요청에 따라 21개 글의 핵심 오류를 수정게시했다. 코드 변경·테스트·빌드·배포는 하지 않았다.

## 결과

| 우선순위 | 글 수 | 의미 |
|---|---:|---|
| P1 | 18 | 오류·내부 모순·근거 부족으로 먼저 수정 또는 재검증 |
| P2 | 112 | 실행 순서·시간·조건·자료 설명 보강 |
| P3 | 46 | 유지 또는 문체·연결·가독성 보완 |

수정 범위: 전면 보완 24개 / 부분 보완 147개 / 현 구성 유지 5개. P1 18개가 모두 확정된 게임 수치 오류라는 뜻은 아니다. 우선순위와 수정 범위는 별개이며, 미디어 보완도 별도 항목이다.

## 가장 먼저 처리할 문제

1. **실행 불가능·선행 순서:** 테테전 배럭더블의 첫 서플 11, 3해처리 뮤탈의 레어 누락, 가드라의 스파이어→레어 역순.
2. **게임 조작 설명:** 빨무 프로토스 글의 프로브 건설 대기·생산 그룹·유닛 완성 시 공급 증가 설명.
3. **글 사이의 불일치:** 공1업 5팩으로 연결하지만 실제 빌드에는 아머리/공업이 없음. 973의 원공1업 필수 주장도 별도 변형과 구분 필요.
4. **근거 없는 정밀 수치:** 테테전 드랍십/레이스/골리앗의 중앙값·사분위 수치에 표본과 출처가 없음.
5. **정찰·승패 단정:** 레어 때 챔버면 100%, 530 판별 100%, 수비 후 2차 러시 절대 불가 등은 추가 확인 조건으로 교체.
6. **실습에 부족한 짧은 글:** 저저전 9투 소개·오버풀·12앞, 프저전 투게이트, 저프전 선게이트 대응 등을 순서와 판단 기준 중심으로 재작성.

## 미디어와 링크

- 176개 중 본문 이미지 태그가 없는 글 78개, 임베드 영상이 없는 글 35개, alt가 빈 이미지를 포함한 글 75개. 이 숫자는 서로 겹칠 수 있다.
- 고유 이미지 주소 211개: 210개 HTTP 200, 1개 HTTP 404. 한글 URL 인코딩 후 재검사한 결과다.
- 고유 YouTube 영상 151개: oEmbed 148개 정상, 2개 403, 1개 404. 메타데이터 응답은 실제 재생·공개 여부·본문과 장면 일치의 보증이 아니다.
- 공략 안의 내부 게시글 링크 52개는 HTTP 200. 응답 성공은 링크 설명과 대상 내용의 일치까지 보증하지 않는다.
- 이미지 404: 저프전 /2의 `유사 히드라웨이브.webp`.
- 영상 재생 확인: 프저전 /9 `yGL5GmmQKao`(403), 저프전 /4 `xw0blAyB9h4`(403), 저저전 /7 `ZJHuCOI6Dh0`(404).
- 표의 ‘본문 이미지 없음’은 이미지 태그 부재, ‘임베드 영상 없음’은 iframe 부재다. 제목 이미지/최상단 배치/영상 대체 링크까지 없다는 뜻은 아니다.

## 보완 순서

P1 18개부터 사실관계와 문장을 바로잡고, 다음으로 전면 보완 24개의 실행 절차를 채운다. 양쪽에 겹치는 글이 있으므로 합계를 작업 수로 사용하지 않는다. 이후 대표 빌드에서 사용하는 업그레이드·공급·시간 표현을 통일하고 관련 대응 글을 함께 맞춘다.

빌드형 글의 최소 구성은 적용 맵·조건 → 공급별 순서 → 첫 병력/업그레이드 완료와 출발·도착 시각 → 정찰별 분기 → 실패 후 선택이다. 확실하지 않은 시각을 새로 만들어 넣지 말고 특정 영상/리플레이 장면을 근거로 쓴다. 미작성 링크와 반복 서술은 정리하고 기존 URL은 유지하는 방향이 적절하다.

## 근거 대조에서 주의한 항목

구형 Blizzard 러커 페이지는 크기를 Large로 표기하지만 BWAPI는 Medium이다. 감염 테란 피해도 구형 요약표와 BWAPI가 다르다. 따라서 꿀팁 /18의 러커 중형·감염 테란 폭발형을 오류로 확정하지 않았고 현행 게임 실험이 필요할 경우를 분리했다. 공식이라는 이유만으로 오래된 표를 기계적으로 덮어쓰지 않는다.

## 외부 근거

- S1: [Blizzard — Terran Basics: 커맨드 공급 10](https://classic.battle.net/scc/terran/basic.shtml)
- S2: [Blizzard — Probe: 소환 시작 후 채취 복귀 가능](https://classic.battle.net/scc/protoss/units/probe.shtml)
- S3: [BWAPI UnitType: 유닛 크기·속도·선행 조건 데이터](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp)
- S4: [BWAPI WeaponType: 피해 유형·사거리·기본 재사용 대기시간](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp)
- S5: [Blizzard — 패치 1.18: 스타크래프트·브루드워 무료화](https://news.blizzard.com/en-us/article/20674424/starcraft-brood-war-patch-1-18-patch-notes)
- S6: [JackyVSO — ASL/KSL 시대 통계와 표본 설명](https://jackyvso.github.io/Starcraft/)
- S7: [강구열 — ASL21 공식맵: 래더 전체 목록과 구분 필요](https://910map.tistory.com/238)
- S8: [강구열 — ASL22·2026 시즌2 샌드박스 배포](https://910map.tistory.com/250)
- S9: [Blizzard — 구형 Lurker 페이지: BWAPI와 크기 표기 상충](https://classic.battle.net/scc/zerg/units/lurker.shtml)

## 게시판별 전체 176개

### 테테전 — 13개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [테테전 기본 정석 운영은 어떻게?](https://sc1hub.com/boards/tvstboard/readPost?postNum=2) · tvstboard/2 | 빌드 / **P2** / 부분 보완 | 착공·완성 시각을 구분하고 3팩 벌처→탱크 전환과 공격형 2팩 대응의 분기를 표로 정리. 시간 범위는 첨부 영상 장면 근거 연결. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [그래도 빨리 끝내려면?](https://sc1hub.com/boards/tvstboard/readPost?postNum=3) · tvstboard/3 | 빌드 모음 / **P2** / 전면 보완 | 날빌 3종이 한 문장 소개에 그침. 대표 빌드별 생산 순서·첫 병력·공격 시점·막힌 뒤 선택을 추가하거나 상세 글로 연결. | 본문 이미지 없음 |
| [잘 하면 게임 터트리는 원팩원스타](https://sc1hub.com/boards/tvstboard/readPost?postNum=4) · tvstboard/4 | 빌드 / **P2** / 전면 보완 | 원팩원스타 생산 순서와 출발 시점이 없음. 시즈 2발+레이스 2발 탱크 처치 설명은 양측 공방업 조건을 명시. | 본문 이미지 없음 |
| [바둑같은 테테전 후반 운영. 뭘 하면 될까요?](https://sc1hub.com/boards/tvstboard/readPost?postNum=5) · tvstboard/5 | 후반 운영 / **P3** / 부분 보완 | 시야·멀티·드랍 판단은 충분. 앞부분과 후반부가 반복되므로 압축하고 실제 드랍 장면을 연결. 고정 빌드를 억지로 넣을 필요 없음. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [통하면 꿀잼인 투스타 레이스](https://sc1hub.com/boards/tvstboard/readPost?postNum=6) · tvstboard/6 | 빌드 / **P2** / 부분 보완 | 3:25~3:35 스타포트 착공과 5:30~6:00 첫 2레이스 출발의 근거·게임 속도 확인. 생산 지연과 이동 시간을 분리. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [부유한 배럭더블 운영](https://sc1hub.com/boards/tvstboard/readPost?postNum=7) · tvstboard/7 | 빌드 / **P1** / 전면 보완 | 첫 서플 11은 기본 커맨드 공급 10과 충돌. 0:40~0:55 인구 11, 1:00~1:20 배럭 완성도 연결 불가. 오프닝과 시간표를 영상 기준으로 재작성. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 / 근거 [S1](https://classic.battle.net/scc/terran/basic.shtml) |
| [테테전 빌드 상성 구도 정리](https://sc1hub.com/boards/tvstboard/readPost?postNum=8) · tvstboard/8 | 상성 정리 / **P2** / 전면 보완 | 본문에 투팩 상성이 애매함·정리 필요라는 미완성 메모가 남음. 가위바위보 분류를 조건별 유불리와 대응으로 완성. | 본문 이미지 없음; 임베드 영상 없음 |
| [스타1 테테전 생더블 대처, 바로 공격해야 할까?](https://sc1hub.com/boards/tvstboard/readPost?postNum=9) · tvstboard/9 | 대처법 / **P3** / 부분 보완 | 내 오프닝별 대응 기준은 충분. 같은 경고를 압축하고 빠른 압박/따라 확장 사례를 각각 한 장면씩 연결. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [테테전 벌처 싸움, 마인업과 속업 뭐부터 해야 할까?](https://sc1hub.com/boards/tvstboard/readPost?postNum=10) · tvstboard/10 | 전술 / **P3** / 부분 보완 | 마인 비발동 대상 설명에서 벌처와 SCV의 분류를 분리. SCV를 공중에 떠서 이동하는 유닛처럼 설명하지 말고 일꾼 예외로 표기; 간접 폭발 피해 예외 추가. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [테테전 초반 정찰법: SCV로 상대 빌드 판별하기](https://sc1hub.com/boards/tvstboard/readPost?postNum=11) · tvstboard/11 | 정찰 / **P3** / 부분 보완 | 특정 공개 빌드의 공급 순서를 인용하지만 원 출처 링크 없음. 오프닝 출처 추가, 문체를 반말로 통일하고 반복 압축. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [테테전 드랍십 운영: 탱크 두 기로 자리 싸움 뒤집는 법](https://sc1hub.com/boards/tvstboard/readPost?postNum=12) · tvstboard/12 | 드랍 운영 / **P1** / 부분 보완 | 최근 경기 집계 중앙값 11:40·사분위 9:28~14:54의 표본·기간·출처 없음. 집계 완성 시점에서 출발 목표를 도출한 근거도 불명확. 통계는 검증하거나 삭제하고 특정 운영 사례로 교체. | 영상 제목은 전진 스타포트 드랍. 본문 후반 드랍 통계/타이밍을 뒷받침하는 자료인지 장면 대조 필요. |
| [상대 레이스가 보였다: 터렛과 골리앗은 얼마나 준비해야 할까?](https://sc1hub.com/boards/tvstboard/readPost?postNum=13) · tvstboard/13 | 대처법 / **P1** / 부분 보완 | 첫 레이스·골리앗 중앙값과 25% 경계의 집계 출처가 없음. 전체 경기 통계로 빠른 레이스 방어 시점을 정하면 오판 위험. 빠른 오프닝의 실제 최단 위협 기준으로 재검증. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [테테전 삼룡이 타이밍: 세 번째 커맨드는 언제 지어야 할까?](https://sc1hub.com/boards/tvstboard/readPost?postNum=15) · tvstboard/15 | 확장 판단 / **P3** / 부분 보완 | 영상 2:25/게임 6:45라는 구체적 근거와 착공 미확인 한계는 잘 구분. 이미지 캡션은 탱크 라인이 먼저라 하나 본문은 벌처로 먼저 확장이라 어긋나므로 캡션 수정. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |

### 테저전 — 19개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [저그전에도 업테란? 선엔베 4배럭 빌드](https://sc1hub.com/boards/tvszboard/readPost?postNum=2) · tvszboard/2 | 빌드 / **P2** / 전면 보완 | 서론은 공업 후 아카, 빌드 줄은 엔베→아카 이후 공업으로 순서 불일치. 인구수·공1업/뮤탈 방어 시점 추가, 현재 메타 단정에 기준 시기 명시. | alt 비어 있음 1개 |
| [안정적인 저그전 운영, 투배럭 아카 빌드](https://sc1hub.com/boards/tvszboard/readPost?postNum=3) · tvszboard/3 | 빌드 / **P2** / 부분 보완 | 인구수 순서는 있으나 첫 메딕·스팀·터렛 완료 시각이 없음. 2해처리/3해처리 뮤탈 정찰별 터렛·진출 분기와 후퇴 조건 추가. | alt 비어 있음 1개 |
| [개사기 8배럭 완막. 그리고...](https://sc1hub.com/boards/tvszboard/readPost?postNum=4) · tvszboard/4 | 역사·맵 / **P3** / 부분 보완 | 현재 모든 맵에서 해결된 것처럼 단정. 완막 가능/불가 맵·버전과 입구 그림을 붙이고 역사 설명임을 명시. | 본문 이미지 없음 |
| [저그의 악몽이었던, 테란의 111 빌드](https://sc1hub.com/boards/tvszboard/readPost?postNum=5) · tvszboard/5 | 전략사 / **P2** / 부분 보완 | 제목은 빌드인데 본문은 유래와 쇠퇴 설명. 역사 글로 제목·시기를 맞추거나 대표 111 오프닝 추가. 스타2 수입·창시·사장 원인의 출처 확인. | alt 비어 있음 1개 |
| [레더맵 테란 심시티 모음](https://sc1hub.com/boards/tvszboard/readPost?postNum=6) · tvszboard/6 | 심시티 자료 / **P2** / 전면 보완 | 본문 두 문장과 영상에 의존. 수록 맵·버전·스폰·영상 타임스탬프 및 현재 적용 여부를 명시. | 본문 이미지 없음 |
| [테란의 정석, SK테란의 의미와 운영법](https://sc1hub.com/boards/tvszboard/readPost?postNum=7) · tvszboard/7 | 운영 / **P2** / 부분 보완 | SK 전환 조건·배럭/스타포트·첫 베슬/이레디 기준이 없음. 베슬 8기와 탱크 전부 손해라는 일반화를 상황별로 완화. | 본문 이미지 없음 |
| [레이트 메카닉 빌드. 이제는 안 쓰는 이유](https://sc1hub.com/boards/tvszboard/readPost?postNum=8) · tvszboard/8 | 전략사·대처 / **P2** / 부분 보완 | 이제는 안 쓴다·퀸 때문에 망했다·4가스는 못 이긴다는 단정에 시기·경기 근거 필요. 스웜 내 시즈 피해는 직접타/스플래시/버로우 예외를 정확히 설명. | 본문 이미지 없음 |
| [저그전 어렵죠? 초보추천 킹팩골! 5팩 골리앗](https://sc1hub.com/boards/tvszboard/readPost?postNum=9) · tvszboard/9 | 빌드 / **P2** / 부분 보완 | 골리앗 사업의 선행 머신숍이 순서에서 빠짐. 첫 골리앗·사업·5팩 진출 목표와 방업 선택 근거 추가. 저그전 설명의 테란전 오기 수정. | alt 비어 있음 1개 |
| [늦깎이 취업한 발키리! 발리오닉 빌드](https://sc1hub.com/boards/tvszboard/readPost?postNum=10) · tvszboard/10 | 빌드 / **P2** / 부분 보완 | 발키리 2기/4기 선택과 첫 발키리·뮤탈 도착·베슬 전환 시간 누락. 스커지 호위와 럴커 확인 시 전환을 체크리스트화. | alt 비어 있음 1개 |
| [자주 상대하게 되는, 미친 저그 상대법](https://sc1hub.com/boards/tvszboard/readPost?postNum=11) · tvszboard/11 | 대처법 / **P1** / 부분 보완 | 레어 때 챔버면 거의 100% 미친 저그라는 판정은 불충분. 방업·하이브·가스·병력을 함께 확인하도록 수정. 울트라 방5업과 총 방어력 표기 구분 및 필패 단정 완화. | alt 비어 있음 1개 |
| [제일 어려운 컨트롤? 발키리 백샷 하는 법](https://sc1hub.com/boards/tvszboard/readPost?postNum=12) · tvszboard/12 | 컨트롤 / **P3** / 부분 보완 | P컨 원리는 설명하지만 실제 입력 방향·공격 개시 장면이 없음. 명령 3단계와 영상 시점 추가, 발사 중 명령 불가능이라는 표현은 실제 행동 제한과 구분 검증. | alt 비어 있음 1개 |
| [정찰로 저그 빌드 구분하는 법](https://sc1hub.com/boards/tvszboard/readPost?postNum=13) · tvszboard/13 | 정찰 / **P2** / 부분 보완 | 해처리 체력 색과 레어 시각을 맵 불문 판별표처럼 읽기 쉬움. 기준 맵·정찰 출발·실제 도착/착공·색상 경계를 근거 영상과 연결; 앞마당 필수 단정 수정. | alt 비어 있음 1개 |
| [매서운 중반 러쉬, 선럴커 찌르기 대처하기](https://sc1hub.com/boards/tvszboard/readPost?postNum=14) · tvszboard/14 | 대처법 / **P2** / 부분 보완 | 히드라덴만 보고 터렛을 취소하도록 단정. 레어·스파이어·저글링·럴커 연구를 함께 확인하고 스캔 확보·수비 완료 시점을 추가. | alt 비어 있음 1개 |
| [아놔 이게 뭐야! 투스타 레이스 빌드](https://sc1hub.com/boards/tvszboard/readPost?postNum=16) · tvszboard/16 | 빌드 / **P2** / 부분 보완 | 인구수 순서는 있으나 스타포트/첫 레이스/클로킹 시점과 드론·스포어 대응별 중단 기준이 없음. 111 사장 같은 메타 단정에 시기 표시. | alt 비어 있음 1개 |
| [저그전엔 드랍쉽이닷! 드랍쉽 사용법](https://sc1hub.com/boards/tvszboard/readPost?postNum=17) · tvszboard/17 | 전술 / **P3** / 부분 보완 | 드랍 역할과 위험 설명은 적절. 탑승 조합·정면에 남길 병력·출발 전 시야 기준 및 언급한 14:40 장면 링크 추가. | alt 비어 있음 1개 |
| [역사와 전통의 분노유발전략. 8배럭 벙커링 빌드](https://sc1hub.com/boards/tvszboard/readPost?postNum=18) · tvszboard/18 | 빌드 / **P2** / 부분 보완 | 완막 방지 지형을 건물을 지을 수 있는 샛길로 적어 같은 주제 #4의 건설 불가 지형과 충돌. SCV 동원 수·벙커 착공/퇴각 기준 추가. | alt 비어 있음 1개 |
| [필독꿀팁~! 럴커 겹치기 구분하는 법](https://sc1hub.com/boards/tvszboard/readPost?postNum=19) · tvszboard/19 | 컨트롤 / **P3** / 부분 보완 | 클릭/드래그/초상화 절차는 구체적. 초상화 옵션·리마스터 설정별 재현 확인 및 동일 럴커 선택 가능성 예외, 병력 규모 오기 수정. | alt 비어 있음 1개 |
| [뮤짤에 미쳤냐!? 530 뮤탈 빌드 대처](https://sc1hub.com/boards/tvszboard/readPost?postNum=20) · tvszboard/20 | 대처법 / **P1** / 부분 보완 | 2:35 레어/3:40 스파이어면 100% 530이라는 단정과 5:30에 터렛 지어주면 된다는 표현은 오판 위험. 터렛 완성 마감·엔베 착공 역산 및 실제 빌드 증거 필요. | alt 비어 있음 1개 |
| [왜케 쌔!? 히럴디파 상대법](https://sc1hub.com/boards/tvszboard/readPost?postNum=21) · tvszboard/21 | 대처법 / **P3** / 부분 보완 | 히드라 확인 후 방어 탱크 조합이라는 목적은 명확. 히드라 규모·탱크 추가 시작·베슬 유지·상대 울트라 전환 신호를 더 구체화. | alt 비어 있음 1개 |

### 테프전 — 17개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [의외로 어려운 운영법, 업테란 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=2) · tvspboard/2 | 빌드 / **P2** / 부분 보완 | 공2방1·200 진출을 제목 핵심으로 삼지만 공방업 시작/완료 시각이 없음. 불패·점사 금지 단정을 상황별로 완화하고 시드모드 오기 수정. | alt 비어 있음 1개 |
| [테란의 찌르기, 타이밍 러쉬 빌드 정리](https://sc1hub.com/boards/tvspboard/readPost?postNum=3) · tvspboard/3 | 빌드 모음 / **P2** / 부분 보완 | 1탱·2탱·5탱은 이름만 있고 설명·링크 없음. 5팩을 공1타로 표기하나 연결된 #7 빌드에는 아머리/공업이 없어 변형 구분 필요. | alt 비어 있음 1개 |
| [안티 캐리어 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=4) · tvspboard/4 | 대처·전술 / **P2** / 부분 보완 | 공2업 골리앗 대 인터셉터 설명에 상대 공방업 조건 및 사거리업·생산 규모 누락. 6캐리어 필패·멀티 반드시 파괴 단정을 줄이고 미완성 요청문 정리. | 본문 이미지 없음 |
| [이기고 시작하는 운영법~! 배럭 더블 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=5) · tvspboard/5 | 빌드 / **P2** / 부분 보완 | 앞마당·벙커·첫 탱크 시각과 선질럿 도착별 SCV 대응 누락. 마린 수 범위에 대응 조건과 실패 후 복구 추가. | alt 비어 있음 1개 |
| [이 시간에 이 물량!? 5팩 타이밍 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=7) · tvspboard/7 | 빌드 / **P1** / 부분 보완 | 연결 글은 공1업 5팩으로 소개하지만 본문 순서에는 아머리·공업이 없음. 무업 5팩과 공1업 변형을 명확히 분리하고 8:30 출발의 병력/업그레이드 조건 검증. | alt 비어 있음 1개 |
| [컨트롤이 중요한 선질럿 막기 공략](https://sc1hub.com/boards/tvspboard/readPost?postNum=8) · tvspboard/8 | 대처·심시티 / **P2** / 부분 보완 | 핵심이 건물 틈 활용인데 맵·방향별 배치도가 없음. 마린 통과/질럿 차단 예시와 SCV 동원·벙커 완성 기준 추가. | 본문 이미지 없음 |
| [쉽고 강한 초반 전략. 2팩, 투팩 찌르기 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=10) · tvspboard/10 | 빌드 / **P2** / 부분 보완 | 5분 전후가 출발인지 도착인지 불명확. 2탱/3탱/5탱 변형의 생산·업그레이드·탐지 대책 분리, 중수에게 안 통한다는 단정 완화. | alt 비어 있음 1개 |
| [안전한 정석 시작, 팩더블 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=11) · tvspboard/11 | 빌드 / **P2** / 부분 보완 | 오프닝 순서는 있으나 앞마당 착공·탱크/시즈 완료와 후속 선택 기준이 없음. 연결된 무업 5팩을 공1업 5팩으로 부르는 부분 교정. | 본문 이미지 없음 |
| [쓰면 손절 당한다는 중반속공올인!! 노노사 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=12) · tvspboard/12 | 빌드 / **P2** / 부분 보완 | 올인 빌드 핵심인 출발 시점·탱크/벌처 규모·업그레이드 순서가 없음. 노엔베 제목과 48엔베의 역할을 설명하고 리버/다크 배제 범위를 분리. | alt 비어 있음 1개 |
| [소소해서 더 무서운, 3탱 찌르기 ](https://sc1hub.com/boards/tvspboard/readPost?postNum=13) · tvspboard/13 | 빌드 / **P2** / 부분 보완 | 3탱 진출의 실제 시각·벌처/SCV 동반 수와 엔베 마감 추가. 본문 순서에서 35서플까지 공급이 막히지 않는지 커맨드 완료 포함 재현 확인. | alt 비어 있음 1개 |
| [어느새 이겨있는 마인 트리플 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=14) · tvspboard/14 | 빌드 / **P2** / 부분 보완 | 대각·선질럿 조건은 잘 명시. 삼룡이 착공/가동과 첫 시즈 시각 추가, 생넥 상대 거의 필패라는 단정 완화. | alt 비어 있음 1개 |
| [쉽게 가즈아~ 파워 FD테란 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=15) · tvspboard/15 | 빌드 / **P2** / 부분 보완 | 8마린 2탱 구성은 명확하나 출발·합류·커맨드 시각 없음. 가스 1기 조절과 재투입, 일꾼 중단/복구를 연습 기준으로 구체화. | alt 비어 있음 1개 |
| [시작부터 장난질? 가스 러쉬 대처법](https://sc1hub.com/boards/tvspboard/readPost?postNum=16) · tvspboard/16 | 대처법 / **P2** / 부분 보완 | 서두는 배럭더블 강제라 하지만 뒤에는 5SCV 가스 파괴 후 팩더블을 제안. 분기형 설명으로 통일하고 무조건 SCV 취소 대신 보유 자원 조건 추가. | alt 비어 있음 1개 |
| [공격적 신메타! 11업 8팩 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=17) · tvspboard/17 | 빌드 / **P2** / 전면 보완 | 11업 8팩 제목에 비해 인구수 순서·업그레이드 순서/완료·8팩 확보·진출 시각이 없음. 아비터 시대 종료 등 메타 단정도 기준 시점과 사례 필요. | alt 비어 있음 1개 |
| [의외로 강한 고전전략! 바카닉 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=18) · tvspboard/18 | 빌드 / **P2** / 전면 보완 | 대략적 흐름만 있어 실행 가능한 빌드가 아님. 대표 오프닝·스팀/사업/시즈 완료·탱크/메딕 구성·출발 시점을 검증해 추가. | alt 비어 있음 1개 |
| [업테란인 줄 알았지!? 공1업 6팩 타이밍 러쉬](https://sc1hub.com/boards/tvspboard/readPost?postNum=19) · tvspboard/19 | 빌드 / **P2** / 부분 보완 | 9:30~10:30 진출은 있으나 탱크 한 부대 안팎·공1업 완료의 실제 영상 시점과 생산 타당성 확인. 엔베가 순서에 없는데 진출 터렛을 권하므로 선행 조건 보완. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [스캔 한 번으로 다 보인다! 테프전 정찰별 빌드 판별법](https://sc1hub.com/boards/tvspboard/readPost?postNum=20) · tvspboard/20 | 정찰 / **P3** / 유지 | 건물 하나로 단정하지 않고 넥서스·가스·게이트 조합으로 판단하며 대응까지 연결. 추가 작업은 판독표 가독성과 영상 장면 연결 정도. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |

### 저테전 — 21개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [테란전 정석!! 투해처리 뮤탈 운영 빌드 강의](https://sc1hub.com/boards/zvstboard/readPost?postNum=3) · zvstboard/3 | 빌드 / **P2** / 부분 보완 | 인구수와 후반 흐름은 충실하지만 첫 뮤탈·럴커·디파/컨슘 시각 없음. 최소 3가스/4가스를 기술상 필수와 안정적 자원 권장으로 구분, 울트라 완성=종결 단정 완화. | alt 비어 있음 1개 |
| [에라 모르겠다~~ 4드론](https://sc1hub.com/boards/zvstboard/readPost?postNum=4) · zvstboard/4 | 빌드 / **P2** / 전면 보완 | 개념은 길게 반복하지만 첫 풀·링 생산·정찰/공격 시점이 없음. 드론 생산 중단과 풀 변태 후 경제·후속 링 순서, 실패 기준을 짧게 재구성. | 본문 이미지 없음 |
| [테란전 초반 빌드 정리 및 분석](https://sc1hub.com/boards/zvstboard/readPost?postNum=5) · zvstboard/5 | 빌드 모음 / **P3** / 부분 보완 | 세 오프닝 비교는 유용. 9풀 익스트랙터 트릭 과정에서 변태로 줄어드는 공급을 단계별 표시하고 각 빌드 첫 링 시각·조건 연결. | 본문 이미지 없음; 임베드 영상 없음 |
| [테란견제의 필수! 뮤탈컨트롤 심화과정](https://sc1hub.com/boards/zvstboard/readPost?postNum=6) · zvstboard/6 | 컨트롤 / **P2** / 부분 보완 | M컨 미작성 메모, 뭉치기 조건과 공격 각도 수치 근거 없음. #zvszboard/3과 공통 원리는 한 곳에서 관리하고 테란전 사례를 분리. | 본문 이미지 없음 |
| [의외로 잘 먹히는 뮤탈 링올인](https://sc1hub.com/boards/zvstboard/readPost?postNum=7) · zvstboard/7 | 빌드 / **P2** / 전면 보완 | 스파이어 뒤 링을 찍는 의도만 있고 링 수·발업·공격 시점·뮤탈 생산과 라바 배분 없음. 테란 진출을 못 싸먹었을 때 중단 조건 추가. | 본문 이미지 없음 |
| [그냥 힘으로 부숴버리는 5럴커 뚫기 전략](https://sc1hub.com/boards/zvstboard/readPost?postNum=8) · zvstboard/8 | 빌드 / **P2** / 부분 보완 | 5히드라/2오버 준비는 있으나 기본 오프닝·럴커업 시작/완료·5럴커 변태/도착 시각 없음. 가스 확보와 링 동반·발각 시 분기 추가. | 본문 이미지 없음 |
| [숙지는 하고 있자! 111 대처법](https://sc1hub.com/boards/zvstboard/readPost?postNum=9) · zvstboard/9 | 대처법 / **P2** / 부분 보완 | 벌처 전 1성큰이라는 방향만 있음. 8배럭/팩더블/전진스타 분기별 확인 신호와 성큰 완성·대공·뮤탈 분배 기준 필요. | 본문 이미지 없음 |
| [3해처리 뮤탈 운영. 이제 안 쓰는 이유](https://sc1hub.com/boards/zvstboard/readPost?postNum=10) · zvstboard/10 | 빌드·역사 / **P1** / 부분 보완 | 빌드 순서에 레어가 빠진 채 스파이어로 진행하고 첫 오버도 생략 표시 없음. 선행 건물·시간표 복구. 이제 안 쓰는 빌드라는 주장은 시기·경기 근거로 제한. | alt 비어 있음 1개 / 근거 [S3](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| [안3햇? 밖3햇? 헛갈리는 저그 빌드 용어 정리](https://sc1hub.com/boards/zvstboard/readPost?postNum=12) · zvstboard/12 | 용어 설명 / **P3** / 유지 | 생산용/확장용 해처리와 레어 전후 구분이 명확. 현재 메타라는 표현에 기준 연도를 붙이고 2햇 운영 안의 세 번째 해처리라는 범위를 유지. | 본문 이미지 없음; 임베드 영상 없음 |
| [제일 쉬운 테란전 운영법! 미친 저그 빌드](https://sc1hub.com/boards/zvstboard/readPost?postNum=13) · zvstboard/13 | 빌드 / **P2** / 부분 보완 | 울트라 이전 공백을 버틸 성큰·스커지와 하이브/첫 울트라/방업 완료 시각이 없음. 이레디 맞은 울트라를 이득으로 일반화하지 말고 피해·아군 전염 및 교전 조건 설명. | 본문 이미지 없음 |
| [아니 벌써 뜬다고? 530 뮤탈 빌드](https://sc1hub.com/boards/zvstboard/readPost?postNum=14) · zvstboard/14 | 빌드 / **P2** / 부분 보완 | 530과 6뮤탈 목표는 명확. 풀/레어/스파이어 착공·완성·첫 뮤탈 완성을 영상 시각으로 연결하고 5:30 완성과 적진 도착을 구분. | alt 비어 있음 1개 |
| [날먹 킹팩골? 5팩 파워 골리앗 대처하기](https://sc1hub.com/boards/zvstboard/readPost?postNum=15) · zvstboard/15 | 대처법 / **P2** / 부분 보완 | 5팩 골리앗이면 아카데미가 거의 불가능하다는 단정과 버로우 링 안전 보장 수정. 3:30 성큰 완성 기준, 2멀티 동시 확장·빈집 조건에 상대 진출/터렛 확인 추가. | alt 비어 있음 1개 |
| [꼭 숙지필요!! 더러운 8배럭 막는 법](https://sc1hub.com/boards/zvstboard/readPost?postNum=16) · zvstboard/16 | 대처·컨트롤 / **P1** / 부분 보완 | 미네랄 이동을 공중판정으로 설명한 것은 충돌 무시와 공격 대상을 혼동시킴. 지상 일꾼의 유닛 충돌 예외로 교정. 드론 2기부터 불리/3~4기 포기식 단정도 상황별로 수정. | alt 비어 있음 1개 |
| [이제 필수테크닉!! 럴커 겹치기 하는 법](https://sc1hub.com/boards/zvstboard/readPost?postNum=17) · zvstboard/17 | 컨트롤 / **P3** / 부분 보완 | 겹치기 절차는 구체적. 럴커 위 오버로드는 클릭 방해일 뿐 자동으로 이레디를 대신 맞는 보호막은 아님을 명시하고 직접 선택 가능한 예외 추가. | alt 비어 있음 1개 |
| [별명이 가필패!? 가디언 쓰면 지는 이유](https://sc1hub.com/boards/zvstboard/readPost?postNum=18) · zvstboard/18 | 유닛 활용 / **P3** / 부분 보완 | 불리한 수비용과 적진 지형 활용을 구분한 점은 좋음. 긴 스타2 개발 유닛 일화는 별도 참고로 빼고 스타1 실전 사례·스커지 호위에 집중. | alt 비어 있음 1개 |
| [가필패? 가필승! 짭제의 초패스트 7분 가디언](https://sc1hub.com/boards/zvstboard/readPost?postNum=19) · zvstboard/19 | 빌드 / **P2** / 부분 보완 | 7분대 7가디언이라는 핵심 수치의 각 테크 완료·가스/뮤탈/변태 시점 검증 필요. 레이스·발키리 대응을 스커지/디바우러 추가 비용과 함께 설명. | alt 비어 있음 1개 |
| [BBS보다 무서운 전진 6배럭 막는 법](https://sc1hub.com/boards/zvstboard/readPost?postNum=20) · zvstboard/20 | 대처법 / **P2** / 부분 보완 | 드론 대규모 동원만 있고 정찰 시점·드론 분할·마린/SCV 우선순위·풀/링 생산 절차 부족. 미네랄 2기 고정을 상황별 기준으로 보완. | 본문 이미지 없음; 임베드 영상 없음 |
| [프로도 어렵다하는 히럴디파 운영법](https://sc1hub.com/boards/zvstboard/readPost?postNum=21) · zvstboard/21 | 운영 / **P2** / 부분 보완 | 히드라덴 속업·발업은 중복 표현으로 속업·사업인지 교정 필요. 저럴디파는 4가스 이후에만 강하다는 단정이 #3의 3가스 디파 설명과 충돌. 플레이그/컨슘 우선순위 추가. | alt 비어 있음 1개 |
| [저그도 전면전 잘한다! 가드라 빌드](https://sc1hub.com/boards/zvstboard/readPost?postNum=22) · zvstboard/22 | 빌드 / **P1** / 부분 보완 | 스파이어 이후 레어라는 순서는 선행 조건 역전. 레어→스파이어→퀸즈네스트/하이브→그레이터 스파이어 순서로 원 영상 대조. 첫 가디언·방업/히드라 준비 시각 추가. | alt 비어 있음 1개 / 근거 [S3](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| [알고 있어야함! BBS 대처법](https://sc1hub.com/boards/zvstboard/readPost?postNum=23) · zvstboard/23 | 대처법 / **P2** / 부분 보완 | BBS 발견 즉시 앞마당 포기·본진 2성큰을 모든 상황에 고정. 확인 시점·거리·풀/앞마당 완성 상태에 따른 유지/취소 분기를 추가. | alt 비어 있음 1개 |
| [갑자기 벌쳐가 왜 나와!? 레이트 메카닉 상대법](https://sc1hub.com/boards/zvstboard/readPost?postNum=24) · zvstboard/24 | 대처법 / **P3** / 부분 보완 | 전환·마인 제거·퀸 추가 흐름은 명확. 벌처당 마인 3개 한도와 재충전 불가를 명시하고 퀸 브루들링 연구·마나 준비 시간을 추가. | alt 비어 있음 1개 |

### 저저전 — 8개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [유독심한 상성빨, 저저전 빌드상성 정리](https://sc1hub.com/boards/zvszboard/readPost?postNum=2) · zvszboard/2 | 상성 모음 / **P2** / 부분 보완 | 업데이트 예정 링크 4개가 남음. 9풀류 안의 발업/레어 및 9투 분류를 명료화하고 정찰·거리 예외를 상성표에 함께 표시. | alt 비어 있음 1개 |
| [날아오는 스커지를 피하면서 잡아내는 뮤탈컨트롤](https://sc1hub.com/boards/zvszboard/readPost?postNum=3) · zvszboard/3 | 컨트롤 / **P2** / 부분 보완 | M컨 미작성 메모가 남음. 화면 밖 유닛이면 모두 뭉친다는 설명과 60/160도 수치는 재현 근거 필요. 홀드 시 실제 사거리 증가와 이동 특성을 구분. | 본문 이미지 없음 |
| [그냥 대충하고 싶은 사람들 위한 날먹 빌드](https://sc1hub.com/boards/zvszboard/readPost?postNum=4) · zvszboard/4 | 빌드 / **P2** / 전면 보완 | 제목은 쉬운 빌드 추천이나 추천 빌드 이름·순서가 본문에 없음. 영상의 전략을 특정하고 오프닝·첫 링·공격/중단 기준 작성. | 본문 이미지 없음; 영상 제목으로 9투 빌드는 특정 가능. 이를 본문에 적고 실제 순서 추출 필요. |
| [오버풀로 빌드상성극복하기](https://sc1hub.com/boards/zvszboard/readPost?postNum=5) · zvszboard/5 | 빌드 / **P2** / 전면 보완 | 오버풀 제목에 본문은 뮤탈/저글링 역할 세 문장뿐. 오프닝과 상대 9풀·12앞별 생산 분기를 추가. | 본문 이미지 없음 |
| [공격적이면서 안정적인 9레어 빌드](https://sc1hub.com/boards/zvszboard/readPost?postNum=6) · zvszboard/6 | 빌드 / **P2** / 부분 보완 | 13레어까지는 구체적이나 스파이어·추가 가스·첫 뮤탈/스커지·발업 분기와 타이밍 누락. 4드론/9발업이 12앞 확실히 이긴다는 단정 완화. | 본문 이미지 없음 |
| [충격과 공포? 저저전 히드라 전략!](https://sc1hub.com/boards/zvszboard/readPost?postNum=7) · zvszboard/7 | 빌드 / **P2** / 전면 보완 | 히드라 상성 소개만 있고 전략 실행법 없음. 해처리/가스/덴·속업/사업·대공 수비·첫 공격 시점을 영상 기준으로 정리. | 본문 이미지 없음; 영상 ZJHuCOI6Dh0: oEmbed 404 / 재생 확인 필요 |
| [초반만 잘 버티면 필승~12앞마당 빌드](https://sc1hub.com/boards/zvszboard/readPost?postNum=8) · zvszboard/8 | 빌드 / **P2** / 전면 보완 | 12앞 제목이지만 빌드 순서·링/성큰·레어/스포어 타이밍 없음. 9풀 대응과 선뮤탈 수비 후 역전 조건 추가; 필승 표현 완화. | 본문 이미지 없음 |
| [3분 정찰로 보는 저저전 빌드 판별법](https://sc1hub.com/boards/zvszboard/readPost?postNum=9) · zvszboard/9 | 정찰 / **P1** / 부분 보완 | 0~1분 오버로드로 상대 드론/풀을 확인하는 구간은 맵·거리별 도달 검증 필요. 적 저글링 알을 식별할 수 있다는 서술은 일반 플레이 시야 기준으로 재검증. 관전자 정보와 플레이어 정보 분리. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |

### 저프전 — 13개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [쉽지만 강력한 히드라웨이브! 973 빌드 사용법](https://sc1hub.com/boards/zvspboard/readPost?postNum=2) · zvspboard/2 | 빌드 / **P2** / 부분 보완 | 인구수·드론 배분·변형 분기는 충실. 첫 히드라/속업/사업 완료·7히드라 도착 시각과 근거 영상 추가. #14의 973 공1업 필수 주장과 일치 여부 정리. | alt 비어 있음 2개; 이미지 HTTP 404 |
| [기본정석 운영, 5해처리 히드라](https://sc1hub.com/boards/zvspboard/readPost?postNum=3) · zvspboard/3 | 빌드 / **P2** / 전면 보완 | 5해처리 정석인데 스포닝풀을 포함한 초반 인구수와 스파이어/첫 히드라/발질 수비 시각 없음. 히드라 10기 기준의 상대 병력 조건, 업그레이드 순서 추가. | 본문 이미지 없음 |
| [빠르다고 좋은 게 아냐? 하이브 운영](https://sc1hub.com/boards/zvspboard/readPost?postNum=4) · zvspboard/4 | 후반 운영 / **P3** / 부분 보완 | 고정 오프닝보다 하이브 전환 조건을 설명하는 글로 적절. 3업만이 아닌 아드레날린/디파일러 목적을 분리하고 울트라 비추천 적용 조건 보완. | 본문 이미지 없음; 영상 xw0blAyB9h4: oEmbed 403 / 재생 확인 필요 |
| [알아채기 쉽지않은 5뮤탈 운영](https://sc1hub.com/boards/zvspboard/readPost?postNum=5) · zvspboard/5 | 운영 / **P2** / 전면 보완 | 5뮤탈 생산을 위한 레어·스파이어·가스·라바 확보와 전환 시점 없음. 커세어 수·공업·스커지 호위에 따른 취소/전환 기준 추가. | 본문 이미지 없음 |
| [저그도 중요한 심시티 기본강의](https://sc1hub.com/boards/zvspboard/readPost?postNum=6) · zvspboard/6 | 심시티 자료 / **P2** / 전면 보완 | 투혼 영상 소개 세 문장에 의존. 스폰별 건물 배치·질럿/다크 차단 통로와 오버로드 탐지 위치를 본문에 요약. | 본문 이미지 없음 |
| [모르면 초반에 터짐. 투게이트 대처법](https://sc1hub.com/boards/zvspboard/readPost?postNum=7) · zvspboard/7 | 대처법 / **P2** / 전면 보완 | 질럿 수에 저글링을 맞추라는 설명만 있어 실행 기준 부족. 선풀/선앞별 초기 링 생산·성큰·드론 동원과 포위 위치 추가. | 본문 이미지 없음 |
| [기본 운영을 위한 선게이트 대처법](https://sc1hub.com/boards/zvspboard/readPost?postNum=8) · zvspboard/8 | 대처법 / **P2** / 전면 보완 | 선게이트가 까다로운 이유만 설명하고 대응 절차 없음. 첫/추가 질럿 확인→링 생산→드론 복귀 조건과 심시티 예시 필요. | 본문 이미지 없음 |
| [토스 유일한 사기빌드? 8겟뽕 대처](https://sc1hub.com/boards/zvspboard/readPost?postNum=9) · zvspboard/9 | 대처법 / **P2** / 부분 보완 | 템플러 저격 방향은 있으나 8겟 확인 시점·드론 중단·병력 확보 기준 없음. 뮤탈 한 부대 전멸을 감수하는 조건에 잔여 히드라/커세어·다크아칸 상태 명시. | 본문 이미지 없음 |
| [973보다 더 날먹? 9투 올인 빌드](https://sc1hub.com/boards/zvspboard/readPost?postNum=10) · zvspboard/10 | 빌드 / **P2** / 부분 보완 | 초반 순서는 있으나 첫 링·2해처리 생산·도착 시점과 포지더블 확인 후 중단 기준 없음. 19 세 번째 해처리의 목적/위치·후속 자원 계획 추가. | 본문 이미지 없음 |
| [제공권? 뺏으면 그만이야~ 뮤커지 운영](https://sc1hub.com/boards/zvspboard/readPost?postNum=11) · zvspboard/11 | 운영 / **P2** / 부분 보완 | 6뮤탈+스커지 구성은 있으나 생산 순서·가스/라바 예산·첫 출발 시각 없음. 커세어 5기 임계점을 공업·컨트롤 조건과 구분. | alt 비어 있음 1개 |
| [스톰만 없으면 내가 이겨! 뮤탈 히드라 운영법](https://sc1hub.com/boards/zvspboard/readPost?postNum=12) · zvspboard/12 | 운영 / **P3** / 부분 보완 | 히드라/뮤탈 역할과 분산 운용은 명확. 뮤탈을 전부 잃어도 이득이라는 부분은 남은 적병력·저격 성공량 조건으로 제한; 거이 오기 수정. | alt 비어 있음 1개 |
| [뭐가 나올까나~? 짭제식 야바위 운영](https://sc1hub.com/boards/zvspboard/readPost?postNum=13) · zvspboard/13 | 빌드·심리전 / **P2** / 부분 보완 | 개략 분기는 좋지만 두 테크의 착공·첫 병력·삼룡이 시각과 가스 예산이 없음. 상대가 본 건물에 따른 자동 선택처럼 읽히지 않게 실제 수비 확인을 우선. | alt 비어 있음 1개 |
| [공업이냐 방업이냐? 저프전 저그 업그레이드 순서 정리](https://sc1hub.com/boards/zvspboard/readPost?postNum=14) · zvspboard/14 | 업그레이드 / **P1** / 부분 보완 | 973은 원공1업 먼저라는 일반화가 #2의 빠른 히드라 빌드와 충돌하며 근거 없음. 초반 챔버 투자로 공격이 늦는 변형을 분리. 타격 횟수는 공방업·실드·회복 미반영 조건 명시. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |

### 프테전 — 14개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [쉽고 안정적인 정석. 23넥 아비터 운영 빌드](https://sc1hub.com/boards/pvstboard/readPost?postNum=2) · pvstboard/2 | 빌드 / **P2** / 부분 보완 | 기준 글도 예외 아님. 긴 인구수 줄을 초반/중반으로 분리하고 첫 아비터·리콜 준비를 추가. 5팩 공1업 변형·상대 5:00 마인/12:10 21업을 범용 확정 시각처럼 쓰지 않게 조건·영상 연결. | 본문 이미지 없음 |
| [테란 개거품물게 하는 대각 생넥 캐리어 ㅋㅋ](https://sc1hub.com/boards/pvstboard/readPost?postNum=3) · pvstboard/3 | 빌드 / **P2** / 부분 보완 | 30사업 이후 40파일런 사이 공급 보충이 생략됐고 옵저버토리·캐리어 수용량 업도 빠짐. 첫 캐리어/4캐리어 시각, 대각 거리와 상대 압박 조건 검증. | alt 비어 있음 1개 |
| [꼴보기 싫은 배럭더블 참교육! 전진로보 전략](https://sc1hub.com/boards/pvstboard/readPost?postNum=4) · pvstboard/4 | 빌드 / **P2** / 부분 보완 | 전진 로보 의도는 명확하지만 인구수·서포트베이·첫 리버 도착 시각과 스캐럽 예산 없음. 전진 건설 가능 위치·발각 후 분기 필요. | 본문 이미지 없음 |
| [프테전 리버 후 속셔템 운영, 셔틀은 어떻게 써야 할까?](https://sc1hub.com/boards/pvstboard/readPost?postNum=5) · pvstboard/5 | 운영 / **P3** / 부분 보완 | 출발·전환·교전·대응까지 충실하고 조건부 시간 범위도 있음. 7분 아둔/10~11분 교전 목표를 실제 영상 타임스탬프와 연결하고 리버·스톰·속업 가스 예산 검증. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [테란의 무서운 초반러쉬, 투팩 대처법](https://sc1hub.com/boards/pvstboard/readPost?postNum=6) · pvstboard/6 | 대처법 / **P2** / 부분 보완 | 3:40/4:00 앞마당 부재의 대안 빌드와 확인 조건 명시. 앞마당 프로브 전멸해도 유리라는 단정은 양측 손실·후속 커맨드 기준으로 수정. | 본문 이미지 없음 |
| [때리고 시작하는 선질럿 찌르기 운영](https://sc1hub.com/boards/pvstboard/readPost?postNum=7) · pvstboard/7 | 빌드 / **P2** / 전면 보완 | 2질럿·26넥 외 인구수 순서와 첫/두 번째 질럿·사업·확장 시각 없음. 가스보다 파일런을 먼저 짓는 정확한 공급 단계와 상대 팩더블 대응 추가. | 본문 이미지 없음 |
| [테란을 당황시키자! 다크드랍 전략](https://sc1hub.com/boards/pvstboard/readPost?postNum=8) · pvstboard/8 | 빌드 / **P2** / 전면 보완 | 다크드랍의 효과만 설명하고 로보/아둔/템카/셔틀/다크 순서와 첫 드랍·확장 시각이 없음. 탐지 확인 후 철수·후속 스톰 기준 추가. | 본문 이미지 없음 |
| [프로토스의 로망 캐리어 빌드 사용법](https://sc1hub.com/boards/pvstboard/readPost?postNum=10) · pvstboard/10 | 빌드 모음·컨트롤 / **P2** / 부분 보완 | 캐리어 변형은 한 문장씩 소개에 그침. 대표 빌드 1개를 실행 가능하게 정리하고 캐리어 수용량 업·인터셉터 비용/충원 조건 추가. 컨트롤 유지 조건 영상 확인. | alt 비어 있음 1개 |
| [테란의 원기옥? 5팩 타이밍 막는 법!](https://sc1hub.com/boards/pvstboard/readPost?postNum=11) · pvstboard/11 | 대처법 / **P2** / 부분 보완 | 무업 5팩/공1업 5팩·6팩을 구분하지 않아 대응 마감이 모호. 상대 출발 전 8게이트 완성·병력 생산에 필요한 시각과 정찰 실패 시 분기 추가. | 본문 이미지 없음 |
| [프테전 기본 개념. 테란 상대로 어떻게 싸울까?](https://sc1hub.com/boards/pvstboard/readPost?postNum=12) · pvstboard/12 | 기본 운영 / **P3** / 부분 보완 | 확장으로 상대를 움직이게 한다는 개념은 명확. 종족 사기성 반복을 줄이고 확장/견제/수비를 고르는 실전 사례 2~3개 추가. | 본문 이미지 없음 |
| [짤막 성공! 테란 초반 찌르기 수비 후엔 뭘할까?](https://sc1hub.com/boards/pvstboard/readPost?postNum=13) · pvstboard/13 | 수비 후 운영 / **P1** / 부분 보완 | 초반 병력을 잃은 테란은 2차 러시 절대 못 온다는 단정이 위험. 잔여 팩토리·탱크·앞마당·후속 병력 확인 후 트리플/수비를 나누도록 교정. | 본문 이미지 없음; 임베드 영상 없음 |
| [최신 프테전 정석? 나름 안전한 19넥 빌드](https://sc1hub.com/boards/pvstboard/readPost?postNum=14) · pvstboard/14 | 빌드 / **P2** / 부분 보완 | 19넥 오프닝은 있으나 넥서스/사업/추가 드라군·옵저버 시각과 FD 수비 체크포인트 없음. 최신·요즘 맵이라는 표현에 기준 연도와 실제 맵 적용 범위 추가. | alt 비어 있음 1개 |
| [막고 나면 이겨있다! 생넥 후 치즈 러쉬 대처](https://sc1hub.com/boards/pvstboard/readPost?postNum=15) · pvstboard/15 | 대처법 / **P2** / 부분 보완 | 치즈 수비를 차근차근 대응으로 끝내지 말고 프로브 동원·벙커 차단·질럿/드라군 합류 순서를 명시. 대각 무적·투팩 땡큐 같은 표현은 실제 조건으로 제한. | alt 비어 있음 1개 |
| [프테전 패스트 아비터, 첫 아비터는 몇 분에 나와야 할까?](https://sc1hub.com/boards/pvstboard/readPost?postNum=16) · pvstboard/16 | 빌드 / **P2** / 부분 보완 | 29아비터·노옵·옵 포함의 완성/리콜 시간을 구분한 점은 좋음. 6:30/9:30/10분/12분 근거 영상과 생산·마나 조건 검증, 첫 스테이시스 연구·에너지 준비를 순서에 추가. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |

### 프저전 — 21개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [선게이트 운영법](https://sc1hub.com/boards/pvszboard/readPost?postNum=2) · pvszboard/2 | 빌드·운영 / **P2** / 전면 보완 | 선게이트의 이유·난이도만 있고 첫 질럿/포지·넥서스·캐논·코어 순서와 정찰별 분기 없음. 대표 오프닝 및 초반 링 수비 마감 작성. | 본문 이미지 없음 |
| [필독하세욧! 973 빌드 대처](https://sc1hub.com/boards/pvszboard/readPost?postNum=3) · pvszboard/3 | 대처법 / **P2** / 부분 보완 | 973 확인·캐논·커세어 역할은 설명함. 노발업 5:30만 기다리면 늦을 수 있으므로 첫 히드라 도착 전 완성 기준과 맵별 4캐논 배치·추가 조건 필요. | 본문 이미지 없음 |
| [대저그전 중반 정석 빌드, 커공발 운영법](https://sc1hub.com/boards/pvszboard/readPost?postNum=4) · pvszboard/4 | 빌드·운영 / **P2** / 부분 보완 | 변수 때문에 건물 순서만 제시했으나 기준 오프닝 하나의 첫 커세어·공1업/발업·스톰 시각은 필요. 스파이어면 무조건 5커세어 대신 실제 생산 관찰; 업데이트 예정 링크 정리. | 본문 이미지 없음 |
| [안전하게 시작하자! 포지 더블 운영](https://sc1hub.com/boards/pvszboard/readPost?postNum=5) · pvszboard/5 | 빌드 / **P2** / 부분 보완 | 12앞 분기가 캐논러시 링크뿐이라 정상 확장 대응이 없음. 넥서스/캐논 선후·완성 마감과 코어 이후 연결, 포지더블을 무조건 안전으로 읽지 않게 맵 조건 추가. | 본문 이미지 없음 |
| [암기필수! 토스 저그전 심시티 모음(2024 시즌1)](https://sc1hub.com/boards/pvszboard/readPost?postNum=6) · pvszboard/6 | 심시티 자료 / **P2** / 부분 보완 | 2024 시즌 자료로 보존하되 트로이 업데이트 예정·블리츠 질럿 누락을 해결. 이미지마다 맵 버전·스폰·통과 가능 유닛·질럿 위치 설명과 대체텍스트 필요. | 임베드 영상 없음; alt 비어 있음 27개 |
| [투게이트 시작 운영 플레이](https://sc1hub.com/boards/pvszboard/readPost?postNum=7) · pvszboard/7 | 빌드 / **P2** / 전면 보완 | 투게이트 운영이라는 제목에 두 문장뿐. 99/1012 구분·첫 질럿·멀티/포지·상대 링 대응을 영상에서 추출해 작성. | 본문 이미지 없음 |
| [알고도 못 막는다는 강력한 8겟뽕 빌드](https://sc1hub.com/boards/pvszboard/readPost?postNum=8) · pvszboard/8 | 빌드 / **P2** / 부분 보완 | 10분·4템·드라군 수는 있으나 인프라 착공·스톰/옵저버 완성·프로브 수가 없음. 8게이트 두 바퀴=16드라와 1부대 반=18드라 차이를 정리; 무조건 승리형 문구 완화. | 본문 이미지 없음 |
| [저그전 쉽게 가자~ 1012리버 전략](https://sc1hub.com/boards/pvszboard/readPost?postNum=9) · pvszboard/9 | 빌드 / **P2** / 부분 보완 | 15파일런 뒤 25가스·30코어까지 추가 파일런이 생략돼 공급 한도와 충돌. 생략 항목 명시와 첫 리버/셔틀·뮤탈 위협·확장 시각 추가. | 본문 이미지 없음; 영상 yGL5GmmQKao: oEmbed 403 / 재생 확인 필요 |
| [혁명이었던 커세어 다크. 요즘 안 쓰는 이유](https://sc1hub.com/boards/pvszboard/readPost?postNum=10) · pvszboard/10 | 전략사 / **P3** / 부분 보완 | 커세어다크 유래와 쇠퇴 설명으로 목적 정리. 요즘 안 쓴다는 단정에 기준 연도·경기 사례를 붙이고 후반 다크 활용 글과 연결. | 본문 이미지 없음 |
| [엽기전략 아님! 저그전 다크 아칸 활용법](https://sc1hub.com/boards/pvszboard/readPost?postNum=11) · pvszboard/11 | 전술 / **P2** / 부분 보완 | 마엘스트롬 활용은 구체적이나 합체 후 초기 에너지·마법 비용/연구·첫 사용 가능 시각 없음. 뮤탈 공격 직전 급히 만들면 못 쓰는 공백을 안내. | 본문 이미지 없음 |
| [커세어 쓰기 힘들죠? 더 쉬운 운영 선아둔 빌드](https://sc1hub.com/boards/pvszboard/readPost?postNum=12) · pvszboard/12 | 빌드 / **P3** / 부분 보완 | 인구수·시간표·오버풀 조건·973 분기가 비교적 충실. 각 시간의 출발/완료를 표시하고 6분 뮤탈 위협·캐논/아콘 준비를 첨부 장면과 연결. | 본문 이미지 없음 |
| [저그전? 귀찮아~! 센터 99게이트](https://sc1hub.com/boards/pvszboard/readPost?postNum=13) · pvszboard/13 | 빌드 / **P2** / 부분 보완 | 오프닝은 있으나 첫 질럿/3질럿 도착·일꾼 중단/공급 추가가 없음. 12앞이면 발견해도 포기라는 확정 승패는 맵·정찰·컨트롤 조건으로 제한. | 본문 이미지 없음 |
| [제발 좀 나가라~저그전 초장기전 운영법](https://sc1hub.com/boards/pvszboard/readPost?postNum=14) · pvszboard/14 | 후반 운영 / **P3** / 부분 보완 | 반땅 자원전·가스·아콘/리버 이유는 충분. 드라군 생산 중단·커세어 제외를 상대 공중 전환에 따라 조정하고 다크스웜 피해 예외를 정확히 표기. | 본문 이미지 없음 |
| [필독!! 토스 저그전 심시티 모음(2024 시즌2)](https://sc1hub.com/boards/pvszboard/readPost?postNum=15) · pvszboard/15 | 심시티 자료 / **P3** / 부분 보완 | 2024 시즌2 자료로 보존. 맵 버전과 각 이미지의 질럿 위치·통과 규칙·대체텍스트를 보강하고 최신 시즌 안내로 연결. | 임베드 영상 없음; alt 비어 있음 29개 |
| [오버로드 냠냠~투스타 커세어 전략](https://sc1hub.com/boards/pvszboard/readPost?postNum=16) · pvszboard/16 | 빌드 / **P2** / 부분 보완 | 투스타 착공·공1업·6~7커세어·스톰 지연 시각 없음. 두 번째 스타와 아둔 선택을 상대 뮤탈/히드라 관찰로 연결. | 본문 이미지 없음 |
| [깔끔하게 정리해보자~ 프저전 빌드 로드맵](https://sc1hub.com/boards/pvszboard/readPost?postNum=17) · pvszboard/17 | 빌드 모음 / **P3** / 유지 | 초중후반 선택과 상세 글 연결이 명확해 별도 빌드 반복 불필요. 연결 글 보완 후 초보/숙련 추천과 시즌 기준만 정리. | 임베드 영상 없음; alt 비어 있음 1개 |
| [얍삽이 아니고 스킬인데요? 캐논 러쉬](https://sc1hub.com/boards/pvszboard/readPost?postNum=18) · pvszboard/18 | 전술·심시티 / **P2** / 부분 보완 | 지형 의존 전략인데 구체적 건설 위치·필요 자원·취소/철수 기준 없음. 12앞이면 캐논러시 필수라는 단정을 선택지로 수정. | 본문 이미지 없음 |
| [저그전 공방업 업그레이드 순서](https://sc1hub.com/boards/pvszboard/readPost?postNum=19) · pvszboard/19 | 업그레이드 / **P1** / 부분 보완 | 히드라 6방→5방은 방어·재생 조건에 따라 달라지며 이를 적지 않음. 플레이그가 모든 체력을 1로 만든다는 설명은 피해 상한/실드 보호를 구분. 공1→방3·실드 로드맵도 상대 조합에 따라 조건화. | 본문 이미지 없음; 임베드 영상 없음 |
| [필독! 토스 저그전 심시티 모음(2025 시즌1)](https://sc1hub.com/boards/pvszboard/readPost?postNum=20) · pvszboard/20 | 심시티 자료 / **P3** / 부분 보완 | 2025 시즌1 역사 자료로 보존. 시간형 섬맵 예외 설명을 구체화하고 버전·이미지 대체텍스트·현재 맵 안내를 추가. | 임베드 영상 없음; alt 비어 있음 31개 |
| [저그전도 쉽게 가자~! 파워 드라군 빌드](https://sc1hub.com/boards/pvszboard/readPost?postNum=21) · pvszboard/21 | 빌드 / **P2** / 부분 보완 | 상대 오버풀 2링 조건·공급은 있으나 첫 5드라/공1업/사업·출발 시각 없음. 973 3캐논 대응은 히드라 규모·지형과 비교해 #3의 4캐논 기준과 구분. | alt 비어 있음 1개 |
| [대충 외워! 토스 저그전 심시티 2025 시즌2](https://sc1hub.com/boards/pvszboard/readPost?postNum=22) · pvszboard/22 | 심시티 자료 / **P3** / 부분 보완 | 2025 시즌2 자료로 보존. 맵별 버전·방향·질럿 배치 설명을 추가하고 현재 시즌 공략으로 착각하지 않게 시점 표시. | 임베드 영상 없음; alt 비어 있음 35개 |

### 프프전 — 11개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [안정적인 프프전 정석 운영, 기어리버 빌드](https://sc1hub.com/boards/pvspboard/readPost?postNum=2) · pvspboard/2 | 빌드 / **P2** / 부분 보완 | 순서·리버 수비·상성 설명은 충실. 첫 옵저버/리버·앞마당·3겟 도착 시각 추가, 3겟을 막는 기준 맵/입구 조건 명시. | alt 비어 있음 1개 |
| [동족전에서 더 중요한 프프전 빌드 상성 정리](https://sc1hub.com/boards/pvspboard/readPost?postNum=4) · pvspboard/4 | 상성 모음 / **P3** / 부분 보완 | 주요 글 연결은 좋으나 다크더블 vs 기어리버를 본문 앞뒤에서 다르게 평가. 첫 압박/확장 이후로 구간을 분리하고 압승 표현을 조건부로 수정. | 임베드 영상 없음; alt 비어 있음 1개 |
| [투게이트 대처, 질럿 찌르기 막는 법!](https://sc1hub.com/boards/pvspboard/readPost?postNum=5) · pvspboard/5 | 대처법 / **P3** / 부분 보완 | 99/1012 차이와 배터리 위치·후속 정찰이 구체적. 첫 드라군/3질럿 도착 관계의 맵별 근거와 공급·시각만 보강. | 본문 이미지 없음 |
| [올인이지만 기본 빌드. 21 3게이트 드라군](https://sc1hub.com/boards/pvspboard/readPost?postNum=6) · pvspboard/6 | 빌드 / **P2** / 부분 보완 | 드라군 사업이 순서에 없고 29 이후 파일런 배제한 채 계속 생산하라고 해 공급 한도가 불명확. 고정 공격 병력과 생산 중단/추가 파일런 조건, 출발 시각 명시. | alt 비어 있음 1개 |
| [부유한 정석빌드, 원겟 멀티 3겟 운영](https://sc1hub.com/boards/pvspboard/readPost?postNum=7) · pvspboard/7 | 빌드 / **P2** / 부분 보완 | 첫 파일런이 빠졌으나 생략 표시 없음. 30넥·7드라·첫 옵저버 목표와 다크 탐지 마감, 프로브 중단/복귀 조건을 보완. | alt 비어 있음 1개 |
| [의외로 잘 먹히는 다크 더블 전략](https://sc1hub.com/boards/pvspboard/readPost?postNum=8) · pvspboard/8 | 빌드 / **P2** / 부분 보완 | 첫 파일런 누락, 다크·넥서스·캐논/스톰 시각 없음. 1다크 예시와 2다크 권장의 자원·시간 차이를 분리하고 문체 통일. | 본문 이미지 없음 |
| [난 그냥 프프전 하기 싫다. 센터99게이트](https://sc1hub.com/boards/pvspboard/readPost?postNum=9) · pvspboard/9 | 빌드 / **P1** / 부분 보완 | 서두는 프로브 9마리를 맵 중앙에 활용한다고 설명하지만 실제는 인구 9에 게이트 2개, 전진 프로브 1기. 6번째로 생산한 프로브와 인구수 6의 의미도 구분해 교정. | 본문 이미지 없음; 임베드 영상 없음 |
| [쟤 멀티 완전 빠른데 어쩌지? 포지 더블 대처](https://sc1hub.com/boards/pvspboard/readPost?postNum=10) · pvspboard/10 | 대처법 / **P3** / 부분 보완 | 리버 압박/따라 확장 두 선택은 명확. 2캐논 비용은 300, 넥서스는 400이라 대략 비교임을 표시하고 경제 우위는 상대 프로브·확장 활성화 시점으로 판단. | 본문 이미지 없음; 임베드 영상 없음 |
| [본진입구 지형 따라 기어리버 대신 쓸 빌드](https://sc1hub.com/boards/pvspboard/readPost?postNum=11) · pvspboard/11 | 맵·빌드 선택 / **P3** / 유지 | 입구 지형에 따른 기어리버/옵3겟/3겟 선택과 관련 글 연결이 목적에 맞음. 도미네이터 버전·스폰과 실제 배치 장면 보강 정도. | 임베드 영상 없음; alt 비어 있음 1개 |
| [제일 안전한 프프전 빌드, 옵3겟 운영](https://sc1hub.com/boards/pvspboard/readPost?postNum=12) · pvspboard/12 | 빌드 / **P2** / 부분 보완 | 옵3겟 초반 공급 순서는 있으나 첫 옵저버·상대 3겟 수비·리버/앞마당 전환 시각 없음. 프로브 생산 재개 조건과 병력 손실 시 분기 추가. | alt 비어 있음 1개 |
| [아 쫌 쏘라고!!! 리버 버그자리에 대한 분석](https://sc1hub.com/boards/pvspboard/readPost?postNum=13) · pvspboard/13 | 게임 메커니즘 / **P3** / 부분 보완 | 영상 4:35 근거는 있으나 리전 2~3개를 넘으면 공격 불가라는 기술 설명의 재현 조건 필요. 무조건 멈춤/특정 지형 경로 실패를 구분하고 스캐럽 비용 명시. | alt 비어 있음 1개 |

### 팀플 — 15개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [헌터 저그 기초 운영](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=2) · teamplayguideboard/2 | 빌드 / **P3** / 유지 | 헌터 3대3·9풀 정의와 영상 시각/게임 시각을 구분해 설명함. 현 구성 유지; 인용한 실측과 영상 장면의 일치 여부는 별도 재생 검증 대상으로 남김. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [빨무 종족 티어와 기본 운영](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=3) · teamplayguideboard/3 | 종족 선택 / **P3** / 부분 보완 | 빨무 P>T>Z를 객관적 순위처럼 단정하지 말고 인원·맵·실력대와 작성자 의견임을 표시. 종족별 상세 빌드 연결은 유지. | 본문 이미지 없음; 임베드 영상 없음 |
| [빨무 프로토스 정석 운영](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=4) · teamplayguideboard/4 | 빌드 / **P2** / 부분 보완 | 인구수 순서는 있으나 첫 병력·코어·스톰·셔틀 완료 시점과 아카이브/서포트베이 선행 조건이 부족. 질럿 유지와 테크 전환 분기 추가. | 본문 이미지 없음 |
| [빨무 테란 정석 운영](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=5) · teamplayguideboard/5 | 빌드 / **P2** / 부분 보완 | 3배럭→5배럭→메카닉의 시간 기준과 전환 비용 추가. 5분 전 터렛은 착공/완성 구분; 머신샵·아머리·컨트롤타워 조건 보완. | 본문 이미지 없음 |
| [빨무 저그 정석 운영](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=6) · teamplayguideboard/6 | 빌드 / **P2** / 부분 보완 | 7해처리 노저글링은 팀 방어·안전한 자리 조건을 먼저 명시. 첫 히드라 도착, 드론/라바 배분, 초반 공격을 받았을 때 중단 조건 추가. | 본문 이미지 없음 |
| [헌터 팀플 종족 티어와 기본 개념](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=7) · teamplayguideboard/7 | 종족 선택 / **P3** / 부분 보완 | 헌터 Z>P>T와 올저그 대 올테란 평가는 조합·자리·실력 조건 없이 일반화됨. 경험적 추천으로 표시하고 통신·초반 역할 설명은 유지. | 본문 이미지 없음 |
| [헌터 토스 3게이트 빌드(21쓰리게이트)](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=8) · teamplayguideboard/8 | 빌드 / **P2** / 전면 보완 | 3게이트 순서만으로는 연습하기 부족. 추가 파일런, 첫 3/6질럿 합류 시점, 프로브 재개와 가스·테크 전환을 보충. | 본문 이미지 없음 |
| [헌터 테란 기본 정석 빌드와 운영법](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=9) · teamplayguideboard/9 | 빌드 / **P2** / 부분 보완 | 2배럭 아카의 첫 메딕·스팀·스캔·합류 시각 추가. 벙커는 고정 필수 대신 위치와 적 조합별 선택으로 구분. | 본문 이미지 없음 |
| [빨무 잘하는 법 정리](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=10) · teamplayguideboard/10 | 맵 기초 / **P2** / 부분 보완 | 6인용이고 6시/12시가 없다는 설명은 해당 빨무 맵에 한정됨. 정확한 맵명·버전·인원과 배치도를 붙여 다른 빨무 변형과 구분. | alt 비어 있음 1개 |
| [빨무 프로토스 초보용 빌드! 공발업 질럿 빌드](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=11) · teamplayguideboard/11 | 빌드 / **P2** / 부분 보완 | 66 시타델까지의 인구수 외에 첫 공격·발업 완료 시점 추가. 러커를 보고 드라군을 준비해도 된다는 설명에는 사전 정찰과 옵저버/팀 탐지 조건이 필요. | alt 비어 있음 1개 |
| [헌터 팀플, 합류할까 역공할까?](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=12) · teamplayguideboard/12 | 팀 판단 / **P3** / 부분 보완 | 세 질문 중 두 개가 합류 쪽이라는 판정에서 세 번째 질문은 응답 방향이 반대여서 혼동됨. 질문별 합류/빈집 답을 명시하고 초기 전원 합류와 이후 별동대 운영 구분. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [헌터 팀플 초반 러시 막는 법](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=13) · teamplayguideboard/13 | 초반 방어 / **P3** / 부분 보완 | 20초를 버티면 역전은 지원군 도착을 전제로 한 예시로 표시. 다색 병력만으로 상대의 일꾼·테크 희생을 확정하지 말고 실제 병력량과 정찰을 함께 판단. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [빨무 프로토스 물량이 안 나오는 이유](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=14) · teamplayguideboard/14 | 생산 운영 / **P1** / 부분 보완 | 프로브가 게이트 건설 시간 내내 묶인다는 설명을 소환 개시 후 복귀로 수정. 여러 게이트를 기존 생산 그룹에 함께 묶는 문구는 스타1 조작에 맞게 개별 단축키/화면 생산으로 교체. 인구수는 유닛 완성 때가 아니라 생산 시작 때 확보되는 점도 수정. | 하단 영상은 3포지 빌드. 본문의 기본 생산 주기 설명과 직접 연결되는 장면 확인 필요. / 근거 [S2](https://classic.battle.net/scc/protoss/units/probe.shtml) |
| [빨무 테란 메카닉 빌드, 언제 전환해야 할까?](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=15) · teamplayguideboard/15 | 전환 판단 / **P3** / 부분 보완 | 바이오닉 유지와 메카닉 전환의 조건은 유용해 현 구조 유지. 기존 초반 빌드 연결과 팩토리·머신샵·탱크에 필요한 가스 여유를 예시로 보완. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |
| [빨무 저그가 유독 어렵게 느껴지는 이유](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=16) · teamplayguideboard/16 | 팀 운영 / **P3** / 부분 보완 | 병력 합류와 생산 균형 설명은 유지. 나이더스는 하이브와 출구 크립이 필요하므로 타 종족 팀원 기지에 바로 연결할 수 있는 것처럼 읽히지 않게 조건 추가. | 접근 검사 특이사항 없음; 실제 재생·배치는 별도 |

### 꿀팁 — 24개

| 글 | 유형 / 우선순위 / 범위 | 검수 의견 | 미디어·근거 |
|---|---|---|---|
| [나에게 제일 어울리는 종족은? 스타1 종족 추천](https://sc1hub.com/boards/tipboard/readPost?postNum=2) · tipboard/2 | 종족 선택 / **P2** / 부분 보완 | 입문 난도·고수 단계 난도·승률을 구분. 프로 수준 T>Z>P는 근거 없는 고정 순위로 제시하지 말고 실력대·맵별 조건과 개인적 평가를 표시. | 임베드 영상 없음; alt 비어 있음 1개 |
| [고인물 속 생존기! 스타 실력 늘리는 법 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=3) · tipboard/3 | 연습법 / **P3** / 부분 보완 | 상대 종족별 빌드 하나와 리플레이 복기 방식은 유지. 한 빌드만 하면 특정 등급에 도달한다는 표현은 개인차가 있는 연습 목표로 완화하고 실습 글 링크 추가. | 임베드 영상 없음; alt 비어 있음 1개 |
| [스타1 빌드 타임 - 건물, 유닛, 업그레이드 시간 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=4) · tipboard/4 | 시간 자료 / **P1** / 부분 보완 | 저그 업그레이드 표에서 168·188·208초 행이 모두 1단계로 표기됨. 단계명을 바로잡고 Fastest 시계·프레임 환산·반올림 기준을 통일. 그레이터 스파이어 등 누락과 스타웃 오타 점검. | 임베드 영상 없음; alt 비어 있음 1개 |
| [APM, 손속도, 멀티태스킹 늘리는 법](https://sc1hub.com/boards/tipboard/readPost?postNum=5) · tipboard/5 | 연습법 / **P3** / 부분 보완 | APM과 실력의 차이는 유용하나 같은 설명이 반복됨. 생산 확인→미니맵→병력 확인 같은 구체적인 연습 한 회차로 압축. | 본문 이미지 없음 |
| [시대를 바꾼, 빌드 혁명 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=6) · tipboard/6 | 전략사 / **P2** / 부분 보완 | 빌드의 창시자·첫 사용 연도·유행 시기를 구분. 111·2해처리 등 역사적 주장마다 당시 경기나 인터뷰를 연결하고 최초가 불확실한 항목은 단정하지 않기. | 본문 이미지 없음 |
| [스타1 옵션 설정 공략](https://sc1hub.com/boards/tipboard/readPost?postNum=7) · tipboard/7 | 환경 설정 / **P2** / 부분 보완 | FPS 100 등 고정 권장값에 하드웨어·모니터 조건이 없음. 화면 프레임과 게임 진행 속도를 분리하고 현재 리마스터 메뉴 기준으로 옵션명·필러박스 설명 확인. | 임베드 영상 없음; alt 비어 있음 5개 |
| [스타크래프트 치트키 모음. 그리고 입력문구 의미](https://sc1hub.com/boards/tipboard/readPost?postNum=8) · tipboard/8 | 치트 자료 / **P3** / 부분 보완 | 대표 치트 목록으로는 충분. 치트 사용 시 빌드 시간 연습이 달라지는 점과 싱글 사용 범위 표시. 명칭 유래는 추측과 확인된 사실을 구분. | alt 비어 있음 1개; 하단 영상 제목은 스타2 공허의 유산 시네마틱. 치트 사용법의 근거 영상과 구분 필요. |
| [래더맵 업데이트! 2024 시즌2 래더맵 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=9) · tipboard/9 | 시즌 맵 / **P3** / 부분 보완 | 2024 시즌2 기록으로 유지하고 현재 시즌 글로 연결. 밴 추천은 종족 승률 통계가 아닌 적응 난도·개인적 판단임을 표시; 배포 맵 버전 확인. | 임베드 영상 없음; alt 비어 있음 9개 |
| [작은 차이가 큰 차이를 만든다! 미네랄 부스팅 방법](https://sc1hub.com/boards/tipboard/readPost?postNum=10) · tipboard/10 | 컨트롤 / **P2** / 부분 보완 | 미네랄 부스팅의 개념만으로 재현하기 어려움. 한 종족·한 맵에서 클릭 순서와 성공/실패 장면을 붙이고 가속 설명을 실제 동작 근거로 검증. | alt 비어 있음 1개 |
| [필독! 스타크래프트 마우스, 키보드 추천](https://sc1hub.com/boards/tipboard/readPost?postNum=11) · tipboard/11 | 장비 자료 / **P2** / 부분 보완 | 장비 가격·선수 사용·수치에 확인 날짜와 제조사/당사자 근거가 없음. FC750 등 정확한 모델·유무선 변형을 구분하고 가격과 사용 장비는 현행 여부 재확인. | alt 비어 있음 1개 |
| [새해 스타 많이! 스타 래더 맵 2025 시즌1 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=12) · tipboard/12 | 시즌 맵 / **P3** / 부분 보완 | 2025 시즌1 자료로 보존하고 최신 시즌 안내로 연결. 맵 버전과 다운로드 출처를 표시하고 밴 의견을 통계와 구분. | 임베드 영상 없음; alt 비어 있음 9개 |
| [스타크래프트1 무료 다운로드 사이트](https://sc1hub.com/boards/tipboard/readPost?postNum=13) · tipboard/13 | 구매·설치 / **P1** / 부분 보완 | 오리지널 무료화 시기를 2017년 8월로 설명하나 공식 1.18 무료화는 2017년 4월. 리마스터 출시와 구분하고 판매 가격은 현재 공식 상점·확인 날짜 기준으로 재확인. | 임베드 영상 없음; alt 비어 있음 3개 / 근거 [S5](https://news.blizzard.com/en-us/article/20674424/starcraft-brood-war-patch-1-18-patch-notes) |
| [2016~2024 스타1 종족 밸런스 통계(공식전 기준)](https://sc1hub.com/boards/tipboard/readPost?postNum=14) · tipboard/14 | 통계 / **P2** / 부분 보완 | 원 출처는 2016~2024 ASL/SSL 18시즌·KSL 4시즌 2,107경기의 프로 오프라인 자료임을 명시. 래더 전체로 일반화하지 말고 승률과 우승 수 구분; 마지막 밸런스 패치·25년 서술은 별도 근거 확인. | 본문 이미지 없음; 임베드 영상 없음 / 근거 [S6](https://jackyvso.github.io/Starcraft/) |
| [스타 래더 맵 2025 시즌2 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=15) · tipboard/15 | 시즌 맵 / **P3** / 부분 보완 | 2025 시즌2 기록으로 유지. 해당 시즌의 맵 버전·원 다운로드 자료를 연결하고 최신 시즌으로 이동할 수 있게 보완. | 임베드 영상 없음; alt 비어 있음 9개 |
| [스타1 유닛 크기와 피해 타입 관계 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=16) · tipboard/16 | 피해 자료 / **P2** / 부분 보완 | 일반/폭발/진동형 크기별 비율 표는 유용. 실드에는 크기별 감산이 적용되지 않는 점, 방어력 적용 순서와 건물 판정 예시를 추가. | 본문 이미지 없음; 임베드 영상 없음 |
| [스타1 테란 유닛 능력치 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=17) · tipboard/17 | 유닛 자료 / **P2** / 부분 보완 | 이동속도·공격속도의 단위와 프레임/실효 주기 기준이 없음. 테란의 .5 표기와 다른 종족 정수 표를 통일하고 스팀·벌처 속업 등 조건을 명시. 무기 사거리 수치는 BWAPI와 항목별 대조 대상으로 남김. | 본문 이미지 없음; 임베드 영상 없음 / 근거 [S3](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp), [S4](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp) |
| [스타1 저그 유닛 능력치 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=18) · tipboard/18 | 유닛 자료 / **P2** / 부분 보완 | 러커 중형·감염 테란 폭발형은 BWAPI 데이터와 일치하므로 구형 공식 웹 표만 보고 변경하지 않기. 스커지의 자폭은 동작이며 피해 유형은 일반형으로 구분. 변태 추가 비용/총비용과 공격속도 단위를 통일; 구형 공식 문서와 상충하는 수치는 게임 실험으로 최종 검증. | 본문 이미지 없음; 임베드 영상 없음 / 근거 [S3](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp), [S4](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp), [S9](https://classic.battle.net/scc/zerg/units/lurker.shtml) |
| [스타1 프로토스 유닛 능력치 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=19) · tipboard/19 | 유닛 자료 / **P2** / 부분 보완 | 고급 유닛의 선행 테크 건물을 보완. 아콘·다크아콘 비용 0은 합체 추가 비용임을 표시하고 원 재료 총비용 병기. 이동속도·공격속도 단위와 캐리어 사거리 의미 구분. | 본문 이미지 없음; 임베드 영상 없음 / 근거 [S3](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp), [S4](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp) |
| [스타1 테란 유닛 상성 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=20) · tipboard/20 | 상성 자료 / **P2** / 부분 보완 | 단일 강함/약함 분류에 자원량·업그레이드·지형·탐지 조건 추가. 특히 배틀크루저 대 스커지는 배틀 수와 집중화력에 따라 달라짐. | 본문 이미지 없음; 임베드 영상 없음 |
| [스타1 저그 유닛 상성 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=21) · tipboard/21 | 상성 자료 / **P2** / 부분 보완 | 유닛별 상성을 같은 비용·수량·업그레이드 조건과 분리해 설명. 스커지·러커 등은 목표 접근·탐지·집중화력 때문에 단순 상성표와 실전 결과가 달라지는 사례 추가. | 본문 이미지 없음; 임베드 영상 없음 |
| [스타1 프로토스 유닛 상성 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=22) · tipboard/22 | 상성 자료 / **P2** / 부분 보완 | 스카웃의 가성비 평가는 상대 유닛 상성이 아님. 다크템플러의 탐지 여부, 캐리어의 지형·인터셉터·업그레이드 조건을 붙여 실전 적용 가능하게 보완. | 본문 이미지 없음; 임베드 영상 없음 |
| [스타1 유닛 상성 실전 분석!](https://sc1hub.com/boards/tipboard/readPost?postNum=23) · tipboard/23 | 실전 상성 / **P1** / 부분 보완 | 드라군이 스팀 마린보다 기본 이동속도가 빠르다는 문구 수정 필요(대략 5 대 6). 히드라→질럿 50%, 벌처→드라군 25%는 체력 피해 설명이며 실드에는 그대로 적용되지 않음을 명시. 질럿 2회 타격으로 저글링 처치도 공방업 조건 표시. | 본문 이미지 없음; 임베드 영상 없음 / 근거 [S3](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| [또 한살 먹다니..스타 래더 맵 2026 시즌1 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=24) · tipboard/24 | 시즌 맵 / **P2** / 부분 보완 | 2026 시즌1 기록으로 보존. 연결한 /238은 ASL21 공식맵 7종 자료라 래더 9종 목록 전체의 근거가 되지 않음. 래더 시즌 공지/맵팩을 별도로 연결하고 최신 시즌 안내 추가. | 임베드 영상 없음; alt 비어 있음 9개 / 근거 [S7](https://910map.tistory.com/238) |
| [늦었지만 올린다! 스타 래더 맵 2026 시즌2 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=25) · tipboard/25 | 시즌 맵 / **P3** / 부분 보완 | 2026 시즌2·ASL22 관련 배포 자료가 존재하는 것은 확인. 7월 28일 적용일과 래더 9종의 정확한 버전은 별도 시즌 공지/맵팩으로 확인; 7월 30일 샌드박스 배포일과 구분. 밴 추천의 적응 난도 관점은 유지. | 임베드 영상 없음 / 근거 [S8](https://910map.tistory.com/250) |

## 한줄 공략 15개

[현재 한줄 공략 목록](https://sc1hub.com/strategy-tips) 기준. ID는 검수 시점의 게시물 식별자다.

| ID / 분류 | 원문 | 우선순위 | 검수 의견 |
|---|---|---|---|
| 18 / 테저전 | 저글링을 적게 뽑은 걸 봤다면 땡마린으로 압박을 가보자 | P2 | 저글링 수 외에 성큰·후속 링·내 마린 수와 퇴로 확인 조건을 추가. 링이 적다는 이유만으로 무조건 진출하지 않기. |
| 17 / 프테전 | 메카닉 상대로 정면에서 갈아넣지 말고 멀티 견제와 시야 확보로 탱크 라인을 쪼개 각개격파해라. | P3 | 유지. 정면 병력을 유지하며 멀티 견제하는 원칙으로 적절. |
| 16 / 팀플 | 팀플에선 한쪽이 정면을 묶는 동안 다른 쪽은 측면을 압박해 적의 대응을 나눈다. | P3 | 유지. 양쪽 교전 시점을 맞추고 각개격파당하지 않을 병력 조건을 붙이면 더 좋음. |
| 13 / 저테전 | 테란이 앞마당을 3분 30초쯤 먹으면 팩더블이다. | P1 | 앞마당 시각 하나로 팩더블을 확정할 수 없음. 착공/완성·팩토리/가스·병력을 함께 확인하도록 변경. |
| 11 / 저프전 | 하템 스톰이 준비되면 히드라 한 부대 정도는 따로 빼서 템 저격을 시도한다. | P2 | 한 부대 고정 대신 정면을 유지할 병력을 남기고 커세어/질럿에 끊기지 않는 분리 규모로 조건화. |
| 10 / 테저전 | 5팩 골리앗 운영 시 앞마당, 본진, 팩 주변에 터렛 공사(1~2개씩)를 해주며 대공을 대비한다. | P2 | 터렛 개수는 자원 배치·접근 방향·뮤탈 수에 따라 달라짐. 1~2개는 예시임을 밝히고 완성 마감과 골리앗 배치 연결. |
| 9 / 팀플 | 빨무 테란은 늦어도 5분 전엔 터렛으로 다크템플러 대비해야 한다. | P2 | 5분은 맵·상대 다크 빌드에 따라 달라짐. 터렛 완성 기준과 엔베 선행, 더 빠른 다크 정찰 대응을 명시. |
| 8 / 테테전 | 시즈 탱크 대치 상황에서는 시야를 확보해야 시즈 모드 사거리 이점을 최대한 활용할 수 있다. | P3 | 유지. 시야를 확보해 시즈 사거리를 활용한다는 내용이 명료. |
| 7 / 프저전 | 커세어로 오버로드 두 마리를 잡아도 스커지 맞고 죽으면 손해다. | P2 | 오버로드 2기와 커세어 교환을 항상 손해라고 할 수 없음. 자원·공급 차단·후속 시야·상대 뮤탈에 따른 교환 가치로 설명. |
| 6 / 저테전 | 테란이 테크 타는 듯하면 3분 30초에 벌처 대비 성큰 하나를 꼭 지어야 한다. | P1 | 테크 느낌만으로 3:30 성큰을 고정하지 말고 팩토리/벌처 확인과 도착 전에 완성하는 기준으로 수정. |
| 5 / 저테전 | 테란 111체제는 저그 올인 플레이에 쉽게 무너질 수 있다. | P3 | 쉽게 무너질 수 있다는 조건부 문장은 유지. 대표적인 올인과 필요한 정찰 신호를 상세 글로 연결하면 유용. |
| 4 / 테저전 | 바이오닉 부대가 앞마당 앞에만 나가도 뮤탈짤짤이를 위축시킬 수 있다 | P3 | 유지. 뮤탈의 본진 공격을 견제하는 원칙으로 적절하나 무리한 추격을 뜻하지 않도록 퇴로 조건 보완. |
| 3 / 저테전 | 버로우 저글링은 빠른 테크 테란 상대로 강력한 CCTV 역할을 한다. | P3 | 유지. 버로우 연구 비용과 스캔·마인 노출 가능성을 상세 글에 보완. |
| 2 / 프저전 | 선발업 히드라를 조심해라. 레어 테크로 오해하기 쉽고 정찰도 어렵다. | P3 | 유지. 선발업 히드라를 레어 테크로 오판하지 말라는 경고로 적절. |
| 1 / 저프전 | 토스가 선게이트 더블 안정적으로 했으면, 변수두지말고 안전하게 운영해라. | P2 | 안전하게 운영하라는 말만으로 행동이 정해지지 않음. 드론 추가·히드라 수비·다음 정찰 중 구체적인 한 행동으로 바꾸기. |

## 기타 게시판 보조 자료

- [테프전 빌드에 바카닉도 추가해주심 감사하겠습니다.](https://sc1hub.com/boards/funboard/readPost?postNum=92) (funboard/92): 공략 추가 요청 속 바카닉 설명. 본진 바카닉도 팩더블과 같다고 적어 확장 시점 구분이 모호함. 정식 공략 tvspboard/18에서 본진/앞마당 변형과 진출 시각을 정리하고 이 요청 글을 연결. 회원 요청문을 운영자 공략처럼 재작성할 대상은 아님.
- [드라군이 멍청한 이유. 스타1 길찾기 인공지능에 대하여](https://sc1hub.com/boards/videolinkboard/readPost?postNum=72) (videolinkboard/72): 길찾기 설명 영상 소개글. 본문은 영상 요약이므로 빌드 오더 불필요. 두 번째 임베드 src에 리터럴 \r\n이 포함돼 URL 정리가 필요하며, 이동 알고리즘의 기술적 정확성은 실제 영상 검증 대상으로 남김.
