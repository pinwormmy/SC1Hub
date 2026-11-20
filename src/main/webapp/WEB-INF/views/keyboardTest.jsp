<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<title><c:out value="${pageTitle}"/></title>
<%@include file="./include/head.jspf" %>
<link rel="stylesheet" href="/css/keyboard-test.css?v=${applicationScope.assetVersion}">
</head>
<body>
<%@include file="./include/header.jspf" %>
    <div class="section-inner">
        <div class="container">
            <main class="sc-kt">
                <div class="sc-kt-intro">
                    <h1>스타1 키보드 동시입력·키 씹힘 테스트</h1>
                    <p>키를 여러 개 동시에 눌러 몇 개까지 입력되는지 확인하고, 스타크래프트1 부대지정·화면지정 조합에서 키 씹힘(고스팅)이 없는지 바로 점검하세요.</p>
                </div>

                <section class="sc-kt-panel" aria-labelledby="scKtSummaryTitle">
                    <div class="sc-kt-panel__header">
                        <h2 id="scKtSummaryTitle">실시간 동시입력 측정</h2>
                        <div class="sc-kt-controls">
                            <label class="sc-kt-toggle">
                                <input type="checkbox" id="scKtPrevent" checked>
                                <span>브라우저 단축키 방지</span>
                            </label>
                            <button type="button" id="scKtReset">기록 초기화</button>
                        </div>
                    </div>
                    <div class="sc-kt-panel__body">
                        <div class="sc-kt-stats">
                            <div class="sc-kt-stat">
                                <span class="sc-kt-stat__label">현재 동시 입력</span>
                                <strong id="scKtCurrent" class="sc-kt-stat__value">0</strong>
                            </div>
                            <div class="sc-kt-stat">
                                <span class="sc-kt-stat__label">최대 동시 입력</span>
                                <strong id="scKtMax" class="sc-kt-stat__value">0</strong>
                            </div>
                            <div class="sc-kt-stat">
                                <span class="sc-kt-stat__label">롤오버 판정</span>
                                <strong id="scKtGrade" class="sc-kt-grade">대기 중</strong>
                            </div>
                        </div>
                        <div class="sc-kt-pressed-row">
                            <span class="sc-kt-pressed-row__label">눌린 키</span>
                            <div id="scKtPressed" class="sc-kt-pressed" aria-live="polite">
                                <span class="sc-kt-pressed__empty">아직 입력이 없습니다. 키를 동시에 눌러보세요.</span>
                            </div>
                        </div>
                        <p id="scKtAlert" class="sc-kt-alert" role="status" aria-live="assertive" hidden></p>
                    </div>
                </section>

                <section class="sc-kt-panel" aria-labelledby="scKtBoardTitle">
                    <div class="sc-kt-panel__header">
                        <h2 id="scKtBoardTitle">키보드 입력 맵</h2>
                        <span class="sc-kt-hint">한/영 상태와 무관하게 물리 키 기준으로 인식</span>
                    </div>
                    <div class="sc-kt-panel__body">
                        <p class="sc-kt-mobile-note">물리 키보드 연결 환경(PC)에서 여러 키를 동시에 눌러 테스트하는 기능입니다.</p>
                        <div class="sc-kt-board-wrap">
                            <div class="sc-kt-board" role="group" aria-label="키보드 입력 상태">
                                <div class="sc-kt-row sc-kt-row--fn">
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="Escape">Esc</span>
                                    <span class="sc-kt-spacer" aria-hidden="true"></span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F1">F1</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F2">F2</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F3">F3</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F4">F4</span>
                                    <span class="sc-kt-spacer" aria-hidden="true"></span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F5">F5</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F6">F6</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F7">F7</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F8">F8</span>
                                    <span class="sc-kt-spacer" aria-hidden="true"></span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F9">F9</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F10">F10</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F11">F11</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="F12">F12</span>
                                </div>
                                <div class="sc-kt-row">
                                    <span class="sc-kt-key" data-key-code="Backquote">~</span>
                                    <span class="sc-kt-key" data-key-code="Digit1">1</span>
                                    <span class="sc-kt-key" data-key-code="Digit2">2</span>
                                    <span class="sc-kt-key" data-key-code="Digit3">3</span>
                                    <span class="sc-kt-key" data-key-code="Digit4">4</span>
                                    <span class="sc-kt-key" data-key-code="Digit5">5</span>
                                    <span class="sc-kt-key" data-key-code="Digit6">6</span>
                                    <span class="sc-kt-key" data-key-code="Digit7">7</span>
                                    <span class="sc-kt-key" data-key-code="Digit8">8</span>
                                    <span class="sc-kt-key" data-key-code="Digit9">9</span>
                                    <span class="sc-kt-key" data-key-code="Digit0">0</span>
                                    <span class="sc-kt-key" data-key-code="Minus">-</span>
                                    <span class="sc-kt-key" data-key-code="Equal">=</span>
                                    <span class="sc-kt-key sc-kt-key--wide" data-key-code="Backspace">Back</span>
                                </div>
                                <div class="sc-kt-row">
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="Tab">Tab</span>
                                    <span class="sc-kt-key" data-key-code="KeyQ">Q</span>
                                    <span class="sc-kt-key" data-key-code="KeyW">W</span>
                                    <span class="sc-kt-key" data-key-code="KeyE">E</span>
                                    <span class="sc-kt-key" data-key-code="KeyR">R</span>
                                    <span class="sc-kt-key" data-key-code="KeyT">T</span>
                                    <span class="sc-kt-key" data-key-code="KeyY">Y</span>
                                    <span class="sc-kt-key" data-key-code="KeyU">U</span>
                                    <span class="sc-kt-key" data-key-code="KeyI">I</span>
                                    <span class="sc-kt-key" data-key-code="KeyO">O</span>
                                    <span class="sc-kt-key" data-key-code="KeyP">P</span>
                                    <span class="sc-kt-key" data-key-code="BracketLeft">[</span>
                                    <span class="sc-kt-key" data-key-code="BracketRight">]</span>
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="Backslash">\</span>
                                </div>
                                <div class="sc-kt-row">
                                    <span class="sc-kt-key sc-kt-key--lg" data-key-code="CapsLock">Caps</span>
                                    <span class="sc-kt-key" data-key-code="KeyA">A</span>
                                    <span class="sc-kt-key" data-key-code="KeyS">S</span>
                                    <span class="sc-kt-key" data-key-code="KeyD">D</span>
                                    <span class="sc-kt-key" data-key-code="KeyF">F</span>
                                    <span class="sc-kt-key" data-key-code="KeyG">G</span>
                                    <span class="sc-kt-key" data-key-code="KeyH">H</span>
                                    <span class="sc-kt-key" data-key-code="KeyJ">J</span>
                                    <span class="sc-kt-key" data-key-code="KeyK">K</span>
                                    <span class="sc-kt-key" data-key-code="KeyL">L</span>
                                    <span class="sc-kt-key" data-key-code="Semicolon">;</span>
                                    <span class="sc-kt-key" data-key-code="Quote">'</span>
                                    <span class="sc-kt-key sc-kt-key--wide" data-key-code="Enter">Enter</span>
                                </div>
                                <div class="sc-kt-row">
                                    <span class="sc-kt-key sc-kt-key--xl" data-key-code="ShiftLeft">Shift</span>
                                    <span class="sc-kt-key" data-key-code="KeyZ">Z</span>
                                    <span class="sc-kt-key" data-key-code="KeyX">X</span>
                                    <span class="sc-kt-key" data-key-code="KeyC">C</span>
                                    <span class="sc-kt-key" data-key-code="KeyV">V</span>
                                    <span class="sc-kt-key" data-key-code="KeyB">B</span>
                                    <span class="sc-kt-key" data-key-code="KeyN">N</span>
                                    <span class="sc-kt-key" data-key-code="KeyM">M</span>
                                    <span class="sc-kt-key" data-key-code="Comma">,</span>
                                    <span class="sc-kt-key" data-key-code="Period">.</span>
                                    <span class="sc-kt-key" data-key-code="Slash">/</span>
                                    <span class="sc-kt-key sc-kt-key--xl" data-key-code="ShiftRight">Shift</span>
                                </div>
                                <div class="sc-kt-row">
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="ControlLeft">Ctrl</span>
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="MetaLeft">Win</span>
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="AltLeft">Alt</span>
                                    <span class="sc-kt-key sc-kt-key--space" data-key-code="Space">Space</span>
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="AltRight">Alt</span>
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="MetaRight">Win</span>
                                    <span class="sc-kt-key sc-kt-key--md" data-key-code="ControlRight">Ctrl</span>
                                    <span class="sc-kt-spacer" aria-hidden="true"></span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="ArrowLeft">←</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="ArrowUp">↑</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="ArrowDown">↓</span>
                                    <span class="sc-kt-key sc-kt-key--sm" data-key-code="ArrowRight">→</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="sc-kt-panel" aria-labelledby="scKtCombosTitle">
                    <div class="sc-kt-panel__header">
                        <h2 id="scKtCombosTitle">스타1 실전 조합 체크리스트</h2>
                        <span class="sc-kt-hint">각 조합을 동시에 눌러 통과 여부 확인</span>
                    </div>
                    <div class="sc-kt-panel__body">
                        <div class="sc-kt-combos">
                            <div class="sc-kt-combo" data-combo-id="ctrl-group">
                                <div class="sc-kt-combo__top">
                                    <strong>Ctrl + 1</strong>
                                    <span class="sc-kt-combo__badge" data-combo-status>대기</span>
                                </div>
                                <p>부대 지정 기본 2키 조합</p>
                            </div>
                            <div class="sc-kt-combo" data-combo-id="shift-attack">
                                <div class="sc-kt-combo__top">
                                    <strong>Shift + 1 + A</strong>
                                    <span class="sc-kt-combo__badge" data-combo-status>대기</span>
                                </div>
                                <p>부대 추가 + 어택(3키 고스팅 대표 구간)</p>
                            </div>
                            <div class="sc-kt-combo" data-combo-id="screen-prod">
                                <div class="sc-kt-combo__top">
                                    <strong>F2 + S + D</strong> <span class="sc-kt-hint">또는 A + S + D</span>
                                    <span class="sc-kt-combo__badge" data-combo-status>대기</span>
                                </div>
                                <p>화면 지정 및 연속 생산 3키 조합</p>
                            </div>
                            <div class="sc-kt-combo" data-combo-id="four-key">
                                <div class="sc-kt-combo__top">
                                    <strong>문자·숫자 4키 동시 입력</strong>
                                    <span class="sc-kt-combo__badge" data-combo-status>대기</span>
                                </div>
                                <p>3키에서 막히면 2~3KRO 매트릭스 한계</p>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="sc-kt-panel" aria-labelledby="scKtDiagnosisTitle">
                    <div class="sc-kt-panel__header">
                        <h2 id="scKtDiagnosisTitle">진단 해석 및 해결 가이드</h2>
                    </div>
                    <div class="sc-kt-panel__body">
                        <p id="scKtDiagnosis" class="sc-kt-diagnosis">위 키보드 맵에서 스타1 실전 조합을 직접 눌러보면 동시입력 한계와 원인 안내가 여기에 표시됩니다.</p>
                        <ul class="sc-kt-guides">
                            <li>
                                <a href="/boards/tipboard/readPost?postNum=92" data-kt-guide="troubleshoot">스타 하다가 게임이 내려간다? 고정 키·윈도우 키·키 씹힘 해결법 →</a>
                            </li>
                            <li>
                                <a href="/boards/tipboard/readPost?postNum=11" data-kt-guide="gear">스타1 키보드·마우스 추천 가이드 보기 →</a>
                            </li>
                        </ul>
                    </div>
                </section>

                <%@include file="./include/coupangDynamicAd.jspf" %>
            </main>
        </div>
    </div>
<script src="/js/keyboard-test.js?v=${applicationScope.assetVersion}" defer></script>
<%@include file="./include/footer.jspf" %>
</body>
</html>
