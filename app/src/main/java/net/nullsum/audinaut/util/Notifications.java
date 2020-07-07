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
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.MediaMetadataCompat;

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
        Intent notificationIntent = new Intent(context, SubsonicFragmentActivity.class);
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, true);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent cancelIntent = new Intent("KEYCODE_MEDIA_STOP")
                .setComponent(new ComponentName(context, DownloadService.class))
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP));
        int[] compactActions = new int[]{0, 1, 2};

        MediaSessionCompat mediaSession = new MediaSessionCompat(context, "Audinaut");
        MediaSessionCompat.Token mediaToken = mediaSession.getSessionToken();
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

        mediaSession.setMetadata(metadataBuilder
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getAlbumArt(context, song))
                //.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, R.drawable.notification_logo)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                .build() );

        MediaStyle mediaStyle = new MediaStyle()
                .setShowActionsInCompactView(compactActions)
                .setShowCancelButton(true)
                .setCancelButtonIntent(PendingIntent.getService(context, 0, cancelIntent, 0))
                .setMediaSession(mediaToken);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_PLAYING_ID)
                .setChannelId(CHANNEL_PLAYING_ID)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setSubText(song.getAlbum())
                .setTicker(song.getTitle())
                .setOngoing(playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setLargeIcon(getAlbumArt(context, song))
                .setSmallIcon(R.drawable.notification_logo)
                .setStyle(mediaStyle)
                .setContentIntent(PendingIntent.getActivity(context, 0, notificationIntent, 0));
        addActions(context, builder, playing);
        final Notification notification = builder.build();

        if(playing) {
            notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        }

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

    private static Bitmap getAlbumArt(Context context, MusicDirectory.Entry song) {
        try {
            ImageLoader imageLoader = SubsonicActivity.getStaticImageLoader(context);
            Bitmap bitmap = null;
            if (imageLoader != null) {
                bitmap = imageLoader.getCachedImage(context, song, false);
            }
            if (bitmap == null) {
                // set default album art
                return BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_album);
            } else {
                return bitmap;
            }
        } catch (Exception x) {
            Log.w(TAG, "Failed to get notification cover art", x);
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_album);
        }
    }

    private static void addActions(final Context context, final NotificationCompat.Builder builder, final boolean playing) {
        PendingIntent pendingIntent;

        Intent prevIntent = new Intent("KEYCODE_MEDIA_PREVIOUS");
        prevIntent.setComponent(new ComponentName(context, DownloadService.class));
        prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0);
        builder.addAction(R.drawable.notification_media_backward, "Previous", pendingIntent);
        if (playing) {
            Intent pauseIntent = new Intent("KEYCODE_MEDIA_PLAY_PAUSE");
            pauseIntent.setComponent(new ComponentName(context, DownloadService.class));
            pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            pendingIntent = PendingIntent.getService(context, 0, pauseIntent, 0);
            builder.addAction(R.drawable.notification_media_pause, "Pause", pendingIntent);
        } else {
            Intent playIntent = new Intent("KEYCODE_MEDIA_PLAY");
            playIntent.setComponent(new ComponentName(context, DownloadService.class));
            playIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
            pendingIntent = PendingIntent.getService(context, 0, playIntent, 0);
            builder.addAction(R.drawable.notification_media_start, "Play", pendingIntent);
        }
        Intent nextIntent = new Intent("KEYCODE_MEDIA_NEXT");
        nextIntent.setComponent(new ComponentName(context, DownloadService.class));
        nextIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
        pendingIntent = PendingIntent.getService(context, 0, nextIntent, 0);
        builder.addAction(R.drawable.notification_media_forward, "Next", pendingIntent);
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
