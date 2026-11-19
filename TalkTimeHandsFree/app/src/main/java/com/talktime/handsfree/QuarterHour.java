package com.talktime.handsfree;

/** Nearest quarter-hour, with exact halfway points rounded up. Pure Java. */
public final class QuarterHour {
    public static final long STEP_MS=15L*60L*1000L;
    public static long nearest(long epochMillis) {
        return Math.floorDiv(epochMillis + STEP_MS/2, STEP_MS)*STEP_MS;
    }
    public static int nearestBreak(int minutes) {
        if(minutes<0)throw new IllegalArgumentException("Negative break");
        return (int)(((long)minutes+7L)/15L)*15;
    }
    public static int paidMinutes(long start,long end,int breakMinutes) {
        long minutes=(nearest(end)-nearest(start))/60000L;
        return (int)minutes-nearestBreak(breakMinutes);
    }
    private QuarterHour(){}
}
