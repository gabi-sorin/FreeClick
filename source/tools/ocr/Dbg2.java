package com.freeclick.core;
import java.awt.Font;
public class Dbg2 { public static void main(String[] a){
  FontBook book = AwtText.book();
  Font f = AwtText.font(a[0], 0, Integer.parseInt(a[2]));
  String s = a[1];
  int px=f.getSize(); int w = px*(s.length()+4), h=px*3;
  AwtText.Scene sc = AwtText.scene(s, f, 0x111111, 0xFFFFFF, 0xFFFFFF, w, h, px, px*2, 0);
  TextReader.Result r = TextReader.read(sc.argb, w, h, sc.x0-4, sc.y0-4, sc.x1+4, sc.y1+4, Integer.parseInt(a[3]), book);
  System.out.println("read '"+r.text+"' conf "+r.confidence);
  java.util.List<FontBook.Sample> all = book.snapshot();
  for (TextReader.Glyph g : r.glyphs) {
    System.out.printf("glyph %c dist %.3f box %dx%d aspect %.2f top %.2f bottom %.2f flat %s%n", g.c, g.dist, g.x1-g.x0, g.y1-g.y0, g.f.aspect, g.f.top, g.f.bottom, g.f.v==null);
    java.util.List<Object[]> best = new java.util.ArrayList<Object[]>();
    for (FontBook.Sample sm : all) { if (!TextReader.allowed(sm, Integer.parseInt(a[3]))) continue; best.add(new Object[]{TextReader.distance(g.f, sm.f, true), sm}); }
    java.util.Collections.sort(best, new java.util.Comparator<Object[]>(){ public int compare(Object[] x, Object[] y){ return Float.compare((Float)x[0],(Float)y[0]);}});
    for (int i=0;i<6;i++){ FontBook.Sample sm=(FontBook.Sample)best.get(i)[1]; System.out.printf("   %c %.3f (asp %.2f top %.2f bot %.2f)%n", sm.c, best.get(i)[0], sm.f.aspect, sm.f.top, sm.f.bottom);} 
  }
}}
