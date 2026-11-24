package com.freeclick.app;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

/** Invisible activity: shows Android's "start recording?" dialog and hands the result to the service. */
public final class CaptureRequestActivity extends Activity {
    private static final int REQ = 7;
    private boolean asked;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) asked = b.getBoolean("asked");
        if (!asked) {
            asked = true;
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("asked", asked);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, VideoRecorderService.class)
                        .setAction(VideoRecorderService.ACTION_START)
                        .putExtra(VideoRecorderService.EXTRA_CODE, resultCode)
                        .putExtra(VideoRecorderService.EXTRA_DATA, data)
                        .putExtra(VideoRecorderService.EXTRA_MODE, getIntent().getStringExtra(VideoRecorderService.EXTRA_MODE));
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
            } else {
                VideoRecorderService.consentDenied(getApplicationContext());
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
