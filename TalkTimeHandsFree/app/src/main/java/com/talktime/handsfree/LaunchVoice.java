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
import android.speech.tts.UtteranceProgressListener;
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
    private boolean reviewPromptPending=false;
    private boolean welcomeQueued=false;
    private final Runnable welcomeAction = this::welcome;

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
        if(tts!=null)tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){
                if(id!=null && (id.startsWith("TalkTimeReview-") || id.startsWith("TalkTimePrompt-")))handler.post(()->{
                    if(foreground&&!destroyed){reviewPromptPending=false;arm(450);}
                });
            }
            @Override public void onError(String id){
                if(id!=null && (id.startsWith("TalkTimeReview-") || id.startsWith("TalkTimePrompt-")))handler.post(()->{
                    if(foreground&&!destroyed){reviewPromptPending=false;arm(1100);}
                });
            }
        });
    }
    void onResume(){
        foreground=true;
        if(activity.consumeReviewRequest())promptForReview();
        else if(activity.autoVoiceEnabled())promptOnOpen();
    }
    void onPause(){
        foreground=false;
        handler.removeCallbacks(startAction);
        handler.removeCallbacks(welcomeAction);
        welcomeQueued=false;
        if(recognizer!=null && active){try{recognizer.cancel();}catch(Exception ignored){}}
        active=false;
    }
    void onPermissionResult(boolean allowed){
        if(allowed)promptOnOpen();
        else activity.setVoiceMessage("Microphone permission declined. Voice commands are off.");
    }
    void arm(long ms){
        if(destroyed||!foreground||TimeStore.enabled(activity))return;
        handler.removeCallbacks(startAction);
        handler.postDelayed(startAction,ms);
    }
    // Assistant can hold audio focus briefly after launching TalkTime.
    // Wait for TalkTime's activity to actually have focus before speaking or opening the mic.
    void onWindowFocusChanged(boolean hasFocus){
        if(hasFocus && foreground && welcomeQueued)handler.postDelayed(welcomeAction,950);
        if(!hasFocus){
            handler.removeCallbacks(welcomeAction);
            handler.removeCallbacks(startAction);
        }
    }
    void promptOnOpen(){
        if(destroyed || TimeStore.enabled(activity))return;
        triedAgain=false;
        welcomeQueued=true;
        activity.setVoiceMessage("Waiting for Google to finish. TalkTime will say READY before listening.");
        handler.removeCallbacks(welcomeAction);
        if(foreground && activity.hasWindowFocus())handler.postDelayed(welcomeAction,1100);
    }
    private void welcome(){
        if(!welcomeQueued || !foreground || destroyed || !activity.hasWindowFocus())return;
        welcomeQueued=false;
        triedAgain=false;
        String prompt="TalkTime ready. Tell me your start or finish time.";
        activity.setVoiceMessage(prompt);
        if(ttsReady && tts!=null){
            tts.speak(prompt,TextToSpeech.QUEUE_FLUSH,null,"TalkTimePrompt-"+System.nanoTime());
        }else{
            // No text-to-speech engine: still allow spoken commands without a tap.
            arm(1400);
        }
    }
    void startNow(){
        // Also speak when the user manually invokes the in-app command button.
        promptOnOpen();
    }
    void promptForReview(){
        triedAgain=false;
        String spoken="TalkTime evening review. "+TimeStore.todayReport(activity)+" Say yes to confirm, or say your correct finish time.";
        activity.setVoiceMessage(spoken);
        handler.removeCallbacks(startAction);
        reviewPromptPending=true;
        if(ttsReady&&tts!=null){
            tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"TalkTimeReview-"+System.nanoTime());
        }else{
            // Device without speech synthesis can still show the prompt and listen.
            reviewPromptPending=false;arm(1600);
        }
    }
    private void listen(){
        if(destroyed||!foreground||!activity.hasWindowFocus()||active||TimeStore.enabled(activity))return;
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
            activity.setVoiceMessage("TalkTime is listening NOW. Say your start or finish time, without Hey Google.");
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
        TimeStore.heard(activity,words.get(0));
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
    @Override public void onReadyForSpeech(Bundle params){activity.setVoiceMessage("MIC READY: TalkTime is listening. Say: I started at 1 PM.");}
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