package com.freeclick.core;
/** OCR debugging: java -Docr.verbose=1 ... Dbg5 "Font" w|n [style] */
public class Dbg5 { public static void main(String[] a){ int st = a.length>2?Integer.parseInt(a[2]):0;
 OcrBench.Stats s = a[1].equals("w") ? OcrBench.words(a[0], st, new int[]{14,16,18,22,28,36}, 40, 5) : OcrBench.numbers(a[0], st, new int[]{14,16,18,22,28,36,48}, 60, 5);
 System.out.println(s.rate()+" chars "+s.charAcc()); } }
