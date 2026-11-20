// StarCraft 1 keyboard rollover and key-ghosting tester (/keyboard-test).
(() => {
    const currentEl = document.getElementById('scKtCurrent');
    const maxEl = document.getElementById('scKtMax');
    const gradeEl = document.getElementById('scKtGrade');
    const pressedEl = document.getElementById('scKtPressed');
    const alertEl = document.getElementById('scKtAlert');
    const diagnosisEl = document.getElementById('scKtDiagnosis');
    const resetBtn = document.getElementById('scKtReset');
    const preventEl = document.getElementById('scKtPrevent');

    if (!currentEl || !maxEl || !gradeEl || !pressedEl || !alertEl || !diagnosisEl || !resetBtn || !preventEl) {
        return;
    }

    const KEY_LABELS = {
        Escape: 'Esc', Backquote: '~', Minus: '-', Equal: '=', Backspace: 'Back',
        Tab: 'Tab', BracketLeft: '[', BracketRight: ']', Backslash: '\\',
        CapsLock: 'Caps', Semicolon: ';', Quote: '\'', Enter: 'Enter',
        ShiftLeft: 'L-Shift', ShiftRight: 'R-Shift', Comma: ',', Period: '.', Slash: '/',
        ControlLeft: 'L-Ctrl', ControlRight: 'R-Ctrl', MetaLeft: 'L-Win', MetaRight: 'R-Win',
        AltLeft: 'L-Alt', AltRight: 'R-Alt', Space: 'Space',
        ArrowLeft: '←', ArrowUp: '↑', ArrowDown: '↓', ArrowRight: '→',
    };

    const PREVENT_ALWAYS_CODES = new Set([
        'F1', 'F2', 'F3', 'F4', 'Tab', 'Space',
        'AltLeft', 'AltRight', 'ArrowLeft', 'ArrowUp', 'ArrowDown', 'ArrowRight',
        'ContextMenu',
    ]);
    const PREVENT_WITH_CTRL_CODES = new Set([
        'Digit0', 'Digit1', 'Digit2', 'Digit3', 'Digit4',
        'Digit5', 'Digit6', 'Digit7', 'Digit8', 'Digit9',
        'KeyA', 'KeyS', 'KeyD', 'KeyF', 'KeyW',
    ]);

    const keyElements = new Map();
    document.querySelectorAll('[data-key-code]').forEach((el) => {
        keyElements.set(el.dataset.keyCode, el);
    });

    const comboCards = new Map();
    document.querySelectorAll('[data-combo-id]').forEach((el) => {
        comboCards.set(el.dataset.comboId, {
            card: el,
            badge: el.querySelector('[data-combo-status]'),
        });
    });

    const activeCodes = new Set();
    const testedCodes = new Set();
    const passedCombos = new Set();
    const trackedCombos = new Set();
    let maxKeys = 0;
    let startedTracked = false;
    let shiftTapTimestamps = [];
    let alertMessage = '';

    function trackEvent(eventName, params) {
        if (typeof window.gtag === 'function') {
            window.gtag('event', eventName, params || {});
        }
    }

    function formatKeyLabel(code) {
        if (KEY_LABELS[code]) {
            return KEY_LABELS[code];
        }
        if (code.startsWith('Key')) {
            return code.slice(3);
        }
        if (code.startsWith('Digit')) {
            return code.slice(5);
        }
        return code;
    }

    function isAlphanumericCode(code) {
        return /^Key[A-Z]$/.test(code) || /^Digit[0-9]$/.test(code);
    }

    function isTextInputTarget(target) {
        if (!target) {
            return false;
        }
        if (target.closest && target.closest('#scTerminal')) {
            return true;
        }
        return Boolean(target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));
    }

    function shouldPreventDefault(event) {
        if (!preventEl.checked) {
            return false;
        }
        if (PREVENT_ALWAYS_CODES.has(event.code)) {
            return true;
        }
        return Boolean(event.ctrlKey && !event.metaKey && PREVENT_WITH_CTRL_CODES.has(event.code));
    }

    function hasAny(codes) {
        return codes.some((code) => activeCodes.has(code));
    }

    function evaluateCombos() {
        const hasCtrl = hasAny(['ControlLeft', 'ControlRight']);
        const hasShift = hasAny(['ShiftLeft', 'ShiftRight']);

        if (hasCtrl && activeCodes.has('Digit1')) {
            markComboPassed('ctrl-group');
        }
        if (hasShift && activeCodes.has('Digit1') && activeCodes.has('KeyA')) {
            markComboPassed('shift-attack');
        }
        if ((activeCodes.has('F2') && activeCodes.has('KeyS') && activeCodes.has('KeyD'))
                || (activeCodes.has('KeyA') && activeCodes.has('KeyS') && activeCodes.has('KeyD'))) {
            markComboPassed('screen-prod');
        }
        let alphaCount = 0;
        activeCodes.forEach((code) => {
            if (isAlphanumericCode(code)) {
                alphaCount += 1;
            }
        });
        if (alphaCount >= 4) {
            markComboPassed('four-key');
        }
    }

    function markComboPassed(comboId) {
        if (!passedCombos.has(comboId)) {
            passedCombos.add(comboId);
        }
        const entry = comboCards.get(comboId);
        if (entry) {
            entry.card.classList.add('is-passed');
            if (entry.badge) {
                entry.badge.textContent = '통과';
            }
        }
        if (!trackedCombos.has(comboId)) {
            trackedCombos.add(comboId);
            trackEvent('keyboard_test_combo_pass', { combo_id: comboId });
        }
    }

    function updateKeyVisuals() {
        keyElements.forEach((el, code) => {
            const isActive = activeCodes.has(code);
            el.classList.toggle('is-active', isActive);
            el.classList.toggle('is-tested', testedCodes.has(code));
            el.classList.toggle('is-warn', code === 'MetaLeft' || code === 'MetaRight');
        });
    }

    function renderPressedList() {
        if (activeCodes.size === 0) {
            const emptyEl = document.createElement('span');
            emptyEl.className = 'sc-kt-pressed__empty';
            emptyEl.textContent = '아직 입력이 없습니다. 키를 동시에 눌러보세요.';
            pressedEl.replaceChildren(emptyEl);
            return;
        }
        const fragment = document.createDocumentFragment();
        activeCodes.forEach((code) => {
            const chipEl = document.createElement('span');
            chipEl.className = 'sc-kt-chip';
            chipEl.textContent = formatKeyLabel(code);
            fragment.appendChild(chipEl);
        });
        pressedEl.replaceChildren(fragment);
    }

    function renderSummary() {
        currentEl.textContent = String(activeCodes.size);
        maxEl.textContent = String(maxKeys);

        gradeEl.classList.remove('is-good', 'is-warn');
        if (maxKeys === 0) {
            gradeEl.textContent = '대기 중';
        } else if (maxKeys >= 6) {
            gradeEl.textContent = '무한/다중 입력 양호';
            gradeEl.classList.add('is-good');
        } else if (maxKeys >= 4) {
            gradeEl.textContent = '실전 조합 양호';
            gradeEl.classList.add('is-good');
        } else if (maxKeys === 3) {
            gradeEl.textContent = '3키 주의 (조합 한계 가능)';
            gradeEl.classList.add('is-warn');
        } else {
            gradeEl.textContent = '2키 이하 (고스팅 의심)';
            gradeEl.classList.add('is-warn');
        }

        if (alertMessage) {
            alertEl.textContent = alertMessage;
            alertEl.hidden = false;
        } else {
            alertEl.hidden = true;
            alertEl.textContent = '';
        }
    }

    function renderDiagnosis() {
        if (maxKeys === 0) {
            diagnosisEl.textContent = '위 키보드 맵에서 스타1 실전 조합을 직접 눌러보면 동시입력 한계와 원인 안내가 여기에 표시됩니다.';
            return;
        }
        if (maxKeys <= 3) {
            diagnosisEl.textContent = `현재 최대 ${maxKeys}키까지 확인되었습니다. Shift+1+A 또는 4키 동시입력에서 키가 더 이상 추가되지 않는다면 일반 2~3KRO 매트릭스 한계일 가능성이 높습니다. 아래 해결 가이드와 추천 키보드를 확인해보세요.`;
            return;
        }
        if (passedCombos.size === comboCards.size) {
            diagnosisEl.textContent = `최대 ${maxKeys}키 동시 입력과 스타1 실전 조합 4가지를 모두 통과했습니다. 하드웨어 동시입력 문제는 없으며, 게임 중 튕김·씹힘이 있다면 고정 키나 윈도우 키 설정을 점검하세요.`;
            return;
        }
        diagnosisEl.textContent = `최대 ${maxKeys}키까지 동시 입력되었습니다. 아직 확인하지 않은 스타1 실전 조합 카드도 함께 눌러 이상 여부를 점검해보세요.`;
    }

    function renderAll() {
        updateKeyVisuals();
        renderPressedList();
        renderSummary();
        renderDiagnosis();
    }

    function checkWarnings(event) {
        if (event.code === 'MetaLeft' || event.code === 'MetaRight') {
            alertMessage = '윈도우(Win) 키 입력이 감지되었습니다. 게임 중 창이 내려간다면 키보드 Win Lock 또는 레지스트리 차단을 확인하세요.';
            return;
        }
        if ((event.code === 'ShiftLeft' || event.code === 'ShiftRight') && !event.repeat) {
            const now = Date.now();
            shiftTapTimestamps = shiftTapTimestamps.filter((ts) => now - ts <= 4000);
            shiftTapTimestamps.push(now);
            if (shiftTapTimestamps.length >= 5) {
                alertMessage = 'Shift 5회 연타가 감지되었습니다. Windows 고정 키 단축키가 켜져 있으면 스타1 도중 바탕화면으로 튕길 수 있습니다.';
            }
        }
    }

    function clearActiveKeys() {
        if (activeCodes.size === 0) {
            return;
        }
        activeCodes.clear();
        renderAll();
    }

    function resetAll() {
        activeCodes.clear();
        testedCodes.clear();
        passedCombos.clear();
        shiftTapTimestamps = [];
        maxKeys = 0;
        alertMessage = '';
        comboCards.forEach((entry) => {
            entry.card.classList.remove('is-passed');
            if (entry.badge) {
                entry.badge.textContent = '대기';
            }
        });
        renderAll();
    }

    document.addEventListener('keydown', (event) => {
        if (isTextInputTarget(event.target)) {
            return;
        }
        if (shouldPreventDefault(event)) {
            event.preventDefault();
        }
        if (!event.code) {
            return;
        }
        if (!startedTracked) {
            startedTracked = true;
            trackEvent('keyboard_test_start');
        }
        activeCodes.add(event.code);
        testedCodes.add(event.code);
        if (activeCodes.size > maxKeys) {
            maxKeys = activeCodes.size;
        }
        checkWarnings(event);
        evaluateCombos();
        renderAll();
    });

    document.addEventListener('keyup', (event) => {
        if (!event.code || !activeCodes.has(event.code)) {
            return;
        }
        if (shouldPreventDefault(event) && !isTextInputTarget(event.target)) {
            event.preventDefault();
        }
        activeCodes.delete(event.code);
        renderAll();
    });

    window.addEventListener('blur', clearActiveKeys);
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden') {
            clearActiveKeys();
        }
    });

    resetBtn.addEventListener('click', resetAll);

    document.querySelectorAll('[data-kt-guide]').forEach((linkEl) => {
        linkEl.addEventListener('click', () => {
            trackEvent('keyboard_test_guide_click', {
                guide: linkEl.dataset.ktGuide || 'unknown',
            });
        });
    });

    renderAll();
})();
