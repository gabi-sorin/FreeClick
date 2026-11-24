package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.freeclick.core.ActionFormatter;

import java.util.Date;
import java.util.List;

/** The screen recordings made by FreeClick: play, share, delete. */
public final class VideoListActivity extends Activity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        LinearLayout root = Ui.vertical(this);
        root.addView(Ui.header(this, "Filmări", false, false, null, null));
        ScrollView sv = new ScrollView(this);
        content = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        content.setPadding(p, 0, p, p);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        content.addView(Ui.colored(this, VideoRecorderService.isRecording() ? "■ Oprește filmarea" : "● Filmează ecranul acum",
                Ui.DANGER, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (VideoRecorderService.isRecording()) VideoRecorderService.stop(VideoListActivity.this);
                        else { VideoRecorderService.requestStart(VideoListActivity.this, null); moveTaskToBack(true); }
                        render();
                    }
                }), Ui.matchWrap());
        content.addView(Ui.muted(this, "Filmările se salvează în Galerie, în dosarul Movies/FreeClick. "
                + "Pot fi pornite automat la rularea unui macro (Setări rulare → Filmare)."));
        List<VideoStore.Item> items = VideoStore.list(this);
        content.addView(Ui.title(this, "Filmări (" + items.size() + ")"));
        for (final VideoStore.Item it : items) {
            LinearLayout box = Ui.vertical(this);
            box.addView(Ui.text(this, it.name, 15, Ui.TEXT, true));
            String meta = DateFormat.getDateFormat(this).format(new Date(it.dateMs)) + " "
                    + DateFormat.getTimeFormat(this).format(new Date(it.dateMs))
                    + " · " + Formatter.formatShortFileSize(this, it.size)
                    + (it.durationMs > 0 ? " · " + ActionFormatter.ms(it.durationMs) : "");
            box.addView(Ui.muted(this, meta));
            LinearLayout row = Ui.horizontal(this);
            row.addView(Ui.button(this, "▶ Redă", new View.OnClickListener() {
                @Override public void onClick(View v) { VideoStore.open(VideoListActivity.this, it); }
            }), Ui.weight(1));
            row.addView(Ui.button(this, "Trimite", new View.OnClickListener() {
                @Override public void onClick(View v) { VideoStore.share(VideoListActivity.this, it); }
            }), Ui.weight(1));
            row.addView(Ui.button(this, "Șterge", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(VideoListActivity.this).setTitle("Ștergi filmarea?")
                            .setPositiveButton("Șterge", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    VideoStore.delete(VideoListActivity.this, it);
                                    render();
                                }
                            })
                            .setNegativeButton("Anulează", null).show();
                }
            }), Ui.weight(1));
            box.addView(row, Ui.matchWrap());
            content.addView(Ui.card(this, box));
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { Ui.goBack(this); }
}
