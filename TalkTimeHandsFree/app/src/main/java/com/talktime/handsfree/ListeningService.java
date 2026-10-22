package com.talktime.handsfree;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

/** Optional ongoing microphone foreground service. Starts only with a user action while the app is visible. */
public final class ListeningService extends Service implements RecognitionListener {
    public static final String STOP="com.talktime.handsfree.STOP", CHANNEL="talktime_voice";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean running=false, speaking=false, waiting=false, speechActive=false;
    private long armedUntil=0;
    private int errors=0;
    private final Runnable next=this::listen;

    @Override public void onCreate(){super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Hands-free voice listening",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("TalkTime microphone listener. Stop any time from the notification.");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        tts=new TextToSpeech(this, status->{if(status==TextToSpeech.SUCCESS){tts.setLanguage(Locale.US);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){handler.post(()->{speaking=false;queueListen(350);});}
            @Override public void onError(String id){handler.post(()->{speaking=false;queueListen(700);});}
        });}});
    }
    private Notification notification(String message){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(this,3,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,ListeningService.class).setAction(STOP);
        PendingIntent stopAction=PendingIntent.getService(this,4,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("TalkTime: listening for 'Hey TalkTime'")
            .setContentText(message).setContentIntent(content).setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop listening",stopAction).build()).build();
    }
    private void announceNotification(String msg){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(17,notification(msg));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        try {
            if(Build.VERSION.SDK_INT>=29)startForeground(17,notification("Microphone active. Say Hey TalkTime."),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(17,notification("Microphone active. Say Hey TalkTime."));
        }catch(Exception e){stopSelf();return START_NOT_STICKY;}
        if(!running){
            running=true;TimeStore.enabled(this,true);
            if(!SpeechRecognizer.isRecognitionAvailable(this)){
                announceNotification("No speech recognition provider is installed. Tap Stop.");
                stopSelf();return START_NOT_STICKY;
            }
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            queueListen(200);
        }
        return START_NOT_STICKY; // not silently restarting mic after a device reboot or OS kill
    }
    private void queueListen(long delay){
        if(!running||speaking)return;
        handler.removeCallbacks(next);handler.postDelayed(next,delay);
    }
    private void listen(){
        if(!running||speaking||speechActive||recognizer==null)return;
        try {
            speechActive=true;
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            // Speech service decides whether it supports on-device recognition.
            recognizer.startListening(i);
        }catch(Exception ex){speechActive=false;queueListen(2000);}
    }
    private void say(String text){
        if(!running)return;
        speaking=true;handler.removeCallbacks(next);
        if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}
        speechActive=false;
        if(tts==null){speaking=false;queueListen(850);return;}
        try{int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"tt_"+System.nanoTime());if(result==TextToSpeech.ERROR){speaking=false;queueListen(1000);}}
        catch(Exception ex){speaking=false;queueListen(1000);}
    }
    private void handleWords(String spoken){
        if(spoken==null||spoken.isBlank())return;
        boolean wake=CommandParser.hasWake(spoken);
        if(wake){
            String command=CommandParser.removeWake(spoken);
            if(command.isBlank()){
                armedUntil=System.currentTimeMillis()+12000;
                say("I'm listening.");return;
            }
            armedUntil=0;process(command);return;
        }
        if(System.currentTimeMillis()<armedUntil){armedUntil=0;process(spoken);}
    }
    private void process(String spoken){
        CommandParser.Command command=CommandParser.parse(spoken);
        if("stop".equals(command.kind)){say("Hands-free listening is off.");handler.postDelayed(this::stopSelf,1100);return;}
        String answer;
        try{answer=TimeStore.execute(this,command);}catch(Exception e){answer="I couldn't save that. Open TalkTime to check your hours.";}
        announceNotification(answer);
        say(answer);
    }
    @Override public void onResults(Bundle results){
        speechActive=false;
        ArrayList<String> phrases=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(phrases!=null&&!phrases.isEmpty())handleWords(phrases.get(0));
        errors=0;
        if(!speaking)queueListen(450);
    }
    @Override public void onError(int error){
        speechActive=false;
        if(!running||speaking)return;
        if(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)errors=0;
        else errors++;
        // Avoid a busy retry loop on devices that prohibit recurring recognizer sessions.
        if(errors>=12){announceNotification("Recognition unavailable. Stop listening and restart from the app.");stopSelf();return;}
        queueListen(errors==0?550:Math.min(8000,errors*1100L));
    }
    @Override public void onReadyForSpeech(Bundle params){}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float rmsdB){}
    @Override public void onBufferReceived(byte[] buffer){}
    @Override public void onEndOfSpeech(){}
    @Override public void onPartialResults(Bundle results){}
    @Override public void onEvent(int eventType,Bundle params){}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        running=false;TimeStore.enabled(this,false);handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}}
        if(tts!=null){tts.stop();tts.shutdown();}
        super.onDestroy();
    }
}