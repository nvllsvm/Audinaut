package net.nullsum.audinaut.util;

import android.content.Context;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Scott on 11/24/13.
 */
public final class SyncUtil {
    private static ArrayList<SyncSet> syncedPlaylists;
    private static String url;

    private static void checkRestURL(Context context) {
        int instance = Util.getActiveServer(context);
        String newURL = Util.getRestUrl(context, null, instance, false);
        if (url == null || !url.equals(newURL)) {
            syncedPlaylists = null;
            url = newURL;
        }
    }

    // Playlist sync
    public static boolean isSyncedPlaylist(Context context, String playlistId) {
        checkRestURL(context);
        if (syncedPlaylists == null) {
            syncedPlaylists = getSyncedPlaylists(context);
        }
        return syncedPlaylists.contains(new SyncSet(playlistId));
    }

    private static ArrayList<SyncSet> getSyncedPlaylists(Context context) {
        return getSyncedPlaylists(context, Util.getActiveServer(context));
    }

    private static ArrayList<SyncSet> getSyncedPlaylists(Context context, int instance) {
        String syncFile = getPlaylistSyncFile(context, instance);
        ArrayList<SyncSet> playlists = FileUtil.deserializeCompressed(context, syncFile, ArrayList.class);
        if (playlists == null) {
            playlists = new ArrayList<>();

            // Try to convert old style into new style
            ArrayList<String> oldPlaylists = FileUtil.deserialize(context, syncFile, ArrayList.class);
            // If exists, time to convert!
            if (oldPlaylists != null) {
                for (String id : oldPlaylists) {
                    playlists.add(new SyncSet(id));
                }

                FileUtil.serializeCompressed(context, playlists, syncFile);
            }
        }
        return playlists;
    }

    public static void removeSyncedPlaylist(Context context, String playlistId) {
        int instance = Util.getActiveServer(context);
        removeSyncedPlaylist(context, playlistId, instance);
    }

    private static void removeSyncedPlaylist(Context context, String playlistId, int instance) {
        String playlistFile = getPlaylistSyncFile(context, instance);
        ArrayList<SyncSet> playlists = getSyncedPlaylists(context, instance);
        SyncSet set = new SyncSet(playlistId);
        if (playlists.contains(set)) {
            playlists.remove(set);
            FileUtil.serializeCompressed(context, playlists, playlistFile);
            syncedPlaylists = playlists;
        }
    }

    private static String getPlaylistSyncFile(Context context, int instance) {
        return "sync-playlist-" + (Util.getRestUrl(context, null, instance, false)).hashCode() + ".ser";
    }

    public static void removeMostRecentSyncFiles(Context context) {
        int total = Util.getServerCount(context);
        for (int i = 0; i < total; i++) {
            File file = new File(context.getCacheDir(), getMostRecentSyncFile(context, i));
            file.delete();
        }
    }

    private static String getMostRecentSyncFile(Context context, int instance) {
        return "sync-most_recent-" + (Util.getRestUrl(context, null, instance, false)).hashCode() + ".ser";
    }

    public static class SyncSet implements Serializable {
        public final String id;

        public SyncSet(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SyncSet && this.id.equals(((SyncSet) obj).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
