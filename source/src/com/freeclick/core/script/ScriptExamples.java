package com.freeclick.core.script;

/** Ready-made scripts shown under "Exemple" (all of them are checked by the tests). */
public final class ScriptExamples {
    private ScriptExamples() {}

    public static final String[] NAMES = {
            "Începe aici (primul script)",
            "Apasă când punctul e verde",
            "Farm până la 1000 aur",
            "Rulează lista de pași de 10 ori",
            "Pop-up-uri: închide reclama oricând apare",
            "Funcții, struct, vector, pair",
            "Statistici păstrate între rulări",
            "Rafale unde atingi tu (degetul tău comandă)",
    };

    public static final String[] CODE = {
            "// Liniile care încep cu // sunt comentarii: nu se rulează.\n"
                    + "// ▶ rulează scriptul. Ce scrie print() apare în consolă.\n"
                    + "\n"
                    + "print(\"Salut! Pornesc la \", ora());\n"
                    + "\n"
                    + "point butonul = point(540, 1200);   // „+ Atingere” din bară scrie singur coordonatele\n"
                    + "for (int i = 1; i <= 3; i++) {\n"
                    + "    tap(butonul);\n"
                    + "    print(\"Atingerea \", i);\n"
                    + "    wait(500);                       // 500 ms; merge și 2s sau 1min\n"
                    + "}\n",

            "// Așteaptă ca un punct să devină verde, apoi îl apasă. Se repetă la nesfârșit.\n"
                    + "point punct = point(540, 1200);\n"
                    + "color verde = #34A853;              // „+ Culoare” alege culoarea de pe ecran\n"
                    + "\n"
                    + "while (true) {\n"
                    + "    if (aproape(pixel(punct), verde, 30)) {\n"
                    + "        tap(punct);\n"
                    + "        print(\"verde → apăsat\");\n"
                    + "        wait(1s);\n"
                    + "    }\n"
                    + "    wait(200);\n"
                    + "}\n",

            "// Citește aurul dintr-o zonă și se oprește la 1000.\n"
                    + "rect zonaAur = rect(40, 120, 300, 170);   // „+ Zonă” o alege de pe ecran\n"
                    + "point colecteaza = point(540, 1650);\n"
                    + "\n"
                    + "while (true) {\n"
                    + "    int aur = citeste(zonaAur);\n"
                    + "    print(\"aur = \", aur);\n"
                    + "    if (aur >= 1000) {\n"
                    + "        print(\"Gata, am \", aur, \" aur\");\n"
                    + "        vibreaza();\n"
                    + "        stop();\n"
                    + "    }\n"
                    + "    tap(colecteaza);\n"
                    + "    wait(2s);\n"
                    + "}\n",

            "// 'pasi' e lista de pași a scriptului (o editezi ca în modul manual: ⋮ → Lista de pași).\n"
                    + "print(\"Lista are \", pasi.size(), \" pași\");\n"
                    + "for (int tura = 1; tura <= 10; tura++) {\n"
                    + "    print(\"Tura \", tura);\n"
                    + "    ruleaza(pasi);\n"
                    + "}\n"
                    + "\n"
                    + "// Poți și schimba pașii din cod, de exemplu:\n"
                    + "// pasi[0].x = 500;  pasi[0].activ = false;  pasi.push_back(pasTap(point(100, 200)));\n",

            "// Un eveniment: oricând apare imaginea „Reclama X” cât timp scriptul așteaptă, o apasă.\n"
                    + "// (decupează butonul X cu „+ Imagine” și numește-l Reclama X)\n"
                    + "cand (apare(\"Reclama X\")) {\n"
                    + "    tap(gaseste(\"Reclama X\"));\n"
                    + "    print(\"am închis o reclamă\");\n"
                    + "}\n"
                    + "\n"
                    + "la_fiecare(30s) {\n"
                    + "    print(\"încă rulez, timp \", timp() / 1000, \" s\");\n"
                    + "}\n"
                    + "\n"
                    + "while (true) {\n"
                    + "    tap(540, 1200);\n"
                    + "    wait(1s);     // evenimentele rulează cât scriptul așteaptă\n"
                    + "}\n",

            "// Ca în C++: funcții cu tipuri, struct, vector, pair, referințe (&).\n"
                    + "struct Tinta {\n"
                    + "    point loc;\n"
                    + "    string nume;\n"
                    + "    int apasari;\n"
                    + "};\n"
                    + "\n"
                    + "bool eVerde(point q) {\n"
                    + "    return aproape(pixel(q), #34A853, 30);\n"
                    + "}\n"
                    + "\n"
                    + "void apasaDeN(Tinta& t, int n) {\n"
                    + "    for (int i = 0; i < n; i++) { tap(t.loc); wait(150); }\n"
                    + "    t.apasari += n;\n"
                    + "}\n"
                    + "\n"
                    + "int main() {\n"
                    + "    vector<Tinta> tinte;\n"
                    + "    tinte.push_back(Tinta{point(200, 900), \"stânga\", 0});\n"
                    + "    tinte.push_back(Tinta{point(880, 900), \"dreapta\", 0});\n"
                    + "    for (auto& t : tinte) apasaDeN(t, 3);\n"
                    + "    pair<string, int> cel = make_pair(tinte[0].nume, tinte[0].apasari);\n"
                    + "    cout << cel.first << \" a fost apăsată de \" << cel.second << \" ori\" << endl;\n"
                    + "    return 0;\n"
                    + "}\n",

            "// 'pastreaza' ține valoarea și după ce scriptul se termină (pe zile, între rulări).\n"
                    + "pastreaza int rulari = 0;\n"
                    + "pastreaza int totalAtingeri = 0;\n"
                    + "\n"
                    + "rulari++;\n"
                    + "for (int i = 0; i < 5; i++) { tap(540, 1200); totalAtingeri++; wait(300); }\n"
                    + "print(\"Rularea nr. \", rulari, \", atingeri în total: \", totalAtingeri);\n",
            "// Atingi un loc → îl apasă de 15 ori. Swipe în sus = mai repede, în jos = mai încet.\n"
                    + "// Două degete deodată = gata. Atingerile tale ajung și în joc.\n"
                    + "point tinta = asteaptaAtingere();\n"
                    + "int pauza = 60;\n"
                    + "while (true) {\n"
                    + "    for (int i = 0; i < 15; i++) tap(tinta, 20, pauza);\n"
                    + "    print(\"Atinge alt loc, swipe sus/jos pentru ritm, 2 degete = stop\");\n"
                    + "    string g = asteaptaGest();\n"
                    + "    if (g == \"2 degete\") break;\n"
                    + "    if (g == \"swipe sus\") pauza = max(10, pauza - 20);\n"
                    + "    else if (g == \"swipe jos\") pauza += 20;\n"
                    + "    else tinta = inceputGest();\n"
                    + "}\n"
                    + "print(\"Stop.\");\n",
    };

    /** What a brand new script starts with. */
    /**
     * The clicker's settings ("Durată click", "Durată swipe", "Interval de repetare") written at the top
     * of a script: tap(), swipe() & co. use them when a call does not give its own values.
     */
    public static String settingsHeader(long clickMs, long swipeMs, long intervalMs) {
        return settingsHeader(clickMs, swipeMs, intervalMs, true);
    }

    /** {@code definePoint}: start with "#define p point" (not for a script that already has a variable p). */
    public static String settingsHeader(long clickMs, long swipeMs, long intervalMs, boolean definePoint) {
        return (definePoint ? "#define p point   // scurtătură: p(540, 1200) în loc de point(540, 1200) (atunci nu numi o variabilă p)\n\n" : "")
                + "// Setările clickului (ca în Opțiuni): tap(), swipe(), dubluTap(), apasaLung() le folosesc\n"
                + "// când nu le dai alte valori, ex. tap(punct, 80, 500) = apăsat 80 ms, apoi 500 ms pauză.\n"
                + "const int DURATA_CLICK = " + clickMs + ";        // ms cât stă degetul apăsat\n"
                + "const int DURATA_SWIPE = " + swipeMs + ";       // ms cât durează un swipe\n"
                + "const int INTERVAL_REPETARE = " + intervalMs + ";  // ms de așteptat după fiecare atingere\n\n";
    }

    /** True if the script already has them (the editor does not add them twice). */
    public static boolean hasSettingsHeader(String src) {
        return src != null && (src.contains("DURATA_CLICK") || src.contains("INTERVAL_REPETARE"));
    }

    public static final String NEW_SCRIPT =
            "// Scriptul tău. ▶ îl pregătește: deschizi jocul și apeși ▶ pe panou. 👣 = pas cu pas.\n"
                    + "// Exemple: ⋮ → Exemple. Ajutor: ⋮ → Tutorial.\n"
                    + "\n"
                    + "print(\"Pornesc!\");\n"
                    + "// tap(540, 1200);   ← scoate // ca să atingă; „+ Atingere” alege punctul pe ecran\n"
                    + "wait(1s);\n"
                    + "print(\"Gata.\");\n";

    /** ⋮ → Funcții: every command, grouped, in Romanian. */
    public static final String HELP =
            "ATINGERI\n"
                    + "tap(p[, durata, pauza]) / tap(x, y[, durata, pauza]) · apasaLung(p[, ms, pauza]) · dubluTap(p[, pauza]) · "
                    + "swipe(a, b[, ms, pauza]) · trage(a, b[, ms, ține]) (apasă, ține, apoi mută) · back() · home() · recente() · "
                    + "notificari() · deschide(\"pachet\")\n"
                    + "Fără durată / pauză scrise, folosesc DURATA_CLICK, DURATA_SWIPE și INTERVAL_REPETARE de sus din cod "
                    + "(⋮ → Setările clickului sus în cod), altfel 50 ms apăsare, fără pauză.\n\n"
                    + "tapInZona(zona[, durata, pauza]) — un loc la întâmplare din zonă · zoomIn(centru[, distanta, ms]) · "
                    + "zoomOut(centru[, distanta, ms]) — cu două degete\n\n"
                    + "AȘTEPTĂRI\n"
                    + "asteaptaLiniste([zona, ms_liniste, ms_maxim]) → bool — până nu se mai mișcă nimic (după încărcări) · "
                    + "asteaptaSchimbare(zona[, ms_maxim]) → bool — până se schimbă ceva în zonă (scorul, un mesaj)\n"
                    + "wait(ms) — merge și wait(2s), wait(1min) · waitRandom(a, b) · asteaptaImagine(\"nume\", ms) → bool · "
                    + "asteaptaCuloare(p, culoare, ms[, toleranță]) → bool\n\n"
                    + "ECRANUL\n"
                    + "pixel(p) → color · aproape(c1, c2[, toleranță]) → bool · citeste(zona) → număr (-1 dacă nu poate) · "
                    + "citesteText(zona) → string · apare(\"imagine\") → bool · gaseste(\"imagine\") → point (−1, −1 dacă nu e) · "
                    + "gasesteToate(\"imagine\") → vector<point> · exista(p) · latime() · inaltime() · ecran() → rect\n"
                    + "model({p1, p2, p3}, {#c1, #c2, #c3}[, toleranță]) → bool — toate punctele au culorile lor (mai rapid decât o imagine)\n"
                    + "salveazaCaptura([\"nume\"]) — poza ecranului în Galerie → FreeClick · copiaza(text) — în clipboard · "
                    + "deschideLink(\"https://…\") — o pagină sau un link de aplicație\n\n"
                    + "LISTA DE PAȘI\n"
                    + "pasi (vector<Pas>: tip, x, y, x2, y2, durata, intarziere, repetari, activ, nota) · ruleaza(pasi[, ori]) · "
                    + "ruleaza(pasi[i][, ori]) · pasiDin(\"salvare\") · ruleazaMacro(\"salvare\"[, ori]) · pasTap(p) · "
                    + "pasSwipe(a, b[, ms]) · pasPauza(ms) · pasApasaLung(p[, ms])\n\n"
                    + "CONSOLA ȘI ALTELE\n"
                    + "print(a, b, …) · cout << a << endl; · toast(\"text\") · vibreaza([ms]) · stop() · timp() (ms de la pornire) · "
                    + "ora() (\"HH:MM:SS\") · oraNum() · minut() · ziua() (1 = luni) · random(a, b)\n\n"
                    + "DECIZII\n"
                    + "if / else · switch (x) { case 1: … break; case 2: case 3: … break; default: … } — merge pe int, string, color\n\n"
                    + "TIPURI\n"
                    + "int, double, bool, string, color (#34A853; .r .g .b .hex()), point (point(x, y); .x .y; p + q), "
                    + "rect (rect(x1, y1, x2, y2); centru(z), contine(z, p)), vector<T>, pair<A, B> (.first .second, make_pair), "
                    + "struct Nume { … };, auto, const, referințe T& în parametri și în for (auto& x : v)\n\n"
                    + "TEXT ȘI VECTORI\n"
                    + "s.size() · s.substr(poz, lung) · s.find(x) · s.contine(x) · s.upper() · s.replace(a, b) · to_string(x) · "
                    + "to_int(s) · v.push_back(x) · v.pop_back() · v.size() · v.empty() · v.clear() · v.back() · v.erase(i) · "
                    + "v.insert(i, x) · v.contine(x) · sort(v) · reverse(v) · min/max/abs · sqrt, pow, round, floor, ceil\n\n"
                    + "EVENIMENTE (sus, în afara funcțiilor; rulează cât scriptul așteaptă)\n"
                    + "cand (conditie) { … } · la_fiecare(30s) { … } · la_ora(\"22:00\") { … }\n\n"
                    + "PĂSTRATE ÎNTRE RULĂRI\n"
                    + "pastreaza int total = 0;  (int, double, bool, string, color)";
}
