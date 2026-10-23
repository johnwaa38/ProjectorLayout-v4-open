import com.talktime.handsfree.ShiftInput;
import java.time.LocalTime;
public class ShiftInputTest {
 static void eq(String phrase,int h,int m){LocalTime t=ShiftInput.parseTime(phrase);if(t.getHour()!=h||t.getMinute()!=m)throw new AssertionError(phrase+" -> "+t);}
 static void fails(String date,String s,String e,String b){boolean failed=false;try{ShiftInput.parse(date,s,e,b);}catch(IllegalArgumentException ex){failed=true;}if(!failed)throw new AssertionError("Should fail: "+date+" "+s+" "+e+" "+b);}
 public static void main(String[] args){
  eq("1:00 PM",13,0); eq("8 AM",8,0);eq("4:30 pm",16,30);eq("13:00",13,0);eq("08:00",8,0);
  fails("2026-10-23","1:00","4:00 PM","30");fails("wrong","8 AM","4 PM","30");fails("2026-10-23","8 AM","4 PM","five");
  var a=ShiftInput.parse("2026-10-23","8 AM","4:30 PM","30");if(a.paidMinutes!=480)throw new AssertionError("minutes: "+a.paidMinutes);
  var overnight=ShiftInput.parse("2026-10-23","10 PM","6 AM","0");if(overnight.paidMinutes!=480)throw new AssertionError("overnight: "+overnight.paidMinutes);
  System.out.println("Shift editing, AM/PM validation, and overnight tests passed");
 }
}