package com.freeclick.core;
import java.awt.Font;
public class Boxes { public static void main(String[] a){
  Font f = AwtText.font(a[0], 0, Integer.parseInt(a[2])); String s=a[1]; int px=f.getSize(); int w=px*(s.length()+4), h=px*3;
  AwtText.Scene sc = AwtText.scene(s, f, 0xFFFFFF, 0x1E5AA8, 0x1E5AA8, w, h, px, px*2, 0);
  TextReader.Result r = TextReader.read(sc.argb, w, h, sc.x0-4, sc.y0-4, sc.x1+4, sc.y1+4, TextReader.MODE_TEXT, AwtText.book());
  System.out.println("'"+r.text+"' capH "+(r.glyphs.isEmpty()?0:r.glyphs.get(0).capH));
  for (TextReader.Glyph g : r.glyphs) System.out.printf("  %c x %d-%d y %d-%d comps %d dist %.2f top %.2f bot %.2f%n", g.c, g.x0, g.x1, g.y0, g.y1, g.comps.length, g.dist, g.f.top, g.f.bottom);
}}
