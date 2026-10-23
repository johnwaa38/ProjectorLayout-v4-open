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
import java.util.ArrayList;
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
    /** These values are written only when Android actually commits a changed record. */
    private static String saved(Context c,String result){
        preferences(c).edit().putLong("last_saved_at",System.currentTimeMillis()).putString("last_saved_result",result).commit();
        return result;
    }
    public static void heard(Context c,String phrase){
        preferences(c).edit().putString("last_heard",phrase).putLong("last_heard_at",System.currentTimeMillis()).apply();
    }
    public static void response(Context c,String result){preferences(c).edit().putString("last_response",result).apply();}
    public static String lastHeard(Context c){return preferences(c).getString("last_heard","No command recognized yet.");}
    public static String lastResponse(Context c){return preferences(c).getString("last_response","No command processed yet.");}
    public static String lastSaved(Context c){
        long when=preferences(c).getLong("last_saved_at",0);
        if(when==0)return "No confirmed save in this version yet. Existing shifts are listed below.";
        return hm(when)+" • "+preferences(c).getString("last_saved_result","Change saved");
    }

    public static boolean enabled(Context c){return preferences(c).getBoolean("listening_enabled",false);}
    public static void enabled(Context c,boolean value){preferences(c).edit().putBoolean("listening_enabled",value).apply();}
    private static String hm(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a",Locale.US));}
    private static String date(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
    public static synchronized String clockIn(Context c){
        if(active(c)!=0)return "You're already clocked in, since "+hm(active(c))+".";
        preferences(c).edit().putLong(ACTIVE,System.currentTimeMillis()).commit();
        return saved(c,"Clocked in at "+hm(active(c))+". Saved.");
    }
    public static synchronized String setStart(Context c,int clockMinute,boolean yesterday){
        LocalDate day=LocalDate.now().minusDays(yesterday?1:0);
        long t=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(clockMinute).toInstant().toEpochMilli();
        long age=System.currentTimeMillis()-t;
        if(age < -60000L)return "That start time is in the future. Say today's actual start time.";
        if(age>36L*3600000L)return "That's more than 36 hours ago. Add that shift using its start and end times.";
        preferences(c).edit().putLong(ACTIVE,t).commit();
        return saved(c,"Your start time is saved as "+hm(t)+(yesterday?" yesterday":" today")+".");
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
        return saved(c,"Clocked out. Saved "+formatHours((int)duration-breakMinutes)+" hours"+(breakMinutes>0?", minus "+breakMinutes+" minutes of break":"")+".");
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
        return saved(c,"Saved "+formatHours((int)mins-breakMinutes)+" hours for "+(yesterday?"yesterday":"today")+".");
    }
    /** Explicit finish command. Does not silently substitute the time the user speaks for the actual finish. */
    public static synchronized String finishAt(Context c,int clockMinute,int breakMinutes,boolean yesterday){
        long start=active(c);
        LocalDate day=LocalDate.now().minusDays(yesterday?1:0);
        long end=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(clockMinute).toInstant().toEpochMilli();
        if(start==0)return "No running shift. Say I worked 8 AM to 4 PM to add a completed shift.";
        if(end<=start)end=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(clockMinute).toInstant().toEpochMilli();
        long mins=Math.round((end-start)/60000.0);
        if(end>System.currentTimeMillis()+60000L)return "That finish time is in the future. Nothing saved.";
        if(mins<1||mins>2160||breakMinutes<0||breakMinutes>=mins)return "That finish time or break doesn't look right. Nothing saved.";
        add(c,start,end,breakMinutes,"Voice finish time");
        preferences(c).edit().remove(ACTIVE).commit();
        return saved(c,"Saved a shift from "+hm(start)+" to "+hm(end)+", "+formatHours((int)mins-breakMinutes)+" paid hours. Please confirm your hours.");
    }
    public static synchronized String replaceRange(Context c,int a,int b,int breaks,boolean yesterday){
        LocalDate day=LocalDate.now().minusDays(yesterday?1:0);
        long start=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(a).toInstant().toEpochMilli();
        long end=day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(b).toInstant().toEpochMilli();
        if(end<=start)end=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(b).toInstant().toEpochMilli();
        long minutes=Math.round((end-start)/60000.0);
        if(end>System.currentTimeMillis()+60000L||minutes<1||minutes>2160||breaks<0||breaks>=minutes)
            return "Those hours don't look right. Nothing was changed.";
        JSONArray old=array(c);JSONArray updated=new JSONArray();int matches=0;
        for(int i=0;i<old.length();i++)try{
            JSONObject o=old.getJSONObject(i);
            if(day.toString().equals(o.optString("date")))matches++;
        }catch(Exception ignored){}
        if(matches==0)return "No existing shift on that day. Say I worked from 8 AM to 4 PM to add one.";
        if(matches>1)return "That day has multiple shifts. Please edit them individually rather than replace the wrong one.";
        for(int i=0;i<old.length();i++)try{
            JSONObject o=old.getJSONObject(i);
            if(day.toString().equals(o.optString("date"))){
                o.put("start",start);o.put("end",end);o.put("breakMinutes",breaks);
                o.put("minutes",(int)minutes-breaks);o.put("note","Voice corrected shift");
            }
            updated.put(o);
        }catch(Exception ignored){}
        if(!preferences(c).edit().putString(ENTRIES,updated.toString()).commit())return "Couldn't save the correction. Nothing changed.";
        return saved(c,"Corrected your shift to "+hm(start)+" through "+hm(end)+", "+formatHours((int)minutes-breaks)+" hours. Please confirm.");
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
        String result;
        switch(cmd.kind){
            case "in":result=clockIn(c);break;
            case "out":result=clockOut(c,cmd.breakMinutes);break;
            case "set":result=setStart(c,cmd.firstMinute,cmd.yesterday);break;
            case "finish":result=finishAt(c,cmd.firstMinute,cmd.breakMinutes,cmd.yesterday);break;
            case "confirm":result=confirmToday(c);break;
            case "range":result=addRange(c,cmd.firstMinute,cmd.secondMinute,cmd.breakMinutes,cmd.yesterday);break;
            case "replace":result=replaceRange(c,cmd.firstMinute,cmd.secondMinute,cmd.breakMinutes,cmd.yesterday);break;
            case "summary":result=summary(c);break;
            default:result=cmd.message;break;
        }
        response(c,result);
        return result;
    }
    public static synchronized String todayReport(Context c){
        LocalDate today=LocalDate.now();
        JSONArray entries=array(c);
        int count=0, mins=0;
        StringBuilder b=new StringBuilder();
        long running=active(c);
        for(int i=0;i<entries.length();i++)try{
            JSONObject e=entries.getJSONObject(i);
            if(!today.toString().equals(e.optString("date")))continue;
            count++;mins+=e.getInt("minutes");
            if(count<=3)b.append(count>1?"; ":"").append(hm(e.getLong("start"))).append(" to ").append(hm(e.getLong("end")))
                    .append(" (break ").append(e.optInt("breakMinutes")).append(" min)");
        }catch(Exception ignored){}
        if(count>3)b.append("; plus ").append(count-3).append(" more shifts");
        if(running>0 && date(running).equals(today.toString())){
            return (count>0? b.toString()+". ":"")+"Started at "+hm(running)+". No finish time saved. When did you finish?";
        }
        if(running>0)return "A shift from "+hm(running)+" is still running. Please check and finish it.";
        if(count==0)return "No completed hours recorded today. Did you work today?";
        return b+". Total "+formatHours(mins)+" hours. Are these hours correct?"+(isTodayConfirmed(c)?" Confirmed.":"");
    }
    private static String todayFingerprint(Context c){
        JSONArray entries=array(c);String d=LocalDate.now().toString();StringBuilder b=new StringBuilder(d);
        for(int i=0;i<entries.length();i++)try{JSONObject e=entries.getJSONObject(i);
            if(d.equals(e.optString("date")))b.append('|').append(e.optString("id")).append(':').append(e.optLong("start"))
                .append(':').append(e.optLong("end")).append(':').append(e.optInt("breakMinutes"));
        }catch(Exception ignored){}
        return b.toString();
    }
    public static synchronized boolean isTodayConfirmed(Context c){
        return entryCountToday(c)>0 && active(c)==0 && todayFingerprint(c).equals(preferences(c).getString("confirmed_today",""));
    }
    public static synchronized boolean hasCompletedToday(Context c){return entryCountToday(c)>0;}
    private static int entryCountToday(Context c){int n=0;JSONArray a=array(c);String d=LocalDate.now().toString();
        for(int i=0;i<a.length();i++)try{if(d.equals(a.getJSONObject(i).optString("date")))n++;}catch(Exception ignored){}return n;
    }
    public static synchronized String confirmToday(Context c){
        if(active(c)!=0)return "You still have an open shift. Tell me when you finished first.";
        if(entryCountToday(c)==0)return "No finished shifts recorded today. Tell me your start and finish times first.";
        if(!preferences(c).edit().putString("confirmed_today",todayFingerprint(c)).commit())return "Could not save confirmation. Try again.";
        return saved(c,"Confirmed today's hours. Saved.");
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
    /** Edit a single entry by immutable UUID, never an entire day. */
    public static synchronized String editEntry(Context c,String id,String dayText,String startText,String endText,String breakText){
        final ShiftInput.Entry edit;
        try{edit=ShiftInput.parse(dayText,startText,endText,breakText);}
        catch(IllegalArgumentException ex){return "NOT SAVED: "+ex.getMessage();}
        if(edit.end>System.currentTimeMillis()+60000L)return "NOT SAVED: Finish time is in the future.";
        JSONArray old=array(c);boolean found=false;
        for(int i=0;i<old.length();i++)try{
            JSONObject o=old.getJSONObject(i);
            if(id.equals(o.optString("id"))){
                o.put("start",edit.start);o.put("end",edit.end);
                o.put("date",edit.day.toString());o.put("breakMinutes",edit.breaks);
                o.put("minutes",edit.paidMinutes);o.put("note","Manually corrected shift");
                found=true;break;
            }
        }catch(Exception ex){return "NOT SAVED: Unable to edit this record.";}
        if(!found)return "NOT SAVED: That shift wasn't found. Refresh and try again.";
        if(!preferences(c).edit().putString(ENTRIES,old.toString()).commit())return "NOT SAVED: Storage write failed.";
        return saved(c,"Edited "+edit.day+": "+hm(edit.start)+" to "+hm(edit.end)+", "+formatHours(edit.paidMinutes)+" paid hours.");
    }
    /** Adjust a still-open shift without discarding the existing completed entries. */
    public static synchronized String editActiveStart(Context c,String dayText,String timeText){
        if(active(c)==0)return "NOT SAVED: There isn't a running shift to edit.";
        try{
            LocalDate day=ShiftInput.parseDay(dayText);
            long stamp=ShiftInput.at(day,ShiftInput.parseTime(timeText));
            long age=System.currentTimeMillis()-stamp;
            if(age< -60000L||age>36L*3600000L)return "NOT SAVED: Start must be in the last 36 hours.";
            if(!preferences(c).edit().putLong(ACTIVE,stamp).commit())return "NOT SAVED: Storage write failed.";
            return saved(c,"Running start corrected to "+day+" "+hm(stamp)+".");
        }catch(IllegalArgumentException ex){return "NOT SAVED: "+ex.getMessage();}
    }
    public static synchronized String delete(Context c,String id){
        JSONArray original=array(c),updated=new JSONArray();boolean found=false;
        for(int i=0;i<original.length();i++)try{
            JSONObject e=original.getJSONObject(i);
            if(id.equals(e.getString("id")))found=true;else updated.put(e);
        }catch(Exception ignored){}
        if(!found)return "NOT SAVED: Shift not found.";
        if(!preferences(c).edit().putString(ENTRIES,updated.toString()).commit())return "NOT SAVED: Delete failed.";
        return saved(c,"Shift deleted. "+updated.length()+" completed shifts remain.");
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
            return saved(c,"Restored "+incoming.length()+" shifts from backup.");
        }catch(Exception ex){return "Invalid backup. Your existing records were kept.";}
    }
    private TimeStore(){}
}