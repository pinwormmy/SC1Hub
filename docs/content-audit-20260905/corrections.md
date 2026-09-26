# SC1Hub 공략 1차 수정게시 결과 — 2026-09-05

**우선 검수 대상 P1 18개와 연결된 공략 3개, 총 21개 글의 핵심 오류를 순차적으로 수정게시했다.** 각 글은 수정 직전 원문을 다시 읽고, 업데이트 후 관리자 API 본문과 공개 페이지 본문을 대조했다. 21개 모두 API 검증 통과·공개 HTTP 200이며 제목·작성자·기존 이미지와 임베드 영상 주소를 보존했다.

이번 작업은 잘못된 선행 순서, 조작 설명, 근거 없는 통계·단정, 연결된 글 사이 모순을 고치는 1차 작업이다. 176개 전체 글의 전면 개편이나 모든 경기 타이밍의 재현 검증 완료를 뜻하지 않는다.

## 순차 게시 내역

아래 표는 실제 게시·검증을 마친 순서다. P1/P2 등은 수정 전 검수 우선순위이며, 핵심 오류를 고친 글에도 추가 보강 항목이 남을 수 있다.

| 순서 | 글 | 반영한 수정 | 검증 | 근거 |
|---:|---|---|---|---|
| 1 | [빨무 프로토스 물량이 안 나오는 이유](https://sc1hub.com/boards/teamplayguideboard/readPost?postNum=14) · `teamplayguideboard/14` | 프로브는 소환 시작 후 복귀 가능 / 공급 확보 시점을 생산 시작으로 교정 / 여러 건물 동시 부대지정 문구 수정 | API PASS · 공개 200 | [C1](https://classic.battle.net/scc/protoss/units/probe.shtml) |
| 2 | [저그도 전면전 잘한다! 가드라 빌드](https://sc1hub.com/boards/zvstboard/readPost?postNum=22) · `zvstboard/22` | 레어→스파이어 선행 순서 교정 / 중반 요약과 생략 항목 명시 / 방업 완료 조건 명시 | API PASS · 공개 200 | [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| 3 | [스타1 빌드 타임 - 건물, 유닛, 업그레이드 시간 정리](https://sc1hub.com/boards/tipboard/readPost?postNum=4) · `tipboard/4` | 저그 업그레이드 2·3단계 오기 수정 / 스카웃 오타 수정 | API PASS · 공개 200 | 원문 표기 대조 |
| 4 | [난 그냥 프프전 하기 싫다. 센터99게이트](https://sc1hub.com/boards/pvspboard/readPost?postNum=9) · `pvspboard/9` | 9마리 동원 오기→인구수 9·전진 프로브 1기 / 프로브 출발 표현과 파일런 위치 명확화 / 러시 거리 절반·동일 거리 단정 수정 | API PASS · 공개 200 | 원문 표기 대조 |
| 5 | [스타1 유닛 상성 실전 분석!](https://sc1hub.com/boards/tipboard/readPost?postNum=23) · `tipboard/23` | 스팀 마린과 드라군 이동속도 설명 교정 / 체력 피해와 실드 피해 구분 / 질럿·저글링 타격 횟수의 공방업 조건 명시 | API PASS · 공개 200 | [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp), [C3](https://classic.battle.net/scc/terran/um.shtml), [C4](https://classic.battle.net/scc/GS/damage.shtml) |
| 6 | [저그전 공방업 업그레이드 순서](https://sc1hub.com/boards/pvszboard/readPost?postNum=19) · `pvszboard/19` | 플레이그 피해 상한·실드 구분 / 처치 횟수의 공방업·재생 조건 명시 / 방3업 고정 순서를 조합별 선택으로 교정 | API PASS · 공개 200 | [C5](https://classic.battle.net/scc/zerg/units/defiler.shtml), [C6](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp) |
| 7 | [스타크래프트1 무료 다운로드 사이트](https://sc1hub.com/boards/tipboard/readPost?postNum=13) · `tipboard/13` | 무료화 시점과 리마스터 출시 시점 구분 / 확인되지 않은 고정 가격 대신 현재 공식 상점 안내 / 캠페인 오타 수정 | API PASS · 공개 200 | [C7](https://news.blizzard.com/en-us/article/20674424/starcraft-brood-war-patch-1-18-patch-notes), [C8](https://us.shop.battle.net/ko-kr/product/starcraft) |
| 8 | [자주 상대하게 되는, 미친 저그 상대법](https://sc1hub.com/boards/tvszboard/readPost?postNum=11) · `tvszboard/11` | 울트라 방5업 표현→최대 총 방어력 6 / 레어·챔버만으로 전략 확정하는 문장 수정 / 무조건 패배 단정 제거 | API PASS · 공개 200 | [C9](https://classic.battle.net/scc/zerg/units/ultralisk.shtml), [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| 9 | [꼭 숙지필요!! 더러운 8배럭 막는 법](https://sc1hub.com/boards/zvstboard/readPost?postNum=16) · `zvstboard/16` | 미네랄 이동의 충돌 예외와 공중 유닛 판정 구분 / 명령 전환 설명 보완 / 드론 손실만으로 패배 단정 제거 | API PASS · 공개 200 | 원문 표기 대조 |
| 10 | [짤막 성공! 테란 초반 찌르기 수비 후엔 뭘할까?](https://sc1hub.com/boards/pvstboard/readPost?postNum=13) · `pvstboard/13` | 초반 수비 후 2차 러시 불가 단정 제거 / 상대 확장·생산시설·병력에 따른 트리플/수비 분기 | API PASS · 공개 200 | 원문 표기 대조 |
| 11 | [테테전 드랍십 운영: 탱크 두 기로 자리 싸움 뒤집는 법](https://sc1hub.com/boards/tvstboard/readPost?postNum=12) · `tvstboard/12` | 출처 없는 드랍십 중앙값·사분위 통계 제거 / 첫 출발 고정 시간 대신 확인 가능한 건설·생산 소요시간 / 세 번째 커맨드 고정 시각을 정찰 조건으로 교체 | API PASS · 공개 200 | [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| 12 | [상대 레이스가 보였다: 터렛과 골리앗은 얼마나 준비해야 할까?](https://sc1hub.com/boards/tvstboard/readPost?postNum=13) · `tvstboard/13` | 출처 없는 레이스·골리앗 통계 제거 / 5:30~6분 고정 수비 마감 제거 / 엔베→터렛 약 57초 및 이동 여유로 준비 기준 교정 | API PASS · 공개 200 | [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| 13 | [뮤짤에 미쳤냐!? 530 뮤탈 빌드 대처](https://sc1hub.com/boards/tvszboard/readPost?postNum=20) · `tvszboard/20` | 오버풀과 12풀 구분 / 링 수·레어 시각만으로 100% 판정하는 단정 제거 / 터렛 착공/완성 시점 교정 및 소요시간 근거 추가 | API PASS · 공개 200 | [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp) |
| 14 | [공업이냐 방업이냐? 저프전 저그 업그레이드 순서 정리](https://sc1hub.com/boards/zvspboard/readPost?postNum=14) · `zvspboard/14` | 973 원공1업 필수 단정 제거 / 원거리/근접 공업 적용 조합 구분 / 타격 횟수의 단순 계산 조건 명시 / 초반 챔버 고정 시점 제거 | API PASS · 공개 200 | 원문 표기 대조 |
| 15 | [3분 정찰로 보는 저저전 빌드 판별법](https://sc1hub.com/boards/zvszboard/readPost?postNum=9) · `zvszboard/9` | 0~1분 정찰 도달 보장 제거 / 관전자 알 생산 정보와 플레이어가 확인한 유닛 구분 / 건물 진행도·시계만으로 오프닝 확정하지 않도록 교정 | API PASS · 공개 200 | 원문 표기 대조 |
| 16 | [부유한 배럭더블 운영](https://sc1hub.com/boards/tvstboard/readPost?postNum=7) · `tvstboard/7` | 첫 서플 11→9, 배럭 11로 교정 / 원 영상에 맞게 커맨드→가스 순서 복구 / 잘못된 전체 시간표를 직접 확인한 영상/게임 시계 체크포인트로 교체 | API PASS · 공개 200 | [C10](https://classic.battle.net/scc/terran/basic.shtml), [C11](https://www.youtube.com/watch?v=FF8vbIpe_fQ) |
| 17 | [이 시간에 이 물량!? 5팩 타이밍 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=7) · `tvspboard/7` | 5팩과 공1업 완료를 구분 / 본문에 없는 아머리·공업을 전제로 한 해석 차단 / 검증되지 않은 8:30 표준 출발 단정 제거 | API PASS · 공개 200 | 원문 표기 대조 |
| 18 | [테란의 찌르기, 타이밍 러쉬 빌드 정리](https://sc1hub.com/boards/tvspboard/readPost?postNum=3) · `tvspboard/3` | 연결 대상에 없는 공1업 고정 표기 수정 | API PASS · 공개 200 | 원문 표기 대조 |
| 19 | [안전한 정석 시작, 팩더블 빌드](https://sc1hub.com/boards/tvspboard/readPost?postNum=11) · `tvspboard/11` | 5팩 연결 글을 공1업 확정 빌드로 소개하는 문구 수정 | API PASS · 공개 200 | 원문 표기 대조 |
| 20 | [테란의 원기옥? 5팩 타이밍 막는 법!](https://sc1hub.com/boards/pvstboard/readPost?postNum=11) · `pvstboard/11` | 공1업형 영상과 무업형 위협을 구분 / 5팩 완성 확인 뒤에만 대응하는 오해 방지 | API PASS · 공개 200 | 원문 표기 대조 |
| 21 | [3해처리 뮤탈 운영. 이제 안 쓰는 이유](https://sc1hub.com/boards/zvstboard/readPost?postNum=10) · `zvstboard/10` | 첫 오버로드와 누락된 레어 선행 과정 보완 / 원 강의의 레어 완성·스파이어 공사 장면과 영상·게임 시각 구분 / 스파이어의 레어 완료 조건과 인구수 예시 조건 명시 / 3해처리 완전 사장 단정을 선택 조건으로 완화 | API PASS · 공개 200 | [C2](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp), [C12](https://www.youtube.com/watch?v=gKCPDgS6WQk&t=335) |

## 검증의 범위와 남은 보강

- **저장·공개 검증:** 21개 글의 저장된 전체 본문 텍스트가 수정안과 일치하는지, 공개 페이지에 해당 본문이 포함되는지 확인했다. 이미지·영상 주소와 제목·작성자 보존을 확인했고, 포함된 이미지 주소는 HTTP 200과 이미지 형식을 확인했다.
- **공개 화면 표본 확인:** 배럭더블 글의 상단 이미지, 빌드 오더, 영상 체크포인트 부분을 브라우저 스크린샷으로 확인했다.
- **영상 근거:** 테테전 배럭더블은 원 강의의 초반 5개 정지 장면과 발언을 대조했다. 3해처리는 레어 완성·스파이어 공사 장면을 확인했다. 화면에 이미 건설 중인 상태를 정확한 착공 시각으로 바꾸지 않았다.
- **시간값:** 출처 없는 경기 중앙값·사분위 수치를 삭제했다. 건물·유닛 준비시간은 데이터의 프레임 소요량에서 계산한 근삿값이며, 실제 경기 시각이나 보장된 방어 완료 시각과 구분했다. SCV 이동·자원·건설 지연은 별도로 고려하도록 수정했다.
- **남은 내용:** 나머지 P2/P3 글의 빌드 보강, 일부 글의 원 리플레이 실측, 누락 이미지·영상·alt 및 기존 미디어 문제는 이번 1차 교정 범위 밖이다. 영상 151개의 전편 재생 검증도 수행하지 않았다.
- **사이트 코드:** 관리자 콘텐츠 API로 글만 수정했다. 애플리케이션 코드 변경·커밋·빌드·배포·전체 RAG 재색인은 수행하지 않았다.

## 확인한 원본 강의 장면

| 글 | 영상 재생 시각 | 게임 시각 | 실제 화면에서 확인한 상태 |
|---|---:|---:|---|
| 테테전 배럭더블 | 1:00 | 0:54 | 인구수 9, 첫 서플 준비 |
| 테테전 배럭더블 | 1:30 | 1:24 | 인구수 11, 정찰 진행. 강의에서 11배럭 설명 |
| 테테전 배럭더블 | 2:30 | 2:24 | 인구수 16, 앞마당 커맨드 건설 중·가스 0 |
| 테테전 배럭더블 | 3:00 | 2:54 | 인구수 17, 본진 가스 건물 존재 |
| 테테전 배럭더블 | 4:30 | 4:24 | 앞마당 커맨드 완성·SCV 생산, 추가 가스 준비 |
| 3해처리 뮤탈 | 5:35 | 5:02 | 본진 레어 완성 장면 |
| 3해처리 뮤탈 | 5:40 | 5:07 | 스파이어 공사 진행 중, 인구수 29 |

## 근거 링크

- [C1 — https://classic.battle.net/scc/protoss/units/probe.shtml](https://classic.battle.net/scc/protoss/units/probe.shtml)
- [C2 — https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/UnitType.cpp)
- [C3 — https://classic.battle.net/scc/terran/um.shtml](https://classic.battle.net/scc/terran/um.shtml)
- [C4 — https://classic.battle.net/scc/GS/damage.shtml](https://classic.battle.net/scc/GS/damage.shtml)
- [C5 — https://classic.battle.net/scc/zerg/units/defiler.shtml](https://classic.battle.net/scc/zerg/units/defiler.shtml)
- [C6 — https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp](https://github.com/bwapi/bwapi/blob/main/bwapi/BWAPILIB/Source/WeaponType.cpp)
- [C7 — https://news.blizzard.com/en-us/article/20674424/starcraft-brood-war-patch-1-18-patch-notes](https://news.blizzard.com/en-us/article/20674424/starcraft-brood-war-patch-1-18-patch-notes)
- [C8 — https://us.shop.battle.net/ko-kr/product/starcraft](https://us.shop.battle.net/ko-kr/product/starcraft)
- [C9 — https://classic.battle.net/scc/zerg/units/ultralisk.shtml](https://classic.battle.net/scc/zerg/units/ultralisk.shtml)
- [C10 — https://classic.battle.net/scc/terran/basic.shtml](https://classic.battle.net/scc/terran/basic.shtml)
- [C11 — https://www.youtube.com/watch?v=FF8vbIpe_fQ](https://www.youtube.com/watch?v=FF8vbIpe_fQ)
- [C12 — https://www.youtube.com/watch?v=gKCPDgS6WQk&t=335](https://www.youtube.com/watch?v=gKCPDgS6WQk&t=335)

수정 전 검수 기록은 [전체 보고서](report.md)에 남겼다. 검증 시각·본문 해시 등은 [게시 검증 기록](corrections.json)에서 확인할 수 있다.
