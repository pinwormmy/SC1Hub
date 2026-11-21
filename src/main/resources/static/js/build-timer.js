// Build-order practice timer: a wall clock, manually advanced supply steps, and spoken checkpoints.
(() => {
    const dataNode = document.getElementById('scBtData');
    if (!dataNode) return;
    let builds;
    try {
        builds = JSON.parse(dataNode.textContent);
    } catch (e) {
        return;
    }
    if (!Array.isArray(builds) || !builds.length) return;

    const STORAGE_KEY = 'scBuildTimer:v1';
    const $ = (id) => document.getElementById(id);
    const ui = {
        build: $('scBtBuild'), source: $('scBtSource'), summary: $('scBtSummary'),
        clock: $('scBtClock'), now: $('scBtNow'), next: $('scBtNext'), alert: $('scBtAlert'),
        start: $('scBtStart'), step: $('scBtStep'), undo: $('scBtUndo'), reset: $('scBtReset'),
        minus: $('scBtMinus'), plus: $('scBtPlus'), voice: $('scBtVoice'), lead: $('scBtLead'),
        steps: $('scBtSteps'), checkpoints: $('scBtCheckpoints'), note: $('scBtNote'),
        fbForm: $('scBtFeedbackForm'), fbCategory: $('scBtFbCategory'), fbBuild: $('scBtFbBuild'),
        fbMessage: $('scBtFbMessage'), fbContact: $('scBtFbContact'), fbWebsite: $('scBtFbWebsite'),
        fbCount: $('scBtFbCount'), fbSubmit: $('scBtFbSubmit'), fbStatus: $('scBtFbStatus')
    };

    // Storage may be blocked (private mode, previews); the timer works without it.
    const store = {
        read() {
            try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {}; } catch (e) { return {}; }
        },
        write(patch) {
            try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...store.read(), ...patch })); } catch (e) { /* ignore */ }
        }
    };

    const state = {
        build: builds[0],
        baseMs: 0,          // elapsed time banked while paused
        runStart: null,     // performance.now() when the clock last resumed
        started: false,
        current: 0,         // index of the step the player is working on
        splits: [],         // elapsed ms when each step was marked done
        fired: new Set(),   // checkpoint indexes already announced
        previous: [],       // splits of the last saved run of this build, shown for comparison
        lastShownSecond: -1,
        tickId: null,
        wakeLock: null,
        fbBuildDirty: false
    };

    const formatClock = (ms) => {
        const total = Math.max(0, Math.floor(ms / 1000));
        return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0');
    };
    const elapsed = () => state.baseMs + (state.runStart === null ? 0 : performance.now() - state.runStart);
    const running = () => state.runStart !== null;
    const stepText = (step) => (step.supply ? step.supply + ' ' : '') + step.action;
    const trackEvent = (eventName, params) => {
        if (typeof window.gtag === 'function') {
            window.gtag('event', eventName, params || {});
        }
    };

    // --- speech ---------------------------------------------------------
    const synth = 'speechSynthesis' in window ? window.speechSynthesis : null;
    let koreanVoice = null;
    const pickVoice = () => {
        if (!synth) return;
        koreanVoice = synth.getVoices().find((voice) => voice.lang && voice.lang.toLowerCase().startsWith('ko')) || null;
    };
    if (synth) {
        pickVoice();
        synth.addEventListener('voiceschanged', pickVoice);
    } else {
        ui.voice.checked = false;
        ui.voice.disabled = true;
    }
    const speak = (text) => {
        if (!synth || !ui.voice.checked) return;
        synth.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'ko-KR';
        utterance.rate = 1.1;
        if (koreanVoice) utterance.voice = koreanVoice;
        synth.speak(utterance);
    };

    // Keep a phone screen awake while the clock runs.
    const holdWakeLock = async () => {
        if (!('wakeLock' in navigator) || state.wakeLock || !running()) return;
        try {
            state.wakeLock = await navigator.wakeLock.request('screen');
            state.wakeLock.addEventListener('release', () => { state.wakeLock = null; });
        } catch (e) { /* not granted; ignore */ }
    };
    const releaseWakeLock = () => {
        if (state.wakeLock) state.wakeLock.release().catch(() => {});
        state.wakeLock = null;
    };
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') holdWakeLock();
    });

    // --- rendering ------------------------------------------------------
    const lastRunSplits = () => (store.read().lastRuns || {})[state.build.id] || [];

    const renderSteps = () => {
        const previous = state.previous;
        ui.steps.textContent = '';
        state.build.steps.forEach((step, index) => {
            const item = document.createElement('li');
            item.className = 'sc-bt-steps__item';
            if (index < state.current) item.classList.add('is-done');
            if (state.started && index === state.current) item.classList.add('is-current');

            const supply = document.createElement('span');
            supply.className = 'sc-bt-steps__supply';
            supply.textContent = step.supply || '';
            const action = document.createElement('span');
            action.className = 'sc-bt-steps__action';
            action.textContent = step.action;
            const times = document.createElement('span');
            times.className = 'sc-bt-steps__times';
            const mine = state.splits[index] === undefined ? '' : formatClock(state.splits[index]);
            const before = previous[index] === undefined || previous[index] === null ? '' : formatClock(previous[index]);
            times.textContent = (mine || '-') + ' / ' + (before || '-');

            item.append(supply, action, times);
            ui.steps.append(item);
        });
        // Scroll only inside the step list so the clock never leaves the screen.
        const currentItem = ui.steps.querySelector('.is-current');
        if (currentItem && ui.steps.scrollHeight > ui.steps.clientHeight) {
            const top = currentItem.offsetTop - ui.steps.offsetTop;
            if (top < ui.steps.scrollTop || top + currentItem.offsetHeight > ui.steps.scrollTop + ui.steps.clientHeight) {
                ui.steps.scrollTop = Math.max(0, top - ui.steps.clientHeight / 2);
            }
        }
    };

    const renderCheckpoints = () => {
        ui.checkpoints.textContent = '';
        const checkpoints = state.build.checkpoints || [];
        if (!checkpoints.length) {
            const empty = document.createElement('li');
            empty.className = 'sc-bt-checkpoints__empty';
            empty.textContent = '원문에 시간 표기가 없는 빌드입니다. 인구수로 따라가세요.';
            ui.checkpoints.append(empty);
        }
        checkpoints.forEach((checkpoint, index) => {
            const item = document.createElement('li');
            if (state.fired.has(index)) item.classList.add('is-done');
            const time = document.createElement('span');
            time.className = 'sc-bt-checkpoints__time';
            time.textContent = checkpoint.time;
            const text = document.createElement('span');
            text.textContent = checkpoint.text;
            item.append(time, text);
            ui.checkpoints.append(item);
        });
        ui.note.hidden = !state.build.note;
        ui.note.textContent = state.build.note || '';
    };

    const renderNow = () => {
        const steps = state.build.steps;
        if (!state.started) {
            ui.now.textContent = stepText(steps[0]);
            ui.next.textContent = steps[1] ? stepText(steps[1]) : '-';
        } else if (state.current >= steps.length) {
            ui.now.textContent = '빌드 끝';
            ui.next.textContent = '-';
        } else {
            ui.now.textContent = stepText(steps[state.current]);
            ui.next.textContent = steps[state.current + 1] ? stepText(steps[state.current + 1]) : '빌드 끝';
        }
        ui.start.textContent = running() ? '일시정지' : (state.started ? '계속' : '시작');
        ui.step.disabled = !running() || state.current >= steps.length;
        ui.undo.disabled = state.current === 0;
    };

    const renderClock = () => {
        const ms = elapsed();
        const second = Math.floor(ms / 1000);
        if (second !== state.lastShownSecond) {
            state.lastShownSecond = second;
            ui.clock.textContent = formatClock(ms);
        }
    };

    const renderAll = () => {
        renderClock();
        renderNow();
        renderSteps();
        renderCheckpoints();
    };

    let alertTimer = null;
    const flashAlert = (text) => {
        ui.alert.textContent = text;
        ui.alert.hidden = false;
        clearTimeout(alertTimer);
        alertTimer = setTimeout(() => { ui.alert.hidden = true; }, 6000);
    };

    // --- clock and checkpoints -----------------------------------------
    const checkCheckpoints = () => {
        const lead = Number(ui.lead.value) * 1000;
        const ms = elapsed();
        let changed = false;
        (state.build.checkpoints || []).forEach((checkpoint, index) => {
            if (state.fired.has(index) || ms < checkpoint.at * 1000 - lead) return;
            state.fired.add(index);
            changed = true;
            // Skip checkpoints that were already long past (e.g. after a big manual adjustment).
            if (ms - checkpoint.at * 1000 > 5000) return;
            const remaining = Math.round((checkpoint.at * 1000 - ms) / 1000);
            const prefix = remaining > 0 ? remaining + '초 뒤 ' : '';
            flashAlert(checkpoint.time + ' · ' + checkpoint.text);
            speak(prefix + checkpoint.text);
        });
        if (changed) renderCheckpoints();
    };

    const tick = () => {
        renderClock();
        checkCheckpoints();
    };

    const startClock = () => {
        if (running()) return;
        state.runStart = performance.now();
        state.tickId = setInterval(tick, 100);
        holdWakeLock();
    };
    const pauseClock = () => {
        if (!running()) return;
        state.baseMs = elapsed();
        state.runStart = null;
        clearInterval(state.tickId);
        state.tickId = null;
        releaseWakeLock();
    };

    const rearmCheckpointsForCurrentClock = () => {
        const lead = Number(ui.lead.value) * 1000;
        const ms = elapsed();
        (state.build.checkpoints || []).forEach((checkpoint, index) => {
            if (ms < checkpoint.at * 1000 - lead) state.fired.delete(index);
        });
    };

    const adjustClock = (deltaMs) => {
        state.baseMs += deltaMs;
        const overshoot = elapsed();
        if (overshoot < 0) state.baseMs -= overshoot;
        // Moving the clock back re-arms checkpoints that are now in the future again.
        rearmCheckpointsForCurrentClock();
        state.lastShownSecond = -1;
        renderClock();
        renderCheckpoints();
    };

    // --- actions --------------------------------------------------------
    const saveRun = () => {
        if (!state.splits.length) return;
        const totalSteps = state.build.steps.length;
        const isComplete = state.splits.length >= totalSteps;
        const lastRuns = store.read().lastRuns || {};
        const existing = Array.isArray(lastRuns[state.build.id]) ? lastRuns[state.build.id] : [];
        // Keep an existing completed run intact when a new attempt is abandoned early.
        if (!isComplete && existing.length >= totalSteps) return;
        if (!isComplete && existing.length > state.splits.length) return;
        lastRuns[state.build.id] = state.splits.map((ms) => Math.round(ms));
        store.write({ lastRuns });
    };

    const toggleStart = () => {
        if (running()) {
            pauseClock();
        } else {
            const firstStart = !state.started;
            state.started = true;
            startClock();
            if (firstStart) {
                speak(stepText(state.build.steps[0]));
                trackEvent('build_timer_start', {
                    build_id: state.build.id,
                    matchup: state.build.matchup
                });
            }
        }
        renderNow();
        renderSteps();
    };

    const nextStep = () => {
        const steps = state.build.steps;
        if (!running() || state.current >= steps.length) return;
        state.splits[state.current] = elapsed();
        state.current += 1;
        if (state.current >= steps.length) {
            saveRun();
            speak('빌드 끝');
            trackEvent('build_timer_complete', {
                build_id: state.build.id,
                matchup: state.build.matchup,
                duration_sec: Math.round(elapsed() / 1000)
            });
        } else {
            speak(stepText(steps[state.current]));
        }
        renderNow();
        renderSteps();
    };

    const previousStep = () => {
        if (state.current === 0) return;
        state.current -= 1;
        state.splits.length = state.current;
        renderNow();
        renderSteps();
    };

    const reset = () => {
        saveRun();
        pauseClock();
        if (synth) synth.cancel();
        state.baseMs = 0;
        state.started = false;
        state.current = 0;
        state.splits = [];
        state.fired.clear();
        state.previous = lastRunSplits();
        state.lastShownSecond = -1;
        ui.alert.hidden = true;
        renderAll();
    };

    const selectBuild = (id, trackSelection = false) => {
        const build = builds.find((candidate) => candidate.id === id) || builds[0];
        reset();
        state.build = build;
        state.previous = lastRunSplits();
        ui.build.value = build.id;
        ui.source.href = build.sourceUrl;
        ui.source.textContent = '원문 보기';
        ui.summary.textContent = build.summary;
        if (ui.fbBuild && !state.fbBuildDirty
                && Array.from(ui.fbBuild.options).some((option) => option.value === build.id)) {
            ui.fbBuild.value = build.id;
        }
        store.write({ buildId: build.id });
        renderAll();
        if (trackSelection) {
            trackEvent('build_timer_select', {
                build_id: state.build.id,
                matchup: state.build.matchup
            });
        }
    };

    ui.build.addEventListener('change', () => selectBuild(ui.build.value, true));
    ui.start.addEventListener('click', toggleStart);
    ui.step.addEventListener('click', nextStep);
    ui.undo.addEventListener('click', previousStep);
    ui.reset.addEventListener('click', reset);
    ui.minus.addEventListener('click', () => adjustClock(-1000));
    ui.plus.addEventListener('click', () => adjustClock(1000));
    ui.voice.addEventListener('change', () => {
        store.write({ voice: ui.voice.checked });
        if (!ui.voice.checked && synth) synth.cancel();
    });
    ui.lead.addEventListener('change', () => {
        store.write({ lead: ui.lead.value });
        rearmCheckpointsForCurrentClock();
        if (running()) checkCheckpoints();
        else renderCheckpoints();
    });

    document.querySelectorAll('[data-bt-select]').forEach((button) => {
        button.addEventListener('click', () => {
            selectBuild(button.dataset.btSelect, true);
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    });

    document.addEventListener('keydown', (event) => {
        if (event.altKey || event.ctrlKey || event.metaKey) return;
        const target = event.target;
        if (target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT|SUMMARY|A)$/.test(target.tagName))) return;
        if (target && target.closest && target.closest('#scTerminal, #scBtFeedback')) return;
        if (event.key === ' ' && target && target.tagName === 'BUTTON' && target !== ui.start && target !== ui.step) return;
        switch (event.key) {
            case ' ':
                event.preventDefault();
                if (running()) nextStep(); else toggleStart();
                break;
            case 'Backspace':
                event.preventDefault();
                previousStep();
                break;
            case 'ArrowLeft':
                event.preventDefault();
                adjustClock(-1000);
                break;
            case 'ArrowRight':
                event.preventDefault();
                adjustClock(1000);
                break;
            case 'r':
            case 'R':
                reset();
                break;
            default:
        }
    });
    // Start/Step buttons keep focus after a click; stop Space from also "clicking" the focused button.
    [ui.start, ui.step].forEach((button) => {
        button.addEventListener('keydown', (event) => { if (event.key === ' ') event.preventDefault(); });
        button.addEventListener('keyup', (event) => { if (event.key === ' ') event.preventDefault(); });
    });
    window.addEventListener('pagehide', saveRun);

    // --- beta feedback --------------------------------------------------
    const updateFeedbackCount = () => {
        if (!ui.fbMessage || !ui.fbCount) return;
        const length = Array.from(ui.fbMessage.value).length;
        ui.fbCount.textContent = length + ' / 1000';
    };
    const showFeedbackStatus = (text, ok) => {
        if (!ui.fbStatus) return;
        ui.fbStatus.textContent = text;
        ui.fbStatus.classList.toggle('is-ok', !!ok);
        ui.fbStatus.classList.toggle('is-error', !ok);
        ui.fbStatus.hidden = !text;
    };
    if (ui.fbBuild) {
        ui.fbBuild.addEventListener('change', () => {
            state.fbBuildDirty = true;
        });
    }
    if (ui.fbMessage) {
        ui.fbMessage.addEventListener('input', updateFeedbackCount);
        updateFeedbackCount();
    }
    if (ui.fbForm) {
        ui.fbForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const message = ui.fbMessage ? ui.fbMessage.value.trim() : '';
            if (Array.from(message).length < 5) {
                showFeedbackStatus('피드백 내용을 5자 이상 입력해주세요.', false);
                if (ui.fbMessage) ui.fbMessage.focus();
                return;
            }
            const category = ui.fbCategory ? ui.fbCategory.value : 'general';
            const buildId = ui.fbBuild ? ui.fbBuild.value : '';
            const contact = ui.fbContact ? ui.fbContact.value.trim() : '';
            const website = ui.fbWebsite ? ui.fbWebsite.value : '';

            if (ui.fbSubmit) {
                ui.fbSubmit.disabled = true;
                ui.fbSubmit.textContent = '전송 중...';
            }
            showFeedbackStatus('', true);
            try {
                const response = await fetch('/api/build-timer/feedback', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/json',
                        Accept: 'application/json'
                    },
                    body: JSON.stringify({ category, buildId, message, contact, website })
                });
                const data = await response.json().catch(() => null);
                if (response.ok && data && data.success) {
                    showFeedbackStatus(data.message || '피드백이 접수되었습니다. 소중한 의견 감사합니다!', true);
                    if (ui.fbMessage) ui.fbMessage.value = '';
                    updateFeedbackCount();
                    trackEvent('build_timer_feedback_submit', {
                        category,
                        build_id: buildId || 'all'
                    });
                } else {
                    const errorMessage = (data && data.error) ? data.error : '피드백 전송에 실패했습니다. 잠시 후 다시 시도해주세요.';
                    showFeedbackStatus(errorMessage, false);
                }
            } catch (e) {
                showFeedbackStatus('네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.', false);
            } finally {
                if (ui.fbSubmit) {
                    ui.fbSubmit.disabled = false;
                    ui.fbSubmit.textContent = '피드백 보내기';
                }
            }
        });
    }

    const saved = store.read();
    if (typeof saved.voice === 'boolean' && synth) ui.voice.checked = saved.voice;
    if (saved.lead !== undefined && Array.from(ui.lead.options).some((option) => option.value === String(saved.lead))) {
        ui.lead.value = String(saved.lead);
    }
    const requestedBuildId = new URLSearchParams(window.location.search).get('build');
    const initialBuildId = (requestedBuildId && builds.some((candidate) => candidate.id === requestedBuildId))
            ? requestedBuildId
            : (saved.buildId || builds[0].id);
    selectBuild(initialBuildId);
})();
