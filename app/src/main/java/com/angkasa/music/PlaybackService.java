
package com.angkasa.music;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class PlaybackService extends Service {

    public static final String ACTION_UPDATE = "ANGKASA_UPDATE";
    public static final String ACTION_PLAY = "ANGKASA_PLAY";
    public static final String ACTION_NEXT = "ANGKASA_NEXT";
    public static final String ACTION_PREVIOUS = "ANGKASA_PREVIOUS";

    private static final String CHANNEL_ID = "angkasa_music";
    private static final int NOTIFICATION_ID = 1001;

    private MediaSessionCompat mediaSession;

    private String title = "ANGKASA";
    private String artist = "Music Player";
    private boolean playing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "ANGKASA");

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                sendToActivity("toggle()");
            }

            @Override
            public void onPause() {
                sendToActivity("toggle()");
            }

            @Override
            public void onSkipToNext() {
                sendToActivity("next(false)");
            }

            @Override
            public void onSkipToPrevious() {
                sendToActivity("prev()");
            }
        });

        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_UPDATE.equals(action)) {
                title = intent.getStringExtra("title");
                artist = intent.getStringExtra("artist");
                playing = intent.getBooleanExtra("playing", false);
            } else if (ACTION_PLAY.equals(action)) {
                sendToActivity("toggle()");
            } else if (ACTION_NEXT.equals(action)) {
                sendToActivity("next(false)");
            } else if (ACTION_PREVIOUS.equals(action)) {
                sendToActivity("prev()");
            }
        }

        if (title == null || title.trim().isEmpty()) {
            title = "ANGKASA";
        }

        if (artist == null || artist.trim().isEmpty()) {
            artist = "Music Player";
        }

        updateMediaSession();

        startForeground(NOTIFICATION_ID, buildNotification());

        return START_NOT_STICKY;
    }

    private void updateMediaSession() {
        mediaSession.setMetadata(
            new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "ANGKASA")
                .build()
        );

        long actions =
            PlaybackStateCompat.ACTION_PLAY |
            PlaybackStateCompat.ACTION_PAUSE |
            PlaybackStateCompat.ACTION_PLAY_PAUSE |
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        mediaSession.setPlaybackState(
            new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    playing
                        ? PlaybackStateCompat.STATE_PLAYING
                        : PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    playing ? 1f : 0f
                )
                .build()
        );
    }

    private PendingIntent actionIntent(String action, int code) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);

        return PendingIntent.getService(
            this,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("ANGKASA Music")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                actionIntent(ACTION_PREVIOUS, 1)
            )
            .addAction(
                playing
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play",
                actionIntent(ACTION_PLAY, 2)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                actionIntent(ACTION_NEXT, 3)
            )
            .setStyle(
                new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build();
    }

    private void sendToActivity(String javascript) {
        MainActivity.runJavascript(javascript);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ANGKASA Music Playback",
                NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }

        MainActivity.serviceRequested = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
