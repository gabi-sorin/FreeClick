# FreeClick: auto-clicker pentru Android, fără paywall

Alternativă la Clickmate: înregistrează și repetă atingeri, swipe-uri și gesturi cu mai multe degete. Totul e gratuit, fără reclame și fără acces la internet. Merge pe Android 7.0+ (API 24), testat la compilare până la Android 14 (API 34). Nu cere root.

## Instalare
1. Copiază `FreeClick.apk` pe telefon și deschide-l. Permite „Instalare din surse necunoscute” dacă ți se cere.
2. Deschide FreeClick și apasă **„Activează în Setări → Accesibilitate”**. Găsește **FreeClick** în listă și pornește-l.
   - Pe **Android 13+**, butonul poate fi gri („Setare restricționată”). Mergi la Setări → Aplicații → FreeClick → meniul ⋮ → **„Permite setările restricționate”**, apoi încearcă din nou.
3. (Opțional) Permite notificările. Sunt necesare pentru butonul „Oprește” de la filmare.

## Cum se folosește (ca în Clickmate)
Pe ecranul principal **atingi un mod** și panoul plutitor pentru modul respectiv apare imediat (Starea se pornește singură). La „Combină”, dacă nu ai încă o combinație, se deschide direct lista de combinații. Panoul îl muți trăgând de el. Apoi deschizi jocul sau aplicația.

| Mod | Panou | Ce faci |
|---|---|---|
| **Ținte manuale** | ▶ + − ≡ | **+** adaugă tap, swipe (S1→E1), apăsare lungă sau condiție de culoare · **−** șterge ultima țintă · tragi țintele cu degetul · atingi o țintă ca s-o setezi |
| **Mod înregistrare** | ▶ ● − ≡ | **●** înregistrează ce faci, cu tot cu pauze (cu bara ◀ ● ■ ⌷ pentru Înapoi / Acasă / Recente / Captură) · **■** oprește · **▶** redă · **−** șterge ultima acțiune |
| **Combină înregistrări** | ▶ ≡ | ≡ → **Combinații (Încarcă/Salvează)** → **+** creează o combinație din înregistrările și presetările salvate |

- **≡ → Opțiuni (Încarcă/Salvează)**: Salvează / Salvează ca / Încarcă, exportă sau gestionează, plus setările modului (interval de repetare, durată click/swipe, număr de repetări, pauză înainte de repetare, viteză, acțiuni aleatoare) și secțiunea **Avansat**.
- Fiecare mod își păstrează lucrul curent („Nesalvat”) și după ce oprești Starea.
- **În redare**: ■ stop · ❚❚ pauză · **1×** schimbă viteza pe loc (0,1× … 10×).
- **Ține apăsat −** (sau ≡ → **Șterge tot**) ca să ștergi toate punctele/acțiunile; cere confirmare.
- **Puncte pe ecran**: ≡ → **Ascunde punctele** le face invizibile, iar macro-ul rulează la fel. Setarea permanentă e în ☰ → Setări → Puncte pe ecran (separat pentru ținte manuale și înregistrări). În modul înregistrare sunt ascunse implicit, ca în Clickmate.
- **Oprire de urgență**: apasă de **două ori rapid volum jos**.

## Funcții

### Cele din Clickmate (aici fără plată)
- **Mod înregistrare**: gesturi cu mai multe degete, taps, apăsări lungi, swipe-uri, plus butoanele Înapoi / Acasă / Recente.
- **Mod manual**: ținte numerotate pe ecran pentru tap, apăsare lungă, dublu tap și swipe.
- **Mod combinație**: un macro poate rula alte macro-uri de N ori, cu protecție la bucle (A→B→A e blocat și semnalat).
- **Salvare și editare**: listă de macro-uri; poți redenumi, duplica, șterge. Editorul complet permite reordonarea, duplicarea și editarea fiecărei acțiuni.
- **Redare**: repetări (sau la infinit), pauză între repetări, viteză reglabilă și oprire automată după X secunde.
- **Acțiuni de sistem**: Înapoi, Acasă, Recente, Notificări, Setări rapide, Screenshot, Blocare ecran, Ecran împărțit.
- **Integrare Tasker / MacroDroid**: broadcast `com.freeclick.action.RUN_MACRO` (extra `name`) și `com.freeclick.action.STOP`. Funcția e **oprită implicit**.

### În plus față de Clickmate
- **Condiții de culoare (pipetă)**: macro-ul așteaptă ca un punct de pe ecran să aibă o anumită culoare (sau s-o piardă). Poți seta toleranță, timp maxim și ce se întâmplă la expirare (continuă sau oprește). Inspirat de Klick'r, MacroKit, Macrorify. Pe **Android 11+** culorile se citesc prin serviciul de accesibilitate, **fără dialogul „Share your screen”**.
- **Filmarea ecranului** în MP4, manual sau automat cât rulează macro-ul. Videoclipurile se salvează în Galerie, în Movies/FreeClick, și au listă în aplicație (redare, trimitere, ștergere).
- **Anti-tipar**: abatere aleatoare de poziție (px) și de timp (%), ca să nu fie mișcări identice.
- **Deschide automat aplicația țintă** înainte de rulare.
- **Scurtătură pe ecranul principal** pentru fiecare macro (Android 8+).
- **Programare** la o oră fixă (o dată sau zilnic); se reactivează automat după restart.
- **Tile în Setări rapide**: deschide panoul cu ultimul macro sau oprește redarea.
- **Rulează doar în aplicația X**: dacă apare altă aplicație, redarea intră pe pauză.
- **Jurnal de activitate**: fiecare pas cu oră exactă, plus gesturile anulate.
- **Efect vizual** unde atinge, **numărătoare inversă** la pornire, **panou** reglabil (opacitate, mărime).
- **Scalare automată** a coordonatelor dacă rezoluția sau orientarea diferă.
- **Export / import / trimitere** de macro-uri. Fișierul include și sub-macro-urile.

### Nou în 2.5: grupa a doua + 🧪 Laborator
- **🧪 Laborator** (Setări): pe orice telefon, FreeClick își trimite singur atingeri de test și numără ce ajunge corect.
  - Ce măsoară: atingeri, swipe-uri, două degete deodată, gesturi libere, stratul care trece atingerile mai departe și ritmul maxim.
  - ✅ înseamnă cel puțin 99%. „Copiază rezultatele” îți dă cifrele împreună cu telefonul.
- **Gesturi noi**, testate pe gesturi făcute ca de degete reale:
  - `asteaptaGest()` recunoaște acum și „zoom mareste/micsoreaza”, „rotire dreapta/stanga” și „2 degete swipe sus”…
  - Mai sunt `asteaptaTapuri(3)` (tap triplu), `factorGest()`, `rotireGest()` și `gesturiNoi()`.
  - *7000 de gesturi: 100% recunoscute; cu degete foarte tremurate, 99,9%. 2000 de șiruri de tap-uri: 100%.*
- **Butoanele tale peste joc:** `buton("⏩", zona)`, `apasat(b)`, `asteaptaButon()`, `stergeButon(b)`. O apăsare pe ele nu ajunge niciodată în joc.
- **`ascultaInZona(zona)`:** scriptul aude doar gesturile care încep acolo. Restul ecranului merge direct în joc, fără întârziere.
- **`asteaptaOricare({"a", "b"})`:** îți spune care imagine a apărut prima.
- **Întrebări cât rulează:** `intreaba("…")`, `intreabaNumar("…", 10)`, `alege("…", {"A", "B"})`.
- **Limbaj:** `timeout (30s) { … } else { … }`, `try { … } catch (string e) { … }`.
- **Fișiere și swipe curb:** `scrieFisier` / `adaugaInFisier` / `citesteFisier` / `existaFisier`, plus `swipeCurb(a, b)`.

### Nou în 2.4: măsurat înainte de livrat
Fiecare funcție nouă e testată automat pe mii de cazuri generate (cu zgomot, margini, text peste etc.). Cifrele sunt mai jos.
- **👂 pe panou** cât un script ascultă: semnalul că te așteaptă.
- **`procentCuloare(zona, culoare)`**: cât de plină e o bară (viață, energie), 0–100, orizontală sau verticală; marginile și textul de peste bară nu încurcă.
  *3000 de bare: 99,9% la cel mult 4 puncte, eroare medie 0,4.*
- **`gasesteCuloare(zona, culoare)`**: mijlocul primei pete de culoare; pixelii singuratici nu contează.
  *1500 de ecrane: 100% în pată; 1500 fără culoare: 100% „nu e”.*
- **💻 „Fă script din ea”** (Bibliotecă → ⋮ → Avansat): o înregistrare sau o listă de ținte devine cod, fiecare atingere pe linia ei. Originalul rămâne.
  *2000 de înregistrări: aceleași atingeri, locuri, durate și momente ca motorul (±2 ms la alte viteze, din rotunjiri).*
- **`inregistreaza()`** înregistrează ce faci până stai 3 s fără să atingi, iar **`reda(pasi, ori)`** repetă. Swipe-urile, curbele și gesturile cu mai multe degete se păstrează exact.
  *300 de înregistrări: 300/300 cu aceleași atingeri, locuri, durate și pauze.*
- **`afiseazaPeEcran("…")`**: text peste joc, care nu poate fi atins. **`notifica("…")`**: notificare pe telefon. **`baterie()`, `seIncarca()`, `orientare()`**.
- Ce nu se poate măsura aici (cum arată textul peste joc, notificările, redarea pe telefon): e scris la punctele 92–96.

### Nou în 2.3: degetele tale în cod, documentație, partajare
**👂 Scriptul ascultă ce faci tu pe ecran**
- `asteaptaAtingere()` (sau `wait_for_touch()`) îți dă punctul atins, ca apoi să apeși acolo din cod.
- Mai sunt: `asteaptaGest()` („tap”, „dublu”, „lung”, „swipe sus”…, „2 degete”), `asteaptaSwipe()`, `asteaptaDubluTap()`, `asteaptaApasareLunga()`, `asteaptaDegete(2)`, `asteaptaFaraDegete()`, `asteaptaAtingereInZona(zona)`, `alegeZona()` (tragi un dreptunghi cât rulează) și `pipetaAtingere()` / `get_touch_pipeta()` (culoarea de unde atingi).
- Detaliile ultimului gest: `inceputGest()`, `sfarsitGest()`, `degeteGest()`, `durataGest()`.
- **Regulă:** cele care citesc degetele de acum (`degete()`, `atins()`, `pozitieDeget()`, `ultimaAtingere()`, `atingeriNoi()`) cer `asculta(true);` înainte, altfel apare o eroare clară. Funcțiile `asteapta…()` ascultă singure cât așteaptă.
- Atingerile tale ajung și în joc (`treciMaiDeparte(false)` oprește asta). Un script care ascultă nu pune pauză când atingi.
- **Cum aude FreeClick:** doar cât un script ascultă; altfel nu e nimic în cale.
  - Pe Android 15+, dacă testul din Setări → 👂 iese bine, Android îi dă o copie a atingerilor și jocul le primește imediat.
  - Altfel, un strat prinde gestul și îl trimite în joc după ce ridici degetul.
  - Dacă ascultarea directă ar bloca atingerile, se oprește singură (și la oprirea de urgență).

**Limbaj**
- `#define` ca în C++, cu parametri, `#undef` și `\` pentru linii continuate. Scripturile noi încep cu `#define p point`, deci poți scrie `p(540, 1200)`. Pe captură, punctele scrise așa apar la fel.
- `pipeta(x, y)` / `pipeta(p)` = culoarea din punct. `pipeta()` fără argumente e o greșeală și mesajul spune ce să folosești.
- `atingeDeodata({p1, p2, …})`: mai multe degete în același moment (cel mult 10).

**📖 Documentație** în stilul Godot
- Peste 100 de pagini: fiecare funcție, limbajul și setările.
- Fiecare pagină are formele de apel, alte nume, legături și un exemplu cu „Copiază” / „Pune în cod”.
- Se deschide din ⋮ → 📖 în editor, din 📖 de pe bara editorului (pagina cuvântului de la cursor), din bara cu parametri, din Setări și din ☰.
- Testele verifică automat că fiecare funcție are pagină, că legăturile duc undeva și că fiecare exemplu compilează.

**📤 Partajare**
- Orice salvare, sau mai multe deodată, devine un cod „FREECLICK1:…” pe care îl lipești pe chat, sau un fișier.
- Importul: Biblioteca vede singură un cod copiat, sau „Trimite → FreeClick” din orice aplicație.
- Mereu cu confirmare: nu înlocuiește nimic, iar scripturile nu pornesc singure.

**Altele**
- Opțiuni → ⚙ Avansat → 🖐 → „Atinge țintele deodată”.
- Panoul arată atingerile reale pe secundă la rulările rapide (ex. „3/∞ · 18/s”).
- ⋮ din editorul de cod are acum 3 lucruri (📖, Exemple, 📤) + Avansat, iar „+ 📱 Din ecran” e un singur buton.

### Nou în 2.2.1: 🌙 Mod AFK (sesiuni lungi)
Pornit implicit, din Setări → 🌙 Mod AFK.
- **Ecranul rămâne aprins** cât rulează ceva, chiar dacă ai ascuns panoul.
- **FreeClick are notificarea lui** („🌙 AFK · nume · 1 h 23 min · ciclul 412 · 🔋 76%”), cu Pauză și Stop. Așa Android nu îl oprește după ore.
- **Dacă jocul iese de pe ecran**, se pune pauză, deci nu atinge nimic în altă aplicație. Când revii, continuă.
- **Dacă jocul s-a închis** și nu revine în 20 s, apare „Îl redeschid în 10 s · Anulează”. Apoi jocul e redeschis și rularea o ia de la capăt, de cel mult 5 ori pe oră. Aplicațiile pe care le deschide macro-ul însuși nu contează ca „ieșit din joc”.
- **Dacă Android oprește FreeClick**, sesiunea e reluată când pornește din nou (cu 10 s de Anulează).
- **Pauză la baterie mică** (implicit 15%; continuă la încărcare), **la căldură** (peste 45 °C, până la 40 °C) și **când blochezi telefonul**.
- Opțional: **ecran întunecat** cât rulează.
- Butonul „Nu lăsa Android să oprească FreeClick” deschide dialogul sistemului pentru economisirea bateriei. Pentru Samsung e explicat și pasul în plus.

### Nou în 2.2: 🔥 Turbo și un aspect mai curat
**🔥 Turbo: atingeri rapide exact unde apeși**
- Butonul 🔥 de pe panou (modurile manual și înregistrare) pornește Turbo. Sus apare o bară portocalie; × o închide.
- **Atingere scurtă:** o rafală de atingeri pe locul acela (implicit 10).
- **Ții degetul o clipă și ridici:** atinge acolo non-stop, până atingi din nou ecranul (sau după limita de timp).
- **Swipe:** trece normal, deci poți derula în continuare.
- Bara arată câte atingeri pe secundă se fac de fapt.
- În Setări → 🔥 Turbo alegi ritmul (5–60/s), câte atingeri are o rafală, limita pentru non-stop și o împrăștiere de câțiva px.
- Cu rafala pe 0, atingerile scurte trec normal și doar ținutul apăsat dă rafale. Așa poți folosi jocul ca de obicei.
- Oprirea de urgență (volum jos de 2 ori) oprește și Turbo.

**Aspect**
- Fundal în degrade, carduri cu margine fină și colțuri rotunde, butonul principal mai luminos.
- Pe ecranul principal, modurile sunt carduri cu iconiță și o propoziție. Un card arată dacă panoul plutitor e pornit.
- **Mai puțină aglomerație:**
  - Opțiunile modului arată doar 3 setări; restul e în „⚙ Avansat”, pe grupe (Atingeri, Locație, Timp, Aplicație, Siguranță, Filmare).
  - În bibliotecă, fiecare salvare e un card cu iconița tipului și un rând scurt, de exemplu „azi, 14:32 · 12 pași · ~8 s”, fără secunde și fără cuvinte în plus.
  - În editorul de cod, cele 6 butoane „+ 📱…” au devenit unul: „+ 📱 Din ecran ▾”.

### Nou în 2.1
**Cod**
- `switch (x) { case 1: … break; default: … }`, ca în C++ (merge pe int, string, color).
- **Completare automată:** cât scrii, deasupra tastaturii apar nume care încep la fel: variabilele și funcțiile tale, apoi funcțiile aplicației, cuvintele cheie și tipurile. Atingi unul ca să-l scrii; după `.` apar câmpurile și metodele.
- **Funcții noi pentru jocuri:**
  - `asteaptaLiniste([zona, ms_liniste, ms_maxim])`: așteaptă până nu se mai mișcă nimic, de exemplu după încărcări;
  - `asteaptaSchimbare(zona[, ms_maxim])`: așteaptă până se schimbă ceva în zonă, de exemplu scorul;
  - `model({p1, p2, p3}, {#c1, #c2, #c3}[, toleranță])`: verifică dacă mai mulți pixeli au culorile lor, mai rapid decât o imagine;
  - `tapInZona(zona[, durata, pauza])`: atinge alt loc din zonă la fiecare apel;
  - `zoomIn(centru[, distanta, ms])` și `zoomOut(…)`: zoom cu două degete;
  - `salveazaCaptura(["nume"])`: salvează ecranul în Galerie → FreeClick;
  - `copiaza(text)`: pune textul în clipboard;
  - `deschideLink("https://…")`: deschide un link sau un link de aplicație.
- **Pe captură:** **🎲 Tap în zonă** (tragi un dreptunghi) și **🔢 Model** (atingi 3–5 pixeli, apoi „✅ Scrie modelul”). Punctele unui `model(…)` apar pe captură cu culorile lor și ✓ sau ✗.

**Clicker**
- **Pas „📜 Script”** într-un macro sau într-o combinație: + Adaugă → „Alt macro sau 📜 script”. Macro-ul așteaptă să se termine scriptul, apoi merge mai departe. ■ oprește și scriptul, pauza îl ține pe loc.
- **Zoom cu două degete** ca țintă: + pe panou → „Adaugă zoom”, sau în lista de pași. Marcajul e centrul; ținut apăsat → direcție, distanță, durată.
- **⚡ Pornire automată când deschizi o aplicație:** din Bibliotecă → ⋮ → Avansat → „⚡ Pornire automată”, sau din Opțiuni → 📱 Aplicație.
  - Alegi aplicația și dacă doar apare panoul sau macro-ul pornește singur după 3 s (■ anulează).
  - Merge și pentru scripturi. O aplicație pornește un singur macro.
  - Nu înlocuiește niciodată ceva nesalvat din panou și nu întrerupe o rulare.
- **„Rulează doar în aplicația”** pune pauză când ieși și **reia singur** când te întorci.
- **Cronologie** în editorul de pași (fila „Listă | Cronologie”):
  - pașii stau pe o bandă de timp; tragi un bloc ca să-l muți (tot ce urmează se mută cu el);
  - zoom pe timp cu două degete; dublu-tap pe un bloc îl editează;
  - un ecran mic arată unde atinge pasul ales.
- Meniul ⋮ al unei salvări din Bibliotecă: 3 rânduri + „Avansat ›”, ca la ≡.

**Tutoriale** „atinge ca să continui”, cu „Închide” sau „Nu mai afișa” la final:
- pe ecranul principal;
- peste panoul plutitor, prima dată când îl pornești;
- în editorul de pași;
- oricând din ☰ → Tutorial / Tutorialul panoului.

### Nou în 2.0.1: după primele teste
- **Cât scrii, vezi codul:** cu tastatura deschisă, captura și butoanele ▶ / 👣 se ascund și codul primește tot locul. Revin când închizi tastatura. Fără nicio captură, zona ei e doar o bandă subțire. Panoul plutitor se ascunde cât timp editorul e deschis (nu mai stă peste tastatură).
- **▶ Rulează nu mai pornește scriptul peste editor / ecranul principal:** îl pune pe panou (📜). Deschizi jocul, apeși ▶ pe panou și pornește acolo. Când se termină, rămâne pe panou: ▶ îl rulează din nou. ≡ → „Codul …” te duce înapoi în editor. O eroare te duce direct la linia ei. La fel „Rulează” din lista de scripturi.
- **Durată și pauză la atingeri:** `tap(p, durata, pauza)`, `tap(x, y, durata, pauza)`, `apasaLung(p, ms, pauza)`, `dubluTap(p, pauza)`, `swipe(a, b, ms, pauza)`.
- **Setările clickului sus în cod:** un script nou începe cu `const int DURATA_CLICK`, `DURATA_SWIPE` și `INTERVAL_REPETARE`, cu valorile din Opțiuni. Atingerile fără valori scrise le folosesc (pauza = cât așteaptă după fiecare atingere). Într-un script mai vechi: ⋮ → „Setările clickului sus în cod”. Fără ele, totul merge ca înainte.
- **Ce așteaptă funcția, ca în C++:** cât scrii un apel, deasupra tastaturii apare semnătura, de ex. `void tap(point p, int durata = DURATA_CLICK, int pauza = INTERVAL_REPETARE)`, cu parametrul la care ești îngroșat. Merge pentru funcțiile aplicației, metodele (`v.push_back(…)`), funcțiile tale (`int aduna(int a, int b)`) și struct-urile tale (`Tinta{point p, int ori}`).
- **Bara „Deschide ecranul potrivit”** e pe toată lățimea (cu fonturi mari nu mai iese din ecran), are mereu un **×** mare și dispare singură când revii în editor.
- **Meniul ≡ al panoului:** doar 3 lucruri (Opțiuni sau Codul, Arată aplicația, Oprește) + **Avansat ›** cu restul.
- Simboluri rare (✕ ✓ ❚❚ ✎ ⧉ ↺…), care pe unele telefoane apăreau ca pătrățele, au fost înlocuite cu emoji obișnuite.

### Nou în 2.0: captura jocului lângă cod
În editorul de cod, sus stă o **captură a jocului**, iar jos codul. Le vezi pe amândouă deodată și alegi totul pe poză, fără să scrii pixeli de mână.
- **Capturi:**
  - **+ Captură** → 📷 din joc (Android 11+) sau 🖼 un screenshot din galerie (orice Android);
  - din panou: ≡ → **📷 Captură pentru codul „…”** salvează mai multe la rând, fără să ieși din joc;
  - câte o captură pe ecran de joc („meniu”, „luptă”…): ține apăsat pe una ca s-o redenumești sau ștergi. Maxim 12 pe script, intră în backup.
- **Instrumente pe poză** (cu lupă, ca să nimerești pixelul): 👆 Punct, ↗ Swipe, ▭ Zonă, 🎨 Culoare, ✂ Imagine.
  - Ridici degetul și codul se scrie la cursor.
  - După `=`, `(` sau `,` se scrie doar valoarea (`point(540, 1210)`); altfel o linie întreagă (`tap(point(540, 1210));`, `rect zona1 = rect(…);`, `if (aproape(pixel(…), #34A853, 24)) { }`).
- **Codul și poza sunt legate în ambele sensuri:**
  - fiecare `point`, `tap(x, y)`, `rect`, `swipe` și `pixel` din cod apare ca marcaj, cu numele variabilei;
  - cursorul pe o linie aprinde marcajul ei, iar un marcaj atins mută cursorul la linia lui;
  - **tragi marcajul → cifrele din cod se schimbă singure** (la zone tragi colțurile sau tot dreptunghiul, la swipe capetele sau mijlocul).
- **Ce arată fiecare marcaj:**
  - o zonă arată ce citește chiar acolo;
  - o culoare arată ✓ sau „✗ aici e #…”, iar pe poză se aprind pixelii care s-ar potrivi cu toleranța scrisă;
  - imaginile din `gaseste` / `apare` sunt încadrate unde sunt găsite;
  - pașii din `pasi` apar ca `pasi[0]`, `pasi[1]`…
- **Scalarea:**
  - în cod ajung mereu **coordonatele ecranului real**, nu ale pozei micșorate: ce pui pe poză nimerește același loc în joc;
  - o captură de pe alt telefon se scalează la ecranul scriptului, cu avertisment dacă are alte proporții;
  - un script rulat pe alt telefon își scalează singur atingerile, culorile și zonele.
- **▶ Simulează pe captură:** scriptul rulează fără să atingă nimic, pe ceas simulat, cu poza pe post de ecran.
  - Atingerile apar numerotate, swipe-urile ca săgeți.
  - Glisorul te duce prin ele, cu timpul, linia și ce scrie consola. Atingi textul ca să sari la linie.
  - Captura nu se schimbă, deci vezi prima trecere.
- **👁 Marcaje ▾**: 👁 arată sau ascunde tot; ▾ alegi exact ce vezi (atingeri, swipe-uri, zone, culori, imagini, pașii din listă, simularea).
- **🖼** din bara de sus ascunde captura: **doar cod**. Bara dintre poză și cod se trage ca să împarți ecranul. Când apare tastatura, poza se micșorează singură.
- Butoanele „+ 📱” aleg în continuare pe ecranul real, pentru când n-ai o captură.

### Nou în 1.9: modul Cod (scripturi în stil C++)
Pe ecranul principal, cardul **Cod (scripturi)** (sau ☰ → Cod) deschide lista de scripturi. **+** creează unul nou. La deschidere pornește un **tutorial „atinge ca să continui”**, care îți arată fiecare parte a editorului. În timpul lui nu e niciun buton, ca să nu-l închizi din greșeală. La final alegi **Închide** (îl vezi din nou data viitoare) sau **Nu mai afișa**. Înapoi îl închide oricând. Îl găsești și în ⋮ → Tutorial.
- **Limbajul** seamănă cu C++:
  - tipuri `int`, `double`, `bool`, `string`, `color` (`#34A853`), `point`, `rect`, `pair<A, B>`, `vector<T>` (dinamic), `struct`, `auto`, `const`;
  - funcții cu tip (`void`, `bool`, `int`, `point`…), recursivitate, parametri prin referință `T&`;
  - `if` / `else`, `while`, `do`, `for`, `for (auto& x : v)`, `break` / `continue`;
  - `print(...)` sau `cout << ... << endl`.
  - Durate scurte: `wait(2s)`, `1min`, `500ms`. Merg și `daca` / `altfel` / `cat_timp`.
  - `point` și `rect` vin direct din pipetă și din alegerea de zonă; `color` are `.r .g .b .hex()`; `pair` are `.first .second`.
- **Atingeri din cod**: `tap(p)`, `apasaLung`, `dubluTap`, `swipe(a, b, ms)`, `trage`, `back()`, `home()`, `recente()`, `deschide("pachet")`.
- **Ecranul**: `pixel(p)`, `aproape(c1, c2, toleranță)`, `citeste(zona)` (număr), `citesteText(zona)`, `apare("imagine")`, `gaseste(...)` → point, `gasesteToate(...)` → `vector<point>`, `asteaptaImagine`, `asteaptaCuloare`.
- **Lista de pași a clickerului e în cod** ca `pasi` (`vector<Pas>`):
  - o rulezi toată sau doar un pas: `ruleaza(pasi)`, `ruleaza(pasi[2], 5)`;
  - îi schimbi câmpurile: `pasi[0].x += 50; pasi[1].activ = false;`;
  - adaugi pași noi: `pasi.push_back(pasTap(p))`;
  - iei pașii altei salvări: `pasiDin("Farm")`; rulezi o salvare: `ruleazaMacro("Farm", 3)`.
  - Lista o editezi ca în modul manual (⋮ → Lista de pași), iar un script nou pornește cu țintele din panou.
- **Evenimente** (pop-up-uri): `cand (apare("Reclama X")) { … }`, `la_fiecare(30s) { … }`, `la_ora("22:00") { … }`. Rulează cât scriptul așteaptă.
- **`pastreaza int total = 0;`**: variabila își păstrează valoarea între rulări.
- **Editorul**:
  - numere de linie și cod colorat (culorile `#RRGGBB` apar chiar în culoarea lor);
  - greșelile apar pe loc, cu linia și o sugestie („Linia 7: nu știu ce e «tpa». Ai vrut «tap»?”); atingi mesajul ca să sari la linie;
  - indentare automată.
  - **Butoanele „+”** scriu cod din ce alegi pe ecran:
    - + Atingere → `tap(point(x, y));`;
    - + Swipe;
    - + Culoare → `aproape(pixel(...), #RRGGBB, 24)`;
    - + Zonă → `rect(...)`;
    - + Imagine (decupezi și îi dai un nume) → `gaseste("nume")`;
    - + Pas din listă;
    - plus șabloane: `if`, `while`, `for`, funcție, `struct`, `cand`.
- **Depanare**:
  - atingi numărul unei linii ca să pui ● o oprire;
  - **👣 Pas cu pas** arată sus linia curentă și variabilele, cu ⏭ Pas / ▶ Continuă / ■ Stop.
  - Panoul are ■ și ❚❚ ca la orice rulare; atingerea ecranului pune pauză, ca la macro-uri.
  - O eroare în timpul rulării te duce înapoi în editor, pe linia ei.
- **Siguranță**:
  - o buclă `while (true)` fără `wait` nu blochează telefonul: e încetinită și primești un avertisment;
  - recursivitatea infinită și vectorii uriași se opresc cu mesaj clar;
  - ■ și volum jos de două ori opresc scriptul oricând.
- ⋮ → **Exemple**: 7 scripturi gata făcute (primul script, apasă când e verde, farm până la 1000 aur, lista de pași de 10 ori, pop-up-uri, struct/vector/pair, statistici păstrate). ⋮ → **Funcții** arată toate comenzile.

### Nou în 1.8: culori, citire din ecran, consolă, setări în meniuri
- **Setările stau în meniuri pliabile.** Sus vezi doar ce contează pentru pasul respectiv. Restul e în **⚙ Avansat**, împărțit pe arii: 📍 Locație, ⏱ Timp, 🔁 Repetare, 🎯 Precizie, 📝 Altele. Fiecare arie are în titlu un rezumat („📍 Locație · 540, 1200”), așa că rareori trebuie s-o deschizi. La fel arată setările modului, setările de redare și ☰ → Setări. Aplicația ține minte ce meniuri ai lăsat deschise.
- **Pipetă de culoare** (Android 11+):
  - ecranul îngheață, iar o lupă puternică arată pixelii;
  - vezi live `#HEX · RGB · HSV`;
  - ≡ → **Pipetă: află o culoare** copiază codul și îl scrie în consolă;
  - + → **Așteaptă o culoare** o folosește pentru pas.
- **Culoare exactă:** atingi culoarea unui pas și ai:
  - câmp **hex**;
  - glisoare și câmpuri **R/G/B** și **H/S/V**, sincronizate între ele;
  - **toleranța**;
  - „🎯 De pe ecran”.
- **+ → Citește număr / text din zonă:**
  - tragi un dreptunghi peste un număr sau un nume și vezi live ce se citește;
  - modul **Număr** înțelege `12 345`, `1,234`, `1.5k`, `2.3M`, `45%`, `-7`;
  - modul **Text** citește nume și mesaje;
  - rezultatul intră într-o **variabilă** (implicit `numar1`).
  - Citirea se face **în telefon, fără internet și fără Google**: compară literele cu fonturile telefonului. Se confirmă pe 2 cadre la rând, ca un număr care încă se schimbă să nu dea valori greșite. Dacă nu reușește, variabila rămâne cum era.
- **🔍 Testează citirea:**
  - arată zona mărită, cu o căsuță pe fiecare literă și ce a citit sub ea;
  - dacă e greșit, scrii textul corect și apeși **ÎNVAȚĂ**: literele fontului acela (de exemplu cifre de joc) sunt ținute minte;
  - ☰ → Setări → 📖 Citire text arată literele învățate; ele intră și în backup.
- **+ → Citește culoarea unui punct** pune `#RRGGBB` într-o variabilă.
- **+ → Setează o variabilă (calcule):** `n + 1`, `aur - 100`, `random(1, 6)`, `max(a, b)`, `aur >= 1000`, `nume + "!"`. Se verifică pe loc, cu mesaj de eroare în română.
- **+ → Afișează în consolă:** text cu variabile între acolade, `Aur: {aur}`, chiar și calcule, `{aur * 2}`. Incluse: `{tura}`, `{timp}`, `{ora}`.
- **Consola** (≡ → Consolă):
  - fereastră plutitoare: o tragi de titlu, o strângi într-o bară ▁, copiezi ⧉, golești 🗑;
  - 👣 arată și fiecare pas;
  - în rulare lasă atingerile să treacă prin ea și nu intră în căutarea de imagini;
  - se deschide singură la primul mesaj al unei rulări.

### Nou în 1.7: clic pe imagine
- **+ → Imagine: apasă când apare** (Android 11+, fără dialogul „Share your screen”). Ecranul îngheață, tragi un dreptunghi în jurul butonului; marginile se **lipesc singure de buton** dacă fundalul din jur e simplu („magnet”); o lupă arată pixelii de sub deget. Marcajul are un chenar punctat și o cruce: crucea e locul unde apasă și o poți trage.
- Pentru fiecare imagine alegi: **ce face** (apasă / apasă lung / doar așteaptă / așteaptă să dispară), **dacă nu e pe ecran** (mergi mai departe — implicit / așteaptă max X / așteaptă, altfel oprește), **precizia** (Strictă / Normală / Tolerantă — acceptă și mărimi ușor diferite), **unde caută** (tot ecranul / în jurul locului decupat / jumătatea de sus sau de jos), **una sau toate** copiile, **oprește după N găsiri**.
- **🔍 Testează acum**: caută pe ecranul de acum și desenează rezultatul („Găsit ✓ 96%” sau „Nu apare acum · cel mai apropiat 71%”). **✂ Recapturează** păstrează opțiunile.
- În **înregistrare**, butonul 🖼 de jos adaugă „așteaptă aici imaginea”; timpul petrecut decupând nu intră în înregistrare.
- Din **editor**: Adaugă → Imagine (editorul se dă la o parte, deschizi ecranul dorit, apeși ✂ DECUPEAZĂ).
- În rulare, panoul se strânge la ■ ❚❚ și arată „🔍 Collect · 3 s”; la final: „„Collect” găsită de 47 ori”.
- Căutarea: model de luminozitate (rezistent la luminozitate/contrast), apoi **margini** (deosebește „Collect” de „Collected”) și **culoare** (un buton gri nu trece drept cel verde); apasă abia după ce imaginea apare pe **2 cadre la rând** (nu apasă pe ceva care tocmai dispare).
- Pozele stau în aplicație (`files/images`), se includ în **export/trimitere** și în **backup** (acum `.zip`; backup-urile vechi `.json` se restaurează în continuare). Pozele nefolosite se șterg singure, niciodată dacă vreo salvare nu poate fi citită.
- Ecrane protejate (bănci, unele jocuri): mesaj clar „ecran protejat”, nu „nu am găsit”.

### Nou în 1.6
- **Anulează / Refă**: panou ≡ → „Anulează: …” / „Refă: …”; în editor ↶ ↷. După „−” sau „Șterge tot” apare 5 s o bară „Șters · ANULEAZĂ” (nu mai întreabă „Ești sigur?”).
- **Previzualizare fără atingeri**: ≡ → Previzualizează (sau 👁 în editor). Degete „fantomă” arată unde, în ce ordine și cât de repede ar atinge; nimic nu se apasă de-adevăratelea. ⏭ sare peste o așteptare.
- **Pauză aleatoare**: pas „Pauză” cu „aleatoare între X și Y”.
- **Deschide o aplicație** în mijlocul macro-ului (așteaptă să apară, apoi continuă).
- **Unelte pentru înregistrări** (editor → Unelte): scurtează pauza de început, scurtează pauzele lungi, tot macro-ul ×2; ține apăsat pe rânduri ca să selectezi mai mulți pași (×2, dezactivează, duplică, șterge); ⋮ pe un rând → **Rulează de aici**.
- **Controale în notificare**: pornește / pauză / stop și ascunde panoul din bara de notificări.
- **Pauză când atingi ecranul**: dacă atingi ecranul sau tragi bara de notificări în timpul redării, macro-ul intră în pauză (atingerile nu mai pot nimeri butoanele sistemului); ▶ reia de unde a rămas.
- **Ecranul nu se mai stinge** cât rulează un macro (important la așteptări lungi după o culoare).
- **Limita de timp** oprește exact la timp, chiar și în mijlocul unei așteptări.

### Protecție la tapjacking și siguranță
- Toate ferestrele aplicației sunt de tip **overlay de accesibilitate**. Android le consideră de încredere: nu cer permisiunea „afișare peste alte aplicații”, iar regula anti-tapjacking din Android 12+ nu blochează atingerile.
- În timpul redării, țintele **nu prind niciun gest**. Dacă un gest cade peste panou, panoul devine temporar „transparent la atingere”.
- **Mod sigur** (în setările macro-ului): panoul se ascunde exact cât durează fiecare gest. E pentru aplicațiile care refuză atingeri când ceva e desenat peste ele (bănci, dialoguri de permisiuni, unele jocuri).
- Ecranele FreeClick resping atingerile venite prin ferestre străine puse deasupra lor (`filterTouchesWhenObscured`), deci nimeni nu te poate păcăli să apeși pe ceva în aplicație.
- Scurtăturile conțin un **cod secret unic per instalare**. O altă aplicație nu poate porni macro-uri imitând o scurtătură.
- Integrarea Tasker e dezactivată implicit. ID-urile periculoase din fișierele importate sunt înlocuite automat.
- Serviciul **nu citește conținutul ferestrelor** (textul din aplicații). Culorile, imaginile și numerele se citesc doar din captura de ecran, doar în zonele alese de tine, în telefon, fără internet. Captura stă doar în memorie și se oprește când închizi panoul.

## Datele tale la actualizări
- Salvările, ciornele „Nesalvat” ale fiecărui mod, setările și programările **rămân** când instalezi o versiune nouă peste cea veche. Programările se reactivează automat după actualizare și după repornirea telefonului.
- Asta merge doar dacă toate versiunile sunt semnate cu **aceeași cheie** (`keystore/`). `build.sh` refuză să construiască dacă cheia s-ar schimba.
- **Nu dezinstala** înainte de actualizare: dezinstalarea șterge datele. Pentru siguranță: **☰ → Setări → Date și backup → Exportă backup complet**. Restaurarea readuce totul fără duplicate.
- Fișierele salvate de versiunile vechi se citesc în continuare (există un test automat pentru formatul din versiunea 1.0, iar macro-uri reale făcute cu 1.5 sunt păstrate în teste și trebuie să ruleze identic).
- Un fișier făcut cu o versiune **mai nouă** decât cea instalată e refuzat cu mesajul „Actualizează aplicația”, în loc să fie citit pe jumătate și stricat la salvare.

## Limitări (impuse de Android)
- La înregistrare, aplicația primește gestul **după ce ridici degetul** (la fel ca la Clickmate), fiindcă stratul de înregistrare îl prinde întâi. La redare însă, **pauza inițială și pauzele dintre atingeri sunt reproduse exact**: motorul urmează cronologia înregistrării și compensează întârzierea cu care telefonul confirmă fiecare gest, ca să nu se adune decalaje.
- Un gest poate avea maximum ~20 de degete și 60 de secunde. Gesturile mai lungi sunt comprimate automat.
- Dialogul „Share your screen” apare **doar când pornești o filmare video**. Android îl impune pentru orice înregistrare video a ecranului, iar pe Android 14+ îl cere de fiecare dată. Pe Android 10 sau mai vechi apare și pentru condițiile de culoare.
- Macro-urile nu pot atinge ecranul de blocare.
- Unele jocuri detectează automatizarea și o pot sancționa. Citește regulile jocului.

## Listă de verificare pe telefon
1. Activează serviciul și verifică dacă apare „● Serviciul este activ”.
2. **Ținte manuale** → Stare ON → **+ → Adaugă tap** → mută ținta peste un buton → **▶**: butonul e apăsat la infinit până la **■**.
3. **+ → Adaugă swipe** → pune S1/E1 într-o listă → **▶**: lista defilează.
4. **≡ → Opțiuni** → Interval de repetare 1 s → toate țintele se rar; **Salvează ca** → apare în **Încarcă**.
5. **Mod înregistrare** → **●** → apasă câteva butoane și fă un swipe → **■** → **▶**: se repetă în același ritm.
6. În redare: **1×** → alege 2× (merge mai repede pe loc), **❚❚** pune pauză, **volum jos ×2** oprește tot.
7. **+ → Adaugă condiție de culoare** → atinge un punct (fără dialog pe Android 11+) → **▶**: așteaptă culoarea.
8. **≡ → Filmează ecranul** → oprește din notificare → verifică **☰ → Filmări**.
9. **Combină** → ≡ → Combinații → **+** → adaugă două înregistrări → **▶** le rulează pe rând.
10. În listă: **⋮ → Creează scurtătură** → apasă scurtătura de pe ecranul principal → rulează.

Noutăți 1.6:
11. **−** pe o țintă → apare „Șters · ANULEAZĂ” → apasă ANULEAZĂ: ținta revine. **Ține apăsat −** → se șterge tot fără întrebare → ANULEAZĂ readuce tot.
12. **≡ → Previzualizează**: apar degete „fantomă”, aplicația de dedesubt NU reacționează; la final „Previzualizare gata”.
13. În redare, **trage bara de notificări** → macro-ul intră în pauză („s-a deschis bara de notificări”); **atinge ecranul** în timpul unei glisări lungi → pauză („ai atins ecranul”); **▶** reia.
14. Pune o condiție de culoare cu timp maxim 60 s, cu ecranul setat să se stingă după 30 s → ecranul rămâne aprins.
15. Notificarea FreeClick: **Pauză / Continuă / Stop / Ascunde panoul** funcționează; închide-o cu swipe → **≡ → Arată notificarea** o readuce.
16. Editor: pas **Deschide o aplicație** → alege o aplicație → ▶ o deschide și continuă; **Unelte → Scurtează pauzele lungi**; **⋮ → Rulează de aici**; **↶** anulează.
17. Pauză **aleatoare** 1–3 s între două atingeri → în Jurnal, intervalele diferă.

Noutăți 1.7:
18. **+ → Imagine** pe un buton dintr-o aplicație → dreptunghiul se lipește de buton → ✓ → dă-i un nume → **🔍 Testează acum** arată chenar verde „Găsit”.
19. ▶ cu butonul pe ecran → îl apasă; fără el → merge mai departe (implicit). Cu „Așteaptă până apare” → panoul arată „🔍 nume · N s”.
20. Mută butonul (derulează) → e găsit în noul loc. Trage crucea marcajului → apasă în noul punct.
21. O aplicație bancară → „ecran protejat”.
22. În înregistrare → 🖼 → decupează → continuă → ▶: macro-ul așteaptă imaginea acolo.
23. Backup → restaurează pe alt telefon / după reinstalare → pașii cu imagini merg.

Noutăți 1.8:
24. Atinge o țintă → doar „Tip” e vizibil; **⚙ Avansat** → 📍 Locație / ⏱ Timp au rezumat în titlu; închide și redeschide: meniurile rămân cum le-ai lăsat.
25. **≡ → Pipetă: află o culoare** → lupa arată pixelii, hint-ul se colorează cu culoarea aleasă → ✓ → codul e copiat și apare în consolă.
26. **+ → Așteaptă o culoare** → alegi punctul → atingi culoarea din formular → schimbi hex / R / H → glisoarele se mișcă împreună.
27. **+ → Citește număr / text din zonă** peste un număr dintr-un joc → hint-ul arată ce citește → ✓ → **🔍 Testează citirea** → dacă e greșit, scrie corect → ÎNVAȚĂ → testează din nou.
28. **+ → Afișează în consolă** cu `{numar1}` → ▶ → consola se deschide singură și arată valoarea; în rulare atingerile trec prin consolă.
29. **+ → Setează o variabilă** `n + 1` + Afișează `{n}` → numără turele.
30. Backup → restaurare: literele învățate revin (mesajul spune câte).

Noutăți 1.9 (modul Cod):
31. Ecranul principal → **Cod (scripturi)** → **+** → editorul se deschide și pornește tutorialul. Atingi de 7 ori → ultima bulă are **Închide** / **Nu mai afișa** (atingerile în afara lor nu o închid). Închide → ieși și intri din nou în script → tutorialul revine; Nu mai afișa → nu mai revine, dar ⋮ → Tutorial îl pornește. Rotirea ecranului nu îl repornește.
32. Scrie `tpa(1, 2);` → bara de sus devine galbenă: „Linia N: … Ai vrut «tap»?” → atingi mesajul → cursorul sare pe linie, iar linia e marcată cu roșu.
33. Enter după `{` → rândul nou e indentat; `}` la începutul rândului se mută înapoi.
34. **+ Atingere** → bara „Deschide ecranul potrivit” → 🎯 → alegi punctul → editorul revine cu `tap(point(x, y));` scris la cursor. La fel **+ Swipe**, **+ Culoare**, **+ Zonă**, **+ Imagine** (cere un nume).
35. ⋮ → Exemple → „Începe aici” → ÎNLOCUIEȘTE → ▶ → editorul trece în spate, panoul arată 📜, consola afișează „Atingerea 1…3”, apoi editorul revine.
36. Atinge numărul liniei 8 → apare ● → ▶ → scriptul se oprește acolo cu bara de sus (linia + variabile) → ⏭ Pas → următoarea linie → ▶ Continuă.
37. **👣 Pas cu pas** → fiecare ⏭ execută o linie.
38. Exemplul „Pop-up-uri” cu o imagine numită „Reclama X” → pornești → când apare reclama, e închisă și scrie în consolă.
39. `while (true) { }` fără wait → telefonul rămâne fluid, consola avertizează, ■ oprește.
40. Exemplul „Statistici păstrate” rulat de 2 ori → al doilea raport continuă numărătoarea.
41. ⋮ → Lista de pași → adaugi o țintă → înapoi → `ruleaza(pasi);` o folosește. Script nou cu ținte în panou → `pasi.size()` le numără.
42. Actualizare peste 1.8 → toate salvările, imaginile și literele învățate sunt acolo.

Noutăți 2.0 (captura lângă cod):
43. Editor → **+ Captură → 📷 Fă o captură din joc** → bara „Deschide ecranul potrivit” → deschizi jocul → 📷 → editorul revine cu poza sus (fără panoul FreeClick pe ea).
44. **+ Captură → 🖼 Alege din galerie** un screenshot normal → apare; unul de pe alt telefon / tăiat → mesaj despre proporții.
45. 👆 Punct → atingi un buton pe poză → lupa arată pixelul → ridici → `tap(point(x, y));`. ▶ → în joc atinge exact butonul.
46. Tragi marcajul → cifrele din linie se schimbă în timp ce tragi; ridici → cursorul e pe linia lui.
47. ▭ Zonă peste aur → `rect zona1 = …`; cursorul pe linie → sub dreptunghi „citește: …”.
48. 🎨 Culoare pe un buton verde → `if (aproape(…))`; cursorul pe linie → pixelii potriviți se aprind pe poză.
49. ✂ Imagine → nume → `if (apare("…")) tap(gaseste("…"));` → poza o încadrează.
50. ▶ Simulează pe exemplul „Începe aici” → bulinele 1, 2, 3; glisorul arată timpul și linia; un `while (true)` fără wait → „m-am oprit…”.
51. 👁 → toate dispar; ▾ → doar „Swipe-uri” → se văd doar swipe-urile.
52. 🖼 → doar cod; trag bara dintre ele → rămâne așa după repornire; tastatura deschisă → poza se micșorează.
53. ≡ în panou → „📷 Captură pentru codul …” de 3 ori → editorul are 3 capturi noi.
54. Backup → restaurare → scriptul își are capturile.

Noutăți 2.0.1:
55. Editor → atingi codul → tastatura apare → captura, ▶ / 👣 și panoul plutitor dispar, se văd multe linii de cod → închizi tastatura → revin.
56. Script nou → primele linii sunt DURATA_CLICK / DURATA_SWIPE / INTERVAL_REPETARE cu valorile din Opțiuni. `tap(540, 1200); tap(540, 1200);` → între atingeri trece INTERVAL_REPETARE.
57. Scrii `tap(` → deasupra tastaturii „void tap(point p, …)” cu `point p` îngroșat; după `, ` se îngroașă `int durata`. La o funcție a ta: semnătura ei.
58. ▶ Rulează → editorul trece în spate, panoul arată 📜 → deschizi jocul → ▶ pe panou → scriptul pornește acolo. La final ▶ îl rulează din nou; ≡ → Codul → editorul.
59. „+ 📱 Atingere” cu fontul sistemului mărit → bara are × vizibil; × → înapoi în editor. Dacă revii în editor din „Recente”, bara dispare.
60. ≡ pe panou → 3 rânduri + „Avansat ›” → restul.
61. Caută pătrățele în locul simbolurilor (consolă, editorul de pași, bara de depanare).

Noutăți 2.1:
62. `switch` cu `case` / `default` și fall-through → rezultat ca în C++.
63. Scrii `ta` → bara de sugestii arată `tap`, `tapInZona`…; atingi → `tap(`. După `v.` → `push_back`, `size`…
64. `asteaptaSchimbare(rect(…))` peste scor → `true` când se schimbă scorul. `asteaptaLiniste()` după deschiderea unui meniu.
65. 🔢 Model → 3 pixeli → „✅ Scrie modelul” → `if (model({…}, {…}, 24))`; marcajele arată ✓ sau ✗.
66. 🎲 Tap în zonă → `tapInZona(rect(…));` → ▶ atinge de fiecare dată în alt loc din zonă.
67. `salveazaCaptura("test")` → poza apare în Galerie → FreeClick. `copiaza("abc")` → lipești „abc”. `deschideLink("https://…")`.
68. + pe panou → Adaugă zoom → Zoom în → muți marcajul → ▶ → harta / poza se mărește.
69. Lista de pași → + Adaugă → „Alt macro sau 📜 script” → un script → ▶ → pașii de după așteaptă scriptul; ■ în timpul scriptului oprește tot.
70. Bibliotecă → ⋮ → Avansat → ⚡ Pornire automată → un joc → „Pornește singur” → deschizi jocul → în 3 s pornește; ■ înainte → anulat.
71. Opțiuni → „Rulează doar în aplicația” → ▶ → ieși din aplicație → pauză → revii → reia singur.
72. Editorul unei înregistrări → Cronologie → tragi un bloc → pașii de după se mută; Anulează îl readuce.
73. Prima deschidere a aplicației / a panoului / a editorului de pași → tutorialul; „Nu mai afișa” → nu mai apare; ☰ → Tutorial îl repornește.
74. Panou → 🔥 → bara portocalie sus → atingi un buton din joc → 10 atingeri rapide acolo; bara arată „…/s”.
75. Turbo: ții degetul ~½ s pe un loc și ridici → atinge non-stop; atingi oriunde → se oprește.
76. Turbo: un swipe → pagina se derulează normal; × sus → Turbo se închide, panoul revine.
77. Setări → 🔥 Turbo → rafală 0 → atingerile scurte trec normal, ținut apăsat = non-stop.
78. Turbo pornit → volum jos de 2 ori → Turbo se oprește.
97. Setări → 🧪 Laborator → Pornește → în ~1 minut apar 6 rânduri cu ✅/⚠ și procente; „Copiază rezultatele” → text cu telefonul și cifrele.
98. `print(asteaptaGest());` → faci zoom cu două degete → „zoom mareste”; rotești → „rotire dreapta”.
99. `int b = buton("■", rect(20, 300, 200, 420)); while (!apasat(b)) tap(540, 1200, 30, 300);` → butonul apare, jocul nu primește apăsarea lui, scriptul se oprește.
100. `ascultaInZona(rect(0, 0, 300, 300)); print(asteaptaGest());` → gesturile din restul ecranului merg normal în joc.
101. `int n = intreabaNumar("Câte?", 5);` → apare întrebarea peste joc; `timeout (3s) { while (true) wait(100); } else print("ok");` → „ok” după 3 s.
92. Script care așteaptă `asteaptaAtingere()` → pe panou apare 👂 până atingi; după, dispare.
93. `print(procentCuloare(rect(…bara de viață…), #culoarea ei));` → valoarea se potrivește cu bara din joc la câteva procente.
94. Bibliotecă → o înregistrare → ⋮ → Avansat → 💻 Fă script din ea → ▶ pe panou → face exact ce făcea înregistrarea.
95. `vector<Pas> m = inregistreaza(); reda(m, 3);` → faci câteva gesturi, stai 3 s → le repetă de 3 ori, la fel.
96. `afiseazaPeEcran("Runda 1");` apare sus și nu blochează atingerile; `notifica("test")` apare în notificări.
85. Cod: `point unde = asteaptaAtingere(); tap(unde);` → ▶ pe panou → atingi un loc în joc → FreeClick apasă exact acolo (și atingerea ta ajunge în joc).
86. Cod: `asculta(true); while (degete() < 2) tap(540, 1200, 20, 80);` → pui două degete → se oprește. Fără `asculta(true)` → eroare clară.
87. Setări → 👂 → Testează (Android 15+) → atingi pătratul de 3 ori → ✅ sau rămâne stratul. Fără script care ascultă: nimic în cale.
88. Script nou → începe cu `#define p point` → `tap(p(540, 1200));` merge și apare pe captură; `pipeta()` → eroare cu sugestia `pipetaAtingere()`.
89. ⋮ → 📖 Documentație → caut „wait_for” → pagina asteaptaAtingere → „Pune în cod”; 📖 pe bară cu cursorul pe `tap` → pagina tap.
90. Bibliotecă → ⋮ → Avansat → 📤 → Copiază codul → îl lipești în chat → îl copiezi înapoi → Biblioteca arată „Ai copiat un cod” → Importă.
91. Opțiuni → 🖐 → „Atinge țintele deodată” → ▶ → țintele tap se ating simultan; panoul arată „…/s”.
80. 🌙 ▶ un macro într-un joc → notificarea „🌙 AFK” cu timpul; ecranul nu se stinge nici cu panoul ascuns.
81. 🌙 În timpul rulării deschizi altă aplicație → pauză; revii în 20 s → continuă.
82. 🌙 Închizi jocul din Recente → după 20 s „Îl redeschid în 10 s” → jocul se deschide și macro-ul o ia de la capăt; Anulează → așteaptă.
83. 🌙 Blochezi telefonul → pauză; deblochezi → continuă.
84. Setări → 🌙 → „Nu lăsa Android…” → dialogul sistemului → apoi scrie ✅.
79. Bibliotecă: rânduri cu iconiță, „azi, …” / „ieri, …”; editor cod: „+ 📱 Din ecran ▾” deschide lista.

## Compilare din sursă
Proiectul nu folosește Gradle sau AndroidX: doar uneltele SDK de bază.
```
# Ubuntu: apt install aapt dalvik-exchange apksigner zipalign
ANDROID_JAR=/cale/android-34.jar ./build.sh   # → build/apk/FreeClick.apk
ANDROID_JAR=/cale/android-34.jar ./test.sh    # 195 de teste automate + fuzzing + 193 de verificări statice
```
Cheia de semnare e în `keystore/` (parola `freeclick`). Păstreaz-o ca actualizările să se instaleze peste versiunea veche.

## Cum e testată
- `test.sh` rulează:
  - **195 de teste** (187 pentru logică, 8 pentru partea de aplicație), inclusiv sesiuni complete (înregistrezi → salvezi → repornești/actualizezi → încarci → redai cu întârzieri de telefon simulate);
  - **fuzzing**: mii de macro-uri, fluxuri de atingeri și sesiuni de redare generate aleator, cu pauză, viteză, stop, eșecuri, sub-macro-uri ciclice și condiții de culoare. Verifică de fiecare dată regulile impuse de Android (puncte pe ecran, durate valide, maximum 20 de degete și 60 s, niciodată două gesturi deodată);
  - **trasee „de aur”**: macro-uri făcute cu 1.5 (toate tipurile de pași, viteze, pauze, combinații, culori) sunt redate de motorul nou și comparate pas cu pas, la milisecundă, cu ce făcea 1.5;
  - **căutarea de imagini pe ecrane desenate realist** (text netezit, umbre, gradiente, JPEG, mărimi diferite, mod întunecat): butonul e găsit pe 12 ecrane diferite (similaritate minimă 91%), lipsa lui nu dă fals pozitiv (maxim 56%), geamănul gri și „Collected” sunt respinse; bugete de lucru de max 19 M operații pe căutare;
  - **teste pentru limbajul de scripturi**: tipuri și conversii, funcții, recursivitate, referințe, struct / pair / vector, mesaje de eroare cu linia și sugestii, evenimente, pauză / pas cu pas / opriri, pași rulați prin motorul normal, variabile păstrate, plus text aleator care nu trebuie să blocheze verificarea;
  - **teste pentru captura de lângă cod**: găsirea locurilor din cod (și în cod greșit), rescrierea exactă a cifrelor la tragere, scalarea între ecrane și orientări, simularea pe un ecran fix cu limitele ei;
  - **193 de verificări statice** ale părții Android: manifest, componente expuse, intenții, PendingIntent, resurse, versiuni de API, pași parcurși doar prin `ActionTree`, capturi procesate în afara firului principal.
- Alte semințe pentru fuzzing: `FUZZ_SEEDS="1 2 3" ./test.sh`.
- Ce nu se poate verifica fără telefon (desenarea pe ecran, comportamentul exact al Android) e în lista de verificare de mai sus.

## Structură
- `src/com/freeclick/core/`: logica fără Android (model, JSON, înregistrare, planificare gesturi, motor de redare, condiții de culoare, citire de text). Testată pe JVM.
- `src/com/freeclick/core/script/`: limbajul modului Cod (lexer → parser → verificare de tipuri → interpretor pe firul lui), scris de mână, fără biblioteci.
- `src/com/freeclick/app/`: serviciul de accesibilitate, panoul, țintele, înregistrarea, filmarea și ecranele.
- `test/`: testele automate (ceas virtual pentru motorul de redare).
