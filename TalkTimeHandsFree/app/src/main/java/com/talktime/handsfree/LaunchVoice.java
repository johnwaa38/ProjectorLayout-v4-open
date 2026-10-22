package com.talktime.handsfree;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/**
 * One-shot, opt-in recognition after Google launches the app.
 * The user's phone/Google implementation handles "Hey Google".
 * TalkTime does not pretend to register a custom Assistant command in a sideloaded APK.
 */
final class LaunchVoice implements RecognitionListener {
    static final int PERMISSION_REQUEST=105;
    private final MainActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable startAction = this::listen;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean destroyed=false;
    private boolean active=false;
    private boolean triedAgain=false;
    private boolean foreground=false;
    private boolean ttsReady=false;
    private String pendingSpoken;

    LaunchVoice(MainActivity activity){
        this.activity=activity;
        tts = new TextToSpeech(activity, state->{
            if(state==TextToSpeech.SUCCESS && tts!=null){
                ttsReady = tts.setLanguage(Locale.US)!=TextToSpeech.LANG_NOT_SUPPORTED;
                if(ttsReady && pendingSpoken!=null){
                    String words=pendingSpoken; pendingSpoken=null; speak(words);
                }
            }
        });
    }
    void onResume(){
        foreground=true;
        if(activity.autoVoiceEnabled())arm(1450);
    }
    void onPause(){
        foreground=false;
        handler.removeCallbacks(startAction);
        if(recognizer!=null && active){try{recognizer.cancel();}catch(Exception ignored){}}
        active=false;
    }
    void onPermissionResult(boolean allowed){
        if(allowed)arm(700);
        else activity.setVoiceMessage("Microphone permission declined. Voice commands are off.");
    }
    void arm(long ms){
        if(destroyed||!foreground||TimeStore.enabled(activity))return;
        handler.removeCallbacks(startAction);
        handler.postDelayed(startAction,ms);
    }
    void startNow(){
        triedAgain=false;
        arm(150);
    }
    private void listen(){
        if(destroyed||!foreground||active||TimeStore.enabled(activity))return;
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            activity.setVoiceMessage("Allow microphone access once to use voice commands.");
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION_REQUEST);
            return;
        }
        if(!SpeechRecognizer.isRecognitionAvailable(activity)){
            activity.setVoiceMessage("No Android speech recognizer is available on this phone.");
            return;
        }
        try{
            if(recognizer==null){
                recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
                recognizer.setRecognitionListener(this);
            }
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            active=true;
            activity.setVoiceMessage("Listening now. Say: set my start time to 1 PM.");
            recognizer.startListening(intent);
        }catch(Exception e){
            active=false;
            activity.setVoiceMessage("Couldn't start voice recognition. Try Speak Now.");
        }
    }
    private void speak(String words){
        if(destroyed)return;
        if(!ttsReady||tts==null){pendingSpoken=words;return;}
        tts.speak(words,TextToSpeech.QUEUE_FLUSH,null,"TalkTime-"+System.nanoTime());
    }
    @Override public void onResults(Bundle results){
        active=false;
        ArrayList<String> words=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(words==null||words.isEmpty()){
            retryOrFinish("I didn't hear that. Please repeat your command.");
            return;
        }
        String phrase=CommandParser.removeWake(words.get(0));
        CommandParser.Command command=CommandParser.parse(phrase);
        if("say".equals(command.kind) && !triedAgain){
            retryOrFinish(command.message);
            return;
        }
        String answer;
        try{ answer=TimeStore.execute(activity,command); }
        catch(Exception ex){ answer="Couldn't save that. Please check your time sheet."; }
        activity.setVoiceMessage(answer);
        activity.refreshFromVoice();
        speak(answer);
    }
    private void retryOrFinish(String message){
        activity.setVoiceMessage(message);
        speak(message);
        if(!triedAgain){
            triedAgain=true;
            arm(3200);
        }
    }
    @Override public void onError(int code){
        active=false;
        if(destroyed||!foreground)return;
        if(code==SpeechRecognizer.ERROR_NO_MATCH||code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){
            retryOrFinish("I didn't catch that. Please say your command again.");
        }else{
            activity.setVoiceMessage("Speech recognition unavailable. Try Speak Now.");
        }
    }
    @Override public void onReadyForSpeech(Bundle params){}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float rms){}
    @Override public void onBufferReceived(byte[] data){}
    @Override public void onEndOfSpeech(){}
    @Override public void onPartialResults(Bundle data){}
    @Override public void onEvent(int kind, Bundle data){}
    void destroy(){
        destroyed=true;
        handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}}
        if(tts!=null){tts.stop();tts.shutdown();}
    }
}