package com.talktime.handsfree;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** The native store is authoritative: voice commands are saved without displaying the app. */
public final class TimeStore {
    private static final String PREF="talktime_native_timesheet_v2", ENTRIES="entries", ACTIVE="started_at";
    public static SharedPreferences preferences(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static JSONArray array(Context c){try{return new JSONArray(preferences(c).getString(ENTRIES,"[]"));}catch(Exception e){return new JSONArray();}}
    public static long active(Context c){return preferences(c).getLong(ACTIVE,0);}
    public static boolean enabled(Context c){return preferences(c).getBoolean("listening_enabled",false);}
    public static void enabled(Context c,boolean value){preferences(c).edit().putBoolean("listening_enabled",value).apply();}
    private static String hm(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a",Locale.US));}
    private static String date(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
    public static synchronized String clockIn(Context c){
        if(active(c)!=0)return "You're already clocked in, since "+hm(active(c))+".";
        preferences(c).edit().putLong(ACTIVE,System.currentTimeMillis()).commit();
        return "Clocked in at "+hm(active(c))+". Saved.";
    }
    public static synchronized String setStart(Context c,int clockMinute,boolean yesterday){
        LocalDate day=LocalDate.now().minusDays(yesterday?1:0);
        long t=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(clockMinute).toInstant().toEpochMilli();
        long age=System.currentTimeMillis()-t;
        if(age < -60000L)return "That start time is in the future. Say today's actual start time.";
        if(age>36L*3600000L)return "That's more than 36 hours ago. Add that shift using its start and end times.";
        preferences(c).edit().putLong(ACTIVE,t).commit();
        return "Your start time is saved as "+hm(t)+(yesterday?" yesterday":" today")+".";
    }
    public static synchronized String clockOut(Context c,int breakMinutes){
        long start=active(c),stop=System.currentTimeMillis();
        if(start==0)return "You aren't clocked in.";
        long duration=Math.round((stop-start)/60000.0);
        if(duration<1)return "It's been less than one minute. I'll keep the clock running.";
        if(duration>2160)return "That shift is over 36 hours. I didn't change anything.";
        if(breakMinutes<0||breakMinutes>=duration)return "Your break can't be as long as the shift.";
        add(c,start,stop,breakMinutes,"Voice clock out");
        preferences(c).edit().remove(ACTIVE).commit();
        return "Clocked out. Saved "+formatHours((int)duration-breakMinutes)+" hours"+(breakMinutes>0?", minus "+breakMinutes+" minutes of break":"")+".";
    }
    public static synchronized String addRange(Context c,int a,int b,int breakMinutes,boolean yesterday){
        LocalDate day=LocalDate.now().minusDays(yesterday?1:0);
        long start=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(a).toInstant().toEpochMilli();
        long stop=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(b).toInstant().toEpochMilli();
        if(stop<=start)stop=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(b).toInstant().toEpochMilli();
        if(stop>System.currentTimeMillis()+60000L)return "That shift ends in the future. I didn't save it.";
        long mins=Math.round((stop-start)/60000.0);
        if(mins<1||mins>2160||breakMinutes>=mins)return "Those hours or break don't look right.";
        add(c,start,stop,breakMinutes,"Voice shift entry");
        return "Saved "+formatHours((int)mins-breakMinutes)+" hours for "+(yesterday?"yesterday":"today")+".";
    }
    private static void add(Context c,long start,long stop,int breakMinutes,String note){
        try{
            JSONArray a=array(c);
            JSONObject e=new JSONObject();
            e.put("id",UUID.randomUUID().toString());e.put("start",start);e.put("end",stop);
            e.put("breakMinutes",breakMinutes);e.put("minutes",(int)Math.round((stop-start)/60000.0)-breakMinutes);
            e.put("date",date(start));e.put("note",note);
            a.put(e);
            if(!preferences(c).edit().putString(ENTRIES,a.toString()).commit())throw new RuntimeException("Unable to save shift");
        }catch(Exception ex){throw new IllegalStateException("Unable to save shift",ex);}
    }
    public static synchronized String execute(Context c,CommandParser.Command cmd){
        switch(cmd.kind){
            case "in":return clockIn(c);
            case "out":return clockOut(c,cmd.breakMinutes);
            case "set":return setStart(c,cmd.firstMinute,cmd.yesterday);
            case "range":return addRange(c,cmd.firstMinute,cmd.secondMinute,cmd.breakMinutes,cmd.yesterday);
            case "summary":return summary(c);
            default:return cmd.message;
        }
    }
    public static String formatHours(int minutes){return String.format(Locale.US,"%.2f",minutes/60.0);}
    public static synchronized int weeklyMinutes(Context c){
        LocalDate start=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));int total=0;
        JSONArray a=array(c);
        for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);LocalDate d=LocalDate.parse(o.getString("date"));if(!d.isBefore(start)&&d.isBefore(start.plusDays(7)))total+=o.getInt("minutes");}catch(Exception ignored){}
        return total;
    }
    public static String summary(Context c){int min=weeklyMinutes(c);return "This week you have "+formatHours(min)+" completed hours.";}
    public static String allEntries(Context c){return array(c).toString();}
    public static int entryCount(Context c){return array(c).length();}
    public static synchronized void delete(Context c,String id){
        JSONArray original=array(c),updated=new JSONArray();
        for(int i=0;i<original.length();i++)try{JSONObject e=original.getJSONObject(i);if(!id.equals(e.getString("id")))updated.put(e);}catch(Exception ignored){}
        preferences(c).edit().putString(ENTRIES,updated.toString()).commit();
    }
    public static String csv(Context c){
        StringBuilder s=new StringBuilder("Date,Start,End,Break minutes,Hours,Notes\n");
        JSONArray a=array(c);
        for(int i=0;i<a.length();i++)try{
            JSONObject e=a.getJSONObject(i);
            s.append(e.optString("date")).append(',').append(hm(e.getLong("start"))).append(',').append(hm(e.getLong("end"))).append(',');
            s.append(e.getInt("breakMinutes")).append(',').append(formatHours(e.getInt("minutes"))).append(',');
            s.append('"').append(e.optString("note","").replace("\"","\"\"")).append('"').append('\n');
        }catch(Exception ignored){}
        return s.toString();
    }
    public static String json(Context c){try{
        JSONObject obj=new JSONObject();obj.put("format","talktime-handsfree-2");obj.put("entries",array(c));obj.put("active",active(c));return obj.toString(2);
    }catch(Exception e){return "{}";}}
    public static synchronized String restore(Context c,String input){
        try{
            JSONObject data=new JSONObject(input);if(!"talktime-handsfree-2".equals(data.optString("format")))return "Wrong backup format. No records were changed.";
            JSONArray incoming=data.getJSONArray("entries");if(incoming.length()>20000)return "Backup contains too many entries.";
            for(int i=0;i<incoming.length();i++){JSONObject o=incoming.getJSONObject(i);if(o.getInt("minutes")<1||o.getInt("minutes")>2160||o.getLong("end")<=o.getLong("start")||o.getString("id").length()>128)throw new IllegalArgumentException("Invalid entry");LocalDate.parse(o.getString("date"));}
            long restoredActive=data.optLong("active",0);if(restoredActive<0)throw new IllegalArgumentException("Invalid start");
            if(!preferences(c).edit().putString(ENTRIES,incoming.toString()).putLong(ACTIVE,restoredActive).commit())return "Restore failed. Nothing was saved.";
            return "Restored "+incoming.length()+" shifts from backup.";
        }catch(Exception ex){return "Invalid backup. Your existing records were kept.";}
    }
    private TimeStore(){}
}