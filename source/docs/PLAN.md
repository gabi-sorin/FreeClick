# FreeClick 1.6 → 2.5 — plan (revizia 12: 2.5 livrat; rămân deciziile utilizatorului și Windows)

Scop: aplicația să reacționeze la ce apare pe ecran (imagini, culori), să aibă logică (Dacă / Repetă)
și comodități (previzualizare, pornire automată, unelte pentru înregistrări), fără ca un utilizator
obișnuit cu Clickmate să se simtă pierdut. Revizia 1 a fost verificată de un critic independent și
de un prototip de căutare de imagini; ce s-a schimbat e în §8.

---------------------------------------------------------------------------------------------
## 0. Limite Android verificate (decid designul)

| Fapt | Consecință |
|---|---|
| `takeScreenshot` (Android 11+) fără dialog; max 3 cadre/s pe 12+, 1 cadru/s pe 11. | Reacție la imagini în ~0,4–0,8 s. Un cadru servește toate condițiile din acel moment. Intervalul se adaptează la versiune. |
| Android 7–10: doar MediaProjection. | Dialogul „Share your screen” o dată pe rulare (ca acum la culori). |
| O atingere reală anulează gestul injectat; API-ul de „privit” atingeri (Android 15) cere permisiune de sistem. | Înregistrarea fără întârziere e imposibilă pentru orice aplicație instalată (§6). Bonus: un gest anulat = „utilizatorul a atins ecranul” → semnal sigur pentru pauză automată. |
| Serviciile de accesibilitate pot deschide aplicații din fundal. | „Deschide aplicația” și pornirea automată merg. |
| Captura conține ferestrele noastre (panou, puncte, chenare de test). | Le ascundem înainte de captură; în rulările care citesc ecranul panoul se strânge (§3.2). |
| Fereastră protejată (bănci, unele jocuri) = cadru negru, dar bara de stare și panoul nostru apar. | „Ecran protejat” se detectează din zona aplicației, nu din tot cadrul. |
| Fără „ecran mereu pornit”, o așteptare lungă lasă telefonul să se stingă → capturile eșuează. | `FLAG_KEEP_SCREEN_ON` pe panou cât rulează ceva (bug și în 1.5 la condițiile de culoare lungi). |
| Android 14: notificările „permanente” se pot închide cu swipe. | Notificarea are „arată din nou” în ≡. |

---------------------------------------------------------------------------------------------
## 1. Reguli de intuitivitate

1. **Panoul rămâne ▶ + − ≡.** Tot ce e nou intră prin „+” și „≡”. Logica stă doar în editor.
2. **Un singur fel de a face un lucru.** „Apasă când apare” = țintă imagine; „închide reclame oricând
   apar” = pop-up. Fiecare cu intrarea ei în „+”, nu un comutator care schimbă sensul țintei.
3. **Arată, nu explica.** „Testează acum” desenează rezultatul pe ecran; pe panou se vede live ce
   face macro-ul („🔍 Collect · 3 s”, „⟳ 4/10”), ca să nu pară blocat niciodată.
4. **Implicitul merge din prima** și nu blochează: o țintă imagine în modul manual apasă dacă
   butonul e acolo și altfel merge mai departe.
5. **Propoziții, nu setări.** „Dacă apare [🖼] → apasă pe ea”, „Repetă până apare [🖼 Victorie]”.
   Același text în editor, jurnal, mesajele de final.
6. **Anulează în loc de „Ești sigur?”** (bară „Șters · ANULEAZĂ” 5 s pe ecran).
7. **Nimic prin surprindere.** Pornirea automată arată panoul; atingerile pornesc singure doar dacă
   ai ales asta, cu 3-2-1 anulabil. Atingi ecranul în timpul rulării → pauză, nu luptă cu tine.
8. **Erori cu remediu** („Nu am găsit «Collect» în 30 s. Încearcă precizia Tolerantă.”).
9. **Datele nu se pierd niciodată** (§5): aceeași cheie, fișiere vechi citite neschimbate, fișierele
   mai noi refuzate în loc să fie stricate, backup-urile cu imagini restaurabile.
10. **Iconițe și nume unice:** 🖼 țintă imagine, 🔔 pop-up, ⚡ pornire automată, ⟳ Repetă,
    „Ieși din Repetă” (nu „Ieși”).

---------------------------------------------------------------------------------------------
## 2. Fundația (1.6) — invizibilă, dar fără ea restul se strică

**2.1 `ActionTree`: un singur mod de a parcurge pașii.** Codul citește `.actions` direct în 56 de
locuri. Cu blocuri (pași în interiorul lui Dacă/Repetă) export, import, detectarea buclelor,
„folosește captura?”, scalarea, numerotarea punctelor și „−” ar vedea doar primul nivel (export
incomplet, sub-macro-uri neremapate la import = ar putea rula alt macro al tău). Deci în 1.6:
- vizitator `ActionTree.walk` + index de pași „frunză”; toate cele 56 de locuri migrate;
- regulă în `check.py`: interzise buclele directe pe `.actions` în afara `ActionTree`;
- teste cu macro-uri imbricate pentru fiecare funcție din MacroGraph/MacroCodec/Workspace;
- „−” șterge ultimul pas simplu, niciodată un bloc întreg.

**2.2 Poarta de versiune.** `MacroCodec.VERSION` crește la fiecare versiune care adaugă tipuri
(1.6→2, 1.7→3, 1.8→4…). Un fișier mai nou decât aplicația e refuzat cu „Actualizează FreeClick”
(nu încărcat pe jumătate și apoi salvat stricat). Pașii de tip necunoscut se păstrează ca noduri
opace și se scriu înapoi neschimbați.

**2.3 Motorul, rescris la programare.** Azi: un singur lucru programat și pasul următor ținut
într-un singur slot. Pentru condiții asincrone, reacții și blocuri:
- fiecare cerere (timer, gest, evaluare de condiție) are jetonul ei; pauza, reluarea, stop-ul și
  întreruperile cresc jetonul → un rezultat întârziat nu mai poate porni a doua „ramură” de rulare;
- întreruperea (pop-up) salvează explicit: pasul în așteptare, pauza rămasă și tipul ei; după
  reacție se reia exact de acolo;
- compensarea întârzierii se resetează după orice așteptare asincronă, reacție sau salt din
  previzualizare (altfel pauzele următoare ar fi „mâncate” și atingerile ar ieși în rafale);
  invariant de fuzz: distanța dintre două gesturi ≥ pauza programată − toleranță;
- limita de timp are cronometrul ei (azi se verifică doar între pași; o așteptare de culoare
  „pentru totdeauna” nu se oprea niciodată);
- „gol” = verificare statică la pornire; o tură care nu execută nimic durează minim 100 ms
  (anti-înghețare pentru „Repetă mereu { Dacă … }”);
- ecran mereu pornit cât rulează.

**2.4 Cadre de ecran.** Captura și copierea (10 MB) se mută pe un fir separat; motorul primește un
`Frame` imutabil (pixeli + momentul real al capturii), eliberat abia când nimeni nu-l mai citește.
Regula „cadru făcut după gest + 150 ms” folosește momentul real al capturii. Interval 333 ms pe
Android 12+, 1 s pe Android 11. Imagini integrale doar la nivelurile mici ale piramidei.

**2.5 Pauză automată la atingere.** Dacă un gest e anulat pentru că ai atins ecranul (sau ai tras
bara de notificări), rularea intră în pauză: „Pauză: ai atins ecranul” (setare, implicit pornită).
Previne și atingerile „rătăcite” pe butoanele din bara de notificări.

---------------------------------------------------------------------------------------------
## 3. Funcțiile, pe versiuni

### 1.6 — fundația + câștiguri rapide ✅ (livrat)

- **Anulează/Refă** (regula 6 din §1): panou ≡ → „↶ Anulează: <ce>”, bară după ștergeri; editor ↶ ↷.
  Stive separate pentru panou și editor (salvarea din editor = un singur pas în panou); max 30 de
  pași și max 8 MB.
- **Pauză aleatoare:** în dialogul Pauzei: `Fixă | Aleatoare între [2 s] și [5 s]`.
- **Deschide aplicația** (pas): listă cu iconițe, „așteaptă să se deschidă (max 10 s)”.
- **Previzualizare** (≡ → 👁 și 👁 în editor): degete „fantomă” animate, pas curent pe panou.
  Nimic cu efect nu rulează: gesturi, Înapoi/Acasă/Blocare ecran, deschideri de aplicații sunt
  doar desenate. Condițiile nu citesc ecranul; pe bara previzualizării alegi
  „presupune găsite ✓ / negăsite ✗”. Buclele arată max 2 ture („2 din 100”), ⏭ sare o așteptare.
- **Unelte pentru înregistrări** (editorul-listă, rezolvă ce deranja la „înregistrare cu
  întârziere”): Taie pauza de început, Taie finalul, Scurtează pauzele mai lungi de X s,
  selecție → Accelerează ×2, „Rulează de aici”.
- **Controale în notificare:** „FreeClick · <macro> · ▶/❚❚ · ■ · Arată panoul”, actualizată doar la
  schimbare de stare; dacă o închizi, ≡ → „Arată notificarea”. Pe Android 13+ cere permisiunea o dată.

### 1.7 — Țintă imagine (în secvență) ✅ (livrat)

**Adăugare:** `+` → **Imagine** (în modul manual); în timpul înregistrării, butonul 🖼 din rândul de
jos adaugă „Așteaptă [imaginea]” exact în acel moment; din editor, „Adaugă → Imagine” trimite
editorul în fundal, deschide captura peste aplicația de dinainte și revine cu pasul inserat după
rândul selectat (la fel „Testează acum” din editor).

**Captura:** panoul și punctele se ascund, 250 ms, captură, ecranul apare „înghețat”.
Tragi un dreptunghi → **magnet**: marginile se lipesc de buton (decupaj strâns automat).
Refuzat cu explicație: suprapus pe bara de stare (ceasul se schimbă), prea puține detalii
(o pată de culoare se potrivește oriunde), prea mic (< 16 px), prea mare (> 400 px).
Lupa de sub deget pentru precizie.

**Marcajul:** chenarul imaginii + o cruce = unde apasă (o tragi dacă vrei alt punct); insignă „3 🖼”.

**Dialogul țintei** (atingi marcajul), cu implicitele subliniate:
- **Ce face:** _Apasă_ · Apasă lung · Doar așteaptă · Așteaptă să dispară
- **Dacă nu e pe ecran:** _Mergi mai departe_ · Așteaptă până apare (max [30 s]) · Oprește macro-ul
- **Precizie:** Strictă · _Normală_ · Tolerantă (acceptă și ±8% mărime); „Avansat”: 70–98
- **Caută în:** _Tot ecranul_ · Doar în zona marcată (al doilea chenar, cu mânere)
- **Dacă apare de mai multe ori:** _cel mai de sus_ · toate
- **Oprește macro-ul după** [—] găsiri; `Testează acum`; `Recapturează`

**În timpul rulării:** panoul se strânge pe loc la un ■ mic (nu se mută); zona lui e ignorată, iar
dacă imaginea pare să fie sub el: „🔍 acoperit de panou — mută-l”. Pe panou: „🔍 Collect · 3 s”.
Apăsarea se face doar după ce imaginea e găsită pe **2 cadre la rând** (fără apăsări duble pe
butoane care dispar treptat). La final: „Collect găsit de 47 ori”.

**Căutarea (core, Java pur):** gri → piramidă → NCC grosier pe tot ecranul → rafinare nivel cu nivel
→ suprimarea duplicatelor → verificare de culoare (butonul gri ≠ butonul verde) → caută și cu
miezul șablonului (fără 15% margini), ca un decupaj cu puțin fundal să nu eșueze pe alt fundal.
Pregătirea cadrului (gri, piramidă) se face o dată pe cadru, pentru toate imaginile. Orientarea și
mărimea ecranului la captură se salvează; pe alt ecran se scalează separat pe x și y.

**Prototip (scene sintetice 1080×2400):** găsit exact / cu zgomot și +20 luminozitate / după
redimensionare ✓; absent respins (0,44); buton gri în loc de verde respins de culoare ✓;
decupaj cu fundal pe alt fundal ✗ 8/8 → cu miezul ✓ 8/8; ±5% mărime ✗ (de aici „Tolerantă” cu
mărimi multiple); șablon uniform ✗ (de aici refuzul la captură). ~40 ms pe server → 120–200 ms
estimat pe telefon.

**Stocare:** `files/images/<sha1>.png`; numele = hash (fără coliziuni, deduplicare). Curățenie
mark-and-sweep sub lacătul bibliotecii, după ștergeri și o dată pe zi; **se anulează dacă vreun
macro sau ciornă nu poate fi citit**; la reutilizare se actualizează data fișierului; la import
hash-ul se recalculează (nu ne bazăm pe nume).

### 1.8 — Date: culori, citire din ecran, consolă, variabile + setări în meniuri pliabile ✅ (livrat)

Ideea: macro-ul poate **vedea numere și text**, ține minte valori și **scrie** ce face. Fără asta,
logica din 1.9 n-ar avea despre ce să decidă („dacă aurul ≥ 1000”).

- **Variabile:** nume simple (`aur`, `vieti`, `n`); încep goale la fiecare pornire; incluse:
  `tura` (tura curentă), `timp` (secunde de la pornire), `ora` (HH:MM:SS).
- **Pași noi** (în „+” și în editor):
  - **Citește din zonă** → alegi un dreptunghi pe ecran; mod _Număr_ (12 345 · 1,5k · 2.3M · 45%)
    sau _Text_; rezultatul intră într-o variabilă. Citirea se confirmă pe 2 cadre identice (numerele
    care „curg” nu dau valori greșite). Dacă nu reușește: variabila rămâne cum era + avertisment.
  - **Citește culoarea** unui punct → `#RRGGBB` în variabilă.
  - **Setează variabila** = expresie: `aur - 100`, `n + 1`, `random(1, 6)`, `max(a, b)`, comparații.
  - **Afișează în consolă** = text cu `{variabile}` și chiar `{aur * 2}`.
- **Citirea textului (OCR) fără internet și fără Google:** separ literele (prag automat față de
  culoarea fundalului, bucăți conectate, rânduri, spații) și le compar cu literele fonturilor
  telefonului (desenate la prima folosire). Pentru fonturi de joc ciudate: **„Învață”** — după un
  test, scrii textul corect și aplicația ține minte literele (fișier mic, inclus în backup).
  În modul _Număr_ se caută doar cifre și semne → mult mai sigur.
- **Pipetă de culoare:** ecranul îngheață, o lupă arată pixelii mari, citire live `#HEX · RGB · HSV`.
  **Culoare exactă:** câmp hex, R/G/B și H/S/V cu glisoare, toleranță; „Alege de pe ecran”.
  Folosită la „Așteaptă culoare” și ca unealtă separată (≡ → Pipetă: copiază codul).
- **Consolă:** fereastră plutitoare (≡ → Consolă), trasă oriunde, strânsă într-o bară; ultimele 200
  de rânduri, copiere, golire; opțional „arată și pașii”. În timpul rulării e transparentă la
  atingeri și nu intră în căutarea de imagini / citiri.
- **Alegător de zonă:** același dreptunghi ca la decupajul de imagine (fără magnet), cu
  previzualizare live a ce s-ar citi.
- **Setări în meniuri pliabile (cerut în timpul lui 1.8):** sus doar esențialul pasului; „⚙ Avansat”
  închis, cu câte un meniu pe arie înăuntru (📍 Locație, ⏱ Timp, 🔁 Repetare, 🎯 Precizie, 📝 Altele),
  fiecare cu rezumat în titlu; la fel în setările modului, de redare și ale aplicației; starea
  deschis/închis se ține minte.

### 2.0 — Ecran de lucru: capturi ale jocului lângă cod ✅ (livrat, versionCode 12)

Cerința (după 1.9): partea vizuală **nu înlocuiește codul**. Ajută la a vedea ce se va întâmpla și
la a alege puncte, zone și culori fără să scrii pixeli de mână, văzând **în același timp** codul și
jocul. Blocurile Dacă/Repetă din planul vechi sunt tăiate; pop-up-urile există deja în cod (`cand`).

**De ce capturi, nu jocul live:**
- ecranul împărțit al Android: multe jocuri îl refuză sau repornesc, iar coordonatele din fereastra
  mică nu sunt cele din ecran complet;
- editor plutitor peste joc: tastatura acoperă jocul, iar jocul se mișcă în timp ce alegi.
- O captură e înghețată, exactă la pixel, se poate mări și merge fără joc deschis.

**Capturile:**
- 📷 din editor sau din panoul plutitor („Captură pentru cod”, poți face mai multe la rând), prin
  serviciu, pe Android 11+;
- **import din galerie** (screenshot de sistem, orice versiune); altă rezoluție → coordonatele se
  scalează la telefonul tău, cu avertisment dacă proporțiile diferă;
- mai multe per script, cu nume („meniu”, „luptă”), salvate PNG (culori exacte), în backup.

**Ecranul editorului:** sus captura (zoom cu două degete, lupă la alegere), jos codul, cu despărțitor
tras și 3 poziții (doar cod · jumătate · doar captură + linia curentă). În modul peisaj, una lângă alta.

**Unelte pe captură** (scriu la cursor, sau înlocuiesc valoarea de pe linia cursorului):
👆 Punct · ↗ Swipe · ▭ Zonă (arată live ce ar citi `citeste`) · 🎨 Culoare (lupă, hex, toleranță) ·
✂ Imagine (decupată direct din captură) · 🔍 test `gaseste`.

**Legătura cod ↔ captură:**
- fiecare `point(…)`, `tap(x, y)`, `rect(…)`, `swipe(…)` și `gaseste("…")` apare ca marcaj, cu numele
  variabilei (`butonul`) sau numărul liniei;
- cursorul pe o linie → marcajul ei se aprinde; atingi un marcaj → cursorul sare la linia lui;
- **tragi un marcaj → numerele din cod se schimbă singure**;
- o culoare selectată → pe captură se aprind pixelii care s-ar potrivi (vezi dacă toleranța e prea mare);
- o zonă → textul citit chiar acolo; o imagine → unde e găsită și cu ce %.

**▶ Simulează pe captură:** scriptul rulează fără să atingă telefonul, pe ceas virtual, cu captura
pe post de ecran (`pixel`, `citeste`, `gaseste` citesc din ea). Atingerile apar numerotate în ordine,
swipe-urile ca săgeți, consola arată `print`-urile, un glisor derulează prin timp. Se oprește după
N acțiuni sau 60 s virtuale. Limita: captura nu se schimbă, deci arată prima trecere.

**Live rămâne:** „+ din ecranul real” pentru când captura e veche.

**Cerute la aprobare și făcute:** vedere „doar cod” (🖼 + despărțitor tras); scalare corectă (codul are
coordonatele ecranului real; pe alt telefon scriptul se scalează la rulare); 👁 + ▾ cu categorii.
**Implementare:** core `CodeMarks` (locurile din text, tolerant la cod greșit, rescriere exactă),
`ScreenSpace` (design ↔ ecran, orientare), `ScriptSimulator` (ceas virtual, limite de atingeri /
timp simulat / timp real); app `ShotStore` (PNG + index, backup, copiere la Duplică, ștergere),
`ShotView` (zoom, marcaje, tragere, instrumente cu lupă, simulare), `ShotTools` (citire, imagini,
mască de culoare, decupare, ecranul simulării), editor + `captureScreenshot` în serviciu.

### 2.1 — Comodități ✅ (livrat, versionCode 14; plus switch, completare, funcțiile noi, pas Script, tutoriale)

- **Aplicația macro-ului** (un singur loc în setări): [Alege aplicația] +
  ☐ Deschide-o înainte de pornire · ☐ Rulează doar în ea · ☐ ⚡ Pornește automat când o deschid
  (Arată panoul / Pornește 3-2-1).
  - harta aplicație→macro e centrală (nu se copiază la Duplică/Salvează ca); aceeași aplicație pentru
    alt macro → „Înlocuiești «X»?”; importurile vin fără pornire automată;
  - ieși din aplicație → pauză imediată, iar la întoarcere reia singur;
  - lucrul nesalvat înlocuit de pornirea automată merge într-un singur slot „Recuperat” (nu câte un
    element nou de fiecare dată); nu face nimic dacă rulează/înregistrezi deja.
- **Două degete:** `+` → **Două degete** = o singură acțiune cu N degete și un marcaj cu N mânere;
  presetări **Zoom în / Zoom out**.
- **Cronologie vizuală** (fila „Listă | Cronologie” pentru înregistrări fără blocuri): blocuri pe o
  bandă de timp, tragi ca să muți (și tot ce urmează), zoom cu două degete, mini-schița ecranului
  arată unde apasă pasul selectat.


### Ce mai lipsește ca să acoperim „aproape orice” (gândit la cererea din 1.8)

| Idee | Unde | De ce |
|---|---|---|
| Variabile, citire număr/text/culoare, calcule, consolă, pipetă | **1.8** | cerute; baza pentru decizii |
| Condiții pe variabile, oră/zi, baterie, aleator | 1.9 (cod) / 2.0 (vizual) | „joacă până ai 1000 aur”, „doar noaptea” |
| **Așteaptă până se liniștește ecranul** (nimic nu se mai mișcă X ms) | 1.9 (cod) / 2.0 | după încărcări, fără pauze ghicite |
| **Așteaptă o schimbare într-o zonă** | 1.9 (cod) / 2.0 | „când se schimbă scorul”, notificări în joc |
| **Model din mai mulți pixeli** (3–5 puncte cu culori) | 1.9 (cod) / 2.0 | mai rapid și mai sigur decât o imagine pentru butoane fixe |
| Așteaptă până la ora HH:MM | 1.9 (cod) / 2.0 | evenimente zilnice în jocuri |
| **Trage și ține** (apeși, ții, apoi muți) | 2.0 | drag & drop în jocuri; swipe-ul simplu nu ține înainte |
| Atingere aleatoare într-o zonă | 2.0 | anti-detecție mai bună decât „variație px” |
| Alertă: vibrație / sunet / mesaj scurt | 2.0 | „gata!” când găsește ceva rar |
| Salvează captura de ecran în galerie | 2.0 | dovadă / jurnal vizual |
| Copiază în clipboard (text citit) | 2.0 | coduri, nume |
| Variabile păstrate între rulări (contor total) | 2.0 | statistici pe zile |
| Declanșare la o notificare (permisiune „Acces la notificări”) | după 2.1, la cerere | permisiune sensibilă |
| Deschide link / deep link | 2.0 | pagini anume dintr-o aplicație |
| Scrie text într-un câmp | amânat (ca „Apasă pe text”) | cere citirea conținutului ferestrelor |
| Tot ce e mai sus, în cod | 1.9 Cod | `if (citeste(zona) >= 1000) stop();` |

---------------------------------------------------------------------------------------------
## 4. Amânate / tăiate (de confirmat)

| Ce | Recomandare | De ce |
|---|---|---|
| Widget pe ecranul principal | **Tăiat** | „Scurtătură pe ecranul principal” (biblioteca ⋮) face deja același lucru dintr-o atingere. |
| Apasă pe text | **Amânat, doar cu acordul tău** | Nu merge în jocuri; cere ca serviciul să citească conținutul ecranului, contrar promisiunii actuale; schimbă configurarea serviciului. |
| Înregistrare fără întârziere | **Imposibil** | §0; înlocuit de uneltele pentru înregistrări (1.6). |

---------------------------------------------------------------------------------------------
## 5. Date și compatibilitate

- Poarta de versiune și nodurile opace (§2.2); fișiere „de aur” din 1.5 + o copie înghețată a
  codec-ului 1.5 în teste: totul din 1.5 se citește și rulează identic.
- Backup nou = ZIP (`backup.json` + `images/*.png`), scris și citit pe fir separat, fără să încarce
  totul într-un singur String; backup-urile vechi `.json` rămân restaurabile. Test: restaurare cu
  50 de imagini.
- Șabloane max 400 px peste tot (captură, import).
- Tipuri noi: `IMAGE`, `PRINT`, `SET_VAR`, `READ` (1.8), `IF`, `LOOP`, `BREAK`, `STOP`, `LAUNCH_APP`, `MULTI`; `WAIT.durationMax`;
  `Macro.reactions`; `Condition {IMAGE | COLOR | APP, negate, …}`. `WAIT_COLOR` rămâne.

---------------------------------------------------------------------------------------------
## 6. De ce nu „înregistrare fără întârziere”

Android anulează gestul injectat imediat ce degetul real atinge ecranul, iar API-ul care ar permite
„privitul” atingerilor fără a le opri cere o permisiune rezervată sistemului. Acum atingerile ajung
la aplicație la ~0,1 s după ridicarea degetului, glisările imediat după ce le termini.

---------------------------------------------------------------------------------------------
## 7. Testare

- **Căutarea de imagini:** pe lângă scenele sintetice, scene desenate realist (text anti-aliasat,
  umbre, fundal întunecat de pop-up, compresie JPEG, ±5% mărime, mod întunecat, distractori
  „Collect” / „Collected” / buton gri); bugete în număr de operații, nu milisecunde.
- **„Salvează cazul”** în „Testează acum”: exportă cadrul + șablonul + scorurile → din capturile
  tale reale construim un set de regresie și reglăm pragurile.
- **Motor:** FakeHost cu întârzieri asincrone aleatoare, rezultate în altă ordine, pauză/reluare în
  timpul unei evaluări; blocuri, pop-up-uri care întrerup pauze lungi și reiau restul exact,
  regula cadrului proaspăt, ecranul care nu se stinge, limita de timp în așteptări.
- **Fuzz:** macro-uri aleatoare cu blocuri + pop-up-uri + condiții; invarianți: niciodată două gesturi
  simultane, niciun pas după Stop, distanța minimă dintre gesturi, nicio buclă fără timp, limitele
  Android; operațiile editorului pe arbore („niciun pas pierdut sau dublat”); uneltele pentru
  înregistrări (ordine păstrată, pauze ≥ 0).
- **check.py:** fără bucle directe pe `.actions`; fără captură procesată pe firul principal;
  componente și PendingIntent-uri noi; ghidaje de versiune.

---------------------------------------------------------------------------------------------
## 8. Ce a schimbat criticul (rezumat)

| Observație | Decizie |
|---|---|
| Pașii în blocuri ar scăpa la export/import/verificări (56 de locuri) | Acceptat: `ActionTree` în 1.6 + regulă în check.py |
| Motorul nu poate întrerupe/relua sigur; pauza nu invalidează cererile | Acceptat: programator cu jetoane + înregistrare de întrerupere |
| Compensarea întârzierii ar trage atingeri în rafală după așteptări | Acceptat: resetare + invariant de fuzz |
| Captura copiată pe firul principal, bitmap reciclat sub căutare | Acceptat: fir separat, `Frame` imutabil |
| Curățenia ar putea șterge imagini folosite | Acceptat: mark-and-sweep cu anulare la orice eroare |
| Backup-uri cu imagini prea mari ca să se restaureze | Acceptat: ZIP pe fir separat, max 400 px |
| Versiune mai veche ar strica fișiere mai noi | Acceptat: poartă de versiune + noduri opace |
| Panoul/chenarele noastre apar în cadre | Acceptat: ascundere la captură, panou strâns pe loc (nu mutat) |
| Implicitul „așteaptă 30 s” face modul manual să pară blocat | Acceptat: implicit „dacă nu e, mergi mai departe” + stare live |
| Prea multe concepte suprapuse | Acceptat: intrări separate în „+”, „Repetă” unic, „Aplicația macro-ului” unică, iconițe unice |
| Editorul nu poate captura/testa peste el însuși | Acceptat: trece în fundal și revine |
| Notificarea folosită în timpul rulării → atingeri în bara de notificări | Acceptat: pauză automată la atingere |
| Pornire automată: 10 s de grație când ieși din aplicație | **Respins**: atingerile ar putea apăsa „Instalează” în Magazin Play; pauză imediată + reluare automată la întoarcere |
| Previzualizarea ar rula Înapoi/Acasă de-adevăratelea | Acceptat: toate efectele simulate, condiții presupuse |
| Ecranul se stinge în așteptări lungi; limita de timp ignorată în așteptări | Acceptat (și reparat pentru 1.5) |
| „Simultan cu anterioara” se strică la mutare | Acceptat: o singură acțiune cu N degete |
| Ordinea: pop-up-urile au nevoie de motorul din 1.8 | Acceptat: pop-up-urile mutate în 1.8, fundația în 1.6 |
| Widget, text, cronologie | Widget tăiat, text amânat (de confirmat); cronologia împărțită: unelte în 1.6, vizual în 2.0 |

---------------------------------------------------------------------------------------------
## 8b. 1.9 — Modul Cod (cerut după planul inițial; mutat înainte de blocurile vizuale)

Meniu separat **„Cod”** pe ecranul principal (scripturile sunt salvări de tip „cod”, cu export/backup).
Limbaj în stil C++, scris în aplicație, care face tot ce fac pașii (și mai mult):
```cpp
struct Tinta { point p; color c; string nume; };
vector<Tinta> tinte;

bool eVerde(point p) { return aproape(pixel(p), #34A853, 20); }

void main() {
    int gasite = 0;
    rect zonaAur = rect(40, 120, 300, 170);         // scris de butonul „+ Zonă”
    while (true) {
        if (eVerde(point(540, 1200))) { tap(540, 1200); gasite++; }
        int aur = citeste(zonaAur);
        print("aur = " + to_string(aur) + ", găsite " + to_string(gasite));
        if (aur >= 1000) stop();
        wait(300);
    }
}
```
- **Tipuri:** `int`, `double`, `bool`, `string`, `void`; pentru ce alegi pe ecran `point` (`.x .y`,
  `+`, `dist`), `color` (`.r .g .b`, `hex()`, `#RRGGBB` literal, `aproape`), `rect` (`.x1…y2`,
  `centru()`); `pair<A,B>` (`.first .second`, `make_pair`); `struct` proprii; `vector<T>`
  (`push_back`, `pop_back`, `size`, `clear`, `[i]`, `for (T x : v)`); `const`.
- **Funcții proprii cu tipuri** (`void`, `bool`, `int`… , parametri, `return`, recursivitate cu limită);
  verificare de tipuri înainte de rulare, erori în română cu linia marcată.
- **Control:** `if/else`, `while`, `for`, `for (x : v)`, `break/continue`, `switch` simplu.
- **Acțiuni:** `tap(p)` / `tap(x, y)`, `apasaLung`, `dubluTap`, `swipe(a, b, ms)`, `trage(a, b)` (ține,
  apoi mută), `wait(ms)`, `waitRandom(a, b)`, `back()`, `home()`, `recente()`, `openApp("pachet")`,
  `print(...)` → consola din 1.8, `toast`, `vibreaza()`.
- **Ecran:** `pixel(p)` → `color`, `citeste(rect)` → număr, `citesteText(rect)` → string,
  `gaseste("imagine")` → `point` sau `null`, `apare("imagine")`, `asteapta(cond, ms)`,
  `asteaptaLiniste(ms)`.
- **Lista de pași a clicker-ului, din cod:** `pasi` este un `vector<Pas>`: `ruleaza(pasi)`,
  `ruleaza(pasi[3])`, `pasi[3].x = 500`, `pasi[3].activ = false`, `pasi.push_back(tap(100, 200))`,
  `pasi.erase(2)`; alte salvări: `macro("Farm").ruleaza(3)`.
- **Când se execută (evenimente):** `cand (apare("Collect")) { … }` (oricând apare, ca pop-up-urile),
  `la_fiecare(5 * MIN) { … }`, `la_ora("22:00") { … }`; rulează doar cât scriptul așteaptă, apoi
  continuă exact de unde a rămas.
- **Inserare din ecran:** bară cu „+ Atingere aici”, „+ Swipe”, „+ Culoare”, „+ Zonă”, „+ Imagine”,
  „+ Pas din listă”: alegi pe ecran și se scrie singur `point(540, 1200)`, `#34A853`, `rect(…)`.
- **Editor:** numerotare linii, colorare, completare automată, fragmente (if/while/for/funcție).
- **Depanare:** rulare pas cu pas cu linia curentă colorată, puncte de oprire (atingi numărul liniei),
  panou de variabile, consola.
- **Tutorial „atinge ca să continui”** la prima deschidere (și din ⋮ → Tutorial): bule care arată
  pe rând editorul, inserarea din ecran, ▶, consola, pas cu pas, `pasi`, evenimentele; plus exemple
  gata făcute („apasă când apare verde”, „farm până la 1000 aur”, „repetă lista de 10 ori”).
- **Implementare** (core, Java pur, testabilă pe JVM): lexer → parser (AST) → verificare de tipuri →
  bytecode → mașină virtuală care **cedează** la fiecare funcție asincronă (atingere, pauză, citire de
  ecran) și rulează în același motor (Stop, viteză, pauză automată, previzualizare, consolă identice).
  Expresiile din 1.8 (`Expr`) devin un subset al limbajului.
- **Siguranță:** buget de instrucțiuni pe felie (un `while(true)` fără `wait` nu blochează telefonul),
  limită de memorie (vectori) și de recursivitate, Stop mereu instant.
- **Amestec:** pas „Cod” într-un macro normal (câteva linii între pași); variabile păstrate între
  rulări (`pastreaza int total;`).

**Livrat în 1.9 (versionCode 10)** — diferențe față de plan:
- Interpretor pe arbore, pe firul lui (stivă 32 MB), nu bytecode: apelurile spre telefon (atingere,
  cadru, citire) așteaptă firul principal. Stop = întrerupere, pauză / pas cu pas / opriri = monitor.
  Buclă fără apeluri: după 200k instrucțiuni avertisment + 5 ms de somn.
- `gaseste()` dă `point(-1, -1)` în loc de `null`; pașii noi: `pasTap(p)`, `pasSwipe`, `pasPauza`,
  `pasApasaLung`; altă salvare: `pasiDin("Farm")`, `ruleazaMacro("Farm", 3)`.
- Amânate: `switch`, `asteaptaLiniste`, completare automată, pas „Cod” într-un macro normal (→ 2.0,
  împreună cu blocurile vizuale).
- Cadrele pentru `pixel` / `citeste` / `gaseste`: după ultima atingere + 150 ms și nu mai vechi de un
  interval de captură; ecranul e eșantionat continuu doar dacă scriptul folosește funcții de ecran.

Amânate din 1.9 → 2.1: `switch`, `asteaptaLiniste`, completare automată, pas „Cod” într-un macro normal.

---------------------------------------------------------------------------------------------
## 8b2. Înainte de Windows: tutorial pentru toată aplicația
Același stil ca la Cod (bule „atinge ca să continui”, fără butoane în timpul lui; la final
„Închide” / „Nu mai afișa”), pe ecranul principal, panou, editor și setări.

## 8c. La final: versiunea de Windows (cerută pentru final)

## 9. Verificat pe telefon, la fiecare versiune

1. Trag bara de notificări în timpul unei rulări → pauză.
2. O așteptare de 60 s cu ecranul setat să se stingă la 30 s → nu se stinge.
3. Un joc în modul peisaj (imagini găsite, puncte la locul lor).
4. O aplicație bancară → mesaj „ecran protejat”, nu „nu am găsit”.
5. După update: serviciul pornit, totul la locul lui (inclusiv „serviciul nu funcționează” pe Samsung).
6. Închid notificarea cu swipe → „Arată notificarea” o readuce.
7. Viteza căutării de imagini pe telefonul tău; „Salvează cazul” pentru ce nu găsește.
