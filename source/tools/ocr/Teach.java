package com.freeclick.core;
import java.awt.Font;
/** How much "Învață" helps on a font far from the phone's (Anton): teach one line, measure again. */
public class Teach { public static void main(String[] a){
  String fam = a.length > 0 ? a[0] : "Anton";
  FontBook book = AwtText.referenceBook();
  AwtText.cachedBook = book;
  OcrBench.Stats before = OcrBench.numbers(fam, 0, new int[]{16,22,28,36}, 80, 9);
  String[] lessons = {"0123456789", "1.5k 2,3M 45% -7/8 9B"};
  for (String lesson : lessons) {
    for (int px : new int[]{24, 40}) {
      Font f = AwtText.font(fam, 0, px);
      int w = px*(lesson.length()+4), h = px*3;
      AwtText.Scene sc = AwtText.scene(lesson, f, 0xFFFFFF, 0x1E5AA8, 0x1E5AA8, w, h, px, px*2, 0);
      TextReader.Result r = TextReader.read(sc.argb, w, h, sc.x0-4, sc.y0-4, sc.x1+4, sc.y1+4, TextReader.MODE_TEXT, book);
      int n = book.learn(r, lesson);
      System.out.println("lesson '"+lesson+"' "+px+"px read '"+r.text+"' learned "+n);
    }
  }
  OcrBench.Stats after = OcrBench.numbers(fam, 0, new int[]{16,22,28,36}, 80, 9);
  long t0 = System.nanoTime(); int reads = 0;
  for (int i = 0; i < 3; i++) { OcrBench.numbers("Roboto", 0, new int[]{28}, 20, 11); reads += 20; }
  System.out.printf("%s numbers: before %.0f%%, after teaching %.0f%%; %.1f ms per read; json %d bytes%n", fam, before.rate()*100, after.rate()*100, (System.nanoTime()-t0)/1e6/reads, book.toJson().length());
}}
