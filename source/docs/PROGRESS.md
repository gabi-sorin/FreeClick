# Progres 1.6 (notițe de lucru)

Snapshot 1.5: /home/claude/freeclick-1.5-snapshot (sursa exactă livrată ca 1.5).
Decizii utilizator: „Baga” → widget tăiat, „Apasă pe text” amânat; plan = docs/PLAN.md rev 2.

## Pași 1.6
- [x] A. Fișiere de aur 1.5 (JSON + trasee de rulare ale motorului 1.5) în test/golden
- [x] B. ActionTree + core migrat (gata); RĂMÂNE: app layer + regulă check.py
- [x] C. Poarta de versiune + noduri opace
- [x] D. Motor: jetoane, întrerupere, resetare lateness, deadline timer, gol static, tură min 100 ms
- [x] E. ScreenSampler pe HandlerThread, Frame imutabil cu timestamp, interval per versiune
- [x] F. Pauză automată la atingere + ecran mereu pornit
- [x] G. Anulează/Refă (core + panou + bară + editor)
- [x] H. Pauză aleatoare (WAIT.durationMax)
- [x] I. Pas „Deschide aplicația”
- [x] J. Previzualizare
- [x] K. Unelte înregistrări (taie început/final, scurtează pauze, accelerează selecția, rulează de aici)
- [x] L. Controale în notificare
- [x] M. Versiune 1.6 (code 7), README, ajutor, check.py, teste, fuzz, build, livrare

## Stare
- core: motor nou (Timer obiecte, deadline, pauseOnCancel+retry, preview, skipWait, start de la index,
  LAUNCH_APP, random WAIT, frame-fresh notBefore, tură fără gesturi >=100ms), Workspace undo/redo,
  ActionTree, codec v2 cu poartă de versiune + noduri UNKNOWN. Teste: GoldenTests (trasee 1.5 identice
  în afară de 2 schimbări voite), V16Tests (23), fuzz extins — totul verde pe 11 semințe.
- Cereri noi ale utilizatorului: mod Script C++-like (2.0, după plan), versiune Windows la final.

## 1.6 gata (versionCode 7)
Teste: 105 core + 6 app, fuzz pe 14 semințe, 147 verificări statice; build semnat cu aceeași cheie.
Următorul: 1.7 = Țintă imagine (vezi PLAN.md §3 1.7); prototip în /home/claude/spike.

## 1.7 (în lucru) — Țintă imagine
Feedback 1.6: secțiunea „Avansat” fără „față de Clickmate” (făcut); bara de notificări NU punea pauză pe
telefonul lui → detectare lărgită (orice fereastră System UI în afară de toast/volum/heads-up; ignoră
ce deschide macro-ul însuși) + jurnal „fereastră sistem: …” pentru diagnostic.
Arhitectură 1.7:
- core: ImageSpec (în Action.img), ScreenFrame (argb + niveluri gri leneșe), ImageTemplate, ImageMatcher
  (NCC grosier→fin, NMS, verificare culoare, miez 70%, multi-scală la Tolerantă, culoare-doar pt uniform,
  zonă, excludere panou, „cel mai de sus”/„toate”), contor de operații pt bugete.
- Action IMAGE: press(0 tap,1 lung,2 doar așteaptă,3 să dispară), waitMode(0 continuă,1 așteaptă), duration=timeout,
  stopOnTimeout, allMatches, maxHits, pressMs. Codec VERSION 3.
- Motor: Host.findImage(req, cb) asincron, jeton per cerere, confirmare pe 2 cadre, lateness=0, preview.
- App: ImageStore files/images/<sha1>.png + GC mark-and-sweep; captură cu dreptunghi+magnet+lupă;
  marcaj cu cruce; formular; „Testează acum”; panou compact în rulări cu imagini; backup ZIP.
- [x] core 1.7: ScreenFrame, ImageTemplate (cut/core/scaled/gradient), ImageMatcher (+ verificare pe margini:
  „Collect” vs „Collected” 0.79 vs ≥0.95; miez doar pt decupaje ne-strânse), ImageSpec, Action IMAGE, codec v3,
  motor (findImage asincron, confirmare 2 cadre, retry ≥100ms), B64, bundle cu imagini. Teste: ImageTests (11),
  V17Tests (18), fuzz cu imagini pe 11 semințe — verzi.
- [x] app 1.7: ImageStore+GC, sampler→ScreenFrame cu buffer de rezervă, findImage în serviciu (executor),
  captură (dreptunghi, magnet, lupă), marcaj imagine, formular, Testează, panou compact + stare live,
  „+ → Imagine”, 🖼 în înregistrare, editor (captură/test prin fundal), backup ZIP, export cu imagini, 1.7/code 8.

## 1.7 gata (versionCode 8). Următorul: 1.8 = blocuri Dacă/Repetă + pop-up-uri (PLAN §3 1.8).

## 1.8 (în lucru) — date: culori, citire, consolă, variabile
Cerere: pipetă + culoare exactă (hex/RGB/HSV), dreptunghi care citește număr/text, consolă, plus
idei proprii pentru „aproape orice acțiune” (tabelul din PLAN §3). Renumerotare: 1.9 logică,
2.0 comodități, 2.1 Script, apoi Windows.
- [x] core: Expr (expresii + șabloane), Numbers, pași PRINT/SET_VAR/READ (număr/text/culoare),
  Host.print/readText, confirmare pe 2 cadre, codec v4; V18Tests
- [x] core: TextReader (OCR offline: fundal = mediana marginii, Otsu, componente, linii, litere
  stivuite, despărțire/lipire, grilă 16×16 cu proporții + direcții de contur, metrici de linie în 2
  treceri, majuscule după înălțime, l/I/1 și O/0 după vecini) + FontBook (referințe din fonturile
  telefonului + învățate, JSON, îmbinare la restaurare) + aliniere la „Învață”. Benchmark AWT
  (tools/ocr): numere 90–100% pe fonturi necunoscute, Roboto ~98%; Anton 48% → 78% după 2 lecții.
- [x] app: Section (meniuri pliabile, stare ținută minte), formular pași rescris (esențial + ⚙ Avansat
  pe arii), setări mod/redare/aplicație pe arii, ColorDialog (hex/RGB/HSV/toleranță), ImageCaptureView
  MODE_ZONE (previzualizare citire) + MODE_COLOR (lupă 9×, HEX/RGB/HSV), ConsoleLayer, Letters (fonturi
  telefon + fișier învățat, în backup), service: print/readText, „+” nou, ≡ Consolă/Pipetă, test
  citire cu căsuțe + ÎNVAȚĂ, editor: pași noi + alegere pe ecran, marcaje zone 📖 / 🎨.
- [x] 1.8 / code 9
Cereri noi în timpul lui 1.8: setări în meniuri pliabile (făcut), meniu „Cod” cu tutorial, atingeri
din cod, lista de pași accesibilă și modificabilă din cod + când se execută, funcții cu tipuri (void,
bool…), vector, pair, struct, point/color/rect → PLAN §8b (1.9, mutat înaintea blocurilor vizuale).

## 1.9 — modul Cod (versionCode 10) — gata
- [x] core/script: Lexer, Parser (C++: struct, funcții cu tipuri, referințe, vector/pair, cout, evenimente,
  pastreaza, aliasuri românești), Checker (tipuri, conversii, mesaje cu „ai vrut…”), Interp + Natives,
  ScriptRunner (fir propriu, pauză / pas / opriri, evenimente, buclă încetinită, variabile păstrate),
  ScriptExamples (7 exemple + ajutor). ScriptTests 19.
- [x] app: ScriptSession (gazda pe telefon + bara de depanare), ClickService (rulare, ■ ❚❚, pași prin motor,
  alegere pe ecran pentru editor), CodeEditorActivity (linii, culori, greșeli live, „+” din ecran,
  șabloane, ● opriri, ▶ / 👣, ⋮ Exemple / Funcții / Lista de pași / Tutorial), TutorialOverlay,
  card „Cod (scripturi)” pe ecranul principal + ☰, LibraryActivity pentru scripturi.
- Teste: 174 core + 8 app, 176 verificări statice, fuzz pe 10 semințe — verzi.
- De verificat pe telefon: README 31–42.
Următorul: 2.0 = blocuri vizuale Dacă/Repetă + pop-up-uri (+ pas „Cod” în macro-uri normale).

## 1.9.1 (versionCode 11)
- Tutorial: fără „Sari peste”; la final „Închide” (revine la următoarea deschidere) / „Nu mai afișa”;
  Înapoi îl închide; nu repornește la rotire.
- Cerere: partea vizuală nu înlocuiește codul → PLAN 2.0 rescris ca „Ecran de lucru” (capturi ale
  jocului lângă cod, unelte de alegere, marcaje legate de cod, simulare pe captură). Tutorial pentru
  toată aplicația: la final, înainte de Windows.

## 2.0 — Ecran de lucru (versionCode 12) — gata
Cerințe noi la aprobare: „doar cod”, scalarea corectă (poză micșorată ↔ coordonate reale), un 👁 principal
+ ▾ cu categorii. Făcut: CodeMarks, ScreenSpace, ScriptSimulator (+4 teste), ShotStore, ShotView,
ShotTools, editorul (panou, despărțitor, capturi, instrumente, marcaje legate, simulare), ≡ → Captură
pentru cod, scalare la rulare în ScriptSession, backup / Duplică / Șterge. Tutorialul de cod are pași
noi (cheie nouă: apare o dată pentru toți). Teste: 178 core + 8 app, 186 statice — verzi.
De verificat pe telefon: README 43–54.

## 2.0.1 (versionCode 13) — feedback după testele pe telefon
- Cu tastatura deschisă codul primește tot locul (captura, ▶/👣 și panoul plutitor se ascund); fără captură, bandă subțire.
- ▶ Rulează / Rulează din listă → scriptul e pus pe panou (armScript); ▶ pe panou îl pornește în joc; rămâne pentru rulări noi;
  înapoi în editor doar la eroare. ≡ → Codul.
- tap/apasaLung/dubluTap/swipe: durată + pauză după; DURATA_CLICK / DURATA_SWIPE / INTERVAL_REPETARE declarate sus = implicite
  (scripturi noi le primesc cu valorile din Opțiuni; ⋮ le adaugă la cele vechi).
- Signatures: ce așteaptă apelul (aplicație, metode, funcțiile și struct-urile scriptului), bară deasupra tastaturii.
- Bara de alegere pe ecran: lățime fixă, × mare, dispare la revenirea în editor. Meniul ≡: 3 + Avansat.
- Simboluri rare înlocuite (verificat cu fonturile Noto: ✕ ✓ ❚ ✎ ⧉ ↺ ↶ ↷ ↳ ▁ ▢ ⠿ ☑ ＋).

## 2.1 (versionCode 14) — „continuă cu toate astea”
- Limbaj: switch/case/default; Completions (sugestii de nume) + bara de sugestii în editor.
- Funcții: asteaptaLiniste, asteaptaSchimbare (amprentă de zonă, aceeași regulă în simulare și pe telefon), model, tapInZona,
  zoomIn/zoomOut, salveazaCaptura (MediaStore), copiaza, deschideLink; unelte pe captură 🎲 / 🔢; marcaje pentru model().
- Pas „📜 Script” în macro-uri (MACRO → script: motorul așteaptă; stop/pauză propagate; FakeHost + test).
- Ținta Zoom (Action.zoom, zoomSpan în JSON, degetele urmează centrul); ⚡ pornire automată per aplicație (AutoStart, Prefs);
  reluare automată la întoarcerea în aplicația blocată; Cronologie (TimelineView) în editorul de pași; meniul Bibliotecii 3 + Avansat.
- Tutoriale: ecranul principal, panoul (overlay peste ecran), editorul de pași; ☰ → Tutorial / Tutorialul panoului.
- Teste: 187 core + 8 app, 193 statice, fuzz 10 semințe — verzi. De verificat pe telefon: README 62–73.
- Rămas: versiunea de Windows (ultimul pas din plan).

## 2.2 (versionCode 15) — „mai frumos, mai puțin aglomerat, spam unde apăs”
- 🔥 Turbo: TurboLayer (strat care prinde degetul + bara de sus), core/Turbo (ce înseamnă atingerea, rafale ≤ 20 degete
  pe gest, swipe-ul tău refăcut, ritm măsurat); ClickService.onTurbo, oprit la urgență / închidere; Setări → 🔥 Turbo.
  Oprirea = atingerea ta, care anulează gestul injectat (aceeași regulă ca la pauza la atingere).
- Aspect: degrade, carduri (Ui.surface/modeCard), ecran principal cu carduri de mod + starea panoului.
- Mai puțină aglomerație: Opțiuni = 3 + „⚙ Avansat” pe grupe; Bibliotecă = carduri cu iconiță și dată prietenoasă;
  editorul de cod: un singur „+ 📱 Din ecran ▾”.
- Teste: 192 core (+5 V22Tests) + 8 app, 193 statice, fuzz 10 semințe — verzi. De verificat pe telefon: README 74–79.
- Rămas: versiunea de Windows.

## 2.2.1 (versionCode 16) — 🌙 Mod AFK
- AfkGuard + core/AfkWatch (testat): ecran aprins (fereastră 1 px), SessionService (serviciu în prim-plan, notificare cu
  timp/ciclu/baterie + Pauză/Stop), pauză în afara jocului, „Îl redeschid în 10 s · Anulează” → redeschide + o ia de la capăt
  (max 5/oră), reluare după ce Android oprește FreeClick (afk_session.json, bătaie la 60 s), baterie/căldură/ecran blocat,
  ecran întunecat opțional, cerere de excludere de la economisirea bateriei.

## 2.3 (versionCode 17) — degetele tale în cod, documentație, partajare
- core/TouchTracker (gesturi, degete, testat), TouchListener (copii de la Android 15+ prin reflecție, verificate în Setări, cu
  oprire automată dacă blochează; altfel strat care prinde și retrimite) — activ DOAR cât un script ascultă.
- 21 de funcții noi (asteaptaAtingere/wait_for_touch, asteaptaGest, …, pipetaAtingere/get_touch_pipeta, atingeDeodata);
  regulă: citirile „de acum” cer asculta(true); pipeta() fără argumente = eroare de compilare.
- #define / #undef (Lexer, ca în C++), header nou cu #define p point, CodeMarks/Signatures/culori înțeleg alias-urile.
- 📖 Docs (docs/reference.md → DocsText.java generat de tools/gen_docs.py), DocsActivity; teste: fiecare funcție are pagină,
  link-uri valide, exemple compilează (și sub #define p point).
- 📤 ShareCode (FREECLICK1: + deflate + base64), MacroCodec.exportPack, ShareUi, ImportActivity (Trimite → FreeClick).
- 🖐 MacroTransform.syncTaps + setarea „Atinge țintele deodată”; atingeri/s reale pe panou.
- Teste: 215 core + 8 app, statice fără probleme. De verificat pe telefon: README 85–91.
- Rămas: versiunea de Windows.

## 2.4 (versionCode 18) — prima grupă din brainstorming, măsurată
- Regula nouă: nimic nu se livrează fără măsurători; sub 99% decide utilizatorul.
- 👂 pe panou cât un script ascultă. ColorZones (core): procentCuloare 99,87% / 3000 bare la ±4 (medie 0,44);
  gasesteCuloare 100% / 1500 + 100% / 1500 fără. CodeFromSteps: 2000/2000 identice cu motorul (±2 ms la alte viteze).
  inregistreaza(): 300/300 (TouchTracker păstrează traseele degetelor). afiseazaPeEcran/ascundeTextul, notifica,
  baterie/seIncarca/orientare. Pagini noi în documentație.
- Rămase din brainstorming (de măsurat cu Laboratorul pe telefon): ascultaInZona + butoanele scriptului, asteaptaOricare,
  intreaba/alege, pinch/tap triplu/gesturiNoi, timeout/try-catch, swipe curbe, fișiere. Ale utilizatorului de decis:
  notificări ca declanșator, apasaText, internet, gasesteText. Apoi Windows.

## 2.5 (versionCode 19) — grupa a doua + Laborator
- Regulă de la utilizator: teste detaliate doar pentru problemele „flexibile” (gesturi, citiri de ecran); pentru cele
  rigide (conversii, limbaj) teste obișnuite.
- TouchTracker: zoom/rotire/swipe cu 2 degete (după mișcarea comună vs relativă), șiruri de tap-uri (count). V25Tests cu
  degete „omenești” (tremur, viteze, întârzieri): 7000/7000; varianta tremurată 99,9%; tap-uri 2000/2000; zoom/unghi 1000/1000.
- ScriptButtons (ferestre proprii), ascultaInZona (filtru în tracker + strat doar pe zonă), asteaptaOricare,
  intreaba/intreabaNumar/alege (dialoguri peste joc), timeout/else + try/catch (Parser/Checker/Interp, termene în runner),
  fișiere (files/scripts, 1 MB, 200 fișiere, nume fără foldere), swipeCurb.
- 🧪 LabActivity: atingeri/swipe/2 degete/gest liber/stratul de retrimitere/ritm maxim, ✅ ≥ 99%, raport copiabil.
- Rămase de decis de utilizator: notificări ca declanșator, apasaText, internet, gasesteText. Apoi Windows.
