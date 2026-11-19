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
import java.util.ArrayList;
import java.util.Collections;

/** Gross pay estimates only. Each new shift captures its own hourly rate. */
public final class PayTracker {
    private static final String PREF="talktime_pay_settings", KEY="hourly_rate_cents", OVERTIME="overtime_enabled";
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
    /** Off by default: weekly overtime is optional for workers paid straight time. */
    public static boolean overtimeEnabled(Context context){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(OVERTIME,false);
    }
    public static boolean setOvertimeEnabled(Context context,boolean enabled){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(OVERTIME,enabled).commit();
    }
    private static LocalDate weekStart(JSONObject entry){
        return LocalDate.parse(entry.optString("date")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    private static ArrayList<JSONObject> ordered(String json) {
        ArrayList<JSONObject> entries=new ArrayList<>();
        try{
            JSONArray array=new JSONArray(json);
            for(int i=0;i<array.length();i++)entries.add(array.getJSONObject(i));
        }catch(Exception ignored){}
        Collections.sort(entries,(a,b)->{
            int t=Long.compare(a.optLong("start"),b.optLong("start"));
            return t!=0?t:a.optString("id").compareTo(b.optString("id"));
        });
        return entries;
    }
    /** All paid minutes count toward 40 hours, even legacy shifts lacking a pay rate. */
    public static long shiftCents(String json,JSONObject shift,boolean overtime) {
        if(!shift.has("hourlyRateCents"))return -1;
        int minutes=Math.max(0,shift.optInt("minutes"));
        long prior=0;
        if(overtime){
            try{
                LocalDate startOfWeek=weekStart(shift);
                for(JSONObject earlier:ordered(json)){
                    if(earlier.optString("id").equals(shift.optString("id")))break;
                    if(weekStart(earlier).equals(startOfWeek))
                        prior+=Math.max(0,earlier.optInt("minutes"));
                }
            }catch(Exception ignored){}
        }
        int regular=overtime?(int)Math.min(minutes,Math.max(0,2400-prior)):minutes;
        int extra=minutes-regular;
        long rate=shift.optLong("hourlyRateCents");
        return BigDecimal.valueOf(rate).multiply(BigDecimal.valueOf(regular*2L+extra*3L))
                .divide(BigDecimal.valueOf(120L),0,RoundingMode.HALF_UP).longValue();
    }
    public static long weekCents(String json,boolean overtime){
        long sum=0;
        LocalDate start=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for(JSONObject e:ordered(json)){
            try{
                LocalDate day=LocalDate.parse(e.getString("date"));
                if(!day.isBefore(start)&&day.isBefore(start.plusDays(7))){
                    long value=shiftCents(json,e,overtime);if(value>=0)sum+=value;
                }
            }catch(Exception ignored){}
        }
        return sum;
    }
    public static int unpricedCount(String json){
        int count=0;
        try{JSONArray a=new JSONArray(json);for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).has("hourlyRateCents"))count++;}catch(Exception ignored){}
        return count;
    }
    private PayTracker(){}
}
