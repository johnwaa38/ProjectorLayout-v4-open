package com.talktime.handsfree;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java speech grammar. Never guesses whether an isolated 1:00 means AM or PM. */
public final class CommandParser {
    public static final class Command {
        public final String kind;
        public final int firstMinute, secondMinute, breakMinutes;
        public final boolean yesterday;
        public final String message;
        public Command(String kind, int first, int second, int breaks, boolean yesterday, String message) {
            this.kind=kind; this.firstMinute=first; this.secondMinute=second;
            this.breakMinutes=breaks; this.yesterday=yesterday; this.message=message;
        }
    }
    private static final Pattern WAKE=Pattern.compile("^(?:hey|hi|okay|ok)\\s+(?:talk\\s*time|talktime|talk\\s*times|tock\\s*time)\\b\\s*[,.:!]?\\s*",Pattern.CASE_INSENSITIVE);
    private static final String TIME="(\\d{1,2}(?:[:.]\\d{1,2})?\\s*(?:a\\.?m\\.?|p\\.?m\\.?)?)";
    private static final Pattern HOUR=Pattern.compile("^(\\d{1,2})(?:[:.](\\d{1,2}))?\\s*(am|pm)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE=Pattern.compile("\\b(?:from\\s+|worked\\s+)?"+TIME+"\\s*(?:to|till|until|through|\\-)\\s*"+TIME+"\\b");
    private static final Pattern SET=Pattern.compile("\\b(?:set|change|correct|update|record|make)\\s+(?:my\\s+)?(?:shift\\s+)?(?:start(?:ing)?(?:\\s+time)?|clock\\s*in\\s*time)\\s*(?:to|at|for|@)?\\s*"+TIME+"\\b");
    private static final String[] WORDS={"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen","twenty"};
    private static String normalized(String input) {
        String s=input.toLowerCase(Locale.US).replaceAll("([ap])\\.m\\.","$1m").replaceAll("\\s+"," ").trim();
        for(int i=WORDS.length-1;i>=0;i--)s=s.replaceAll("\\b"+WORDS[i]+"\\b", String.valueOf(i));
        s=s.replaceAll("\\bhalf(?: an? )?hour\\b","30 minutes").replaceAll("\\bthirty\\b","30").replaceAll("\\bforty[- ]five\\b","45");
        s=s.replaceAll("\\b([1-9]|1[0-2])\\s+(15|30|45)\\b","$1:$2");
        return s;
    }
    public static boolean hasWake(String input){return WAKE.matcher(normalized(input)).find();}
    public static String removeWake(String input) {return WAKE.matcher(normalized(input)).replaceFirst("").trim();}
    private static Command say(String s){return new Command("say",-1,-1,0,false,s);}
    private static int minute(String text,boolean rangeStart, boolean rangeEnd, int startHint) {
        Matcher m=HOUR.matcher(text.trim().replace(" ","")); if(!m.matches())return -1;
        int h=Integer.parseInt(m.group(1)),min=m.group(2)==null?0:Integer.parseInt(m.group(2));
        if(min>59 || h>23)return -1;
        String ampm=m.group(3);
        if(ampm!=null){if(h<1||h>12)return -1;return ((h%12)+(ampm.equals("pm")?12:0))*60+min;}
        if(h==0||h>=13)return h*60+min;
        if(!rangeStart&&!rangeEnd)return -2;
        // Range: 8 to 4 => 8 am to 4 pm, 9 pm to 5 => next day 5 am.
        if(rangeStart){if(h==12)return 12*60+min;return (h<=6? h+12:h)*60+min;}
        if(startHint>=18*60){if(h<=8)return h*60+min;return (h+12)*60+min;}
        if(h<=7)return (h+12)*60+min;
        return h*60+min;
    }
    private static int breakMinutes(String input) {
        Matcher m=Pattern.compile("(?:\\b(\\d{1,3})\\s*(minutes?|mins?|hours?|hrs?)\\s*(?:of|for)?\\s*(?:lunch|break)\\b|\\b(?:lunch|break)\\s*(?:was|for|of)?\\s*(\\d{1,3})\\s*(minutes?|mins?|hours?|hrs?)\\b)").matcher(input);
        if(input.matches(".*\\bno\\s+(?:lunch|break)\\b.*"))return 0;
        if(!m.find())return 0;
        String count=m.group(1)!=null?m.group(1):m.group(3),unit=m.group(2)!=null?m.group(2):m.group(4);
        int n=Integer.parseInt(count);return unit.startsWith("hour")||unit.startsWith("hr")? n*60:n;
    }
    public static Command parse(String spoken) {
        String s=normalized(spoken).replaceAll("[!?]"," ");
        boolean yesterday=s.contains("yesterday")||s.contains("last night");
        if(s.isBlank())return say("Say clock in, clock out, or set my start time to 1 PM.");
        if(s.matches(".*\\b(?:stop listening|turn off listening|stop talktime|go to sleep)\\b.*"))return new Command("stop",-1,-1,0,false,"");
        if(s.matches(".*\\b(?:what|how many|tell me|total|show)\\b.*\\b(?:hours|week|time|worked)\\b.*") || s.matches(".*\\b(?:hours this week|weekly hours|weekly total)\\b.*"))return new Command("summary",-1,-1,0,false,"");
        Matcher set=SET.matcher(s);
        if(set.find()) {
            int start=minute(set.group(1).replaceAll("\\s+",""),false,false,-1);
            if(start==-2)return say("Please say A M or P M. For example, set my start time to 1 P M.");
            if(start<0)return say("I couldn't understand the start time.");
            return new Command("set",start,-1,0,yesterday,"");
        }
        if(s.matches(".*\\b(?:clock|check|punch)\\s*(?:me\\s+)?in\\b.*")||s.matches(".*\\bstart (?:my )?shift\\b.*"))return new Command("in",-1,-1,0,false,"");
        if(s.matches(".*\\b(?:clock|check|punch)\\s*(?:me\\s+)?out\\b.*")||s.matches(".*\\bend (?:my )?shift\\b.*"))return new Command("out",-1,-1,breakMinutes(s),false,"");
        Matcher range=RANGE.matcher(s);
        if(range.find()) {
            String t1=range.group(1).trim().replaceAll("\\s+",""),t2=range.group(2).trim().replaceAll("\\s+","");
            int a=minute(t1,true,false,-1),b=minute(t2,false,true,a);
            if(a<0||b<0)return say("Please repeat the hours with A M and P M.");
            int duration=b-a; if(duration<=0)duration+=1440;
            int br=breakMinutes(s);
            if(duration<15 || duration>1440 || br>=duration)return say("Those shift hours don't look right. Please repeat them.");
            return new Command("range",a,b,br,yesterday,"");
        }
        return say("I didn't catch that. Say clock in, clock out, or set my start time to 1 P M.");
    }
    private CommandParser(){}
}