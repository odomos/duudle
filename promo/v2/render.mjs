// Renders promo/index.html frame by frame in headless Chrome (no npm packages needed).
// The page exposes window.renderFrame(n) -> Promise and window.TOTAL_FRAMES.
//
//   node promo/render.mjs <outDir> [start] [end] [step]
//
// Frames are written as <outDir>/f_00000.jpg ... and then encoded with ffmpeg (see README).
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(process.argv[2] || path.join(here, 'frames'));
const argStart = process.argv[3] ? parseInt(process.argv[3], 10) : 0;
const argEnd = process.argv[4] ? parseInt(process.argv[4], 10) : -1;
const step = process.argv[5] ? parseInt(process.argv[5], 10) : 1;
const W = 1080, H = 1920, PORT = 9337;

const chromeCandidates = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
].filter(Boolean);
const chromePath = chromeCandidates.find((p) => existsSync(p));
if (!chromePath) throw new Error('Chrome/Edge not found; set CHROME_PATH');

mkdirSync(outDir, { recursive: true });
const profile = path.join(outDir, '..', 'chrome-profile');
const chrome = spawn(chromePath, [
  '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--allow-file-access-from-files', '--hide-scrollbars', '--mute-audio',
  '--force-device-scale-factor=1', `--window-size=${W},${H}`,
  '--disable-background-timer-throttling', '--no-first-run', '--no-default-browser-check',
  'about:blank',
], { stdio: 'ignore' });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function getTarget() {
  for (let i = 0; i < 100; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      const page = list.find((t) => t.type === 'page');
      if (page) return page;
    } catch { /* not up yet */ }
    await sleep(200);
  }
  throw new Error('Chrome did not start');
}

let exitCode = 0;
try {
  const target = await getTarget();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  let id = 0; const pending = new Map();
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { res, rej } = pending.get(msg.id); pending.delete(msg.id);
      msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result);
    }
  };
  const send = (method, params = {}) => new Promise((res, rej) => {
    const myId = ++id; pending.set(myId, { res, rej });
    ws.send(JSON.stringify({ id: myId, method, params }));
  });
  const evaluate = async (expression) => {
    const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error('Page error: ' + JSON.stringify(r.exceptionDetails.exception?.description || r.exceptionDetails));
    return r.result.value;
  };

  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: pathToFileURL(path.join(here, 'index.html')).href });
  for (let i = 0; i < 300; i++) {
    if (await evaluate('window.__ready === true').catch(() => false)) break;
    await sleep(100);
    if (i === 299) throw new Error('Page never became ready');
  }
  const total = await evaluate('window.TOTAL_FRAMES');
  const end = argEnd < 0 ? total : Math.min(argEnd, total);
  const t0 = Date.now();
  for (let f = argStart; f < end; f += step) {
    await evaluate(`window.renderFrame(${f})`);
    const shot = await send('Page.captureScreenshot', { format: 'jpeg', quality: 95, clip: { x: 0, y: 0, width: W, height: H, scale: 1 } });
    writeFileSync(path.join(outDir, `f_${String(f).padStart(5, '0')}.jpg`), Buffer.from(shot.data, 'base64'));
    if (f % 60 === 0) console.log(`frame ${f}/${end}  ${((Date.now() - t0) / 1000).toFixed(0)}s`);
  }
  console.log(`done: ${Math.ceil((end - argStart) / step)} frames -> ${outDir}`);
  ws.close();
} catch (e) {
  console.error(e); exitCode = 1;
} finally {
  chrome.kill();
}
process.exit(exitCode);
