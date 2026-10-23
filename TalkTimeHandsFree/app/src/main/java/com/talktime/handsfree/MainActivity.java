package com.talktime.handsfree;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Completely native UI. The foreground service stores commands even with the screen off. */
public final class MainActivity extends Activity {
    private static final int MIC_REQUEST=101,NOTIF_REQUEST=102,SAVE_REQUEST=103,IMPORT_REQUEST=104;
    private LinearLayout root, records;
    private TextView status,weekly,shiftStatus,voiceMessage,lastHeard,lastResult,lastSaved;
    private Button autoVoiceButton, reminderButton;
    private TextView reviewStatus;
    private LaunchVoice launchVoice;
    private Button toggle;
    private String exportContent,exportName;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){refresh();handler.postDelayed(this,5000);}};
    private final int bg=Color.rgb(11,18,32),panel=Color.rgb(22,34,52),white=Color.rgb(245,249,255),muted=Color.rgb(169,187,209),cyan=Color.rgb(96,218,230);

    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        root=new LinearLayout(this);root.setPadding(dp(18),dp(20),dp(18),dp(30));root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);setContentView(scroll);
        text("TALK TIME • 2.4",30,white,true);
        text("HANDS-FREE WORK HOURS",13,cyan,true);
        text("Assistant shortcut: say 'Hey Google, open TalkTime Hours'. If it doesn't open, launch the TalkTime Hours icon manually. TalkTime must be visible before its microphone can listen.",16,muted,false);
        voiceMessage=text("VOICE STATUS: Waiting for TalkTime to open",16,cyan,true);
        lastHeard=text("HEARD: No speech recognized yet.",16,white,true);
        lastResult=text("RESULT: No command processed yet.",16,muted,false);
        lastSaved=text("LAST SAVE: No changes recorded by this version yet.",16,cyan,true);
        autoVoiceButton=button("AUTO-LISTEN ON OPEN: ON",false,()->{
            boolean on=!autoVoiceEnabled();
            getPreferences(MODE_PRIVATE).edit().putBoolean("auto_voice",on).apply();
            autoVoiceButton.setText("AUTO-LISTEN ON OPEN: "+(on?"ON":"OFF"));
            if(on)launchVoice.startNow();
        });
        button("SPEAK A COMMAND NOW",true,()->launchVoice.startNow());
        reminderButton=button("DAILY 7:30 PM HOURS REVIEW: ON",false,()->{
            boolean enabled=!EveningReminder.enabled(this);
            EveningReminder.setEnabled(this,enabled);
            reminderButton.setText("DAILY 7:30 PM HOURS REVIEW: "+(enabled?"ON":"OFF"));
            if(enabled)askNotificationPermission();
        });
        reviewStatus=text("Daily review not checked",15,muted,false);
        button("REVIEW / CONFIRM TODAY",false,()->{
            launchVoice.promptForReview();
        });
        text("Around 7:30 PM, TalkTime shows your actual start and finish times. Tap 'Looks right' to confirm or 'Review / correct' to answer by voice. Android may delay notifications.",13,muted,false);
        status=text("Voice listening: OFF",19,white,true);
        toggle=button("ENABLE HANDS-FREE LISTENING",true,()->toggleListening());
        text("Enable once while the app is open. Android displays a permanent microphone notification. Tap Stop at any time. Voice listening may stop if Android closes the service.",13,muted,false);
        weekly=text("THIS WEEK: 0.00 HOURS",25,white,true);
        shiftStatus=text("Not clocked in",17,cyan,true);
        button("EDIT RUNNING START TIME",false,this::editRunningStart);
        rowButtons("Clock In","Clock Out",()->alert(TimeStore.clockIn(this)),()->alert(TimeStore.clockOut(this,0)));
        rowButtons("Set Start","Add Shift",this::promptStart,this::promptShift);
        text("SAVED SHIFTS  •  TAP EDIT TO CORRECT",21,white,true);
        text("Each saved shift below displays its start, finish, break and paid hours. Use EDIT to fix any of them. You'll see SAVED only after storage confirms the update.",13,muted,false);
        records=new LinearLayout(this);records.setOrientation(LinearLayout.VERTICAL);root.addView(records);
        rowButtons("Export CSV","Backup JSON",()->export("TalkTime-hours.csv",TimeStore.csv(this),"text/csv"),()->export("TalkTime-backup.json",TimeStore.json(this),"application/json"));
        button("RESTORE JSON BACKUP",false,()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_REQUEST);
        });
        text("Your records stay on this device. Export a backup regularly; uninstalling the app deletes local records. Android or your speech provider may process voice commands online.",13,muted,false);
        launchVoice=new LaunchVoice(this);
        EveningReminder.schedule(this);
        handler.postDelayed(this::askNotificationPermission,900);
        refresh();
    }
    boolean autoVoiceEnabled(){return getPreferences(MODE_PRIVATE).getBoolean("auto_voice",true);}
    void setVoiceMessage(String message){if(voiceMessage!=null)voiceMessage.setText(message);}
    void refreshFromVoice(){refresh();}
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setPadding(dp(3),dp(12),dp(3),dp(12));
        if(bold)v.setTypeface(null,1);root.addView(v);return v;
    }
    private Button button(String label,boolean primary,Runnable runnable){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);
        b.setTextColor(primary?bg:white);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?cyan:panel));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.setMargins(0,dp(6),0,dp(6));root.addView(b,p);
        b.setOnClickListener(v->runnable.run());return b;
    }
    private void rowButtons(String a,String b,Runnable left,Runnable right){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);root.addView(row);
        Button x=new Button(this);x.setAllCaps(false);x.setText(a);x.setTextColor(white);x.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));
        Button y=new Button(this);y.setAllCaps(false);y.setText(b);y.setTextColor(white);y.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(55),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(x,p);
        LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(0,dp(55),1);q.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(y,q);
        x.setOnClickListener(v->left.run());y.setOnClickListener(v->right.run());
    }
    private void askNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33 && EveningReminder.enabled(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIF_REQUEST);
        }
    }
    boolean consumeReviewRequest(){
        Intent i=getIntent();
        if(i!=null && i.getBooleanExtra("open_review",false)){
            i.removeExtra("open_review"); return true;
        }
        return false;
    }
    private void toggleListening(){
        if(TimeStore.enabled(this)){
            stopService(new Intent(this,ListeningService.class));TimeStore.enabled(this,false);refresh();return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);return;}
        startListening();
    }
    private void startListening(){
        try{
            startForegroundService(new Intent(this,ListeningService.class));TimeStore.enabled(this,true);refresh();
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIF_REQUEST);
        }catch(Exception e){TimeStore.enabled(this,false);alert("Android blocked the microphone service. Open TalkTime and retry.");}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] perms,int[] results){
        super.onRequestPermissionsResult(requestCode,perms,results);
        if(requestCode==LaunchVoice.PERMISSION_REQUEST){launchVoice.onPermissionResult(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED);return;}
        if(requestCode==MIC_REQUEST){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startListening();else alert("Microphone permission is required for hands-free listening.");}
    }
    private void alert(String message){TimeStore.response(this,message);new AlertDialog.Builder(this).setTitle("TalkTime").setMessage(message).setPositiveButton("OK",(d,w)->refresh()).show();refresh();}
    private void promptStart(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("1:00 PM");
        new AlertDialog.Builder(this).setTitle("Set today's start time").setView(input).setPositiveButton("Save",(d,w)->{
            CommandParser.Command parsed=CommandParser.parse("set my start time to "+input.getText());alert(TimeStore.execute(this,parsed));
        }).setNegativeButton("Cancel",null).show();
    }
    private void promptShift(){
        EditText input=new EditText(this);input.setHint("Worked 8 AM to 4:30 PM with 30 minutes lunch");input.setMinLines(2);
        new AlertDialog.Builder(this).setTitle("Enter a shift").setView(input).setPositiveButton("Save",(d,w)->{
            CommandParser.Command parsed=CommandParser.parse(input.getText().toString());alert(TimeStore.execute(this,parsed));
        }).setNegativeButton("Cancel",null).show();
    }
    private EditText editorField(String label,String initial,LinearLayout form){
        TextView title=new TextView(this);title.setText(label);title.setTextColor(muted);title.setTextSize(14);
        title.setPadding(dp(2),dp(8),dp(2),dp(2));form.addView(title);
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(initial);field.setTextSize(18);
        field.setTextColor(white);field.setSelectAllOnFocus(true);
        form.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));
        return field;
    }
    private String dayAt(long ms){return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
    private String timeAt(long ms){return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a",Locale.US));}
    private void editRunningStart(){
        long start=TimeStore.active(this);
        if(start==0){alert("No shift is currently running. Use 'Set Start' to enter your start time.");return;}
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),0);
        EditText date=editorField("Shift start date (YYYY-MM-DD)",dayAt(start),form);
        EditText time=editorField("Start time (include AM or PM)",timeAt(start),form);
        new AlertDialog.Builder(this).setTitle("Correct running start time")
            .setView(form).setPositiveButton("Save correction",(d,w)->alert(TimeStore.editActiveStart(this,date.getText().toString(),time.getText().toString())))
            .setNegativeButton("Cancel",null).show();
    }
    private void editShift(JSONObject shift){
        final String id=shift.optString("id");
        long start=shift.optLong("start"),end=shift.optLong("end");
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(5),dp(20),dp(15));
        EditText date=editorField("Date (YYYY-MM-DD)",shift.optString("date",dayAt(start)),form);
        EditText from=editorField("Start time (AM/PM or 24-hour)",timeAt(start),form);
        EditText to=editorField("Finish time (AM/PM or 24-hour)",timeAt(end),form);
        EditText breaks=editorField("Unpaid break (minutes)",String.valueOf(shift.optInt("breakMinutes")),form);
        TextView hint=new TextView(this);hint.setText("Overnight shifts are supported. A finish earlier than the start means next day.");
        hint.setTextSize(13);hint.setTextColor(muted);form.addView(hint);
        ScrollView wrap=new ScrollView(this);wrap.setFillViewport(false);wrap.addView(form);
        new AlertDialog.Builder(this).setTitle("Edit saved shift")
            .setView(wrap).setPositiveButton("SAVE CHANGES",(d,w)->alert(TimeStore.editEntry(this,id,date.getText().toString(),from.getText().toString(),to.getText().toString(),breaks.getText().toString())))
            .setNegativeButton("Cancel",null).show();
    }
    private void deleteShift(String id){
        new AlertDialog.Builder(this).setTitle("Delete this shift?")
          .setMessage("This cannot be undone unless you have a backup.")
          .setPositiveButton("Delete",(d,w)->alert(TimeStore.delete(this,id)))
          .setNegativeButton("Cancel",null).show();
    }
    private Button inlineButton(String label,Runnable action,LinearLayout row){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(white);b.setTextSize(16);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);lp.setMargins(dp(3),dp(1),dp(3),dp(6));
        row.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    private void refresh(){
        if(status==null)return;
        if(lastHeard!=null)lastHeard.setText("HEARD: "+TimeStore.lastHeard(this));
        if(lastResult!=null)lastResult.setText("RESULT: "+TimeStore.lastResponse(this));
        if(lastSaved!=null)lastSaved.setText("LAST SAVE: "+TimeStore.lastSaved(this));
        boolean on=TimeStore.enabled(this);
        status.setText("Voice listening: "+(on?"ON. Say 'Hey TalkTime'.":"OFF"));status.setTextColor(on?cyan:white);
        toggle.setText(on?"STOP HANDS-FREE LISTENING":"ENABLE HANDS-FREE LISTENING");
        int min=TimeStore.weeklyMinutes(this),extra=Math.max(0,min-2400);
        weekly.setText("THIS WEEK: "+TimeStore.formatHours(min)+" HOURS"+(extra>0?"\nOVER 40 HRS: "+TimeStore.formatHours(extra):""));
        long active=TimeStore.active(this);
        if(reminderButton!=null)reminderButton.setText("DAILY 7:30 PM HOURS REVIEW: "+(EveningReminder.enabled(this)?"ON":"OFF"));
        if(reviewStatus!=null)reviewStatus.setText(TimeStore.todayReport(this));
        shiftStatus.setText(active==0?"Not clocked in":"ON THE CLOCK: "+Instant.ofEpochMilli(active).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE h:mm a",Locale.US)));
        records.removeAllViews();
        JSONArray a;
        try{a=new JSONArray(TimeStore.allEntries(this));}catch(Exception ex){a=new JSONArray();}
        if(a.length()==0){TextView none=new TextView(this);none.setText("No saved shifts yet.");none.setTextColor(muted);none.setPadding(dp(3),dp(12),dp(3),dp(12));records.addView(none);}
        for(int i=a.length()-1;i>=0;i--){
            JSONObject e=a.optJSONObject(i);if(e==null)continue;
            final String id=e.optString("id");
            TextView r=new TextView(this);r.setTextColor(white);r.setTextSize(15);
            String from=Instant.ofEpochMilli(e.optLong("start")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a",Locale.US));
            String until=Instant.ofEpochMilli(e.optLong("end")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a",Locale.US));
            r.setText(e.optString("date")+"   "+TimeStore.formatHours(e.optInt("minutes"))+" hours\n"+from+"  →  "+until+"  •  "+e.optInt("breakMinutes")+"m break");
            r.setPadding(dp(12),dp(13),dp(12),dp(13));r.setBackgroundColor(panel);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));records.addView(r,p);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);records.addView(row);
            inlineButton("EDIT",()->editShift(e),row);
            inlineButton("DELETE",()->deleteShift(id),row);
        }
    }
    private void export(String name,String content,String mime){
        exportContent=content;exportName=name;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(i,SAVE_REQUEST);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==SAVE_REQUEST){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
            if(out==null)throw new IOException("Cannot open file");out.write(exportContent.getBytes(StandardCharsets.UTF_8));alert(exportName+" saved.");
        }catch(Exception e){alert("Unable to write the export file.");}finally{exportContent=null;}}
        if(request==IMPORT_REQUEST){try(InputStream in=getContentResolver().openInputStream(data.getData())){
            if(in==null)throw new IOException("Cannot read backup");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int len;
            while((len=in.read(buf))!=-1){out.write(buf,0,len);if(out.size()>10_000_000)throw new IOException("File too big");}
            String contents=out.toString("UTF-8");
            new AlertDialog.Builder(this).setTitle("Replace all existing shifts?")
                .setMessage("Restore replaces current shifts. Save a backup first.")
                .setPositiveButton("Restore",(d,w)->alert(TimeStore.restore(this,contents)))
                .setNegativeButton("Cancel",null).show();
        }catch(Exception ex){alert("Could not read the backup file.");}}
    }
    @Override protected void onResume(){super.onResume();handler.post(ticker);if(launchVoice!=null)launchVoice.onResume();}
    @Override protected void onPause(){handler.removeCallbacks(ticker);if(launchVoice!=null)launchVoice.onPause();super.onPause();}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(launchVoice!=null){
            if(consumeReviewRequest())launchVoice.promptForReview();
            else if(autoVoiceEnabled())launchVoice.promptOnOpen();
        }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(launchVoice!=null)launchVoice.onWindowFocusChanged(hasFocus);
    }
    @Override protected void onDestroy(){if(launchVoice!=null)launchVoice.destroy();super.onDestroy();}
}