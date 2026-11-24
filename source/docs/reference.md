# Începe aici
## Cum citești documentația
Fiecare funcție are o pagină: ce face, ce îi dai, ce îți întoarce și un exemplu pe care îl poți pune direct în cod.
Parametrii dintre [ ] se pot lăsa deoparte. „ms” înseamnă milisecunde (1000 ms = 1 secundă); poți scrie și 2s, 500ms sau 1min.
Numele subliniate duc la pagina lor. În editor: ⋮ → 📖 Documentație, butonul 📖 din bara de jos (pagina cuvântului pe care stă cursorul) sau atingi bara cu parametrii care apare cât scrii un apel.
Multe funcții au și alte nume (românești și englezești): `tap` = `atinge` = `click`. Pagina le arată sub „Se mai scrie”.
```cod
// un script întreg: atinge un buton de 3 ori, la o secundă distanță
point buton = point(540, 1200);
for (int i = 0; i < 3; i++) {
    tap(buton);
    wait(1s);
}
```
Vezi: [[Primul script]], [[Variabile și tipuri]], [[tap]], [[asteaptaAtingere]]

## Primul script
Un script e o listă de comenzi care rulează de sus în jos, ca un program C++ fără main().
▶ în editor pune scriptul pe panoul plutitor; deschizi jocul și apeși ▶ pe panou. 👣 rulează pas cu pas. Ce scrie [[print]] apare în consolă.
Coordonatele sunt pixeli ai ecranului (x de la stânga, y de sus). Nu trebuie să le scrii de mână: „+ 📱 Din ecran” alege punctul pe ecranul real, iar pe o captură a jocului le tragi cu degetul.
```cod
print("Pornesc la ", ora());
tap(540, 1200);
wait(500);
print("Gata");
```
Vezi: [[Captura jocului]], [[Setările clickului]], [[Exemple]]

## Exemple
⋮ → Exemple are scripturi întregi: apasă când un punct e verde, farm până la un număr, închide reclamele oricând apar, structuri și funcții, statistici păstrate între rulări, rafale unde atingi tu.
„Înlocuiește” pune exemplul în locul codului tău; „Adaugă la final” îl lipește dedesubt.
Vezi: [[Primul script]]

# Atingeri
## tap
Atinge ecranul o dată, într-un punct.
Fără durată și pauză scrise, folosește [[Setările clickului]] (DURATA_CLICK, INTERVAL_REPETARE) de sus din cod, altfel 50 ms apăsat și nicio pauză.
Dacă atingi tu ecranul chiar atunci, Android anulează atingerea; scriptul pune pauză (vezi [[Siguranță]]), afară de scripturile care ascultă degetele (vezi [[Ascultarea atingerilor]]).
```cod
tap(540, 1200);                 // x, y
tap(point(100, 200), 80, 500);  // apăsat 80 ms, apoi 500 ms pauză
```
Vezi: [[apasaLung]], [[dubluTap]], [[tapInZona]], [[swipe]]

## apasaLung
Ține degetul apăsat într-un punct (implicit 800 ms), ca la „apăsare lungă”.
```cod
apasaLung(point(300, 900));        // 800 ms
apasaLung(300, 900, 2000, 500);    // 2 secunde, apoi 500 ms pauză
```
Vezi: [[tap]], [[trage]]

## dubluTap
Două atingeri rapide în același loc (dublu-click).
```cod
dubluTap(point(540, 960));
```
Vezi: [[tap]], [[asteaptaDubluTap]]

## swipe
Trage degetul de la un punct la altul, pe durata dată (implicit DURATA_SWIPE).
```cod
swipe(point(540, 1600), point(540, 600));        // în sus
swipe(100, 1000, 900, 1000, 300, 200);           // la dreapta, 300 ms, apoi 200 ms pauză
```
Vezi: [[trage]], [[asteaptaSwipe]], [[alegeZona]]

## swipeCurb
Un swipe pe o curbă, care pornește și se oprește încet, ca un deget (curbura: 0 = drept, 0,25 implicit, negativ = în partea cealaltă).
```cod
swipeCurb(point(200, 1600), point(900, 600));
swipeCurb(point(900, 1600), point(200, 600), 500, -0.3);
```
Vezi: [[swipe]], [[trage]]

## trage
Apasă, ține, apoi mută degetul și îl ridică: pentru drag & drop (de ex. muți un obiect în joc).
Primește punctul de plecare, punctul de sosire, cât durează mișcarea (implicit 400 ms) și cât ține înainte (implicit 500 ms).
```cod
trage(point(200, 1500), point(800, 600), 600, 400);
```
Vezi: [[swipe]], [[apasaLung]]

## atingeDeodata
Atinge mai multe puncte în același moment, ca mai multe degete (cel mult 10). Durata și pauza ca la [[tap]].
În modul Ținte, același lucru e o setare: Opțiuni → ⚙ Avansat → 🖐 Atingeri → „Atinge țintele deodată”.
```cod
atingeDeodata({point(200, 1800), point(880, 1800)});           // stânga + dreapta deodată
atingeDeodata({point(100, 500), point(500, 500), point(900, 500)}, 40, 300);
```
Vezi: [[tap]], [[zoomIn]]

## tapInZona
Atinge un loc la întâmplare într-o zonă (departe de margini), alt loc la fiecare apel.
Bun ca atingerile să nu cadă mereu în același pixel.
```cod
rect buton = rect(400, 1100, 680, 1300);
for (int i = 0; i < 10; i++) tapInZona(buton, 60, 300);
```
Vezi: [[tap]], [[rect]]

## zoomIn
Zoom cu două degete care se depărtează în jurul unui centru (cât de mult, în px, și cât durează).
```cod
zoomIn(point(540, 1200), 500, 400);
```
Vezi: [[zoomOut]]

## zoomOut
Zoom cu două degete care se apropie.
```cod
zoomOut(point(540, 1200));
```
Vezi: [[zoomIn]]

## back
Apasă butonul Înapoi al telefonului.
```cod
back();
```
Vezi: [[home]], [[recente]]

## home
Du-te pe ecranul principal (Acasă). Cu [[Mod AFK]], ieșirea asta nu contează ca „jocul s-a închis”.
```cod
home();
```
Vezi: [[back]], [[deschide]]

## recente
Deschide lista aplicațiilor recente.
```cod
recente();
```
Vezi: [[home]]

## notificari
Trage în jos bara de notificări.
```cod
notificari();
wait(1s);
back();
```
Vezi: [[back]]

## deschide
Deschide o aplicație după numele pachetului; întoarce true dacă a putut.
Numele pachetului îl vezi în Opțiuni → 📱 Aplicație (lista arată și pachetul).
```cod
if (!deschide("com.android.chrome")) print("Nu e instalată");
wait(3s);
```
Vezi: [[deschideLink]], [[Mod AFK]]

## deschideLink
Deschide o pagină web sau un link de aplicație; întoarce true dacă s-a deschis ceva.
```cod
deschideLink("https://example.com");
```
Vezi: [[deschide]], [[copiaza]]

# Așteptări
## wait
Așteaptă atâtea milisecunde. Merge și wait(2s), wait(1min), wait(1h).
Cât așteaptă, evenimentele ([[Evenimente]]) pot rula.
```cod
wait(1500);
wait(2s);
```
Vezi: [[waitRandom]], [[asteaptaLiniste]]

## waitRandom
Așteaptă un timp la întâmplare între două valori (ms), ca să nu fie mereu la fel.
```cod
waitRandom(300, 900);
```
Vezi: [[wait]], [[random]]

## asteaptaImagine
Așteaptă până apare pe ecran o imagine decupată (după nume), cel mult atâtea ms; întoarce true dacă a apărut.
Imaginile le decupezi pe captură (unealta ✂) sau în lista de pași.
```cod
if (asteaptaImagine("buton_colecteaza", 10s)) tap(gaseste("buton_colecteaza"));
```
Vezi: [[apare]], [[gaseste]], [[gasesteToate]]

## asteaptaOricare
Așteaptă oricare din mai multe imagini și îți spune a câta a apărut (0 = prima din listă); -1 dacă a expirat.
Mai bine decât să le aștepți pe rând: răspunde la cea care vine prima.
```cod
int ce = asteaptaOricare({"victorie", "infrangere", "reclama_x"}, 30s);
switch (ce) {
    case 0: print("am câștigat"); break;
    case 1: print("am pierdut"); break;
    case 2: tap(gaseste("reclama_x")); break;
    default: print("nimic în 30 s");
}
```
Vezi: [[asteaptaImagine]], [[apare]]

## asteaptaCuloare
Așteaptă până punctul are culoarea dată (cu o toleranță, implicit 24), cel mult atâtea ms (implicit 30 s).
```cod
if (asteaptaCuloare(point(540, 300), #34A853, 5s)) print("e verde");
```
Vezi: [[pixel]], [[aproape]], [[model]]

## asteaptaLiniste
Așteaptă până nu se mai mișcă nimic în zonă (de ex. după o încărcare). Întoarce false dacă a trecut timpul maxim.
Implicit: tot ecranul, 800 ms de liniște, cel mult 10 s.
```cod
tap(540, 1800);              // „Joacă”
asteaptaLiniste();           // până se termină animația
asteaptaLiniste(rect(0, 0, 1080, 300), 1s, 20s);
```
Vezi: [[asteaptaSchimbare]], [[wait]]

## asteaptaSchimbare
Așteaptă până se schimbă ceva în zonă (scorul, un mesaj), cel mult atâtea ms (implicit 30 s).
```cod
rect scor = rect(800, 50, 1050, 120);
if (asteaptaSchimbare(scor, 1min)) print("scorul s-a schimbat");
```
Vezi: [[asteaptaLiniste]], [[citeste]]

# Ecranul
## pixel
Culoarea ecranului într-un punct (color). Se mai scrie `culoare` sau `pipeta`. Întoarce -1 dacă ecranul nu poate fi citit.
```cod
color c = pixel(540, 300);
print(c.hex());
if (aproape(c, #34A853, 30)) print("verde");
```
Vezi: [[pipeta]], [[aproape]], [[asteaptaCuloare]], [[color]]

## pipeta
Pipeta în cod: culoarea din punctul dat (exact ca [[pixel]]). Are nevoie de coordonate: pipeta() fără nimic e o greșeală.
Ca să aștepte atingerea ta și să ia culoarea de acolo, folosește [[pipetaAtingere]].
```cod
color c = pipeta(540, 300);
color d = pipeta(point(100, 200));
print(c.hex(), " ", d.hex());
```
Vezi: [[pixel]], [[pipetaAtingere]], [[aproape]]

## pipetaAtingere
Așteaptă să atingi ecranul și îți dă culoarea de unde ai atins (se mai scrie get_touch_pipeta). Cu ms_maxim: -1 dacă nu atingi la timp.
Atingerea ta ajunge și în joc (vezi [[Ascultarea atingerilor]]).
```cod
print("Atinge culoarea dorită");
color tinta = pipetaAtingere();
print("am ales ", tinta.hex());
while (!aproape(pixel(540, 300), tinta, 30)) wait(200);
tap(540, 300);
```
Vezi: [[pipeta]], [[asteaptaAtingere]]

## aproape
true dacă două culori sunt apropiate (toleranța implicită 24; 0 = identice).
```cod
if (aproape(pixel(100, 200), #FF0000, 40)) tap(100, 200);
```
Vezi: [[pixel]], [[model]]

## model
Verifică dintr-odată mai mulți pixeli: fiecare punct trebuie să aibă culoarea lui. Mai rapid și mai sigur decât o imagine.
Pe captură, unealta 🔢 Model îl scrie singură.
```cod
if (model({point(100, 200), point(140, 200), point(120, 260)}, {#FFFFFF, #FFFFFF, #E53935})) print("ecranul de victorie");
```
Vezi: [[aproape]], [[asteaptaCuloare]]

## procentCuloare
Cât de plină e o bară (viață, energie, progres), de la 0 la 100: partea din zonă care are culoarea dată. Pune zona strâns peste bară.
Merge pe bare orizontale (din stânga) și verticale (zonă mai înaltă decât lată); marginea barei și un text scris peste ea nu încurcă. Toleranța implicită: 30.
Măsurat pe 3000 de bare generate (degrade, zgomot, margini, text peste): 99,9% la cel mult 4 puncte de valoarea adevărată.
```cod
rect viata = rect(60, 140, 460, 160);
double plin = procentCuloare(viata, #E53935);
if (plin < 30) tap(980, 1900);   // poțiunea
print("viață: ", plin, "%");
```
Vezi: [[gasesteCuloare]], [[pixel]], [[asteaptaCuloare]]

## gasesteCuloare
Unde e o culoare în zonă: mijlocul primei pete (de sus în jos, de la stânga la dreapta); (-1, -1) dacă nu e. Toleranța implicită: 30.
Pixelii singuratici de aceeași nuanță nu contează: pata trebuie să aibă măcar ~15 px pe o zonă cât ecranul (pentru lucruri mici, fă zona mai mică).
Măsurat pe 1500 de ecrane generate: 100% în pată când există, 100% (-1, -1) când nu există.
```cod
point cufar = gasesteCuloare(ecran(), #FFD54F);
if (exista(cufar)) tap(cufar);
```
Vezi: [[procentCuloare]], [[gaseste]], [[exista]]

## citeste
Citește un număr dintr-o zonă (scor, aur, nivel). Înțelege 1.250, 1,5k, 2.3M. Întoarce -1 dacă nu poate.
Merge mai bine după ce îl înveți cifrele jocului (Setări → 📖 Citire text).
```cod
int aur = citeste(rect(820, 60, 1060, 120));
if (aur >= 1000) stop();
```
Vezi: [[citesteText]], [[asteaptaSchimbare]]

## citesteText
Citește textul dintr-o zonă (string).
```cod
string mesaj = citesteText(rect(100, 900, 980, 1000));
if (mesaj.contine("Victorie")) print("am câștigat");
```
Vezi: [[citeste]], [[Text (string)]]

## apare
true dacă imaginea (după nume) se vede acum pe ecran.
```cod
if (apare("reclama_x")) tap(gaseste("reclama_x"));
```
Vezi: [[gaseste]], [[asteaptaImagine]]

## gaseste
Unde e imaginea pe ecran (punctul de atins); (-1, -1) dacă nu e. Verifici cu [[exista]].
```cod
point poz = gaseste("cufar");
if (exista(poz)) tap(poz);
```
Vezi: [[gasesteToate]], [[apare]]

## gasesteToate
Toate locurile unde apare imaginea, de sus în jos (vector<point>).
```cod
vector<point> monede = gasesteToate("moneda");
for (auto m : monede) tap(m);
```
Vezi: [[gaseste]], [[vector]]

## exista
true dacă un punct e valid (nu e (-1, -1)), de ex. după [[gaseste]].
```cod
point poz = gaseste("buton");
print(exista(poz));
```
Vezi: [[gaseste]]

## latime
Lățimea ecranului în pixeli (a ecranului pentru care e scris scriptul).
```cod
tap(latime() / 2, inaltime() / 2);   // mijlocul ecranului
```
Vezi: [[inaltime]], [[ecran]]

## inaltime
Înălțimea ecranului în pixeli.
```cod
print(latime(), " x ", inaltime());
```
Vezi: [[latime]], [[ecran]]

## ecran
Tot ecranul ca dreptunghi (rect).
```cod
asteaptaLiniste(ecran(), 1s, 15s);
```
Vezi: [[rect]], [[latime]]

## salveazaCaptura
Salvează ecranul ca poză în Galerie → FreeClick (opțional cu un nume). Bun ca dovadă sau ca să vezi ce s-a întâmplat.
```cod
if (apare("victorie")) salveazaCaptura("victorie");
```
Vezi: [[copiaza]]

# Degetele tale
## Ascultarea atingerilor
Funcțiile din capitolul ăsta ascultă ce faci TU pe ecran: unde atingi, ce gest faci, câte degete ții. Așa poți schimba ce face scriptul cât rulează.
FreeClick ascultă doar cât un script ascultă (o funcție asteapta…() sau asculta(true)). Fără asta nu e nimic în cale și nu aude nimic. Cât ascultă, panoul arată 👂 (semnalul că te așteaptă).
Atingerile tale ajung și în joc (poți opri asta cu [[treciMaiDeparte]]).
Pe Android 15+, dacă telefonul permite (Setări → 👂, Testează), FreeClick primește o copie a atingerilor și jocul le primește imediat.
Altfel, cât scriptul ascultă, un strat prinde gestul și îl trimite în joc după ce ridici degetul (cu o mică întârziere; un deget ținut apăsat ajunge în joc când îl ridici).
Regulile: funcțiile asteapta…() ascultă singure cât așteaptă. Cele care citesc degetele de acum ([[degete]], [[atins]], [[pozitieDeget]], [[ultimaAtingere]], [[atingeriNoi]]) cer asculta(true) înainte; fără el, scriptul se oprește cu o eroare care spune asta.
Un script care ascultă nu mai pune pauză când atingi ecranul: atingerea ta e intrare, nu oprire.
```cod
print("Atinge unde vrei să apăs");
point tinta = asteaptaAtingere();
for (int i = 0; i < 20; i++) tap(tinta, 20, 40);
```
Vezi: [[asteaptaAtingere]], [[asteaptaGest]], [[degete]], [[asculta]]

## asteaptaAtingere
Așteaptă să atingi ecranul și îți dă punctul atins. Cu ms_maxim: dacă nu atingi la timp, întoarce (-1, -1).
Se mai scrie `waitForTouch` sau `wait_for_touch`.
```cod
point unde = asteaptaAtingere();
tap(unde);                          // apasă exact acolo
point alt = asteaptaAtingere(5s);
if (!exista(alt)) print("n-ai atins în 5 secunde");
```
Vezi: [[asteaptaGest]], [[ultimaAtingere]], [[Ascultarea atingerilor]]

## asteaptaGest
Așteaptă următorul tău gest și îți spune ce a fost: "tap", "lung", "swipe sus" / "jos" / "stanga" / "dreapta", "2 degete" (tap cu două degete), "2 degete swipe sus"…, "zoom mareste" / "zoom micsoreaza", "rotire dreapta" / "rotire stanga".
După el, [[inceputGest]], [[sfarsitGest]], [[degeteGest]], [[durataGest]], [[factorGest]] și [[rotireGest]] dau detaliile. "" dacă a expirat. Un dublu-tap vine ca două "tap" (pentru el: [[asteaptaDubluTap]] sau [[asteaptaTapuri]]).
Măsurat pe 7000 de gesturi făcute ca de degete reale (tremur, viteze inegale, degete care nu pun deodată): 100% recunoscute; cu degete foarte tremurate, 99,9%.
```cod
string g = asteaptaGest();
if (g == "swipe sus") print("mai repede");
else if (g == "2 degete") stop();
else tap(inceputGest());
```
Vezi: [[asteaptaSwipe]], [[asteaptaDegete]]

## asteaptaSwipe
Așteaptă un swipe al tău și îți dă direcția: "sus", "jos", "stanga", "dreapta" ("" dacă a expirat).
```cod
string d = asteaptaSwipe();
if (d == "stanga") swipe(point(900, 1200), point(100, 1200));
```
Vezi: [[asteaptaGest]], [[alegeZona]]

## asteaptaTapuri
Așteaptă atâtea tap-uri rapide în același loc (3 = tap triplu) și îți dă locul. Se declanșează la al n-lea tap. (-1, -1) dacă a expirat.
Măsurat pe 2000 de șiruri de 1–4 tap-uri omenești: 100% numărate corect.
```cod
point unde = asteaptaTapuri(3);
print("tap triplu la ", unde.x, ", ", unde.y);
```
Vezi: [[asteaptaDubluTap]], [[asteaptaGest]]

## factorGest
Pentru un zoom: de câte ori s-au depărtat degetele (2 = de două ori mai departe; 0,5 = de două ori mai aproape).
```cod
string g = asteaptaGest();
if (g == "zoom mareste") print("mărit de ", factorGest(), " ori");
```
Vezi: [[rotireGest]], [[asteaptaGest]]

## rotireGest
Pentru o rotire: câte grade s-au rotit degetele (pozitiv = în sensul acelor de ceas).
```cod
string g = asteaptaGest();
if (g.contine("rotire")) print(rotireGest(), " grade");
```
Vezi: [[factorGest]], [[asteaptaGest]]

## gesturiNoi
Câte gesturi ai făcut de la ultimul apel (orice gest: tap, swipe, zoom…). Nu așteaptă. Cere [[asculta]](true) înainte.
```cod
asculta(true);
gesturiNoi();
wait(5s);
print("în 5 secunde ai făcut ", gesturiNoi(), " gesturi");
```
Vezi: [[atingeriNoi]], [[asculta]]

## asteaptaDubluTap
Așteaptă un dublu-tap al tău și îți dă locul.
```cod
point loc = asteaptaDubluTap();
dubluTap(loc);
```
Vezi: [[asteaptaGest]]

## asteaptaApasareLunga
Așteaptă să ții degetul apăsat (~½ s) și îți dă locul.
```cod
point loc = asteaptaApasareLunga();
apasaLung(loc, 3000);
```
Vezi: [[asteaptaGest]]

## asteaptaDegete
Așteaptă până sunt atâtea degete deodată pe ecran (de ex. 2 = semnalul tău); true când s-a întâmplat, false dacă a expirat.
```cod
while (true) {
    tap(540, 1200);
    if (asteaptaDegete(2, 300)) break;   // două degete = gata
}
```
Vezi: [[degete]], [[asteaptaFaraDegete]]

## asteaptaFaraDegete
Așteaptă până nu mai e niciun deget pe ecran (ai ridicat tot). Se mai scrie `asteaptaRidicare`.
```cod
asteaptaDegete(1);
print("ții apăsat…");
asteaptaFaraDegete();
print("ai ridicat degetul");
```
Vezi: [[asteaptaDegete]], [[atins]]

## degete
Câte degete sunt pe ecran acum. Cere [[asculta]](true) înainte.
```cod
asculta(true);
while (degete() < 3) wait(50);
print("trei degete!");
asculta(false);
```
Vezi: [[atins]], [[pozitieDeget]]

## atins
true dacă e vreun deget pe ecran acum. Cere [[asculta]](true) înainte.
```cod
asculta(true);
while (!atins()) tap(540, 1200, 20, 100);   // apasă până pui tu degetul
asculta(false);
```
Vezi: [[degete]]

## pozitieDeget
Unde e acum degetul (0 = primul pus, 1 = al doilea…); (-1, -1) dacă nu e. Cere [[asculta]](true) înainte.
```cod
asculta(true);
asteaptaDegete(1);
point unde = pozitieDeget();
print(unde.x, ", ", unde.y);
```
Vezi: [[degete]], [[ultimaAtingere]]

## ultimaAtingere
Unde ai pus ultima dată degetul; (-1, -1) dacă încă nu ai atins. Cere [[asculta]](true) înainte.
```cod
point tinta = asteaptaAtingere();
asculta(true);
while (true) {
    if (atingeriNoi() > 0) tinta = ultimaAtingere();   // ai atins altundeva: mut ținta
    tap(tinta, 20, 80);
}
```
Vezi: [[atingeriNoi]], [[asteaptaAtingere]]

## atingeriNoi
Câte tap-uri ai dat de la ultimul apel (prima dată: 0). Nu așteaptă: bun în bucle care ating și ascultă în același timp. Cere [[asculta]](true) înainte.
```cod
asculta(true);
int total = 0;
while (total < 5) {
    total += atingeriNoi();
    wait(100);
}
print("ai atins de 5 ori");
```
Vezi: [[ultimaAtingere]], [[asculta]]

## asteaptaAtingereInZona
Așteaptă să atingi într-o zonă (atingerile din afara ei nu contează); false dacă a expirat.
Faci așa butoane „virtuale” pe ecran.
```cod
rect stopZona = rect(0, 0, 300, 300);   // colțul din stânga sus
if (asteaptaAtingereInZona(stopZona, 10s)) stop();
```
Vezi: [[asteaptaAtingere]], [[rect]]

## alegeZona
Tragi cu degetul un dreptunghi pe ecran, cât rulează scriptul; îți dă zona (rect). (-1, -1, -1, -1) dacă a expirat.
```cod
print("Trage peste zona cu scorul");
rect z = alegeZona();
print(citeste(z));
```
Vezi: [[asteaptaSwipe]], [[rect]]

## asculta
asculta(true): FreeClick ascultă tot timpul (nu doar cât așteaptă). E obligatoriu înainte de [[degete]], [[atins]], [[pozitieDeget]], [[ultimaAtingere]] și [[atingeriNoi]]. asculta(false) oprește.
Unde nu există ascultarea directă, stratul stă pus cât asculți; atingerile tale trec în joc după ce ridici degetul.
```cod
asculta(true);
while (degete() < 2) tap(540, 1200, 20, 60);
asculta(false);
```
Vezi: [[Ascultarea atingerilor]], [[treciMaiDeparte]]

## treciMaiDeparte
treciMaiDeparte(false): atingerile prinse de strat NU mai ajung în joc (le folosește doar scriptul). Implicit true.
Cu ascultarea directă, atingerile ajung oricum în joc.
```cod
treciMaiDeparte(false);
point unde = asteaptaAtingere();   // jocul nu vede atingerea asta
treciMaiDeparte(true);
```
Vezi: [[asculta]]

## inceputGest
Unde a început ultimul gest așteptat (de [[asteaptaGest]] și celelalte).
```cod
asteaptaSwipe();
print(inceputGest().y, " → ", sfarsitGest().y);
```
Vezi: [[sfarsitGest]], [[asteaptaGest]]

## sfarsitGest
Unde s-a terminat ultimul gest (unde ai ridicat degetul).
```cod
asteaptaSwipe();
swipe(inceputGest(), sfarsitGest());   // repetă swipe-ul tău
```
Vezi: [[inceputGest]]

## degeteGest
Câte degete a avut ultimul gest.
```cod
asteaptaGest();
print(degeteGest());
```
Vezi: [[asteaptaGest]]

## durataGest
Cât a durat ultimul gest, în ms.
```cod
asteaptaGest();
print(durataGest(), " ms");
```
Vezi: [[asteaptaGest]]

## inregistreaza
Înregistrează ce faci tu pe ecran și îți dă pașii (vector<Pas>), cu pauzele dintre ei; îi repeți cu [[ruleaza]] (sau reda).
Se oprește după liniste_ms fără nicio atingere (implicit 3 s) sau după ms_maxim (implicit 60 s). Tap-urile și apăsările lungi rămân ca atare; swipe-urile, curbele și gesturile cu mai multe degete se păstrează exact, deget cu deget.
```cod
print("Arată-mi ce să fac (3 s fără atingeri = gata)");
vector<Pas> miscari = inregistreaza();
print(miscari.size(), " pași; îi repet de 10 ori");
reda(miscari, 10);
```
Vezi: [[ruleaza]], [[Înregistrare în cod]], [[Ascultarea atingerilor]]

## intreaba
Te întreabă ceva cât rulează scriptul și așteaptă răspunsul (text). La Anulează: valoarea implicită.
```cod
string nume = intreaba("Pe ce boss mergem?", "dragon");
print("merg la ", nume);
```
Vezi: [[intreabaNumar]], [[alege]]

## intreabaNumar
Ca [[intreaba]], dar cere un număr (tastatura cu cifre). La Anulează: valoarea implicită.
```cod
int runde = intreabaNumar("Câte runde?", 10);
for (int i = 0; i < runde; i++) { tap(540, 1200); wait(1s); }
```
Vezi: [[intreaba]]

## alege
Îți arată o listă și îți dă a câta variantă ai ales (0 = prima); -1 la Anulează.
```cod
int ce = alege("Ce fac?", {"Farm aur", "Boss", "Nimic"});
if (ce == 0) ruleazaMacro("Farm aur");
else if (ce == 1) ruleazaMacro("Boss");
```
Vezi: [[intreaba]], [[asteaptaButon]]

# Butoanele tale
## buton
Pune un buton al tău peste joc (text + zonă) și îți dă numărul lui. Apăsarea pe el nu ajunge în joc și e mereu auzită (e o fereastră a FreeClick). Butoanele dispar când se oprește scriptul.
Așa îți faci comenzile tale: Start, Stop, Mai repede… fără să cauți panoul. Ca și textul de pe ecran, apar în ce „vede” FreeClick: nu le pune peste zone pe care le citești.
```cod
int repede = buton("⏩", rect(20, 300, 180, 420));
int gata = buton("■", rect(20, 440, 180, 560));
int pauza = 400;
while (!apasat(gata)) {
    if (apasat(repede)) pauza = max(50, pauza - 100);
    tap(540, 1200, 30, pauza);
}
stergeButoanele();
```
Vezi: [[apasat]], [[asteaptaButon]], [[stergeButon]], [[afiseazaPeEcran]]

## apasat
true dacă ai apăsat butonul de la ultima întrebare (fiecare apăsare se numără o dată). Nu așteaptă.
```cod
int b = buton("+1", rect(20, 300, 180, 420));
int total = 0;
while (total < 3) { if (apasat(b)) total++; wait(100); }
print("trei apăsări");
```
Vezi: [[buton]], [[asteaptaButon]]

## asteaptaButon
Așteaptă să apeși oricare buton și îți dă numărul lui; -1 dacă a expirat.
```cod
int da = buton("DA", rect(100, 900, 500, 1100));
int nu = buton("NU", rect(580, 900, 980, 1100));
int ales = asteaptaButon();
stergeButoanele();
print(ales == da ? "da" : "nu");
```
Vezi: [[buton]], [[apasat]]

## stergeButon
Scoate un buton (după numărul lui).
```cod
int b = buton("ok", rect(20, 300, 180, 420));
wait(2s);
stergeButon(b);
```
Vezi: [[stergeButoanele]], [[buton]]

## stergeButoanele
Scoate toate butoanele scriptului (dispar oricum când scriptul se oprește).
```cod
buton("a", rect(20, 300, 180, 420));
buton("b", rect(20, 440, 180, 560));
stergeButoanele();
```
Vezi: [[buton]]

## ascultaInZona
De acum, scriptul aude doar gesturile care ÎNCEP în zona asta; restul ajunge direct în joc, fără nicio întârziere (stratul care ascultă acoperă doar zona).
Bun ca să ții ecranul liber (de ex. un joystick) și să asculți doar un colț. [[ascultaPeTotEcranul]] întoarce la tot ecranul.
```cod
ascultaInZona(rect(0, 0, 300, 300));     // doar colțul din stânga sus
string g = asteaptaGest();
if (g == "2 degete") stop();
ascultaPeTotEcranul();
```
Vezi: [[Ascultarea atingerilor]], [[buton]]

## ascultaPeTotEcranul
Anulează [[ascultaInZona]]: scriptul aude iar gesturile de pe tot ecranul.
```cod
ascultaInZona(rect(0, 0, 300, 300));
ascultaPeTotEcranul();
```
Vezi: [[ascultaInZona]]

# Lista de pași
## pasi
`pasi` e lista de pași din editorul de pași, văzută din cod (vector<Pas>). O poți rula cu [[ruleaza]] sau schimba din cod.
Un Pas are: tip, x, y, x2, y2, durata, intarziere, repetari, activ, nota.
```cod
print(pasi.size(), " pași");
pasi[0].x += 20;
ruleaza(pasi, 3);
```
Vezi: [[ruleaza]], [[pasiDin]]

## ruleaza
Rulează pașii cu motorul obișnuit (imagini, culori, sub-macro-uri), de câte ori vrei, și așteaptă să termine. Se mai scrie `reda`.
```cod
ruleaza(pasi);        // toată lista
ruleaza(pasi[2], 5);  // doar al treilea pas, de 5 ori
```
Vezi: [[pasi]], [[ruleazaMacro]]

## pasiDin
Pașii unei salvări (după nume), ca listă pe care o poți schimba sau rula.
```cod
vector<Pas> boss = pasiDin("Boss");
ruleaza(boss);
```
Vezi: [[ruleazaMacro]]

## ruleazaMacro
Rulează o salvare (după nume) de câte ori vrei.
```cod
ruleazaMacro("Colectează tot", 2);
```
Vezi: [[pasiDin]], [[ruleaza]]

## pasTap
Un pas „tap” nou, de pus în listă.
```cod
vector<Pas> l;
l.push_back(pasTap(point(100, 200)));
l.push_back(pasPauza(500));
ruleaza(l);
```
Vezi: [[pasSwipe]], [[pasPauza]], [[pasApasaLung]]

## pasSwipe
Un pas „swipe” nou (cu durata opțională).
```cod
vector<Pas> l;
l.push_back(pasSwipe(point(540, 1500), point(540, 500), 300));
ruleaza(l);
```
Vezi: [[pasTap]]

## pasPauza
Un pas de așteptare.
```cod
vector<Pas> l;
l.push_back(pasPauza(1s));
```
Vezi: [[pasTap]]

## pasApasaLung
Un pas de apăsare lungă (durata opțională).
```cod
vector<Pas> l;
l.push_back(pasApasaLung(point(300, 300), 1500));
```
Vezi: [[pasTap]]

# Arată și spune
## afiseazaPeEcran
Scrie un text peste joc (sus la mijloc, sau unde spui); nu poate fi atins, deci atingerile trec prin el. afiseazaPeEcran("") sau [[ascundeTextul]] îl scoate; dispare și când se oprește scriptul.
E mai la vedere decât consola. Nu-l pune peste o zonă pe care o citești sau o cauți ([[citeste]], [[gaseste]], [[procentCuloare]]): textul apare și el în ce vede FreeClick.
```cod
int runde = 0;
while (runde < 50) {
    runde++;
    afiseazaPeEcran("Runda " + to_string(runde) + " / 50");
    tap(540, 1200);
    wait(1s);
}
ascundeTextul();
```
Vezi: [[ascundeTextul]], [[print]], [[toast]]

## ascundeTextul
Scoate textul pus cu [[afiseazaPeEcran]].
```cod
afiseazaPeEcran("pornesc…");
wait(2s);
ascundeTextul();
```
Vezi: [[afiseazaPeEcran]]

## notifica
O notificare pe telefon, ca să afli de departe (la [[Mod AFK]]): notifica(text) sau notifica(titlu, text).
Pe Android 13+ FreeClick trebuie să aibă voie să trimită notificări; altfel consola spune asta.
```cod
if (apare("victorie")) notifica("FreeClick", "Am câștigat runda!");
```
Vezi: [[toast]], [[vibreaza]], [[Mod AFK]]

## baterie
Bateria acum, 0…100 (-1 dacă nu se știe).
```cod
if (baterie() < 20 && !seIncarca()) {
    notifica("Baterie mică: mă opresc");
    stop();
}
```
Vezi: [[seIncarca]], [[Mod AFK]]

## seIncarca
true dacă telefonul e la încărcat.
```cod
print(seIncarca() ? "la încărcat" : "pe baterie");
```
Vezi: [[baterie]]

## orientare
"portret" sau "peisaj": cum stă ecranul acum.
```cod
if (orientare() == "peisaj") print("jocul e pe orizontală");
```
Vezi: [[latime]], [[inaltime]]

# Consolă și altele
## print
Scrie în consolă. Primește oricâte valori, de orice tip. Se mai scrie `afiseaza`. Merge și `cout << a << endl;`.
```cod
int aur = 1250;
print("Aur: ", aur, " · ora ", ora());
cout << "la fel" << endl;
```
Vezi: [[toast]]

## toast
Un mesaj scurt jos pe ecran.
```cod
toast("Am terminat runda");
```
Vezi: [[print]], [[vibreaza]]

## vibreaza
Vibrează telefonul (implicit 300 ms): bun ca să te anunțe când s-a întâmplat ceva.
```cod
if (apare("boss")) vibreaza(1000);
```
Vezi: [[toast]]

## stop
Oprește scriptul.
```cod
if (citeste(rect(800, 50, 1050, 120)) >= 1000) stop();
```
Vezi: [[Evenimente]]

## copiaza
Pune text în clipboard (de lipit apoi oriunde).
```cod
copiaza("Scor: " + to_string(citeste(rect(800, 50, 1050, 120))));
```
Vezi: [[deschideLink]]

## timp
Milisecunde de când a pornit scriptul.
```cod
while (timp() < 10min) { tap(540, 1200); wait(1s); }
```
Vezi: [[ora]], [[wait]]

## ora
Ora acum, ca text „HH:MM:SS”.
```cod
print("acum e ", ora());
```
Vezi: [[oraNum]], [[minut]], [[ziua]]

## oraNum
Ora acum ca număr (0–23).
```cod
if (oraNum() >= 23) stop();
```
Vezi: [[ora]], [[minut]]

## minut
Minutul acum (0–59).
```cod
if (minut() == 0) print("fix");
```
Vezi: [[oraNum]]

## ziua
Ziua săptămânii: 1 = luni … 7 = duminică.
```cod
if (ziua() == 7) print("duminică: eveniment special");
```
Vezi: [[ora]]

## random
Un număr întreg la întâmplare între a și b (inclusiv amândouă).
```cod
tap(random(400, 680), random(1100, 1300));
```
Vezi: [[waitRandom]], [[tapInZona]]

# Numere și text
## Matematică
sqrt(x), pow(a, b), round(x), floor(x), ceil(x), abs(x), min(a, b), max(a, b) — ca în C++.
[[dist]] dă distanța dintre două puncte.
```cod
double d = sqrt(pow(3, 2) + pow(4, 2));   // 5
int m = max(10, min(20, 15));             // 15
print(d, " ", m, " ", abs(-7));
```
Vezi: [[dist]], [[Variabile și tipuri]]

## dist
Distanța (în pixeli) dintre două puncte.
```cod
print(dist(point(0, 0), point(300, 400)));   // 500
```
Vezi: [[point]]

## centru
Mijlocul unui dreptunghi.
```cod
tap(centru(rect(400, 1100, 680, 1300)));
```
Vezi: [[rect]], [[contine]]

## contine
true dacă punctul e în dreptunghi.
```cod
point unde = asteaptaAtingere();
if (contine(rect(0, 0, 540, 2400), unde)) print("stânga");
```
Vezi: [[rect]], [[asteaptaAtingereInZona]]

## hex
Culoarea ca text „#RRGGBB”. Merge și c.hex().
```cod
print(hex(pixel(100, 100)));
```
Vezi: [[color]]

## to_int
Textul ca număr întreg (to_int, stoi, numar). to_double pentru zecimale.
```cod
int n = to_int("42");
double d = to_double("1.5");
print(n + 1, " ", d * 2);
```
Vezi: [[to_string]], [[Text (string)]]

## to_string
Orice valoare ca text (to_string sau text).
```cod
string s = "aur: " + to_string(1250);
print(s);
```
Vezi: [[to_int]]

## Text (string)
Textele au metodele din C++: s.size(), s.substr(poz, lung), s.find(x) (-1 dacă nu e), s.contine(x), s.upper(), s.lower(), s.replace(a, b), s.trim(), s.empty(); se lipesc cu +.
```cod
string s = "  Victorie!  ";
s = s.trim();
if (s.contine("Victorie")) print(s.upper(), " ", s.size());
```
Vezi: [[citesteText]], [[to_string]]

## sort
Sortează un vector (crescător). reverse(v) îl întoarce, swap(a, b) schimbă două valori.
```cod
vector<int> v = {3, 1, 2};
sort(v);
reverse(v);
print(v[0]);   // 3
```
Vezi: [[vector]]

# Limbajul
## Variabile și tipuri
Ca în C++: int, double, bool, string, color, point, rect, vector<T>, pair<A, B>, plus struct-urile tale. auto ghicește tipul; const nu se mai schimbă.
```cod
int vieti = 3;
double viteza = 1.5;
bool gata = false;
string nume = "boss";
auto poz = point(10, 20);
const int MAX = 100;
```
Vezi: [[point]], [[rect]], [[color]], [[vector]], [[struct]]

## point
Un punct pe ecran: point(x, y); câmpuri .x și .y; se adună și se scad (p + q).
Cu `#define p point` (scripturile noi îl au) scrii mai scurt: p(540, 1200).
```cod
point a = point(100, 200);
point b = a + point(50, 0);
print(b.x, ", ", b.y);
```
Vezi: [[#define]], [[rect]], [[dist]]

## rect
O zonă: rect(x1, y1, x2, y2) (colțul din stânga sus, cel din dreapta jos); câmpuri .x1 .y1 .x2 .y2.
```cod
rect z = rect(0, 0, 540, 1200);
tap(centru(z));
print(contine(z, point(10, 10)));
```
Vezi: [[centru]], [[contine]], [[alegeZona]]

## color
O culoare: #RRGGBB (de ex. #34A853); câmpuri .r .g .b și .hex(). Le compari cu [[aproape]].
În editor, culorile din cod au fundalul de culoarea lor.
```cod
color verde = #34A853;
print(verde.g, " ", verde.hex());
```
Vezi: [[pixel]], [[aproape]], [[pipeta]]

## if
if (condiție) { … } else if (…) { … } else { … }. Operatori: == != < > <= >= && || !.
```cod
int aur = citeste(rect(820, 60, 1060, 120));
if (aur < 0) print("nu pot citi");
else if (aur >= 1000) stop();
else tap(540, 1200);
```
Vezi: [[switch]], [[while]]

## switch
switch (x) { case 1: … break; default: … } — merge pe int, string și color.
```cod
string g = asteaptaGest();
switch (g) {
    case "swipe sus": print("sus"); break;
    case "2 degete": stop(); break;
    default: tap(inceputGest());
}
```
Vezi: [[if]]

## while
Bucle ca în C++: while (cond) { … }, do { … } while (cond);, for (int i = 0; i < n; i++), for (auto x : v). break iese, continue sare la următoarea.
```cod
for (int i = 0; i < 5; i++) tap(100 + i * 50, 800);
int n = 0;
while (n < 3) { n++; wait(100); }
```
Vezi: [[if]], [[vector]]

## funcții
Funcții cu tipuri, ca în C++; parametrii T& se schimbă în locul de unde au venit.
```cod
bool eVerde(point q) {
    return aproape(pixel(q), #34A853, 30);
}
void apasaDeN(point q, int n) {
    for (int i = 0; i < n; i++) tap(q);
}
if (eVerde(point(540, 300))) apasaDeN(point(540, 300), 3);
```
Vezi: [[struct]], [[Variabile și tipuri]]

## struct
struct Nume { câmpuri; }; și apoi Nume{valori}.
```cod
struct Tinta {
    point loc;
    string nume;
};
Tinta t = Tinta{point(200, 900), "stânga"};
tap(t.loc);
print(t.nume);
```
Vezi: [[funcții]], [[vector]]

## vector
vector<T> ca în C++: v.push_back(x), v.pop_back(), v.size(), v.empty(), v.clear(), v.back(), v.front(), v.erase(i), v.insert(i, x), v.contine(x), v.index_of(x); v[i].
pair<A, B> cu .first și .second și make_pair(a, b).
```cod
vector<point> locuri = {point(100, 800), point(500, 800)};
locuri.push_back(point(900, 800));
for (auto q : locuri) tap(q);
pair<string, int> per = make_pair("aur", 5);
print(per.first, " ", per.second);
```
Vezi: [[sort]], [[gasesteToate]]

## #define
Ca în C++: `#define NUME înlocuire` pune înlocuirea peste tot unde scrii NUME (scris la început de linie).
Merge și cu parametri: `#define DUBLU(x) tap(x); tap(x)`. `#undef NUME` îl scoate. „\” la final de linie continuă pe linia următoare.
Scripturile noi încep cu `#define p point`, ca să scrii p(540, 1200). Atenție: ca în C++, după asta nu poți numi o variabilă p.
```cod
#define p point
#define BUTON p(540, 1200)
#define ATINGE_DE(n) for (int i = 0; i < n; i++) tap(BUTON)
ATINGE_DE(3);
```
Vezi: [[point]], [[Setările clickului]]

## timeout
timeout (ms) { … } else { … }: dacă blocul durează mai mult, e părăsit, iar else (dacă îl scrii) rulează. Se mai scrie `limita`.
Timpul se verifică la fiecare linie și în așteptări; o comandă care durează deja (de ex. un swipe de 2 s) se termină întâi.
Un timeout în altul: cel din afară câștigă când îi expiră timpul.
```cod
timeout (30s) {
    while (!apare("victorie")) { tap(540, 1200); wait(500); }
    print("gata la timp");
} else {
    print("30 de secunde au trecut: merg mai departe");
}
```
Vezi: [[try]], [[while]], [[wait]]

## try
try { … } catch (string e) { … }: dacă în bloc apare o greșeală la rulare (o imagine care nu există, un index prea mare, ecranul care nu se poate citi…), scriptul nu se oprește: merge în catch, iar e e mesajul.
stop(), break, return și timeout trec prin el neschimbate. Se mai scrie `incearca` / `prinde`.
```cod
try {
    vector<int> v = {1, 2, 3};
    print(v[10]);
} catch (string e) {
    print("am prins: ", e);
}
```
Vezi: [[timeout]], [[if]]

## Fișiere
Scripturile pot ține fișiere text în folderul FreeClick (nu se văd din alte aplicații): [[scrieFisier]], [[adaugaInFisier]], [[citesteFisier]], [[existaFisier]].
Numele: litere, cifre, spațiu, . _ - (fără foldere). Cel mult 1 000 000 de caractere într-un fișier și 200 de fișiere. Ca să scoți ceva: [[copiaza]](citesteFisier("…")).
```cod
adaugaInFisier("jurnal.txt", ora() + " runda gata\n");
print(citesteFisier("jurnal.txt"));
```
Vezi: [[pastreaza]], [[copiaza]]

## scrieFisier
Scrie un text într-un fișier (îl înlocuiește dacă există). Vezi [[Fișiere]].
```cod
scrieFisier("scor.txt", to_string(citeste(rect(820, 60, 1060, 120))));
```
Vezi: [[adaugaInFisier]], [[citesteFisier]]

## adaugaInFisier
Adaugă text la sfârșitul unui fișier (îl face dacă nu există). Pune tu "\n" dacă vrei rânduri.
```cod
adaugaInFisier("jurnal.txt", ora() + " am câștigat\n");
```
Vezi: [[scrieFisier]], [[Fișiere]]

## citesteFisier
Textul unui fișier ("" dacă nu există).
```cod
string tot = citesteFisier("jurnal.txt");
print(tot.size(), " caractere");
```
Vezi: [[existaFisier]], [[Fișiere]]

## existaFisier
true dacă fișierul există.
```cod
if (!existaFisier("setari.txt")) scrieFisier("setari.txt", "runde=10");
```
Vezi: [[citesteFisier]]

## Evenimente
Blocuri scrise sus, în afara funcțiilor, care rulează singure cât scriptul așteaptă ([[wait]] și celelalte):
cand (condiție) { … } când condiția devine adevărată; la_fiecare(30s) { … } la fiecare interval; la_ora("22:00") { … } la o oră.
```cod
cand (apare("reclama_x")) {
    tap(gaseste("reclama_x"));
}
la_fiecare(5min) {
    print("încă rulez · ", ora());
}
while (true) { tap(540, 1200); wait(1s); }
```
Vezi: [[pastreaza]], [[stop]]

## pastreaza
`pastreaza` ține o variabilă între rulări (int, double, bool, string, color): data viitoare pornește de unde a rămas.
```cod
pastreaza int rulari = 0;
rulari++;
print("a ", rulari, "-a rulare");
```
Vezi: [[Evenimente]]

## Setările clickului
Sus în cod: DURATA_CLICK (ms cât stă degetul), DURATA_SWIPE (ms cât durează un swipe), INTERVAL_REPETARE (ms de așteptat după fiecare atingere).
[[tap]], [[swipe]], [[dubluTap]] și [[apasaLung]] le folosesc când nu le dai alte valori. ⋮ → Avansat → „Setările clickului sus în cod” le adaugă.
```cod
const int DURATA_CLICK = 40;
const int DURATA_SWIPE = 300;
const int INTERVAL_REPETARE = 200;
tap(540, 1200);   // 40 ms apăsat, apoi 200 ms
```
Vezi: [[#define]], [[tap]]

## Captura jocului
În editor poți ține lângă cod o captură a jocului (⋮ → Avansat → Arată captura jocului; o faci din panou: ≡ → Captură pentru cod).
Pe ea vezi ce ating liniile de cod și le muți cu degetul; uneltele aleg puncte, swipe-uri, zone, culori, imagini, 🎲 Tap în zonă și 🔢 Model.
Coordonatele sunt cele ale ecranului întreg, deci scriptul nimerește la fel și în joc. Simularea arată în ce ordine s-ar atinge.
Vezi: [[Primul script]], [[model]], [[tapInZona]]

# Setări și aplicație
## Înregistrare în cod
💻 Bibliotecă → ⋮ → Avansat → „Fă script din ea”: o înregistrare sau o listă de ținte devine un script nou (originalul rămâne). Fiecare atingere e o linie, cu durata exactă, iar pauzele sunt wait(…).
Pașii care nu sunt doar atingeri (imagini, culori, blocuri, gesturi libere) rulează exact ca înainte cu ruleaza(pasi[k]), pentru că scriptul primește aceiași pași în lista lui.
Verificat pe 2000 de înregistrări generate: aceleași atingeri, în aceleași locuri, cu aceleași durate, la aceleași momente (±2 ms la alte viteze, din rotunjiri).
Vezi: [[pasi]], [[ruleaza]], [[inregistreaza]]

## Moduri
🎯 Ținte (manual): pui ținte pe ecran cu + și le muți cu degetul. 🔴 Înregistrare: repetă ce ai atins tu. 🧩 Combinații: pune mai multe salvări cap la cap. 💻 Cod: scripturi.
Fiecare mod are Opțiuni: 3 setări la vedere, restul în ⚙ Avansat.
Vezi: [[Panoul]], [[Opțiuni]], [[Primul script]]

## Panoul
Panoul plutitor stă peste orice aplicație; îl muți trăgându-l. ▶ pornește, ■ oprește, ⏸ pune pauză; + adaugă ținte, − șterge ultima, 🔥 [[Turbo]], ≡ meniul.
În meniu: Opțiuni, Arată aplicația, Oprește; în Avansat restul (consolă, pipetă, filmare, jurnal…).
Vezi: [[Turbo]], [[Siguranță]]

## Opțiuni
Opțiunile unui macro: interval, durata atingerii, repetări (sau la nesfârșit), plus în ⚙ Avansat: abateri aleatoare, „Rulează doar în aplicația”, „Deschide întâi aplicația”, limita de timp, filmarea.
Vezi: [[Moduri]], [[Mod AFK]]

## Turbo
🔥 pe panou: cât e pornit, o atingere scurtă dă o rafală pe locul acela, ținut apăsat ~½ s și ridicat atinge non-stop până atingi din nou ecranul, iar un swipe trece normal. × sus îl oprește.
Setări → 🔥 Turbo: ritmul (5–60/s), câte atingeri are o rafală (0 = atingerea scurtă trece normal), limita pentru non-stop, împrăștierea.
Bara arată câte atingeri pe secundă se fac de fapt.
Vezi: [[Panoul]], [[Ascultarea atingerilor]]

## Mod AFK
🌙 Pentru rulări lungi (Setări → 🌙): ecranul rămâne aprins, FreeClick are notificarea lui ca Android să nu-l oprească, pauză dacă blochezi telefonul, la baterie mică sau la căldură.
„Se reface singur”: dacă jocul iese de pe ecran, pauză; dacă s-a închis și nu revine în 20 s, îl redeschide și ia rularea de la capăt (cu 10 s de Anulează); dacă Android oprește FreeClick, reia sesiunea.
Opțional: ecran întunecat. Pe Samsung: Setări → Baterie → Limite de utilizare în fundal → Aplicații care nu intră niciodată în repaus → FreeClick.
Vezi: [[Opțiuni]], [[Siguranță]]

## Siguranță
Oprire de urgență: volum jos de 2 ori repede oprește tot (și Turbo). Pauză când atingi ecranul în timpul unei rulări (scripturile care ascultă degetele nu pun pauză).
„Mod sigur” și „Acțiuni aleatoare” sunt în Opțiuni → ⚙ Avansat → 🛡 Siguranță.
Vezi: [[Mod AFK]], [[Ascultarea atingerilor]]

## Laborator
🧪 Setări → Laborator: FreeClick trimite singur atingeri de test pe un pătrat și numără ce a ajuns corect, pe telefonul tău (orice marcă, Android 7+).
Măsoară: atingeri (100), swipe-uri (40), două degete deodată (30), gesturi libere (20), stratul care trece atingerile mai departe (40) și ritmul maxim. ✅ = cel puțin 99%.
„Copiază rezultatele” îți dă un text cu telefonul și cifrele, ca să-l trimiți mai departe.
Vezi: [[Ascultarea atingerilor]], [[Turbo]]

## Partajare
📤 Orice salvare (script, ținte, înregistrare, combinație) sau mai multe deodată devine un cod („FREECLICK1:…”) pe care îl trimiți pe chat, sau un fișier.
Bibliotecă → ⋮ → Avansat → 📤 Partajează; în editorul de cod: ⋮ → 📤 Partajează scriptul; mai multe: Bibliotecă → 📥 Importă / 📤 partajează.
Ca să imporți: copiezi mesajul și deschizi Biblioteca (îl găsește singură), sau „Trimite → FreeClick” din chat. Nimic nu se înlocuiește și scripturile nu pornesc singure.
Vezi: [[Moduri]]

## Pornire automată
⚡ O salvare poate porni când deschizi o aplicație: doar apare pe panou, sau pornește singură după 3 s (■ anulează). Bibliotecă → ⋮ → Avansat → ⚡.
Vezi: [[Programări]], [[Mod AFK]]

## Programări
⏰ O salvare poate rula la o oră fixă, o dată sau zilnic (Bibliotecă → ⋮ → Avansat → Programează). Se reactivează după restart.
Vezi: [[Pornire automată]]

## Citire text
📖 [[citeste]] și [[citesteText]] recunosc cifrele și literele de pe ecran. Pentru fonturi de joc ciudate, îl înveți: Setări → 📖 Citire text.
Vezi: [[citeste]]
