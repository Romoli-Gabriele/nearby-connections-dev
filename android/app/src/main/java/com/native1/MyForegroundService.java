package com.native1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessagesClient;
import com.google.android.gms.nearby.messages.MessagesOptions;
import com.google.android.gms.nearby.messages.NearbyPermissions;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;

import androidx.annotation.Nullable;

public class MyForegroundService extends Service {
    private MyForegroundService context;
    private boolean isKilled;
    private Message message;

    private MessagesClient messagesClient;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = this;
        isKilled = false;

       Bundle bundle = intent.getExtras();
       String messageText = bundle.getString("message");

       messagesClient =  Nearby.getMessagesClient(context, new MessagesOptions.Builder().setPermissions(NearbyPermissions.BLE).build());

        // SERVICE
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        int clock = 0;

                        while (true && !isKilled) {
                            Log.d("ReactNative", "FOREGROUND SERVICE [chiamata numero " + clock + "]");
                            clock++;

                            // NEARBY:
                           // if(context.message != null) context.messagesClient.unpublish(context.message);

                            context.message = new Message(messageText.getBytes());

                            Strategy strategy = new Strategy.Builder().setTtlSeconds(Strategy.TTL_SECONDS_MAX).build();
                            PublishOptions options = new PublishOptions.Builder().setStrategy(strategy).build();

                            context.messagesClient.publish(context.message,options);

                            Log.d("ReactNative", "SEND BACKGROUND");

                            // loop:
                            try {   Thread.sleep(30 * 1000); } // AGGIORNAMENTI OGNI 30 SECONDI!
                            catch (InterruptedException e) {  e.printStackTrace(); }
                        }
                    }
                }).start();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Channel
            final String CHANNEL_ID = "Attività in Background";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            // Azioni
            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            // Notifica
            Notification.Builder notification = new Notification.Builder(context, CHANNEL_ID)
                    .setContentText("Ora sei visibile dagli altri utenti vicini a te")
                    .setContentTitle("Sei online!")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setContentIntent(contentIntent)
                    .setWhen(System.currentTimeMillis())
                    .setUsesChronometer(true);

            // Avvia l'attività
            startForeground(1001, notification.build());
        }

        runClock();

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private void stop(){
        isKilled = true;
        unpublish();
        stopForeground(true);
        stopSelf();
    }

    @Override
    // QUANDO L'APP VIENE CHIUSA
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d("ReactNative", "KILLING SERVICE FROM BACKGROUND");
        this.stop();
    }

    @Override
    public void onDestroy() {
        Log.d("ReactNative", "ON DESTROY");
        super.onDestroy();
        this.stop();
    }

    private void runClock(){
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        int clock = 0;
                        while (true && !isKilled) {
                            Log.d("ReactNative", "[" + clock + "]");
                            clock++;
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
    }


    private void unpublish(){
        if(this.message == null) return;
        Log.d("ReactNative", "UNPUBLISH");
        messagesClient.unpublish(this.message);
        this.message = null;
    }
}
