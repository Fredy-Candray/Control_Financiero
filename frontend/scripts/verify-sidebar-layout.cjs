// Browser layout check using actual application CSS and synthetic navigation only.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const runtime = path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const { chromium } = require(runtime);
(async () => {
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  try {
    const page = await browser.newPage();
    const css = fs.readFileSync(path.join(root, 'src/styles.css'), 'utf8') + '\n' + fs.readFileSync(path.join(root, 'src/app/app.component.css'), 'utf8');
    const groups = [['PRINCIPAL', 'Dashboard'], ['FINANZAS', 'Cuentas', 'Movimientos', 'Calendario', 'Reportes'], ['SISTEMA', 'Usuarios', 'Administrar menú']];
    const nav = groups.map(([title, ...items]) => `<section class="nav-group"><h3 class="nav-group-title">${title}</h3>${items.map(name => `<a href="#"><span>◫</span><span class="nav-label">${name}</span></a>`).join('')}</section>`).join('');
    for (const [width, height, collapsed] of [[1366,600,false],[1024,500,false],[1920,1080,false],[800,600,false],[390,640,false],[640,360,false],[1366,500,true]]) {
      await page.setViewportSize({ width, height });
      await page.setContent(`<html><head><style>${css}</style></head><body><div class="app-shell ${collapsed ? 'sidebar-collapsed' : ''}"><aside class="sidebar drawer open"><div class="brand"><div class="brand-logo">C</div><div class="brand-text"><h2>COFI</h2><p>Control Financiero</p></div></div><nav class="nav-links">${nav}</nav><div class="sidebar-footer"><button class="theme-btn" aria-label="Cambiar tema">☀</button></div><div class="sidebar-toggle-wrapper"><button class="sidebar-toggle-btn">‹</button></div></aside><main class="content-area">Prueba de distribución sin datos reales.</main></div></body></html>`);
      const rects = await page.evaluate(() => {
        const button = document.querySelector('.theme-btn');
        const nav = document.querySelector('.nav-links');
        const r = button.getBoundingClientRect();
        return { top:r.top, bottom:r.bottom, hit:button.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)), navBottom:nav.getBoundingClientRect().bottom, footerTop:document.querySelector('.sidebar-footer').getBoundingClientRect().top };
      });
      assert(rects.top >= 0 && rects.bottom <= height, JSON.stringify({ width,height,collapsed,rects }));
      assert(rects.hit, 'Theme control is covered');
      assert(rects.navBottom <= rects.footerTop + 1, 'Menu overlaps footer');
      await page.locator('.nav-links').evaluate(el => el.scrollTop = el.scrollHeight);
      const last = await page.locator('.nav-links a').last().boundingBox();
      assert(last.y + last.height <= rects.footerTop + 1, 'Last navigation option is inaccessible');
      const output = path.join(root, '../output/sidebar'); fs.mkdirSync(output, { recursive:true });
      await page.screenshot({ path:path.join(output, `${width}x${height}${collapsed ? '-collapsed' : ''}.png`) });
      console.log(`${width}x${height}${collapsed ? ' collapsed' : ''}: theme visible, menu scrollable and accessible`);
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode=1; });
