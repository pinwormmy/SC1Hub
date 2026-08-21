(() => {
    const form = document.querySelector('[data-post-editor-form]');
    if (!form) {
        return;
    }

    const editor = form.querySelector('[data-post-editor]');
    const surface = form.querySelector('[data-editor-surface]');
    const source = form.querySelector('[data-editor-source]');
    const output = form.querySelector('[data-editor-output]');
    const initial = form.querySelector('[data-editor-initial]');
    const toolbar = form.querySelector('[data-editor-toolbar]');
    const status = form.querySelector('[data-editor-status]');
    const titleInput = form.querySelector('input[name="title"]');
    const imageInput = form.querySelector('[data-editor-image-input]');
    const draftNotice = form.querySelector('[data-editor-draft-notice]');
    const draftKey = form.dataset.draftKey;
    const uploadUrl = form.dataset.uploadUrl;
    let currentMode = 'visual';
    let savedRange = null;
    let saveTimer = null;
    let dragDepth = 0;

    const setStatus = (message, state = 'idle') => {
        status.textContent = message;
        status.dataset.state = state;
    };

    const setNoticeFallback = () => {
        const checkbox = form.querySelector('[data-notice-checkbox]');
        const fallback = form.querySelector('[data-notice-fallback]');
        if (checkbox && fallback) {
            fallback.disabled = checkbox.checked;
        }
    };

    const readDraft = () => {
        if (!draftKey) {
            return null;
        }
        try {
            const raw = window.localStorage.getItem(draftKey);
            return raw ? JSON.parse(raw) : null;
        } catch (error) {
            return null;
        }
    };

    const writeDraft = (submitted = false) => {
        if (!draftKey) {
            return;
        }
        const html = currentMode === 'source' ? source.value : surface.innerHTML;
        try {
            window.localStorage.setItem(draftKey, JSON.stringify({
                title: titleInput.value,
                html,
                submitted,
                savedAt: Date.now()
            }));
            setStatus(submitted ? '제출본을 임시 보관함' : '이 기기에 자동 저장됨');
        } catch (error) {
            setStatus('자동 저장을 사용할 수 없음');
        }
    };

    const scheduleDraftSave = () => {
        setStatus('저장 중…', 'saving');
        window.clearTimeout(saveTimer);
        saveTimer = window.setTimeout(() => writeDraft(false), 700);
    };

    const restoreDraft = (draft) => {
        if (!draft) {
            return;
        }
        titleInput.value = draft.title || '';
        surface.innerHTML = draft.html || '';
        source.value = surface.innerHTML;
        draftNotice.hidden = true;
        setStatus('임시본 복원됨');
    };

    const discardDraft = () => {
        try {
            window.localStorage.removeItem(draftKey);
        } catch (error) {
            // Storage may be unavailable in private browsing.
        }
        draftNotice.hidden = true;
        setStatus('임시본 삭제됨');
    };

    const initialHtml = initial ? initial.value : '';
    surface.innerHTML = initialHtml;
    source.value = initialHtml;
    setNoticeFallback();

    const draft = readDraft();
    if (draft && draft.html !== initialHtml) {
        if (draft.submitted) {
            draftNotice.hidden = false;
        } else {
            restoreDraft(draft);
        }
    }

    form.querySelector('[data-editor-restore]')?.addEventListener('click', () => restoreDraft(readDraft()));
    form.querySelector('[data-editor-discard]')?.addEventListener('click', discardDraft);
    form.querySelector('[data-notice-checkbox]')?.addEventListener('change', setNoticeFallback);

    const selectionBelongsToSurface = (selection) => {
        if (!selection || selection.rangeCount < 1) {
            return false;
        }
        const node = selection.getRangeAt(0).commonAncestorContainer;
        return surface.contains(node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement);
    };

    const rememberSelection = () => {
        const selection = window.getSelection();
        if (selectionBelongsToSurface(selection)) {
            savedRange = selection.getRangeAt(0).cloneRange();
        }
    };

    const restoreSelection = () => {
        surface.focus();
        if (!savedRange) {
            return;
        }
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(savedRange);
    };

    const runCommand = (command, value = null) => {
        restoreSelection();
        document.execCommand(command, false, value);
        rememberSelection();
        scheduleDraftSave();
        updateToolbarState();
    };

    const updateToolbarState = () => {
        toolbar.querySelectorAll('[data-editor-command]').forEach((button) => {
            const command = button.dataset.editorCommand;
            let active = false;
            try {
                active = document.queryCommandState(command);
            } catch (error) {
                active = false;
            }
            button.setAttribute('aria-pressed', String(active));
        });
    };

    document.addEventListener('selectionchange', () => {
        rememberSelection();
        updateToolbarState();
    });

    try {
        document.execCommand('defaultParagraphSeparator', false, 'p');
    } catch (error) {
        // Older browsers may not support changing the default paragraph separator.
    }

    toolbar.querySelectorAll('[data-editor-command]').forEach((button) => {
        button.addEventListener('mousedown', (event) => event.preventDefault());
        button.addEventListener('click', () => runCommand(button.dataset.editorCommand));
    });

    form.querySelector('[data-editor-block]').addEventListener('change', (event) => {
        runCommand('formatBlock', event.target.value);
    });

    form.querySelector('[data-editor-link]').addEventListener('click', () => {
        const url = window.prompt('연결할 주소를 입력하세요.', 'https://');
        if (!url) {
            return;
        }
        if (!/^(https?:\/\/|mailto:|\/|#)/i.test(url.trim())) {
            window.alert('http(s), mailto, / 또는 #으로 시작하는 주소만 사용할 수 있습니다.');
            return;
        }
        runCommand('createLink', url.trim());
    });

    const youtubeEmbedUrl = (rawUrl) => {
        try {
            const url = new URL(rawUrl);
            let videoId = '';
            if (url.hostname === 'youtu.be') {
                videoId = url.pathname.slice(1).split('/')[0];
            } else if (url.hostname.endsWith('youtube.com')) {
                videoId = url.searchParams.get('v') || '';
                if (!videoId && url.pathname.startsWith('/shorts/')) {
                    videoId = url.pathname.split('/')[2] || '';
                }
                if (!videoId && url.pathname.startsWith('/embed/')) {
                    videoId = url.pathname.split('/')[2] || '';
                }
            }
            return /^[A-Za-z0-9_-]{6,20}$/.test(videoId)
                ? `https://www.youtube-nocookie.com/embed/${videoId}`
                : null;
        } catch (error) {
            return null;
        }
    };

    form.querySelector('[data-editor-video]').addEventListener('click', () => {
        const rawUrl = window.prompt('유튜브 영상 주소를 입력하세요.');
        if (!rawUrl) {
            return;
        }
        const embedUrl = youtubeEmbedUrl(rawUrl.trim());
        if (!embedUrl) {
            window.alert('올바른 유튜브 주소를 입력해주세요.');
            return;
        }
        const html = `<div class="sc-video-embed"><iframe src="${embedUrl}" title="유튜브 영상" `
            + 'allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" '
            + 'allowfullscreen loading="lazy"></iframe></div><p><br></p>';
        runCommand('insertHTML', html);
    });

    const escapeHtml = (value) => value.replace(/[&<>"]/g, (character) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
    }[character]));

    const uploadImage = async (file) => {
        if (!file || !/^image\/(jpeg|png)$/i.test(file.type)) {
            window.alert('JPEG 또는 PNG 이미지만 첨부할 수 있습니다.');
            return;
        }
        if (file.size > 10 * 1024 * 1024) {
            window.alert('이미지는 10MB 이하만 첨부할 수 있습니다.');
            return;
        }

        setStatus('이미지 최적화 및 업로드 중…', 'saving');
        editor.dataset.uploading = 'true';
        const body = new FormData();
        body.append('upload', file, file.name);
        try {
            const response = await fetch(uploadUrl, {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
                body
            });
            const result = await response.json();
            if (!response.ok || result.uploaded !== 1 || !result.url) {
                const message = result.error?.message || result.message || '이미지 업로드에 실패했습니다.';
                throw new Error(message);
            }
            const alt = escapeHtml(file.name.replace(/\.[^.]+$/, ''));
            const width = Number(result.width) || '';
            const height = Number(result.height) || '';
            const sizeAttrs = width && height ? ` width="${width}" height="${height}"` : '';
            const html = `<figure class="sc-post-image"><img src="${escapeHtml(result.url)}" alt="${alt}"${sizeAttrs}>`
                + '<figcaption>이미지 설명</figcaption></figure><p><br></p>';
            runCommand('insertHTML', html);
            setStatus(`이미지 첨부됨 · ${width || '?'}×${height || '?'}`);
        } catch (error) {
            window.alert(error.message || '이미지 업로드에 실패했습니다.');
            setStatus('이미지 업로드 실패');
        } finally {
            delete editor.dataset.uploading;
            imageInput.value = '';
        }
    };

    form.querySelector('[data-editor-image]').addEventListener('click', () => {
        rememberSelection();
        imageInput.click();
    });
    imageInput.addEventListener('change', () => uploadImage(imageInput.files[0]));

    surface.addEventListener('dragenter', (event) => {
        if (event.dataTransfer?.types?.includes('Files')) {
            event.preventDefault();
            dragDepth += 1;
            surface.classList.add('is-dragging');
        }
    });
    surface.addEventListener('dragover', (event) => {
        if (event.dataTransfer?.types?.includes('Files')) {
            event.preventDefault();
        }
    });
    surface.addEventListener('dragleave', () => {
        dragDepth = Math.max(0, dragDepth - 1);
        if (dragDepth === 0) {
            surface.classList.remove('is-dragging');
        }
    });
    surface.addEventListener('drop', (event) => {
        dragDepth = 0;
        surface.classList.remove('is-dragging');
        const file = Array.from(event.dataTransfer?.files || []).find((item) => item.type.startsWith('image/'));
        if (file) {
            event.preventDefault();
            rememberSelection();
            void uploadImage(file);
        }
    });

    surface.addEventListener('paste', (event) => {
        const image = Array.from(event.clipboardData?.items || [])
            .find((item) => item.type.startsWith('image/'))?.getAsFile();
        if (image) {
            event.preventDefault();
            rememberSelection();
            void uploadImage(image);
            return;
        }
        const text = event.clipboardData?.getData('text/plain');
        if (typeof text === 'string') {
            event.preventDefault();
            document.execCommand('insertText', false, text);
        }
    });

    const safePreviewHtml = (html) => {
        const template = document.createElement('template');
        template.innerHTML = html;
        template.content.querySelectorAll('script, style, object, embed, form').forEach((node) => node.remove());
        template.content.querySelectorAll('*').forEach((element) => {
            Array.from(element.attributes).forEach((attribute) => {
                const name = attribute.name.toLowerCase();
                const value = attribute.value.trim();
                if (name.startsWith('on') || ((name === 'href' || name === 'src') && /^javascript:/i.test(value))) {
                    element.removeAttribute(attribute.name);
                }
            });
            if (element.tagName === 'IFRAME') {
                try {
                    const url = new URL(element.getAttribute('src'));
                    const allowed = ['youtube.com', 'www.youtube.com', 'youtube-nocookie.com', 'www.youtube-nocookie.com'];
                    if (url.protocol !== 'https:' || !allowed.includes(url.hostname)) {
                        element.remove();
                    }
                } catch (error) {
                    element.remove();
                }
            }
        });
        return template.innerHTML;
    };

    const syncFromSource = () => {
        surface.innerHTML = source.value;
    };

    const switchMode = (mode) => {
        if (currentMode === 'source' && mode !== 'source') {
            syncFromSource();
        }
        if (mode === 'source') {
            source.value = surface.innerHTML;
        }
        if (mode === 'preview') {
            form.querySelector('[data-editor-panel="preview"]').innerHTML = safePreviewHtml(surface.innerHTML);
        }

        currentMode = mode;
        form.querySelectorAll('[data-editor-mode]').forEach((button) => {
            const selected = button.dataset.editorMode === mode;
            button.setAttribute('aria-selected', String(selected));
        });
        form.querySelectorAll('[data-editor-panel]').forEach((panel) => {
            panel.hidden = panel.dataset.editorPanel !== mode;
        });
        toolbar.hidden = mode !== 'visual';
        setStatus(mode === 'visual' ? '편집 중' : mode === 'source' ? 'HTML 편집 중' : '미리보기');
    };

    form.querySelectorAll('[data-editor-mode]').forEach((button) => {
        button.addEventListener('click', () => switchMode(button.dataset.editorMode));
    });

    surface.addEventListener('input', scheduleDraftSave);
    source.addEventListener('input', scheduleDraftSave);
    titleInput.addEventListener('input', scheduleDraftSave);

    form.addEventListener('keydown', (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
            event.preventDefault();
            form.requestSubmit();
        }
    });

    form.addEventListener('submit', (event) => {
        window.clearTimeout(saveTimer);
        if (currentMode === 'source') {
            syncFromSource();
        }
        const html = surface.innerHTML.trim();
        const probe = document.createElement('div');
        probe.innerHTML = html;
        const meaningful = probe.textContent.trim() || probe.querySelector('img, iframe, table');
        if (!meaningful) {
            event.preventDefault();
            switchMode('visual');
            surface.focus();
            window.alert('본문을 입력해주세요.');
            return;
        }
        output.value = html;
        setNoticeFallback();
        writeDraft(true);
        setStatus('게시 요청 중…', 'saving');
    });
})();
