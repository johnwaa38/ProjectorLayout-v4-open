package com.talktime.handsfree;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Gross pay estimates only. Each new shift captures its own hourly rate. */
public final class PayTracker {
    private static final String PREF="talktime_pay_settings", KEY="hourly_rate_cents";
    public static long rateCents(Context context) {
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY,0);
    }
    public static boolean setRate(Context context, String value) {
        try {
            BigDecimal rate=new BigDecimal(value.trim().replace("$","")).setScale(2,RoundingMode.UNNECESSARY);
            if(rate.signum()<0 || rate.compareTo(new BigDecimal("100000"))>0)return false;
            long cents=rate.movePointRight(2).longValueExact();
            return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(KEY,cents).commit();
        }catch(Exception ex){return false;}
    }
    public static String dollars(long cents){
        return String.format(Locale.US,"$%,.2f", cents/100.0);
    }
    public static long shiftCents(JSONObject shift) {
        if(!shift.has("hourlyRateCents"))return -1; // historical entries lack a known pay rate
        return Math.round(shift.optInt("minutes")*shift.optLong("hourlyRateCents")/60.0);
    }
    public static long weekCents(String json) {
        long sum=0;
        LocalDate start=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        try {
            JSONArray entries=new JSONArray(json);
            for(int i=0;i<entries.length();i++){
                JSONObject e=entries.getJSONObject(i);
                LocalDate day=LocalDate.parse(e.getString("date"));
                if(!day.isBefore(start)&&day.isBefore(start.plusDays(7))) {
                    long value=shiftCents(e);if(value>=0)sum+=value;
                }
            }
        }catch(Exception ignored){}
        return sum;
    }
    public static int unpricedCount(String json){
        int count=0;
        try{JSONArray a=new JSONArray(json);for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).has("hourlyRateCents"))count++;}catch(Exception ignored){}
        return count;
    }
    private PayTracker(){}
}
