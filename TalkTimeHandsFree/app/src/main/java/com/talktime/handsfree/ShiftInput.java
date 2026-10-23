package com.talktime.handsfree;

import java.time.*;
import java.time.format.*;
import java.util.Locale;

/** Pure Java input validation shared by the Edit Shift and running-start editors. */
public final class ShiftInput {
    public static final class Entry {
        public final LocalDate day;
        public final long start, end;
        public final int breaks, paidMinutes;
        Entry(LocalDate d,long s,long e,int b,int m){day=d;start=s;end=e;breaks=b;paidMinutes=m;}
    }
    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[] {
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.US),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h a").toFormatter(Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    };
    public static LocalDate parseDay(String input){
        try{return LocalDate.parse(input.trim(),DateTimeFormatter.ISO_LOCAL_DATE);}
        catch(Exception ex){throw new IllegalArgumentException("Date must be YYYY-MM-DD, for example 2026-10-23.");}
    }
    public static LocalTime parseTime(String input){
        String value=input.trim().replaceAll("\\s+"," ");
        if(value.matches("(?i)^\\d{1,2}:\\d{2}$") && value.length()<5)
            throw new IllegalArgumentException("For 1:00 through 9:59, include AM/PM. Or type 24-hour time like 13:00.");
        for(DateTimeFormatter f:TIME_FORMATS){try{return LocalTime.parse(value,f);}catch(Exception ignored){}}
        throw new IllegalArgumentException("Enter time with AM or PM, such as 1:00 PM, or 24-hour time like 13:00.");
    }
    public static long at(LocalDate day,LocalTime time){return day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    public static Entry parse(String dayText,String startText,String endText,String breakText){
        LocalDate day=parseDay(dayText);
        LocalTime startTime=parseTime(startText), endTime=parseTime(endText);
        int breaks;
        try{breaks=Integer.parseInt(breakText.trim());}
        catch(Exception ex){throw new IllegalArgumentException("Break must be whole minutes, for example 30.");}
        if(breaks<0||breaks>2160)throw new IllegalArgumentException("Break must be between 0 and 2160 minutes.");
        long start=at(day,startTime);
        long end=at(day,endTime);
        if(end<=start)end=at(day.plusDays(1),endTime);
        int duration=(int)Math.round((end-start)/60000.0);
        if(duration<1||duration>2160)throw new IllegalArgumentException("A shift must last between 1 minute and 36 hours.");
        if(breaks>=duration)throw new IllegalArgumentException("Your break must be shorter than the shift.");
        return new Entry(day,start,end,breaks,duration-breaks);
    }
    private ShiftInput(){}
}