#!/usr/bin/env node
// 모바일 뷰포트 가로 넘침 검사 도구.
//
// 주의: `chrome --headless --window-size=375,812 --screenshot` 방식은 쓰면 안 된다.
// Chrome은 창 최소 폭이 500px이라 375를 줘도 레이아웃은 500px로 계산되고
// 캡처만 375px로 잘려서, 오른쪽 정렬된 헤더 메뉴([로그인]/[회원가입])가
// 잘린 것처럼 보이는 가짜 재현이 된다. 반드시 CDP 모바일 에뮬레이션으로 검사할 것.
//
// 사용법 (Node 22+, 로컬 Chrome 필요):
//   node scripts/check-mobile-overflow.mjs [--width 375] <url> [url...]
// 예:
//   node scripts/check-mobile-overflow.mjs http://localhost:8082/ http://localhost:8082/login
//
// 출력: 페이지별 document.scrollWidth 와, 뷰포트 오른쪽 밖으로 나가는데
// overflow 숨김 조상이 없는(=실제로 가로 스크롤을 만드는) 요소 목록.

import { spawn } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = process.argv.slice(2);
let width = 375;
const urls = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--width') { width = Number(args[++i]) || 375; }
  else urls.push(args[i]);
}
if (!urls.length) {
  console.error('usage: node scripts/check-mobile-overflow.mjs [--width 375] <url> [url...]');
  process.exit(1);
}

const CHROME = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = 9333;
const profile = mkdtempSync(join(tmpdir(), 'sc1hub-overflow-'));
const chrome = spawn(CHROME, [
  '--headless', '--disable-gpu', `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' });
process.on('exit', () => chrome.kill());

// wait for CDP endpoint
let endpoint = null;
for (let i = 0; i < 40 && !endpoint; i++) {
  try {
    const r = await fetch(`http://127.0.0.1:${PORT}/json`);
    endpoint = (await r.json()).find(t => t.type === 'page');
  } catch { await new Promise(r => setTimeout(r, 250)); }
}
if (!endpoint) { console.error('Chrome CDP endpoint not available'); process.exit(1); }

const ws = new WebSocket(endpoint.webSocketDebuggerUrl);
let id = 0; const pending = new Map();
let loadFired = () => {};
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const msgId = ++id; pending.set(msgId, { resolve, reject });
    ws.send(JSON.stringify({ id: msgId, method, params }));
  });
}
function withTimeout(p, ms, label) {
  return Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout: ' + label)), ms))]);
}
ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id); pending.delete(msg.id);
    msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
  } else if (msg.method === 'Page.javascriptDialogOpening') {
    send('Page.handleJavaScriptDialog', { accept: true }).catch(() => {});
  } else if (msg.method === 'Page.loadEventFired') {
    loadFired();
  }
};
await new Promise(r => { ws.onopen = r; });
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width, height: 812, deviceScaleFactor: 2, mobile: true });

const expr = `
(() => {
  const vw = document.documentElement.clientWidth;
  const out = [];
  for (const el of document.querySelectorAll('body *')) {
    const r = el.getBoundingClientRect();
    if (r.right > vw + 1) {
      let clipped = false, p = el.parentElement;
      while (p) {
        const s = getComputedStyle(p);
        if (s.overflowX !== 'visible') {
          const pr = p.getBoundingClientRect();
          if (pr.right <= vw + 1) { clipped = true; break; }
        }
        p = p.parentElement;
      }
      if (!clipped) out.push(el.tagName.toLowerCase()
        + (el.id ? '#' + el.id : '')
        + (typeof el.className === 'string' && el.className ? '.' + el.className.trim().split(/\\s+/).slice(0,3).join('.') : '')
        + ' width=' + Math.round(r.width) + ' right=' + Math.round(r.right));
    }
  }
  return JSON.stringify({ viewport: vw, docScrollWidth: document.documentElement.scrollWidth, offenders: out.slice(0, 20) });
})()`;

let failed = false;
for (const u of urls) {
  try {
    const loaded = new Promise(r => { loadFired = r; });
    await withTimeout(send('Page.navigate', { url: u }), 15000, 'navigate');
    await withTimeout(loaded, 20000, 'load').catch(() => {});
    await new Promise(r => setTimeout(r, 3000));
    const res = await withTimeout(send('Runtime.evaluate', { expression: expr, returnByValue: true }), 15000, 'eval');
    const data = JSON.parse(res.result.value);
    const over = data.docScrollWidth > data.viewport;
    if (over) failed = true;
    console.log(`${over ? 'OVERFLOW' : 'OK'}  ${u}  doc=${data.docScrollWidth} vw=${data.viewport}`);
    for (const o of data.offenders) console.log('   ->', o);
  } catch (e) {
    failed = true;
    console.log(`ERROR  ${u}  ${e.message}`);
  }
}
ws.close(); chrome.kill();
process.exit(failed ? 1 : 0);
