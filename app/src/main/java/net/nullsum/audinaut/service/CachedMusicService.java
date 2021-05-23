/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.nullsum.audinaut.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import net.nullsum.audinaut.domain.Artist;
import net.nullsum.audinaut.domain.Genre;
import net.nullsum.audinaut.domain.Indexes;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.MusicFolder;
import net.nullsum.audinaut.domain.Playlist;
import net.nullsum.audinaut.domain.SearchCritera;
import net.nullsum.audinaut.domain.SearchResult;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.SongDBHandler;
import net.nullsum.audinaut.util.TimeLimitedCache;
import net.nullsum.audinaut.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import okhttp3.Response;

import static net.nullsum.audinaut.domain.MusicDirectory.Entry;

/**
 * @author Sindre Mehus
 */
public class CachedMusicService implements MusicService {
    public static final int CACHE_UPDATE_LIST = 1;
    private static final int CACHE_UPDATE_METADATA = 2;
    private static final String TAG = CachedMusicService.class.getSimpleName();

    private final RESTMusicService musicService;
    private final TimeLimitedCache<Indexes> cachedIndexes = new TimeLimitedCache<>(60 * 60);
    private final TimeLimitedCache<List<Playlist>> cachedPlaylists = new TimeLimitedCache<>(3600);
    private final TimeLimitedCache<List<MusicFolder>> cachedMusicFolders = new TimeLimitedCache<>(10 * 3600);
    private String restUrl;
    private String musicFolderId;

    public CachedMusicService(RESTMusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public void ping(Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        musicService.ping(context, progressListener);
    }

    @Override
    public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        if (refresh) {
            cachedMusicFolders.clear();
        }
        List<MusicFolder> result = cachedMusicFolders.get();
        if (result == null) {
            if (!refresh) {
                result = FileUtil.deserialize(context, getCacheName(context, "musicFolders"), ArrayList.class);
            }

            if (result == null) {
                result = musicService.getMusicFolders(refresh, context, progressListener);
                FileUtil.serialize(context, new ArrayList<>(result), getCacheName(context, "musicFolders"));
            }

            MusicFolder.sort(result);
            cachedMusicFolders.set(result);
        }
        return result;
    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        if (refresh) {
            cachedIndexes.clear();
            cachedMusicFolders.clear();
        }
        Indexes result = cachedIndexes.get();
        if (result == null) {
            String name = "artists";
            name = getCacheName(context, name, musicFolderId);
            if (!refresh) {
                result = FileUtil.deserialize(context, name, Indexes.class);
            }

            if (result == null) {
                result = musicService.getIndexes(musicFolderId, refresh, context, progressListener);
                FileUtil.serialize(context, result, name);
            }
            cachedIndexes.set(result);
        }
        return result;
    }

    @Override
    public MusicDirectory getMusicDirectory(final String id, final String name, final boolean refresh, final Context context, final ProgressListener progressListener) throws Exception {
        MusicDirectory dir = null;
        final MusicDirectory cached = FileUtil.deserialize(context, getCacheName(context, "directory", id), MusicDirectory.class);
        if (!refresh && cached != null) {
            dir = cached;

            new SilentBackgroundTask<Void>(context) {
                MusicDirectory refreshed;
                private boolean metadataUpdated;

                @Override
                protected Void doInBackground() throws Throwable {
                    refreshed = musicService.getMusicDirectory(id, name, true, context, null);
                    updateAllSongs(context, refreshed);
                    metadataUpdated = cached.updateMetadata(refreshed);
                    deleteRemovedEntries(context, refreshed, cached);
                    FileUtil.serialize(context, refreshed, getCacheName(context, "directory", id));
                    return null;
                }

                // Update which entries exist
                @Override
                public void done(Void result) {
                    if (progressListener != null) {
                        if (cached.updateEntriesList(context, refreshed)) {
                            progressListener.updateCache(CACHE_UPDATE_LIST);
                        }
                        if (metadataUpdated) {
                            progressListener.updateCache(CACHE_UPDATE_METADATA);
                        }
                    }
                }

                @Override
                public void error(Throwable error) {
                    Log.e(TAG, "Failed to refresh music directory", error);
                }
            }.execute();
        }

        if (dir == null) {
            dir = musicService.getMusicDirectory(id, name, refresh, context, progressListener);
            updateAllSongs(context, dir);
            FileUtil.serialize(context, dir, getCacheName(context, "directory", id));

            // If a cached copy exists to check against, look for removes
            deleteRemovedEntries(context, dir, cached);
        }
        dir.sortChildren(context);

        return dir;
    }

    @Override
    public MusicDirectory getArtist(final String id, final String name, final boolean refresh, final Context context, final ProgressListener progressListener) throws Exception {
        MusicDirectory dir = null;
        final MusicDirectory cached = FileUtil.deserialize(context, getCacheName(context, "artist", id), MusicDirectory.class);
        if (!refresh && cached != null) {
            dir = cached;

            new SilentBackgroundTask<Void>(context) {
                MusicDirectory refreshed;

                @Override
                protected Void doInBackground() throws Throwable {
                    refreshed = musicService.getArtist(id, name, false, context, null);
                    cached.updateMetadata(refreshed);
                    deleteRemovedEntries(context, refreshed, cached);
                    FileUtil.serialize(context, refreshed, getCacheName(context, "artist", id));
                    return null;
                }

                // Update which entries exist
                @Override
                public void done(Void result) {
                    if (progressListener != null) {
                        if (cached.updateEntriesList(context, refreshed)) {
                            progressListener.updateCache(CACHE_UPDATE_LIST);
                        }
                    }
                }

                @Override
                public void error(Throwable error) {
                    Log.e(TAG, "Failed to refresh getArtist", error);
                }
            }.execute();
        }

        if (dir == null) {
            dir = musicService.getArtist(id, name, refresh, context, progressListener);
            FileUtil.serialize(context, dir, getCacheName(context, "artist", id));

            // If a cached copy exists to check against, look for removes
            deleteRemovedEntries(context, dir, cached);
        }
        dir.sortChildren(context);

        return dir;
    }

    @Override
    public MusicDirectory getAlbum(final String id, final String name, final boolean refresh, final Context context, final ProgressListener progressListener) throws Exception {
        MusicDirectory dir = null;
        final MusicDirectory cached = FileUtil.deserialize(context, getCacheName(context, "album", id), MusicDirectory.class);
        if (!refresh && cached != null) {
            dir = cached;

            new SilentBackgroundTask<Void>(context) {
                MusicDirectory refreshed;
                private boolean metadataUpdated;

                @Override
                protected Void doInBackground() throws Throwable {
                    refreshed = musicService.getAlbum(id, name, false, context, null);
                    updateAllSongs(context, refreshed);
                    metadataUpdated = cached.updateMetadata(refreshed);
                    deleteRemovedEntries(context, refreshed, cached);
                    FileUtil.serialize(context, refreshed, getCacheName(context, "album", id));
                    return null;
                }

                // Update which entries exist
                @Override
                public void done(Void result) {
                    if (progressListener != null) {
                        if (cached.updateEntriesList(context, refreshed)) {
                            progressListener.updateCache(CACHE_UPDATE_LIST);
                        }
                        if (metadataUpdated) {
                            progressListener.updateCache(CACHE_UPDATE_METADATA);
                        }
                    }
                }

                @Override
                public void error(Throwable error) {
                    Log.e(TAG, "Failed to refresh getAlbum", error);
                }
            }.execute();
        }

        if (dir == null) {
            dir = musicService.getAlbum(id, name, refresh, context, progressListener);
            updateAllSongs(context, dir);
            FileUtil.serialize(context, dir, getCacheName(context, "album", id));

            // If a cached copy exists to check against, look for removes
            deleteRemovedEntries(context, dir, cached);
        }
        dir.sortChildren(context);

        return dir;
    }

    @Override
    public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception {
        return musicService.search(criteria, context, progressListener);
    }

    @Override
    public MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception {
        MusicDirectory dir = null;
        MusicDirectory cachedPlaylist = FileUtil.deserialize(context, getCacheName(context, "playlist", id), MusicDirectory.class);
        if (!refresh) {
            dir = cachedPlaylist;
        }
        if (dir == null) {
            dir = musicService.getPlaylist(refresh, id, name, context, progressListener);
            updateAllSongs(context, dir);
            FileUtil.serialize(context, dir, getCacheName(context, "playlist", id));

            File playlistFile = FileUtil.getPlaylistFile(context, Util.getServerName(context, musicService.getInstance(context)), dir.getName());
            if (cachedPlaylist == null || !playlistFile.exists() || !cachedPlaylist.getChildren().equals(dir.getChildren())) {
                FileUtil.writePlaylistFile(context, playlistFile, dir);
            }
        }
        return dir;
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        List<Playlist> result = refresh ? null : cachedPlaylists.get();
        if (result == null) {
            if (!refresh) {
                result = FileUtil.deserialize(context, getCacheName(context, "playlist"), ArrayList.class);
            }

            if (result == null) {
                result = musicService.getPlaylists(refresh, context, progressListener);
                FileUtil.serialize(context, new ArrayList<>(result), getCacheName(context, "playlist"));
            }
            cachedPlaylists.set(result);
        }
        return result;
    }

    @Override
    public void createPlaylist(String id, String name, List<Entry> entries, Context context, ProgressListener progressListener) throws Exception {
        cachedPlaylists.clear();
        Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist")));
        musicService.createPlaylist(id, name, entries, context, progressListener);
    }

    @Override
    public void deletePlaylist(final String id, Context context, ProgressListener progressListener) throws Exception {
        musicService.deletePlaylist(id, context, progressListener);

        new PlaylistUpdater(context, id) {
            @Override
            public void updateResult(List<Playlist> objects, Playlist result) {
                objects.remove(result);
                cachedPlaylists.set(objects);
            }
        }.execute();
    }

    @Override
    public void addToPlaylist(String id, final List<Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
        musicService.addToPlaylist(id, toAdd, context, progressListener);

        new MusicDirectoryUpdater(context, "playlist", id) {
            @Override
            public boolean checkResult(Entry check) {
                return true;
            }

            @Override
            public void updateResult(List<Entry> objects, Entry result) {
                objects.addAll(toAdd);
            }
        }.execute();
    }

    @Override
    public void removeFromPlaylist(final String id, final List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception {
        musicService.removeFromPlaylist(id, toRemove, context, progressListener);

        new MusicDirectoryUpdater(context, "playlist", id) {
            @Override
            public boolean checkResult(Entry check) {
                return true;
            }

            @Override
            public void updateResult(List<Entry> objects, Entry result) {
                // Remove in reverse order so indexes are still correct as we iterate through
                for (ListIterator<Integer> iterator = toRemove.listIterator(toRemove.size()); iterator.hasPrevious(); ) {
                    int index = iterator.previous();

                    objects.remove(index);
                }
            }
        }.execute();
    }

    @Override
    public void overwritePlaylist(String id, String name, int toRemove, final List<Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
        musicService.overwritePlaylist(id, name, toRemove, toAdd, context, progressListener);

        new MusicDirectoryUpdater(context, "playlist", id) {
            @Override
            public boolean checkResult(Entry check) {
                return true;
            }

            @Override
            public void updateResult(List<Entry> objects, Entry result) {
                objects.clear();
                objects.addAll(toAdd);
            }
        }.execute();
    }

    @Override
    public void updatePlaylist(String id, final String name, final String comment, final boolean pub, Context context, ProgressListener progressListener) throws Exception {
        musicService.updatePlaylist(id, name, comment, pub, context, progressListener);

        new PlaylistUpdater(context, id) {
            @Override
            public void updateResult(List<Playlist> objects, Playlist result) {
                result.setName(name);
                result.setComment(comment);
                result.setPublic(pub);

                cachedPlaylists.set(objects);
            }
        }.execute();
    }

    @Override
    public MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        try {
            MusicDirectory dir = musicService.getAlbumList(type, size, offset, refresh, context, progressListener);

            // Do some serialization updates for changes to recently added
            if ("newest".equals(type) && offset == 0) {
                String recentlyAddedFile = getCacheName(context, type);
                ArrayList<String> recents = FileUtil.deserialize(context, recentlyAddedFile, ArrayList.class);
                if (recents == null) {
                    recents = new ArrayList<>();
                }

                // Add any new items
                for (final Entry album : dir.getChildren()) {
                    if (!recents.contains(album.getId())) {
                        recents.add(album.getId());

                        String cacheName, parent;
                        cacheName = "artist";
                        parent = album.getArtistId();

                        // Add album to artist
                        if (parent != null) {
                            new MusicDirectoryUpdater(context, cacheName, parent) {
                                private boolean changed = false;

                                @Override
                                public boolean checkResult(Entry check) {
                                    return true;
                                }

                                @Override
                                public void updateResult(List<Entry> objects, Entry result) {
                                    // Only add if it doesn't already exist in it!
                                    if (!objects.contains(album)) {
                                        objects.add(album);
                                        changed = true;
                                    }
                                }

                                @Override
                                public void save(ArrayList<Entry> objects) {
                                    // Only save if actually added to artist
                                    if (changed) {
                                        musicDirectory.replaceChildren(objects);
                                        FileUtil.serialize(context, musicDirectory, cacheName);
                                    }
                                }
                            }.execute();
                        } else {
                            // If parent is null, then this is a root level album
                            final Artist artist = new Artist();
                            artist.setId(album.getId());
                            artist.setName(album.getTitle());

                            new IndexesUpdater(context) {
                                private boolean changed = false;

                                @Override
                                public boolean checkResult(Artist check) {
                                    return true;
                                }

                                @Override
                                public void updateResult(List<Artist> objects, Artist result) {
                                    if (!objects.contains(artist)) {
                                        objects.add(artist);
                                        changed = true;
                                    }
                                }

                                @Override
                                public void save(ArrayList<Artist> objects) {
                                    if (changed) {
                                        indexes.setArtists(objects);
                                        FileUtil.serialize(context, indexes, cacheName);
                                        cachedIndexes.set(indexes);
                                    }
                                }
                            }.execute();
                        }
                    }
                }

                // Keep list from growing into infinity
                while (recents.size() > 0) {
                    recents.remove(0);
                }
                FileUtil.serialize(context, recents, recentlyAddedFile);
            }

            FileUtil.serialize(context, dir, getCacheName(context, type, Integer.toString(offset)));
            return dir;
        } catch (IOException e) {
            Log.w(TAG, "Failed to refresh album list: ", e);
            if (refresh) {
                throw e;
            }

            MusicDirectory dir = FileUtil.deserialize(context, getCacheName(context, type, Integer.toString(offset)), MusicDirectory.class);

            if (dir == null) {
                // If we are at start and no cache, throw error higher
                if (offset == 0) {
                    throw e;
                } else {
                    // Otherwise just pretend we are at the end of the list
                    return new MusicDirectory();
                }
            } else {
                return dir;
            }
        }
    }

    @Override
    public MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        try {
            MusicDirectory dir = musicService.getAlbumList(type, extra, size, offset, refresh, context, progressListener);
            FileUtil.serialize(context, dir, getCacheName(context, type + extra, Integer.toString(offset)));
            return dir;
        } catch (IOException e) {
            Log.w(TAG, "Failed to refresh album list: ", e);
            if (refresh) {
                throw e;
            }

            MusicDirectory dir = FileUtil.deserialize(context, getCacheName(context, type + extra, Integer.toString(offset)), MusicDirectory.class);

            if (dir == null) {
                // If we are at start and no cache, throw error higher
                if (offset == 0) {
                    throw e;
                } else {
                    // Otherwise just pretend we are at the end of the list
                    return new MusicDirectory();
                }
            } else {
                return dir;
            }
        }
    }

    @Override
    public MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getSongList(type, size, offset, context, progressListener);
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getRandomSongs(size, folder, genre, startYear, endYear, context, progressListener);
    }

    @Override
    public Bitmap getCoverArt(Context context, Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
        return musicService.getCoverArt(context, entry, size, progressListener, task);
    }

    @Override
    public Response getDownloadInputStream(Context context, Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {
        return musicService.getDownloadInputStream(context, song, offset, maxBitrate, task);
    }

    @Override
    public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        List<Genre> result = null;

        if (!refresh) {
            result = FileUtil.deserialize(context, getCacheName(context, "genre"), ArrayList.class);
        }

        if (result == null) {
            result = musicService.getGenres(refresh, context, progressListener);
            FileUtil.serialize(context, new ArrayList<>(result), getCacheName(context, "genre"));
        }

        return result;
    }

    @Override
    public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
        try {
            MusicDirectory dir = musicService.getSongsByGenre(genre, count, offset, context, progressListener);
            FileUtil.serialize(context, dir, getCacheName(context, "genreSongs", Integer.toString(offset)));

            return dir;
        } catch (IOException e) {
            MusicDirectory dir = FileUtil.deserialize(context, getCacheName(context, "genreSongs", Integer.toString(offset)), MusicDirectory.class);

            if (dir == null) {
                // If we are at start and no cache, throw error higher
                if (offset == 0) {
                    throw e;
                } else {
                    // Otherwise just pretend we are at the end of the list
                    return new MusicDirectory();
                }
            } else {
                return dir;
            }
        }
    }

    @Override
    public void startScan(Context c) throws Exception {
        musicService.startScan(c);
    }

    @Override
    public void setInstance(Integer instance) {
        musicService.setInstance(instance);
    }

    private String getCacheName(Context context, String name, String id) {
        String s = musicService.getRestUrl(context, null, false, null) + id;
        return name + "-" + s.hashCode() + ".ser";
    }

    private String getCacheName(Context context, String name) {
        String s = musicService.getRestUrl(context, null, false, null);
        return name + "-" + s.hashCode() + ".ser";
    }

    private void deleteRemovedEntries(Context context, MusicDirectory dir, MusicDirectory cached) {
        if (cached != null) {
            List<Entry> oldList = new ArrayList<>(cached.getChildren());
            oldList.removeAll(dir.getChildren());

            // Anything remaining has been removed from server
            MediaStoreService store = new MediaStoreService(context);
            for (Entry entry : oldList) {
                File file = FileUtil.getEntryFile(context, entry);
                FileUtil.recursiveDelete(file, store);
            }
        }
    }

    private void updateAllSongs(Context context, MusicDirectory dir) {
        List<Entry> songs = dir.getSongs();
        if (!songs.isEmpty()) {
            SongDBHandler.getHandler(context).addSongs(musicService.getInstance(context), songs);
        }
    }

    private void checkSettingsChanged(Context context) {
        int instance = musicService.getInstance(context);
        String newUrl = musicService.getRestUrl(context, null, false, null);
        if (!Util.equals(newUrl, restUrl)) {
            cachedMusicFolders.clear();
            cachedIndexes.clear();
            cachedPlaylists.clear();
            restUrl = newUrl;
        }

        String newMusicFolderId = Util.getSelectedMusicFolderId(context, instance);
        if (!Util.equals(newMusicFolderId, musicFolderId)) {
            cachedIndexes.clear();
            musicFolderId = newMusicFolderId;
        }
    }

    private abstract class SerializeUpdater<T> {
        final Context context;
        final String cacheName;
        final boolean singleUpdate;

        public SerializeUpdater(Context context) {
            this.context = context;
            this.cacheName = getCacheName(context, "playlist");
            this.singleUpdate = true;
        }

        public SerializeUpdater(Context context, String cacheName, String id) {
            this.context = context;
            this.cacheName = getCacheName(context, cacheName, id);
            this.singleUpdate = true;
        }

        public ArrayList<T> getArrayList() {
            return FileUtil.deserialize(context, cacheName, ArrayList.class);
        }

        public abstract boolean checkResult(T check);

        public abstract void updateResult(List<T> objects, T result);

        public void save(ArrayList<T> objects) {
            FileUtil.serialize(context, objects, cacheName);
        }

        public void execute() {
            ArrayList<T> objects = getArrayList();

            // Only execute if something to check against
            if (objects != null) {
                List<T> results = new ArrayList<>();
                for (T check : objects) {
                    if (checkResult(check)) {
                        results.add(check);
                        if (singleUpdate) {
                            break;
                        }
                    }
                }

                // Iterate through and update each object matched
                for (T result : results) {
                    updateResult(objects, result);
                }

                // Only reserialize if at least one match was found
                if (results.size() > 0) {
                    save(objects);
                }
            }
        }
    }

    private abstract class PlaylistUpdater extends SerializeUpdater<Playlist> {
        final String id;

        public PlaylistUpdater(Context context, String id) {
            super(context);
            this.id = id;
        }

        @Override
        public boolean checkResult(Playlist check) {
            return id.equals(check.getId());
        }
    }

    private abstract class MusicDirectoryUpdater extends SerializeUpdater<Entry> {
        MusicDirectory musicDirectory;

        public MusicDirectoryUpdater(Context context, String cacheName, String id) {
            super(context, cacheName, id);
        }

        @Override
        public ArrayList<Entry> getArrayList() {
            musicDirectory = FileUtil.deserialize(context, cacheName, MusicDirectory.class);
            if (musicDirectory != null) {
                return new ArrayList<>(musicDirectory.getChildren());
            } else {
                return null;
            }
        }

        public void save(ArrayList<Entry> objects) {
            musicDirectory.replaceChildren(objects);
            FileUtil.serialize(context, musicDirectory, cacheName);
        }
    }

    private abstract class IndexesUpdater extends SerializeUpdater<Artist> {
        Indexes indexes;

        IndexesUpdater(Context context) {
            super(context, "artists", Util.getSelectedMusicFolderId(context, musicService.getInstance(context)));
        }

        @Override
        public ArrayList<Artist> getArrayList() {
            indexes = FileUtil.deserialize(context, cacheName, Indexes.class);
            if (indexes == null) {
                return null;
            }

            ArrayList<Artist> artists = new ArrayList<>();
            artists.addAll(indexes.getArtists());
            artists.addAll(indexes.getShortcuts());
            return artists;
        }

        public void save(ArrayList<Artist> objects) {
            indexes.setArtists(objects);
            FileUtil.serialize(context, indexes, cacheName);
            cachedIndexes.set(indexes);
        }
    }
}
