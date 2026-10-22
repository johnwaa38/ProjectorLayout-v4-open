package com.talktime.handsfree;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.LocalDate;
import java.time.ZoneId;

/** Daily reminder uses an inexact 7:30 PM alarm. Android may delay it to save battery. */
public final class EveningReminder extends BroadcastReceiver {
    public static final String ACTION_REMIND="com.talktime.handsfree.DAILY_REVIEW";
    public static final String ACTION_CONFIRM="com.talktime.handsfree.CONFIRM_REVIEW";
    private static final String CHANNEL="daily_hours_review";
    private static final int NOTIFICATION_ID=7730;
    private static final int HOUR=19,MINUTE=30;

    public static boolean enabled(Context c){return TimeStore.preferences(c).getBoolean("review_enabled",true);}
    public static void setEnabled(Context c,boolean on){
        TimeStore.preferences(c).edit().putBoolean("review_enabled",on).apply();
        if(on)schedule(c);else cancel(c);
    }
    private static PendingIntent alarmIntent(Context c){
        Intent i=new Intent(c,EveningReminder.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(c,71730,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c){
        if(!enabled(c))return;
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(a==null)return;
        LocalDate now=LocalDate.now();
        long when=now.atTime(HOUR,MINUTE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(when<=System.currentTimeMillis()){
            when=now.plusDays(1).atTime(HOUR,MINUTE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarmIntent(c));
    }
    public static void cancel(Context c){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(a!=null)a.cancel(alarmIntent(c));
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(NOTIFICATION_ID);
    }
    private static PendingIntent open(Context c){
        Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("open_review",true);
        return PendingIntent.getActivity(c,71731,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void notify(Context c,String title,String message,boolean completed){
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Daily hour confirmation",NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Daily start and finish time check at about 7:30 PM");
            manager.createNotificationChannel(channel);
        }
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true).setContentIntent(open(c)).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE);
        if(completed){
            Intent yes=new Intent(c,EveningReminder.class).setAction(ACTION_CONFIRM);
            PendingIntent confirm=PendingIntent.getBroadcast(c,71732,yes,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null,"Looks right",confirm).build());
        }
        builder.addAction(new Notification.Action.Builder(null,"Review / correct",open(c)).build());
        manager.notify(NOTIFICATION_ID,builder.build());
    }
    @Override public void onReceive(Context c,Intent i){
        String action=i==null?"":i.getAction();
        if(ACTION_CONFIRM.equals(action)){
            String result=TimeStore.confirmToday(c);
            notify(c,"TalkTime confirmation",result,false);
            return;
        }
        if(ACTION_REMIND.equals(action)){
            schedule(c); // Reschedule before showing in case the process gets killed.
            if(!enabled(c))return;
            String report=TimeStore.todayReport(c);
            notify(c,"TalkTime: Confirm today's hours",report,TimeStore.active(c)==0 && TimeStore.hasCompletedToday(c) && !TimeStore.isTodayConfirmed(c));
            return;
        }
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action))schedule(c);
    }
}