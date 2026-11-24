package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-neutral description of a gesture ready to inject: each stroke is a
 * polyline with a start offset and a duration. The Android layer converts it
 * 1:1 into GestureDescription.StrokeDescription objects.
 */
public final class GesturePlan {

    public static final class PlannedStroke {
        public final List<float[]> points = new ArrayList<float[]>();
        public long startTime;
        public long duration;

        public PlannedStroke(long startTime, long duration) {
            this.startTime = startTime;
            this.duration = duration;
        }
    }

    public final List<PlannedStroke> strokes = new ArrayList<PlannedStroke>();

    /** Time from gesture start until the last finger lifts. */
    public long totalDuration() {
        long end = 0;
        for (PlannedStroke s : strokes) end = Math.max(end, s.startTime + s.duration);
        return end;
    }
}
