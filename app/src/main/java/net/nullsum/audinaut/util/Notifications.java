/*
  This file is part of Subsonic.
    Subsonic is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    Subsonic is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
    Copyright 2014 (C) Scott Jackson
*/

package net.nullsum.audinaut.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.activity.SubsonicActivity;
import net.nullsum.audinaut.activity.SubsonicFragmentActivity;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.PlayerState;
import net.nullsum.audinaut.provider.AudinautWidgetProvider;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;

import static android.content.Context.NOTIFICATION_SERVICE;

public final class Notifications {
    private static final int NOTIFICATION_ID_PLAYING = 100;
    private static final int NOTIFICATION_ID_DOWNLOADING = 102;
    private static final String CHANNEL_PLAYING_ID = "playback_controls";
    private static final String CHANNEL_DOWNLOADING_ID = "media_download";
    private static final String TAG = Notifications.class.getSimpleName();
    private static boolean playShowing = false;
    private static boolean downloadShowing = false;
    private static boolean downloadForeground = false;
    private static boolean persistentPlayingShowing = false;

    public static void showPlayingNotification(final Context context, final DownloadService downloadService, final Handler handler, MusicDirectory.Entry song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel mChannel = new NotificationChannel(
                    CHANNEL_PLAYING_ID, context.getString(R.string.channel_playing_name), importance);
            mChannel.setDescription(context.getString(R.string.channel_playing_description));
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                    NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(mChannel);
        }

        final boolean playing = downloadService.getPlayerState() == PlayerState.STARTED;

        RemoteViews expandedContentView = new RemoteViews(context.getPackageName(), R.layout.notification_expanded);
        setupViews(expandedContentView, context, song, true, playing);

        RemoteViews smallContentView = new RemoteViews(context.getPackageName(), R.layout.notification);
        setupViews(smallContentView, context, song, false, playing);

        Intent notificationIntent = new Intent(context, SubsonicFragmentActivity.class);
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, true);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final Notification notification = new NotificationCompat.Builder(context, CHANNEL_PLAYING_ID)
                .setChannelId(CHANNEL_PLAYING_ID)
                .setSmallIcon(R.drawable.stat_notify_playing)
                .setContentTitle(song.getTitle())
                .setContentText(song.getTitle())
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCustomContentView(smallContentView)
                .setCustomBigContentView(expandedContentView)
                .setContentIntent(PendingIntent.getActivity(context, 0, notificationIntent, 0))
                .setPriority(NotificationCompat.PRIORITY_LOW).build();

        playShowing = true;
        if (downloadForeground && downloadShowing) {
            downloadForeground = false;
            handler.post(() -> {
                downloadService.stopForeground(true);
                showDownloadingNotification(context, downloadService, handler, downloadService.getCurrentDownloading(), downloadService.getBackgroundDownloads().size());
                downloadService.startForeground(NOTIFICATION_ID_PLAYING, notification);
            });
        } else {
            handler.post(() -> {
                if (playing) {
                    downloadService.startForeground(NOTIFICATION_ID_PLAYING, notification);
                } else {
                    playShowing = false;
                    persistentPlayingShowing = true;
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
                    downloadService.stopForeground(false);
                    notificationManager.notify(NOTIFICATION_ID_PLAYING, notification);
                }
            });
        }

        // Update widget
        AudinautWidgetProvider.notifyInstances(context, downloadService, playing);
    }

    private static void setupViews(RemoteViews rv, Context context, MusicDirectory.Entry song, boolean expanded, boolean playing) {
        // Use the same text for the ticker and the expanded notification
        String title = song.getTitle();
        String arist = song.getArtist();
        String album = song.getAlbum();

        // Set the album art.
        try {
            ImageLoader imageLoader = SubsonicActivity.getStaticImageLoader(context);
            Bitmap bitmap = null;
            if (imageLoader != null) {
                bitmap = imageLoader.getCachedImage(context, song, false);
            }
            if (bitmap == null) {
                // set default album art
                rv.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
            } else {
                imageLoader.setNowPlayingSmall(bitmap);
                rv.setImageViewBitmap(R.id.notification_image, bitmap);
            }
        } catch (Exception x) {
            Log.w(TAG, "Failed to get notification cover art", x);
            rv.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
        }

        // set the text for the notifications
        rv.setTextViewText(R.id.notification_title, title);
        rv.setTextViewText(R.id.notification_artist, arist);
        rv.setTextViewText(R.id.notification_album, album);

        boolean persistent = Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_PERSISTENT_NOTIFICATION, false);
        if (persistent) {
            if (expanded) {
                rv.setImageViewResource(R.id.control_pause, playing ? R.drawable.notification_media_pause : R.drawable.notification_media_start);

                rv.setImageViewResource(R.id.control_previous, R.drawable.notification_media_backward);
                rv.setImageViewResource(R.id.control_next, R.drawable.notification_media_forward);
            } else {
                rv.setImageViewResource(R.id.control_previous, playing ? R.drawable.notification_media_pause : R.drawable.notification_media_start);
                rv.setImageViewResource(R.id.control_pause, R.drawable.notification_media_forward);
                rv.setImageViewResource(R.id.control_next, R.drawable.notification_close);
            }
        } else {
            // Necessary for switching back since it appears to re-use the same layout
            rv.setImageViewResource(R.id.control_previous, R.drawable.notification_media_backward);
            rv.setImageViewResource(R.id.control_pause, R.drawable.notification_media_pause);
            rv.setImageViewResource(R.id.control_next, R.drawable.notification_media_forward);
        }

        // Create actions for media buttons
        PendingIntent pendingIntent;
        int previous = 0, pause, next, close = 0;
        if (persistent && !expanded) {
            pause = R.id.control_previous;
            next = R.id.control_pause;
            close = R.id.control_next;
        } else {
            previous = R.id.control_previous;
            pause = R.id.control_pause;
            next = R.id.control_next;
        }

        if (persistent && close == 0 && expanded) {
            close = R.id.notification_close;
            rv.setViewVisibility(close, View.VISIBLE);
        }

        if (previous > 0) {
            Intent prevIntent = new Intent("KEYCODE_MEDIA_PREVIOUS");
            prevIntent.setComponent(new ComponentName(context, DownloadService.class));
            prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
            pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0);
            rv.setOnClickPendingIntent(previous, pendingIntent);
        }
        if (playing) {
            Intent pauseIntent = new Intent("KEYCODE_MEDIA_PLAY_PAUSE");
            pauseIntent.setComponent(new ComponentName(context, DownloadService.class));
            pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            pendingIntent = PendingIntent.getService(context, 0, pauseIntent, 0);
            rv.setOnClickPendingIntent(pause, pendingIntent);
        } else {
            Intent prevIntent = new Intent("KEYCODE_MEDIA_START");
            prevIntent.setComponent(new ComponentName(context, DownloadService.class));
            prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
            pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0);
            rv.setOnClickPendingIntent(pause, pendingIntent);
        }
        Intent nextIntent = new Intent("KEYCODE_MEDIA_NEXT");
        nextIntent.setComponent(new ComponentName(context, DownloadService.class));
        nextIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
        pendingIntent = PendingIntent.getService(context, 0, nextIntent, 0);
        rv.setOnClickPendingIntent(next, pendingIntent);
        if (close > 0) {
            Intent prevIntent = new Intent("KEYCODE_MEDIA_STOP");
            prevIntent.setComponent(new ComponentName(context, DownloadService.class));
            prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP));
            pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0);
            rv.setOnClickPendingIntent(close, pendingIntent);
        }
    }

    public static void hidePlayingNotification(final Context context, final DownloadService downloadService, Handler handler) {
        playShowing = false;

        // Remove notification and remove the service from the foreground
        handler.post(() -> {
            downloadService.stopForeground(true);

            if (persistentPlayingShowing) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ID_PLAYING);
                persistentPlayingShowing = false;
            }
        });

        // Get downloadNotification in foreground if playing
        if (downloadShowing) {
            showDownloadingNotification(context, downloadService, handler, downloadService.getCurrentDownloading(), downloadService.getBackgroundDownloads().size());
        }

        // Update widget
        AudinautWidgetProvider.notifyInstances(context, downloadService, false);
    }

    public static void showDownloadingNotification(final Context context, final DownloadService downloadService, Handler handler, DownloadFile file, int size) {
        Intent cancelIntent = new Intent(context, DownloadService.class);
        cancelIntent.setAction(DownloadService.CANCEL_DOWNLOADS);
        PendingIntent cancelPI = PendingIntent.getService(context, 0, cancelIntent, 0);

        String currentDownloading, currentSize;
        if (file != null) {
            currentDownloading = file.getSong().getTitle();
            currentSize = Util.formatLocalizedBytes(file.getEstimatedSize(), context);
        } else {
            currentDownloading = "none";
            currentSize = "0";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(
                    CHANNEL_DOWNLOADING_ID, context.getString(R.string.channel_download_name), importance);
            mChannel.setDescription(context.getString(R.string.channel_download_description));
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                    NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(mChannel);
        }

        Intent notificationIntent = new Intent(context, SubsonicFragmentActivity.class);
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW, true);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final Notification notification = new NotificationCompat.Builder(context, CHANNEL_DOWNLOADING_ID)
                .setChannelId(CHANNEL_DOWNLOADING_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getResources().getString(R.string.download_downloading_title, size))
                .setContentText(context.getResources().getString(R.string.download_downloading_summary, currentDownloading))
                .setOngoing(true)
                .addAction(R.drawable.notification_close,
                        context.getResources().getString(R.string.common_cancel),
                        cancelPI)
                .setContentIntent(PendingIntent.getActivity(context, 2, notificationIntent, 0))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getResources().getString(R.string.download_downloading_summary_expanded, currentDownloading, currentSize)))
                .setProgress(10, 5, true).build();

        downloadShowing = true;
        if (playShowing) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(NOTIFICATION_ID_DOWNLOADING, notification);
        } else {
            downloadForeground = true;
            handler.post(() -> downloadService.startForeground(NOTIFICATION_ID_DOWNLOADING, notification));
        }

    }

    public static void hideDownloadingNotification(final Context context, final DownloadService downloadService, Handler handler) {
        downloadShowing = false;
        if (playShowing) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTIFICATION_ID_DOWNLOADING);
        } else {
            downloadForeground = false;
            handler.post(() -> downloadService.stopForeground(true));
        }
    }
}
