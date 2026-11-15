/* Browser smoke tests for the PUBLIC SIMULATOR, not the native Android IME. */
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { dirname, extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const contentType = {
  ".html":"text/html; charset=utf-8",
  ".js":"application/javascript; charset=utf-8",
  ".css":"text/css; charset=utf-8",
  ".webmanifest":"application/manifest+json",
  ".svg":"image/svg+xml",
  ".png":"image/png",
};
const server = createServer(async (req,res)=>{
  let path = decodeURIComponent((req.url||"/").split("?")[0]);
  if(path==="/")path="/index.html";
  const safe=normalize(path).replace(/^[/\\]+/,"");
  if(safe.startsWith("..")){res.writeHead(403);res.end();return;}
  try{
    const file=await readFile(join(root,safe));
    res.writeHead(200,{"content-type":contentType[extname(safe)]||"application/octet-stream"});
    res.end(file);
  }catch{res.writeHead(404);res.end("Not found");}
});
const port=await new Promise(resolve=>server.listen(0,"127.0.0.1",()=>resolve(server.address().port)));
const address=`http://127.0.0.1:${port}`;
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
const errors=[];
const checkErrors=(page,label)=>{
  page.on("pageerror",e=>errors.push(`${label}: ${e.message}`));
  page.on("console",m=>{if(m.type()==="error")errors.push(`${label}: console ${m.text()}`);});
};
try{
  const page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1});
  checkErrors(page,"desktop");
  await page.goto(address,{waitUntil:"networkidle"});
  await page.locator("#keyboardContent .key").first().waitFor();
  assert.ok(await page.locator("#keyboardContent .key").count()>=25,"keyboard keys must render");
  await page.locator('[data-key="h"]').click();
  await page.locator('[data-key="i"]').click();
  assert.equal(await page.locator("#draft").inputValue(),"hi");
  await page.locator('[data-key="back"]').click();
  assert.equal(await page.locator("#draft").inputValue(),"h");
  await page.locator("#sendDemo").click();
  assert.equal(await page.locator(".bubble.outgoing").last().textContent(),"h");
  assert.equal(await page.locator("#draft").inputValue(),"");

  await page.locator('[data-tool="symbols"]').click();
  assert.ok(await page.locator("#symbolsPanel button").count()>10);
  await page.locator("#symbolsPanel button").first().click();
  assert.notEqual(await page.locator("#draft").inputValue(),"");

  await page.locator('[data-tool="stickers"]').click();
  assert.ok(await page.locator("#stickerItems button").count()>5);
  await page.locator("#stickerPacks button").last().click();
  assert.ok(await page.locator("#stickerItems button").count()>5);

  await page.locator('[data-tool="styles"]').click();
  await page.locator("#styleSource").fill("Hello World");
  await page.locator(".style-choice").first().click();
  assert.ok((await page.locator("#draft").inputValue()).length>2);

  await page.locator('[data-tool="prescreen"]').click();
  await page.locator("#prescreenSource").fill("hello");
  await page.locator('[data-format="bold"]').click();
  assert.equal(await page.locator("#prescreenSource").inputValue(),"**hello**");
  await page.locator("#approveDraft").click();
  assert.equal(await page.locator("#draft").inputValue(),"**hello**");

  await page.locator('.side-tab[data-view="appearance"]').click();
  await page.locator("#numberRow").locator("..").click();
  assert.equal(await page.locator("#numberRow").isChecked(),true);
  await page.locator("#roundKeys").locator("..").click();
  assert.equal(await page.locator("#roundKeys").isChecked(),false);
  await page.locator("#layoutChoice").selectOption("azerty");
  await page.locator('.side-tab[data-view="keyboard"]').click();
  assert.equal(await page.locator('[data-key="a"]').count(),1);
  assert.equal(await page.locator('[data-key="1"]').count(),1);

  await page.locator('.side-tab[data-view="voice"]').click();
  await page.locator(".cleanup-mode").last().click();
  await page.locator("#demoClean").click();
  assert.ok((await page.locator("#voiceResult").textContent()).length>15);

  const manifestResp=await page.request.get(address+"/manifest.webmanifest");
  assert.equal(manifestResp.status(),200);
  const manifest=await manifestResp.json();
  assert.equal(manifest.display,"standalone");
  assert.equal(manifest.scope,"./");

  const mobile=await browser.newPage({viewport:{width:375,height:812},deviceScaleFactor:2,isMobile:true,hasTouch:true});
  checkErrors(mobile,"mobile");
  await mobile.goto(address,{waitUntil:"networkidle"});
  await mobile.locator("#keyboardContent .key").first().waitFor();
  assert.equal(await mobile.locator("#draft").getAttribute("inputmode"),"none");
  await mobile.locator('[data-key="a"]').click();
  assert.equal(await mobile.locator("#draft").inputValue(),"a");
  await mobile.locator('[data-viewport="landscape"]').click();
  assert.ok(await mobile.locator("#device").evaluate(el=>Number.parseFloat(getComputedStyle(el).zoom||"1")<0.9),"landscape should scale down on narrow phone");
  const view=await mobile.locator("#device").boundingBox();
  const last=await mobile.locator("#keyboardContent .key").last().boundingBox();
  assert.ok(view&&last);
  assert.ok(last.y+last.height<=view.y+view.height+3,"last keyboard row must fit inside device");
  assert.ok(last.x+last.width<=view.x+view.width+3,"last keyboard key must fit inside device");
  assert.deepEqual(errors,[],"console/runtime errors");
  console.log("PASS Tsuzuri desktop and mobile browser smoke: key input, backspace, mock send, styles, stickers, symbols, Pre-screen, settings, voice demo, manifest, scaled landscape.");
  await page.screenshot({path:"/tmp/tsuzuri-browser-desktop.png",fullPage:true});
  await mobile.screenshot({path:"/tmp/tsuzuri-browser-mobile.png",fullPage:true});
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
