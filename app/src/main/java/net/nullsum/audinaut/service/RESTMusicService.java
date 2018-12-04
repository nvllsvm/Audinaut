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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import net.nullsum.audinaut.domain.Genre;
import net.nullsum.audinaut.domain.Indexes;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.MusicFolder;
import net.nullsum.audinaut.domain.Playlist;
import net.nullsum.audinaut.domain.SearchCritera;
import net.nullsum.audinaut.domain.SearchResult;
import net.nullsum.audinaut.domain.User;
import net.nullsum.audinaut.fragments.MainFragment;
import net.nullsum.audinaut.service.parser.EntryListParser;
import net.nullsum.audinaut.service.parser.ErrorParser;
import net.nullsum.audinaut.service.parser.GenreParser;
import net.nullsum.audinaut.service.parser.IndexesParser;
import net.nullsum.audinaut.service.parser.MusicDirectoryParser;
import net.nullsum.audinaut.service.parser.MusicFoldersParser;
import net.nullsum.audinaut.service.parser.PlaylistParser;
import net.nullsum.audinaut.service.parser.PlaylistsParser;
import net.nullsum.audinaut.service.parser.RandomSongsParser;
import net.nullsum.audinaut.service.parser.SearchResult2Parser;
import net.nullsum.audinaut.service.parser.UserParser;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.Pair;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.SongDBHandler;
import net.nullsum.audinaut.util.Util;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.FormBody.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author Sindre Mehus
 */
public class RESTMusicService implements MusicService {

    private static final String TAG = RESTMusicService.class.getSimpleName();
    private static final OkHttpClient client = new OkHttpClient();
    private Integer instance;

    @Override
    public void ping(Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "ping");

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getMusicFolders");

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new MusicFoldersParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getArtists");

        Builder builder = new FormBody.Builder();

        if (musicFolderId != null) {
            builder.add("musicFolderId", musicFolderId);
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new IndexesParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        SharedPreferences prefs = Util.getPreferences(context);
        String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
        if (cacheLocn != null && id.contains(cacheLocn)) {
            String search = Util.parseOfflineIDSearch(id, cacheLocn);
            SearchCritera critera = new SearchCritera(search, 1, 1, 0);
            SearchResult result = search(critera, context, progressListener);
            if (result.getArtists().size() == 1) {
                id = result.getArtists().get(0).getId();
            } else if (result.getAlbums().size() == 1) {
                id = result.getAlbums().get(0).getId();
            }
        }

        MusicDirectory dir = null;
        int index, start = 0;
        while ((index = id.indexOf(';', start)) != -1) {
            MusicDirectory extra = getMusicDirectoryImpl(id.substring(start, index), name, context);
            if (dir == null) {
                dir = extra;
            } else {
                dir.addChildren(extra.getChildren());
            }

            start = index + 1;
        }
        MusicDirectory extra = getMusicDirectoryImpl(id.substring(start), name, context);
        if (dir == null) {
            dir = extra;
        } else {
            dir.addChildren(extra.getChildren());
        }

        return dir;
    }

    private MusicDirectory getMusicDirectoryImpl(String id, String name, Context context) throws Exception {
        String url = getRestUrl(context, "getMusicDirectory");

        RequestBody formBody = new FormBody.Builder()
                .add("id", id)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new MusicDirectoryParser(context, getInstance(context)).parse(name, response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getArtist");

        RequestBody formBody = new FormBody.Builder()
                .add("id", id)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new MusicDirectoryParser(context, getInstance(context)).parse(name, response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getAlbum");

        RequestBody formBody = new FormBody.Builder()
                .add("id", id)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new MusicDirectoryParser(context, getInstance(context)).parse(name, response.body().byteStream());
        }
    }

    @Override
    public SearchResult search(SearchCritera critera, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "search3");

        Builder builder = new FormBody.Builder();

        builder.add("query", critera.getQuery());
        builder.add("artistCount", Integer.toString(critera.getArtistCount()));
        builder.add("albumCount", Integer.toString(critera.getAlbumCount()));
        builder.add("songCount", Integer.toString(critera.getSongCount()));

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new SearchResult2Parser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getPlaylist");

        RequestBody formBody = new FormBody.Builder()
                .add("id", id)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new PlaylistParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getPlaylists");

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new PlaylistsParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "createPlaylist");

        Builder builder = new FormBody.Builder();

        if (id != null) {
            builder.add("playlistId", id);
        }

        if (name != null) {
            builder.add("name", name);
        }

        for (MusicDirectory.Entry entry : entries) {
            builder.add("songId", getOfflineSongId(entry.getId(), context, progressListener));
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "deletePlaylist");

        RequestBody formBody = new FormBody.Builder()
                .add("id", id)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public void addToPlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "updatePlaylist");

        Builder builder = new FormBody.Builder();
        builder.add("playlistId", id);
        for (MusicDirectory.Entry song : toAdd) {
            builder.add("songIdToAdd", getOfflineSongId(song.getId(), context, progressListener));
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "updatePlaylist");

        Builder builder = new FormBody.Builder();
        builder.add("playlistId", id);

        for (Integer song : toRemove) {
            builder.add("songIndexToRemove", Integer.toString(song));
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public void overwritePlaylist(String id, String name, int toRemove, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "updatePlaylist");

        Builder builder = new FormBody.Builder();
        builder.add("playlistId", id);
        builder.add("name", name);

        for (MusicDirectory.Entry song : toAdd) {
            builder.add("songIdToAdd", getOfflineSongId(song.getId(), context, progressListener));
        }

        for (int i = 0; i < toRemove; i++) {
            builder.add("songIndexToRemove", Integer.toString(i));
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "updatePlaylist");

        Builder builder = new FormBody.Builder();
        builder.add("playlistId", id);
        builder.add("name", name);
        builder.add("comment", comment);
        builder.add("public", Boolean.toString(pub));

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            new ErrorParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getAlbumList2");

        Builder builder = new FormBody.Builder();
        builder.add("type", type);
        builder.add("size", Integer.toString(size));
        builder.add("offset", Integer.toString(offset));

        // Add folder if it was set and is non null
        int instance = getInstance(context);
        if (Util.getAlbumListsPerFolder(context, instance)) {
            String folderId = Util.getSelectedMusicFolderId(context, instance);
            if (folderId != null) {
                builder.add("musicFolderId", folderId);
            }
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new EntryListParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getAlbumList2");

        Builder builder = new FormBody.Builder();
        builder.add("size", Integer.toString(size));
        builder.add("offset", Integer.toString(offset));

        int instance = getInstance(context);
        if ("genres".equals(type)) {
            builder.add("type", "byGenre");
            builder.add("genre", extra);
        } else if ("years".equals(type)) {
            int decade = Integer.parseInt(extra);

            builder.add("type", "byYear");
            builder.add("fromYear", Integer.toString(decade + 9));
            builder.add("toYear", Integer.toString(decade));
        }

        // Add folder if it was set and is non null
        if (Util.getAlbumListsPerFolder(context, instance)) {
            String folderId = Util.getSelectedMusicFolderId(context, instance);
            if (folderId != null) {
                builder.add("musicFolderId", folderId);
            }
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new EntryListParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
        Builder builder = new FormBody.Builder();
        builder.add("size", Integer.toString(size));
        builder.add("offset", Integer.toString(offset));

        String method;
        switch (type) {
            case MainFragment.SONGS_NEWEST:
                method = "getNewaddedSongs";
                break;
            case MainFragment.SONGS_TOP_PLAYED:
                method = "getTopplayedSongs";
                break;
            case MainFragment.SONGS_RECENT:
                method = "getLastplayedSongs";
                break;
            case MainFragment.SONGS_FREQUENT:
                method = "getMostplayedSongs";
                break;
            default:
                method = "getNewaddedSongs";
        }

        String url = getRestUrl(context, method);

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new EntryListParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String musicFolderId, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
        Builder builder = new FormBody.Builder();
        builder.add("size", Integer.toString(size));

        if (genre != null && !"".equals(genre)) {
            builder.add("genre", genre);
        }
        if (startYear != null && !"".equals(startYear)) {
            // Check to make sure user isn't doing 2015 -> 2010 since Subsonic will return no results
            if (endYear != null && !"".equals(endYear)) {
                try {
                    int startYearInt = Integer.parseInt(startYear);
                    int endYearInt = Integer.parseInt(endYear);

                    if (startYearInt > endYearInt) {
                        String tmp = startYear;
                        startYear = endYear;
                        endYear = tmp;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to convert start/end year into ints", e);
                }
            }

            builder.add("fromYear", startYear);
        }
        if (endYear != null && !"".equals(endYear)) {
            builder.add("toYear", endYear);
        }

        String url = getRestUrl(context, "getRandomSongs");

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new RandomSongsParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {

        // Synchronize on the entry so that we don't download concurrently for the same song.
        synchronized (entry) {

            // Use cached file, if existing.
            Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, entry, size);
            if (bitmap != null) {
                return bitmap;
            }

            String url = getRestUrl(context, "getCoverArt");

            Builder builder = new FormBody.Builder();
            builder.add("id", entry.getCoverArt());

            RequestBody formBody = builder.build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                InputStream in = response.body().byteStream();

                byte[] bytes = Util.toByteArray(in);

                // Handle case where partial was downloaded before being cancelled
                if (task != null && task.isCancelled()) {
                    return null;
                }

                OutputStream out = null;
                try {
                    out = new FileOutputStream(FileUtil.getAlbumArtFile(context, entry));
                    out.write(bytes);
                } finally {
                    Util.close(out);
                }

                // Size == 0 -> only want to download
                if (size == 0) {
                    return null;
                } else {
                    return FileUtil.getSampledBitmap(bytes, size);
                }
            }
        }
    }

    @Override
    public Response getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {

        OkHttpClient eagerClient = client.newBuilder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        String url = getRestUrl(context, "stream");

        Builder builder = new FormBody.Builder();
        builder.add("id", song.getId());
        builder.add("maxBitRate", Integer.toString(maxBitrate));

        RequestBody formBody = builder.build();

        Request.Builder requestBuilder = new Request.Builder();
        if (offset > 0) {
            requestBuilder.header("Range", "bytes=" + offset + "-");
        }

        requestBuilder.url(url);
        requestBuilder.post(formBody);

        Request request = requestBuilder.build();

        return eagerClient.newCall(request).execute();
    }


    @Override
    public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getGenres");

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new GenreParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
        Builder builder = new FormBody.Builder();
        builder.add("genre", genre);
        builder.add("count", Integer.toString(count));
        builder.add("offset", Integer.toString(offset));

        // Add folder if it was set and is non null
        int instance = getInstance(context);
        if (Util.getAlbumListsPerFolder(context, instance)) {
            String folderId = Util.getSelectedMusicFolderId(context, instance);
            if (folderId != null) {
                builder.add("musicFolderId", folderId);
            }
        }

        String url = getRestUrl(context, "getSongsByGenre");

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new RandomSongsParser(context, getInstance(context)).parse(response.body().byteStream());
        }
    }

    @Override
    public User getUser(boolean refresh, String username, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getUser");

        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            List<User> users = new UserParser(context, getInstance(context)).parse(response.body().byteStream());
            if (users.size() > 0) {
                // Should only have returned one anyways
                return users.get(0);
            } else {
                return null;
            }
        }
    }

    private String getOfflineSongId(String id, Context context, ProgressListener progressListener) throws Exception {
        SharedPreferences prefs = Util.getPreferences(context);
        String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
        if (cacheLocn != null && id.contains(cacheLocn)) {
            Pair<Integer, String> cachedSongId = SongDBHandler.getHandler(context).getIdFromPath(Util.getRestUrlHash(context, getInstance(context)), id);
            if (cachedSongId != null) {
                id = cachedSongId.getSecond();
            } else {
                String searchCriteria = Util.parseOfflineIDSearch(id, cacheLocn);
                SearchCritera critera = new SearchCritera(searchCriteria, 0, 0, 1);
                SearchResult result = search(critera, context, progressListener);
                if (result.getSongs().size() == 1) {
                    id = result.getSongs().get(0).getId();
                }
            }
        }

        return id;
    }

    @Override
    public void setInstance(Integer instance) {
        this.instance = instance;
    }

    public int getInstance(Context context) {
        if (instance == null) {
            return Util.getActiveServer(context);
        } else {
            return instance;
        }
    }


    public String getRestUrl(Context context, String method) {
        return getRestUrl(context, method, true);
    }

    public String getRestUrl(Context context, String method, boolean allowAltAddress) {
        if (instance == null) {
            return Util.getRestUrl(context, method, allowAltAddress);
        } else {
            return Util.getRestUrl(context, method, instance, allowAltAddress);
        }
    }
}
