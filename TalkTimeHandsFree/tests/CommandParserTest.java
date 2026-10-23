import com.talktime.handsfree.CommandParser;
public class CommandParserTest {
  static void eq(String phrase,String expected,int first){
    var c=CommandParser.parse(phrase);
    if(!c.kind.equals(expected)||(first!=-99&&first!=c.firstMinute))throw new AssertionError(phrase+" -> "+c.kind+"/"+c.firstMinute+" instead "+expected+"/"+first);
  }
  public static void main(String[] args){
    if(!CommandParser.hasWake("Hey TalkTime, set my start time to 1 PM"))throw new AssertionError("wake");
    if(!CommandParser.removeWake("Hey TalkTime, clock in").equals("clock in"))throw new AssertionError("strip wake");
    eq("set my start time @ 1.00 PM","set",780);
    eq("set my start time to 1 PM","set",780);
    eq("set my start time to one p.m.","set",780);
    eq("set my start time to 1:00","say",-99);
    eq("I started at 1 PM","set",780);
    eq("I started working at 1 PM","set",780);
    eq("Started work at 7 AM","set",420);
    eq("I started at 1", "say",-99);
    eq("clock in","in",-99);
    eq("clock me out","out",-99);
    eq("how many hours this week","summary",-99);
    eq("worked 8 am to 4:30 pm with 30 minutes lunch","range",480);
    eq("worked 8 to 4 with a 30 minute break","range",480);
    eq("stop listening","stop",-99);
    eq("I finished work at 5 PM","finish",1020);
    eq("set my end time to 6:30 pm","finish",1110);
    eq("I finished at 5", "say", -99);
    eq("yes that's right", "confirm",-99);
    eq("confirm", "confirm",-99);
    eq("correct my hours to 8 AM to 5 PM","replace",480);
    System.out.println("Voice command parser tests passed");
  }
}