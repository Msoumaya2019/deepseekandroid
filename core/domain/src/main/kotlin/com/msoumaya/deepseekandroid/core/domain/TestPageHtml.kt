package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.TestLine
import com.msoumaya.deepseekandroid.core.model.TestLineType
import com.msoumaya.deepseekandroid.core.model.TestPage
import kotlinx.serialization.json.jsonPrimitive

/**
 * Le document d'une page de la composition « Coran avec règles de Tajwid ».
 *
 * Porté depuis `src/coranTest/html.ts`. L'original ne peint pas 604 images : il **compose** une
 * page avec les vrais glyphes de sa police, puis mesure leurs rectangles dans le moteur de
 * rendu. Ce fichier produit le document ; la WebView l'affiche, et le script qu'il contient
 * renvoie les mesures à l'application.
 *
 * ## Ce que le document porte, et pourquoi il le porte
 *
 * - les **glyphes** de chaque mot, dans la police de la page — jamais le texte arabe, qui sert
 *   seulement de libellé d'accessibilité ;
 * - le **canevas logique** de 1000 × 2120, qui n'est pas une dimension extraite d'une image mais
 *   le ratio de présentation ; les glyphes ne sont ni étirés ni coupés ;
 * - un **script de mesure** : après le chargement des polices, il relève la boîte de chaque mot,
 *   la normalise par rapport au canevas, fusionne les mots d'un même verset **sur une même
 *   ligne**, et renvoie le tout à l'application. Aucun rectangle n'est estimé à l'œil.
 *
 * ## Ce qui n'est pas encore porté, et qui est dit
 *
 * L'original ajoute deux choses que ce document ne porte pas encore : le **bandeau de séance**
 * et la **règle de marge** du programme. Les deux dépendent d'une séance en cours, qui relève
 * de l'apprentissage — une phase ultérieure. Ils sont donc absents, et non approximés : un
 * bandeau vide ou une règle fausse seraient pires qu'une absence annoncée.
 *
 * ## La seule déviation, et elle est nommée
 *
 * L'original envoie ses messages par `window.ReactNativeWebView.postMessage`, le pont de React
 * Native. Ici, le script essaie d'abord `window.CoranTest.postMessage`, l'interface exposée par
 * la WebView Android, puis retombe sur le pont d'origine, puis sur `window.parent`. Le
 * document reste donc affichable dans un navigateur d'aperçu, comme celui de l'original.
 */
object TestPageHtml {

    /** Les trois polices, en URI `data:` prêtes à être insérées dans la feuille de style. */
    data class Fonts(val page: String, val title: String, val basmala: String)

    /**
     * Les glyphes de la basmala, **par sourate**.
     *
     * Trois dessins différents, et non un seul : la basmala d'Al-Baqara et celle des sourates 95
     * et 97 portent une graphie propre. Les points de code sont écrits en échappement Unicode
     * plutôt qu'en clair : ce sont des formes de présentation arabe, et une copie d'écran les
     * aurait rendus invisibles à la relecture.
     */
    private const val BASMALA_BAQARA = "\uFC9A\uFC9B\uFC9E\uFCA4"
    private const val BASMALA_95_97 = "\uFB57\uFCAB\uFCAE\uFCB4"
    private const val BASMALA_DEFAULT = "\uFCAA\uFCAB\uFCAE\uFCB4"

    private val ornament: String by lazy { loadOrnament() }

    /** Le document complet d'une page. */
    fun document(page: TestPage, fonts: Fonts): String {
        val lines = page.lines.mapIndexed { index, line -> renderLine(page, index, line) }.joinToString("")
        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
            append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; ")
            append("font-src data: http://127.0.0.1:*; style-src 'unsafe-inline'; script-src 'unsafe-inline';\">")
            append("<style>").append(styles(page, fonts)).append("</style>")
            append("</head><body>")
            append("<main id=\"paper\" aria-label=\"Coran avec règles de Tajwid, page ").append(page.page).append("\">")
            append("<div class=\"top\"><span>Juz ").append(page.juz).append("</span>")
            append("<span class=\"surah\">").append(surahLabel(page.surah)).append("</span></div>")
            append("<div class=\"body\">").append(lines).append("</div>")
            append("<footer>").append(arabicDigits(page.page)).append("</footer>")
            append("<div id=\"verse-overlay\" aria-hidden=\"true\"></div>")
            append("</main>")
            append("<script>").append(script(page.page)).append("</script>")
            append("</body></html>")
        }
    }

    // -----------------------------------------------------------------------
    // Le balisage d'une ligne
    // -----------------------------------------------------------------------

    private fun renderLine(page: TestPage, index: Int, line: TestLine): String {
        // La basmala prend la graphie de la **sourate qui suit** : c'est la première ligne
        // suivante qui porte des mots. Sans ligne suivante, c'est la sourate de la page.
        val basmalaSurah = page.lines.drop(index + 1)
            .firstOrNull { it.words.isNotEmpty() }
            ?.words?.first()?.surah
            ?: page.surah

        val body = when (line.type) {
            TestLineType.AYAH -> line.words.joinToString("") { word ->
                "<span class=\"word\" data-id=\"${word.id}\" data-verse=\"${word.verseKey}\"" +
                    " aria-label=\"${escape(word.arabic)}\">${escape(word.glyphs)}</span>"
            }

            TestLineType.SURAH_NAME ->
                "<div class=\"surah-heading\">$ornament<span class=\"surah\">" +
                    "${surahLabel(line.surah ?: page.surah)}</span></div>"

            TestLineType.BASMALLAH ->
                "<span class=\"basmala\">${basmalaGlyphs(basmalaSurah)}</span>"
        }

        val centered = if (line.centered) "centered" else ""
        return "<div class=\"line $centered\" data-line=\"${line.line}\">$body</div>"
    }

    private fun basmalaGlyphs(surah: Int): String = when (surah) {
        2 -> BASMALA_BAQARA
        95, 97 -> BASMALA_95_97
        else -> BASMALA_DEFAULT
    }

    private fun surahLabel(surah: Int): String = "surah" + surah.toString().padStart(3, '0')

    /** Le numéro de page en chiffres arabes-indiens, comme sur la page imprimée. */
    private fun arabicDigits(value: Int): String =
        value.toString().map { ('\u0660' + (it - '0')) }.joinToString("")

    private fun escape(value: String): String = buildString(value.length) {
        for (character in value) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

    // -----------------------------------------------------------------------
    // La feuille de style
    // -----------------------------------------------------------------------

    private fun styles(page: TestPage, fonts: Fonts): String {
        // 602 des 604 pages portent une taille fractionnaire ; deux tombent juste. Le CSS de
        // l'original écrit « 70px » et non « 70.0px », et l'écrire autrement changerait le
        // document produit pour une valeur qui, elle, ne change pas.
        val size = if (page.fontSize == page.fontSize.toLong().toDouble()) {
            page.fontSize.toLong().toString()
        } else {
            page.fontSize.toString()
        }
        return """
        @font-face{font-family:Page;src:url('${fonts.page}') format('woff2')}
        @font-face{font-family:Title;src:url('${fonts.title}') format('woff2')}
        @font-face{font-family:Basmala;src:url('${fonts.basmala}') format('woff2')}
        *{box-sizing:border-box}
        html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#faf7f2;touch-action:none;user-select:none;-webkit-user-select:none}
        #paper{width:${TestPageIndex.PAGE_WIDTH}px;height:${TestPageIndex.PAGE_HEIGHT}px;position:absolute;visibility:hidden;transform-origin:top left;padding:70px 30px 55px;color:#111;background:#faf7f2}
        .top{height:105px;display:flex;align-items:center;justify-content:space-between;font:30px sans-serif;color:#795e50}
        .top .surah{font:68px Title}
        .body{height:1830px;display:flex;flex-direction:column;justify-content:center}
        .line{height:122px;flex-shrink:0;display:flex;direction:rtl;align-items:center;justify-content:space-between;font:${size}px Page;white-space:nowrap;line-height:1}
        .line.centered{justify-content:center;gap:8px}
        .word{display:inline-block;direction:rtl}
        .surah-heading{position:relative;width:100%;height:100px;display:flex;justify-content:center;align-items:center}
        .surah-heading svg{position:absolute;width:100%;height:100%;inset:0}
        .line .surah{position:relative;font:90px Title}
        .basmala{font:80px Basmala}
        footer{text-align:center;font:30px sans-serif;margin-top:20px}
        #verse-overlay{position:absolute;inset:0;pointer-events:none;visibility:hidden}
    """.trimIndent()
    }

    // -----------------------------------------------------------------------
    // Le script
    // -----------------------------------------------------------------------

    private fun script(pageNumber: Int): String = """
        const paper=document.getElementById('paper'),overlay=document.getElementById('verse-overlay');
        let scale=1,zoom=1,panX=0,panY=0,pinch=null,lastTap=null,tapTimer=null;
        let readerState={enabled:false},regions={},hold=null,gesture=null;
        const send=data=>{const s=JSON.stringify(data);if(window.CoranTest&&window.CoranTest.postMessage)window.CoranTest.postMessage(s);else if(window.ReactNativeWebView)window.ReactNativeWebView.postMessage(s);else window.parent.postMessage(data,'*')};
        function paintZoom(){const w=${TestPageIndex.PAGE_WIDTH}*scale*zoom,h=${TestPageIndex.PAGE_HEIGHT}*scale*zoom;panX=w<=innerWidth?(innerWidth-w)/2:Math.max(innerWidth-w,Math.min(0,panX));panY=h<=innerHeight?0:Math.max(innerHeight-h,Math.min(0,panY));paper.style.transform='scale('+(scale*zoom)+')';paper.style.left=panX+'px';paper.style.top=panY+'px';}
        function fit(){scale=Math.min(innerWidth/${TestPageIndex.PAGE_WIDTH},innerHeight/${TestPageIndex.PAGE_HEIGHT});paintZoom();drawOverlay();}
        function zoomAt(value,x,y){const next=Math.max(1,Math.min(3,value)),ratio=next/zoom;panX=x-(x-panX)*ratio;panY=y-(y-panY)*ratio;zoom=next;paintZoom();}
        window.resetReaderZoom=()=>{zoom=1;fit();};
        // Les reperes de marge. La regle est celle de `MarginAnnotations`, en Kotlin, et elle est
        // ecrite ici une seconde fois parce que le document est une ile : il est le seul a
        // connaitre ses propres rectangles, et rien ne les lui donne. Les deux implementations
        // doivent donc bouger ensemble -- `ReaderTest` est la specification de celle-ci.
        function marginAnchors(regions,start,end,through){const first=new Map();for(const region of regions){if(region.id<start||region.id>end)continue;const old=first.get(region.id);if(!old||region.line<old.line||(region.line===old.line&&region.y<old.y))first.set(region.id,region);}const groups=new Map();for(const region of first.values()){const group=groups.get(region.line)||[];group.push(region);groups.set(region.line,group);}return [...groups.values()].sort((a,b)=>a[0].y-b[0].y).map(group=>{group.sort((a,b)=>a.id-b.id);return {y:Math.min(...group.map(r=>r.y)),height:Math.max(...group.map(r=>r.height)),items:group.map(r=>({id:r.id,ayah:r.ayah,done:r.id<=through})),bottom:Math.max(...regions.filter(r=>group.some(g=>g.id===r.id)).map(r=>r.y+r.height))};});}
        function drawMargin(){const session=readerState.session||[];if(!readerState.enabled||!session.length)return;const ids=new Map(session.map((key,index)=>[key,index+1]));const entries=Object.entries(regions).flatMap(([key,zones])=>zones.map(zone=>({...zone,id:ids.get(key)||0,ayah:Number(key.split(':')[1])})));const groups=marginAnchors(entries,1,session.length,readerState.sessionDone||0);if(!groups.length)return;const all=entries.filter(entry=>entry.width>0);const minX=Math.min(...all.map(entry=>entry.x))*${TestPageIndex.PAGE_WIDTH};const rect=paper.getBoundingClientRect(),available=rect.left+minX*scale*zoom;const px=Math.min(24,Math.max(12,available-5)),size=px/scale,left=minX-size-4/scale,color=readerState.sessionColor||readerState.primary;const rail=document.createElement('div');Object.assign(rail.style,{position:'absolute',left:(left+size/2)+'px',top:groups[0].y*${TestPageIndex.PAGE_HEIGHT}+'px',width:(1/scale)+'px',height:(groups[groups.length-1].bottom-groups[0].y)*${TestPageIndex.PAGE_HEIGHT}+'px',backgroundColor:color,opacity:'0.35'});overlay.appendChild(rail);for(const group of groups){const mark=document.createElement('div');mark.textContent=group.items.map(item=>item.ayah).join('·');const done=group.items.every(item=>item.done);Object.assign(mark.style,{position:'absolute',left:left+'px',top:(group.y+group.height*0.35)*${TestPageIndex.PAGE_HEIGHT}-size/2+'px',width:size+'px',minHeight:size+'px',padding:'2px',display:'flex',alignItems:'center',justifyContent:'center',borderRadius:size+'px',border:(1/scale)+'px solid '+color,backgroundColor:done?color:(readerState.background||'#faf7f2'),color:done?'white':color,font:(Math.max(8,group.items.length>1?9:11)/scale)+'px sans-serif',textAlign:'center',overflowWrap:'anywhere'});overlay.appendChild(mark);}}
        function drawOverlay(){overlay.replaceChildren();overlay.style.visibility=readerState.enabled?'visible':'hidden';if(!readerState.enabled)return;for(const [key,zones] of Object.entries(regions)){const active=key===readerState.playing,bookmarked=readerState.bookmarks.includes(key),difficult=readerState.difficulty.includes(key),selected=key===readerState.selected;if(!active&&!bookmarked&&!difficult&&!selected)continue;for(const zone of zones){const mark=document.createElement('div');Object.assign(mark.style,{position:'absolute',left:zone.x*100+'%',top:zone.y*100+'%',width:zone.width*100+'%',height:zone.height*100+'%',borderRadius:'7px',backgroundColor:difficult?'#E85B5B':active||selected?readerState.selection:bookmarked?readerState.primary:readerState.gold,opacity:difficult?'0.20':active||selected?'0.38':'0.16',border:'none'});overlay.appendChild(mark);}if(bookmarked&&zones[0]){const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('viewBox','0 0 24 24');Object.assign(icon.style,{position:'absolute',right:'0',top:zones[0].y*100+'%',width:'23px',height:'23px',color:readerState.primary});const path=document.createElementNS('http://www.w3.org/2000/svg','path');path.setAttribute('d','M6 3h12v18l-6-4-6 4z');path.setAttribute('fill','currentColor');icon.appendChild(path);overlay.appendChild(icon);}}drawMargin();}
        window.applyReaderState=state=>{readerState=state;const background=/^#[0-9a-f]{6}${'$'}/i.test(state.background)?state.background:'#faf7f2';paper.style.backgroundColor=background;document.body.style.backgroundColor=background;document.documentElement.style.backgroundColor=background;drawOverlay();};
        addEventListener('message',event=>{if(event.source===window.parent&&event.data?.type==='reader-state')window.applyReaderState(event.data.state);});
        function ready(){fit();paper.style.visibility='visible';const p=paper.getBoundingClientRect();const words=[...document.querySelectorAll('.word')].map(w=>{const b=w.getBoundingClientRect();return {id:Number(w.dataset.id),key:w.dataset.verse,region:{x:(b.left-p.left)/p.width,y:(b.top-p.top)/p.height,width:b.width/p.width,height:b.height/p.height,line:Number(w.parentElement.dataset.line)}}});regions={};for(const word of words){const zones=regions[word.key]||(regions[word.key]=[]),r=word.region,same=zones.find(z=>z.line===r.line);if(same){const right=Math.max(same.x+same.width,r.x+r.width),bottom=Math.max(same.y+same.height,r.y+r.height);same.x=Math.min(same.x,r.x);same.y=Math.min(same.y,r.y);same.width=right-same.x;same.height=bottom-same.y;}else zones.push({...r});}drawOverlay();send({type:'ready',page:$pageNumber,words});}
        addEventListener('resize',ready);
        document.fonts.ready.then(ready).catch(()=>send({type:'error',page:$pageNumber}));
        function hit(x,y){const p=paper.getBoundingClientRect(),nx=(x-p.left)/p.width,ny=(y-p.top)/p.height;for(const [key,zones] of Object.entries(regions))if(zones.some(r=>nx>=r.x&&nx<=r.x+r.width&&ny>=r.y&&ny<=r.y+r.height))return key;return null;}
        function cancelHold(){if(hold!==null)clearTimeout(hold);hold=null;}
        function begin(x,y){cancelHold();gesture={x,y,key:hit(x,y),held:false,moved:false,zoomed:zoom>1.01,px:panX,py:panY};if(readerState.enabled)hold=setTimeout(()=>{if(gesture&&!gesture.moved){gesture.held=true;send({type:'longpress',key:gesture.key});}},450);}
        function move(x,y){if(gesture&&Math.hypot(x-gesture.x,y-gesture.y)>10){gesture.moved=true;cancelHold();}if(gesture&&gesture.zoomed){panX=gesture.px+x-gesture.x;panY=gesture.py+y-gesture.y;paintZoom();}}
        function end(x,y){cancelHold();if(!gesture)return;const g=gesture,dx=x-g.x,dy=y-g.y;gesture=null;if(!g.held&&!g.moved){const now=Date.now();if(lastTap&&now-lastTap.time<280&&Math.hypot(x-lastTap.x,y-lastTap.y)<28){if(tapTimer)clearTimeout(tapTimer);tapTimer=null;lastTap=null;zoomAt(zoom>1.01?1:2,x,y);return;}lastTap={time:now,x,y};tapTimer=setTimeout(()=>{tapTimer=null;lastTap=null;if(readerState.enabled)send({type:'tap',key:g.key});},280);}else if(!g.zoomed&&Math.abs(dx)>60&&Math.abs(dx)>Math.abs(dy)*1.5)send({type:'swipe',dx,dy,fromEdge:g.x<20});}
        function startPinch(touches){cancelHold();if(tapTimer)clearTimeout(tapTimer);tapTimer=null;lastTap=null;gesture=null;const a=touches[0],b=touches[1];pinch={distance:Math.hypot(b.clientX-a.clientX,b.clientY-a.clientY),zoom,x:panX,y:panY,cx:(a.clientX+b.clientX)/2,cy:(a.clientY+b.clientY)/2};}
        addEventListener('touchstart',e=>{if(e.touches.length>1){startPinch(e.touches);return;}const t=e.touches[0];begin(t.clientX,t.clientY);},{passive:true});
        addEventListener('touchmove',e=>{if(e.touches.length>1){if(!pinch)startPinch(e.touches);const a=e.touches[0],b=e.touches[1],next=Math.max(1,Math.min(3,pinch.zoom*Math.hypot(b.clientX-a.clientX,b.clientY-a.clientY)/Math.max(1,pinch.distance))),ratio=next/pinch.zoom;panX=(a.clientX+b.clientX)/2-(pinch.cx-pinch.x)*ratio;panY=(a.clientY+b.clientY)/2-(pinch.cy-pinch.y)*ratio;zoom=next;paintZoom();return;}if(!pinch){const t=e.touches[0];if(t)move(t.clientX,t.clientY);}},{passive:true});
        addEventListener('touchend',e=>{if(pinch){if(!e.touches.length){pinch=null;gesture=null;}return;}const t=e.changedTouches[0];end(t.clientX,t.clientY);},{passive:true});
        addEventListener('touchcancel',()=>{cancelHold();gesture=null;pinch=null;},{passive:true});
        addEventListener('pointerdown',e=>{if(e.pointerType==='mouse')begin(e.clientX,e.clientY)});
        addEventListener('pointermove',e=>{if(e.pointerType==='mouse')move(e.clientX,e.clientY)});
        addEventListener('pointerup',e=>{if(e.pointerType==='mouse')end(e.clientX,e.clientY)});
        addEventListener('contextmenu',e=>e.preventDefault());
    """.trimIndent()

    private fun loadOrnament(): String {
        val stream = TestPageHtml::class.java.classLoader
            ?.getResourceAsStream("${TestPageIndex.DIRECTORY}/ornament.json")
            ?: error("Ressource absente : ${TestPageIndex.DIRECTORY}/ornament.json")
        return AppJson.parseToJsonElement(stream.use { it.readBytes().decodeToString() }).jsonPrimitive.content
    }
}
