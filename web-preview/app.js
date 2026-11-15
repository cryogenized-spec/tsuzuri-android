/* Tsuzuri web preview — independent, network-free UI simulator.
 * No Android IME, microphone capture, API key collection or AI requests.
 */
(() => {
  'use strict';
  const $ = (selector, root=document) => root.querySelector(selector);
  const $$ = (selector, root=document) => [...root.querySelectorAll(selector)];
  const state = {
    view: 'keyboard', tool: 'type', theme: 'default', layout: 'qwerty',
    shift: false, symbols: false, numberRow: false, roundKeys: true, oneHanded: false,
    keyScale: 100, cleanup: 'natural', viewport: 'portrait', stickerPack: 'Favorites',
    deferredInstall: null, toastTimer: null
  };
  const device = $('#device');
  const draft = $('#draft');
  const board = $('#keyboardContent');
  const themeData = [
    {id:'default',name:'Midnight',note:'Deep lavender',keys:['#292c40','#51566c']},
    {id:'light',name:'Paper',note:'Soft daylight',keys:['#f6f7fc','#b8bfcd']},
    {id:'ink',name:'Graphite',note:'Quiet focus',keys:['#142024','#526c6d']},
    {id:'plum',name:'Burgundy',note:'Warm character',keys:['#34232e','#906a83']}
  ];
  const layouts = {
    qwerty:['qwertyuiop','asdfghjkl','zxcvbnm'],
    azerty:['azertyuiop','qsdfghjklm','wxcvbn'],
    qwertz:['qwertzuiop','asdfghjkl','yxcvbnm']
  };
  const accentChars = {a:'á',e:'é',i:'í',o:'ó',u:'ú',c:'ç',n:'ñ',s:'š',y:'ý',A:'Á',E:'É',I:'Í',O:'Ó',U:'Ú'};
  const stickerPacks = {
    Favorites:['🌸','🐈','✨','🌙','☕','🌿','💜','🌻','🫧','🌼'],
    Expressions:['😎','🥹','🫶','🥳','💀','😤','🥰','🫠','👀','🙃'],
    Shapes:['💠','💫','🌈','🪐','⭐','🎴','🦋','🎐','🔮','🗝️']
  };
  const styleSets = [
    {id:'bold', name:'Bold serif', type:'math', upper:0x1d400,lower:0x1d41a,digit:0x1d7ce},
    {id:'italic',name:'Italic serif',type:'math',upper:0x1d434,lower:0x1d44e},
    {id:'sans',name:'Sans serif',type:'math',upper:0x1d5a0,lower:0x1d5ba,digit:0x1d7e2},
    {id:'sansbold',name:'Bold sans',type:'math',upper:0x1d5d4,lower:0x1d5ee,digit:0x1d7ec},
    {id:'mono',name:'Monospace',type:'math',upper:0x1d670,lower:0x1d68a,digit:0x1d7f6},
    {id:'fraktur',name:'Fraktur',type:'math',upper:0x1d504,lower:0x1d51e,exception:{C:0x212d,H:0x210c,I:0x2111,R:0x211c,Z:0x2128}},
    {id:'frakturbold',name:'Bold Fraktur',type:'math',upper:0x1d56c,lower:0x1d586},
    {id:'double',name:'Double struck',type:'math',upper:0x1d538,lower:0x1d552,digit:0x1d7d8,exception:{C:0x2102,H:0x210d,N:0x2115,P:0x2119,Q:0x211a,R:0x211d,Z:0x2124}},
    {id:'circled',name:'Circled',type:'circled'},
    {id:'fullwidth',name:'Fullwidth',type:'fullwidth'},
    {id:'squared',name:'Squared',type:'squared'},
    {id:'superscript',name:'Superscript',type:'sup'},
    {id:'spaced',name:'Letter spacing',type:'spaced'},
    {id:'alternating',name:'Alternating case',type:'alternating'},
    {id:'smallcaps',name:'Small capitals',type:'smallcaps'},
    {id:'strike',name:'Strike-through',type:'strike'},
    {id:'underline',name:'Underline',type:'underline'},
    {id:'star',name:'Star border',type:'border',before:'✦ ',after:' ✦'},
    {id:'flowers',name:'Floral border',type:'border',before:'❀ ',after:' ❀'},
    {id:'bracket',name:'Ornate quote',type:'border',before:'「',after:'」'}
  ];
  const smallCapMap = {a:'ᴀ',b:'ʙ',c:'ᴄ',d:'ᴅ',e:'ᴇ',f:'ꜰ',g:'ɢ',h:'ʜ',i:'ɪ',j:'ᴊ',k:'ᴋ',l:'ʟ',m:'ᴍ',n:'ɴ',o:'ᴏ',p:'ᴘ',q:'ǫ',r:'ʀ',s:'ꜱ',t:'ᴛ',u:'ᴜ',v:'ᴠ',w:'ᴡ',x:'x',y:'ʏ',z:'ᴢ'};
  const superscriptMap = {a:'ᵃ',b:'ᵇ',c:'ᶜ',d:'ᵈ',e:'ᵉ',f:'ᶠ',g:'ᵍ',h:'ʰ',i:'ⁱ',j:'ʲ',k:'ᵏ',l:'ˡ',m:'ᵐ',n:'ⁿ',o:'ᵒ',p:'ᵖ',r:'ʳ',s:'ˢ',t:'ᵗ',u:'ᵘ',v:'ᵛ',w:'ʷ',x:'ˣ',y:'ʸ',z:'ᶻ',0:'⁰',1:'¹',2:'²',3:'³',4:'⁴',5:'⁵',6:'⁶',7:'⁷',8:'⁸',9:'⁹'};
  function esc(s){return String(s).replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function toast(text){const x=$('#toast');x.textContent=text;x.classList.add('visible');clearTimeout(state.toastTimer);state.toastTimer=setTimeout(()=>x.classList.remove('visible'),2900);}
  function transform(text, style) {
    if(!style) return text;
    if(style.type==='border')return style.before+text+style.after;
    if(style.type==='spaced')return [...text].map(c=>c===' '?'   ':c).join(' ');
    if(style.type==='alternating'){let n=0;return [...text].map(c=>/[a-z]/i.test(c)?((n++%2)?c.toLowerCase():c.toUpperCase()):c).join('');}
    if(style.type==='strike'||style.type==='underline')return [...text].map(c=>c===' '?c:c+(style.type==='strike'?'\u0336':'\u0332')).join('');
    if(style.type==='smallcaps')return [...text].map(c=>smallCapMap[c.toLowerCase()]||c).join('');
    if(style.type==='sup')return [...text].map(c=>superscriptMap[c.toLowerCase()]||c).join('');
    return [...text].map(c=>{
      const code=c.codePointAt(0),up=code>=65&&code<=90,lo=code>=97&&code<=122,digit=code>=48&&code<=57;
      if(style.type==='fullwidth')return up||lo||digit?String.fromCodePoint(code+0xfee0):c;
      if(style.type==='circled'){if(up)return String.fromCodePoint(0x24b6+code-65);if(lo)return String.fromCodePoint(0x24d0+code-97);return c;}
      if(style.type==='squared')return up||lo?String.fromCodePoint(0x1f130+(up?code-65:code-97)):c;
      if(style.type==='math'){
        const exception=style.exception?.[c];if(exception)return String.fromCodePoint(exception);
        if(up&&style.upper)return String.fromCodePoint(style.upper+code-65);
        if(lo&&style.lower)return String.fromCodePoint(style.lower+code-97);
        if(digit&&style.digit)return String.fromCodePoint(style.digit+code-48);
      }
      return c;
    }).join('');
  }
  function setView(view){
    state.view=view;$$('.view').forEach(x=>x.classList.toggle('active',x.id==='view-'+view));
    $$('.side-tab').forEach(x=>x.classList.toggle('current',x.dataset.view===view));
    $$('.mobile-tabs button').forEach(x=>x.classList.toggle('active',x.dataset.view===view));
    $('#page-title').textContent=({keyboard:'Keyboard laboratory',appearance:'Appearance studio',voice:'Voice & AI',about:'Beta readiness'})[view];
    window.scrollTo({top:0,behavior:'instant'});
  }
  function applyTheme(theme){
    state.theme=theme;
    device.classList.remove('theme-light','theme-ink','theme-plum');
    if(theme!=='default')device.classList.add('theme-'+theme);
    $$('.theme-card,.swatch').forEach(x=>{x.classList.toggle('selected',x.dataset.theme===theme);x.setAttribute('aria-pressed',String(x.dataset.theme===theme));});
  }
  function buildThemes(){
    $('#themeOptions').innerHTML=themeData.map(x=>'<button type="button" class="theme-card'+(state.theme===x.id?' selected':'')+'" data-theme="'+x.id+'"><span class="theme-mini" style="background:'+x.keys[0]+'"><i style="background:'+x.keys[1]+'"></i><i style="background:'+x.keys[1]+'"></i><i style="background:'+x.keys[1]+'"></i></span><span>'+esc(x.name)+'</span><small>'+esc(x.note)+'</small></button>').join('');
    $('#inspectorThemes').innerHTML=themeData.map(x=>'<button type="button" class="swatch'+(state.theme===x.id?' selected':'')+'" data-theme="'+x.id+'" title="'+esc(x.name)+'" aria-label="'+esc(x.name)+'" aria-pressed="'+(state.theme===x.id)+'"></button>').join('');
  }
  function setup(){
    buildThemes();renderProviders();renderCleanupSettings();
    $$('.side-tab,.mobile-tabs button').forEach(x=>x.addEventListener('click',()=>setView(x.dataset.view)));
    $$('[data-jump]').forEach(x=>x.addEventListener('click',()=>setView(x.dataset.jump)));
    $$('[data-viewport]').forEach(x=>x.addEventListener('click',()=>{
      state.viewport=x.dataset.viewport;device.className=device.className.replace(/\b(portrait|compact|landscape)\b/g,'').trim()+' '+state.viewport;
      $$('[data-viewport]').forEach(v=>v.classList.toggle('selected',v===x));fitLandscapeDevice();renderKeyboard();
    }));
    document.addEventListener('click',e=>{const t=e.target.closest('[data-theme]');if(t)applyTheme(t.dataset.theme);});
    $('.tool').forEach(x=>x.addEventListener('click',()=>setTool(x.dataset.tool)));
    $('#toggleNativeKeyboard').addEventListener('click',()=>{
      const enabled=draft.getAttribute('inputmode')==='text';
      draft.setAttribute('inputmode',enabled?'none':'text');
      $('#toggleNativeKeyboard').setAttribute('aria-pressed',String(!enabled));
      toast(enabled?'Phone soft keyboard disabled.':'Phone soft keyboard enabled explicitly.');
      if(!enabled)draft.focus();
    });
    window.addEventListener('resize',fitLandscapeDevice);
    const bind=(sel,fn)=>{$(sel).addEventListener('change',fn);};
    bind('#numberRow',e=>{state.numberRow=e.target.checked;renderKeyboard();});
    bind('#roundKeys',e=>{state.roundKeys=e.target.checked;renderKeyboard();});
    bind('#oneHanded',e=>{state.oneHanded=e.target.checked;renderKeyboard();});
    const updateLayout=(value)=>{state.layout=value;$('#layoutChoice').value=value;$('#quickLayout').value=value;renderKeyboard();};
    bind('#layoutChoice',e=>updateLayout(e.target.value));bind('#quickLayout',e=>updateLayout(e.target.value));
    bind('#accentColor',e=>document.documentElement.style.setProperty('--accent',e.target.value));
    $('#fontSize').addEventListener('input',e=>{state.keyScale=Number(e.target.value);$('#fontSizeOut').textContent=state.keyScale+'%';renderKeyboard();});
    $('#clearComposer').addEventListener('click',()=>{draft.value='';draft.focus();renderKeyboard();toast('Draft cleared.');});
    $('#sendDemo').addEventListener('click',sendMessage);
    draft.addEventListener('input',()=>{if(state.tool==='type')renderKeyboard();});
    $('#demoClean').addEventListener('click',()=>{const result=cleanupText($('#voiceSample').value,state.cleanup);$('#voiceResult').textContent=result;});
    $('#installBtn').addEventListener('click',async()=>{if(!state.deferredInstall)return;state.deferredInstall.prompt();await state.deferredInstall.userChoice;state.deferredInstall=null;$('#installBtn').hidden=true;});
    window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();state.deferredInstall=e;$('#installBtn').hidden=false;});
    if('serviceWorker' in navigator && location.protocol==='https:')window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js').catch(()=>{}));
    applyTheme('default');fitLandscapeDevice();renderKeyboard();
  }
  function fitLandscapeDevice(){
    if(state.viewport==='landscape'){
      const available=$('.stage').clientWidth-20;
      device.style.zoom=String(Math.min(1,Math.max(.25,available/740)));
    }else{
      device.style.zoom='';
    }
  }
  function setTool(tool){state.tool=tool;$$('.tool').forEach(b=>b.classList.toggle('active',b.dataset.tool===tool));renderKeyboard();}
  function insert(text){
    const a=draft.selectionStart??draft.value.length,b=draft.selectionEnd??a;
    draft.setRangeText(text,a,b,'end');draft.focus();draft.dispatchEvent(new Event('input',{bubbles:true}));
  }
  function deleteBack(){
    const a=draft.selectionStart??draft.value.length,b=draft.selectionEnd??a;
    if(a!==b){draft.setRangeText('',a,b,'end');}else if(a>0){
      const before=draft.value.slice(0,a);const graphemes=typeof Intl.Segmenter==='function'?[...new Intl.Segmenter(undefined,{granularity:'grapheme'}).segment(before)].map(x=>x.segment):[...before];
      const previous=graphemes.at(-1)||'';draft.setRangeText('',a-previous.length,a,'end');
    }
    draft.focus();draft.dispatchEvent(new Event('input',{bubbles:true}));
  }
  function sendMessage(){const text=draft.value.trim();if(!text){toast('Type a sample message first.');return;}const el=document.createElement('div');el.className='bubble outgoing';el.textContent=text;$('#messages').append(el);$('#messages').scrollTop=$('#messages').scrollHeight;draft.value='';draft.dispatchEvent(new Event('input',{bubbles:true}));toast('Sent in the mock conversation only.');}
  function makeKey(label, code, special=false,wide=false){
    const b=document.createElement('button');b.type='button';b.className='key'+(special?' key-special':'')+(wide?' key-wide':'')+(code==='space'?' key-space':'')+(code==='enter'?' key-enter':'');b.textContent=label;b.setAttribute('aria-label',label);b.dataset.key=code;
    const hint=accentChars[code.toLowerCase()]||(code==='a'?'Ctrl+A':code==='c'?'Ctrl+C':code==='v'?'Ctrl+V':code==='x'?'Ctrl+X':'');
    if(hint&&code.length===1){const h=document.createElement('span');h.className='hint';h.textContent=hint;b.append(h);}
    let timer=null,longPressed=false;
    b.addEventListener('pointerdown',()=>{longPressed=false;if(code.length===1&&/[a-z]/i.test(code))timer=setTimeout(()=>{longPressed=true;insert(accentChars[code]||code);},540);});
    ['pointerup','pointercancel','pointerleave'].forEach(ev=>b.addEventListener(ev,()=>{clearTimeout(timer);}));
    b.addEventListener('click',()=>{if(longPressed){longPressed=false;return;}handleKey(code);});return b;
  }
  function handleKey(code){
    if(code==='shift'){state.shift=!state.shift;renderKeyboard();return;}
    if(code==='back'){deleteBack();return;}
    if(code==='space'){insert(' ');return;}
    if(code==='enter'){insert('\n');return;}
    if(code==='symbol'){state.symbols=!state.symbols;state.shift=false;renderKeyboard();return;}
    if(code==='lang'){toast('English (US) · language switching requires Android.');return;}
    if(code==='arrowleft'){draft.selectionStart=draft.selectionEnd=Math.max(0,draft.selectionStart-1);draft.focus();return;}
    if(code==='arrowright'){draft.selectionStart=draft.selectionEnd=Math.min(draft.value.length,draft.selectionStart+1);draft.focus();return;}
    insert(state.shift?code.toUpperCase():code);
    if(state.shift){state.shift=false;renderKeyboard();}
  }
  function renderKeyboard(){
    device.classList.toggle('one-handed',state.oneHanded);
    device.style.setProperty('--key-radius',state.roundKeys?'7px':'2px');
    device.style.setProperty('--key-size',(19*state.keyScale/100)+'px');
    if(state.tool==='symbols')return renderSymbols();
    if(state.tool==='stickers')return renderStickers();
    if(state.tool==='voice')return renderVoiceTool();
    if(state.tool==='styles')return renderStyleTool();
    if(state.tool==='prescreen')return renderPrescreenTool();
    board.innerHTML='';
    const prediction=document.createElement('div');prediction.className='prediction';
    const words=draft.value.trim().split(/\s+/);const last=words.at(-1)||'';
    [last?'“'+last+'”':'Tsuzuri',last?last.toLowerCase():'English',last?last.charAt(0).toUpperCase()+last.slice(1):'Writing tools'].forEach((word,i)=>{const x=document.createElement('span');x.textContent=word;if(i===1)x.className='suggest-active';prediction.append(x);});
    board.append(prediction);
    const deck=document.createElement('div');deck.className='key-deck';
    const row=(keys)=>{const r=document.createElement('div');r.className='key-row';keys.forEach(k=>{if(typeof k==='string'){r.append(makeKey(state.shift?k.toUpperCase():k,k));}else r.append(makeKey(k[0],k[1],!!k[2],!!k[3]));});deck.append(r);};
    if(state.numberRow)row([...'1234567890']);
    if(state.symbols){
      row([...'1234567890']);row([...'@#$%&*-+()']);row([['ABC','symbol',true,true],...'!?;:/\'"', ['⌫','back',true,true]]);
    }else{
      const layout=layouts[state.layout]||layouts.qwerty;
      row([...layout[0]]);row([...layout[1]]);
      row([[state.shift?'⇧':'↑','shift',true,true],...layout[2],['⌫','back',true,true]]);
    }
    row([[state.symbols?'ABC':'?123','symbol',true,true],[',',','],['🌐','lang',true],['space','space',false,true],['.','.'],['↵','enter',true,true]]);
    board.append(deck);
  }
  function renderSymbols(){board.innerHTML='<div class="tool-pane"><div class="tool-pane-head"><strong>Symbols &amp; text art</strong><small>Tap to insert</small></div><div id="symbolsPanel" class="sticker-list"></div><p class="tool-feedback">Native symbols and emoji are supported in Android; this preview uses plain characters.</p></div>';const codes=['★','☆','♥','♡','☯','☀','☾','∞','→','←','↑','↓','✓','✗','△','◇','✺','✿','♫','⚡','♡','(•ᴗ•)','(╥﹏╥)','¯\\_(ツ)_/¯'];codes.forEach(code=>{let b=document.createElement('button');b.type='button';b.className='sticker-item';b.textContent=code;b.style.fontSize=code.length>2?'13px':'22px';b.addEventListener('click',()=>insert(code));$('#symbolsPanel').append(b);});}
  function renderStickers(){board.innerHTML='<div class="tool-pane"><div class="tool-pane-head"><strong>Sticker gallery</strong><small>Illustrative emoji placeholders</small></div><div class="pane-tabs" id="stickerPacks"></div><div class="sticker-list" id="stickerItems"></div><div class="tool-feedback">Real Android import and image sending require a device; these samples insert Unicode emoji only.</div></div>';Object.keys(stickerPacks).forEach(name=>{let b=document.createElement('button');b.textContent=name;b.className=state.stickerPack===name?'selected':'';b.addEventListener('click',()=>{state.stickerPack=name;renderStickers();});$('#stickerPacks').append(b);});stickerPacks[state.stickerPack].forEach(e=>{let b=document.createElement('button');b.type='button';b.className='sticker-item';b.textContent=e;b.title='Insert sample symbol';b.addEventListener('click',()=>{insert(e);toast('Sample inserted as text; not an Android image sticker.');});$('#stickerItems').append(b);});}
  function cleanupText(text,mode){
    if(mode==='off')return text;
    let x=text.replace(/\b(um+|uh+|erm+|like,? you know)\b[,.\s]*/gi,'').replace(/\b(and then,?\s+){2,}/gi,'and then ').replace(/\s{2,}/g,' ').trim().replace(/^([a-z])/,(_,a)=>a.toUpperCase());
    if(mode==='concise')x=x.replace(/^(Ja,?\s*)/i,'').replace(/and,?\s+then\s+/gi,'then ').replace(/\bI need to go to the mall tomorrow and,?\s+pay my account\b/i,'Tomorrow, I need to go to the mall to pay my account').replace(/\s+/g,' ');
    return x;
  }
  function renderVoiceTool(){
    board.innerHTML='<div class="tool-pane"><div class="tool-pane-head"><strong>Voice / transcript flow</strong><small>Recording disabled in preview</small></div><div class="voice-demo-block"><strong>● Voice transcript simulator</strong><p>“Um, I need to go to the mall and then pay my account, and I’ll probably catch a taxi home.”</p><div class="pane-tabs" id="voiceToolModes"></div><button type="button" id="insertVoiceSample">Insert example transcript</button></div><div class="tool-feedback">Groq Whisper and OpenAI transcription work only in Android builds after device testing. No microphone or AI requests happen here.</div></div>';
    renderModeButtons($('#voiceToolModes'));$('#insertVoiceSample').addEventListener('click',()=>{insert(cleanupText("Um, I need to go to the mall and then pay my account, and I'll probably catch a taxi home.",state.cleanup));toast('Inserted demo text; no audio was recorded.');});
  }
  function renderModeButtons(root){root.innerHTML='';for(const [id,label]of [['off','Verbatim'],['natural','Natural'],['concise','Concise']]){const b=document.createElement('button');b.textContent=label;b.className=state.cleanup===id?'selected':'';b.addEventListener('click',()=>{state.cleanup=id;renderCleanupSettings();if(state.tool==='voice')renderVoiceTool();});root.append(b);}}
  function renderCleanupSettings(){const root=$('#cleanupModes');root.innerHTML='';for(const [id,label]of [['off','Verbatim'],['natural','Natural cleanup'],['concise','Concise']]){const b=document.createElement('button');b.type='button';b.className='cleanup-mode'+(id===state.cleanup?' selected':'');b.textContent=label;b.setAttribute('aria-pressed',String(id===state.cleanup));b.addEventListener('click',()=>{state.cleanup=id;renderCleanupSettings();});root.append(b);}}
  function renderStyleTool(){
    const previous=$('#styleSource')?.value||draft.value||'The quick brown fox';
    board.innerHTML='<div class="tool-pane"><div class="tool-pane-head"><strong>Unicode typography</strong><small>Experimental web-only feature</small></div><textarea class="style-input" id="styleSource" aria-label="Text to transform"></textarea><div class="style-grid" id="styleChoices"></div><p class="tool-feedback">Preview-only: these Unicode mappings are not implemented in the Android keyboard yet. Some glyphs may not display in every app.</p></div>';
    $('#styleSource').value=previous;
    const fill=()=>{const source=$('#styleSource').value;$('#styleChoices').innerHTML='';styleSets.forEach(style=>{let b=document.createElement('button');b.type='button';b.className='style-choice';const n=document.createElement('small');n.textContent=style.name;const value=document.createElement('span');value.textContent=transform(source.slice(0,22),style);b.append(n,value);b.addEventListener('click',()=>{insert(transform(source,style));toast('Applied Unicode style to mock draft.');});$('#styleChoices').append(b);});};
    $('#styleSource').addEventListener('input',fill);fill();
  }
  function renderPrescreenTool(){
    const source=draft.value||'I need to go to the mall tomorrow, pay my account and buy groceries.';
    board.innerHTML='<div class="tool-pane"><div class="tool-pane-head"><strong>Pre-screen editing</strong><small>Local draft, no AI</small></div><textarea class="prescreen-editor" id="prescreenSource" aria-label="Pre-screen draft"></textarea><div class="prescreen-ops"><button data-format="bold">Bold</button><button data-format="italic">Italic</button><button data-format="strike">Strike</button><button data-format="heading">Heading</button><button data-format="cleanup">Clean text</button><button class="apply" id="approveDraft">Approve → composer</button></div><div class="tool-feedback">Formatting is a local text demonstration. Native Pre-screen AI proposals, approvals and transfer require Android testing.</div></div>';
    const editor=$('#prescreenSource');editor.value=source;
    $$('[data-format]',board).forEach(b=>b.addEventListener('click',()=>{
      const startSelection=editor.selectionStart,endSelection=editor.selectionEnd;
      const start=startSelection===endSelection?0:startSelection;
      const end=startSelection===endSelection?editor.value.length:endSelection;
      const selected=editor.value.slice(start,end);let output=selected;
      if(b.dataset.format==='bold')output='**'+selected+'**';
      if(b.dataset.format==='italic')output='*'+selected+'*';
      if(b.dataset.format==='strike')output='~~'+selected+'~~';
      if(b.dataset.format==='heading')output='# '+selected;
      if(b.dataset.format==='cleanup'){editor.value=cleanupText(editor.value,'natural');return;}
      editor.setRangeText(output,start,end,'select');editor.focus();
    }));
    $('#approveDraft').addEventListener('click',()=>{draft.value=editor.value;draft.dispatchEvent(new Event('input',{bubbles:true}));setTool('type');toast('Approved draft placed in mock composer.');});
  }
  function renderProviders(){
    const providers=[
      {name:'Groq Whisper Large V3 Turbo',detail:'Rate-limited free tier · no card required · recorded audio',symbol:'G',url:'https://console.groq.com/keys'},
      {name:'OpenAI GPT-Transcribe',detail:'Completed dictation · own paid API credits',symbol:'O',url:'https://platform.openai.com/api-keys'},
      {name:'Mistral Voxtral Mini 3',detail:'Completed dictation · Mistral API key',symbol:'M',url:'https://console.mistral.ai/'},
      {name:'Android voice input',detail:'Installed speech service · no separate cloud API key',symbol:'A',url:'https://developer.android.com/reference/android/speech/SpeechRecognizer'}
    ];
    $('#providerList').innerHTML=providers.map(x=>'<div class="provider"><span class="provider-glyph">'+x.symbol+'</span><div class="provider-meta"><strong>'+esc(x.name)+'</strong><small>'+esc(x.detail)+'</small></div><a href="'+esc(x.url)+'" target="_blank" rel="noopener noreferrer">Setup ↗</a></div>').join('');
  }
  setup();
})();
