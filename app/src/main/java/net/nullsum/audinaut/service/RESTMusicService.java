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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import java.util.concurrent.TimeUnit;


import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.FormBody;
import okhttp3.FormBody.Builder;
import okhttp3.RequestBody;
import okhttp3.Call;
import okhttp3.Credentials;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;
import android.util.Log;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.*;
import net.nullsum.audinaut.fragments.MainFragment;
import net.nullsum.audinaut.service.parser.EntryListParser;
import net.nullsum.audinaut.service.parser.ErrorParser;
import net.nullsum.audinaut.service.parser.GenreParser;
import net.nullsum.audinaut.service.parser.IndexesParser;
import net.nullsum.audinaut.service.parser.MusicDirectoryParser;
import net.nullsum.audinaut.service.parser.MusicFoldersParser;
import net.nullsum.audinaut.service.parser.PlayQueueParser;
import net.nullsum.audinaut.service.parser.PlaylistParser;
import net.nullsum.audinaut.service.parser.PlaylistsParser;
import net.nullsum.audinaut.service.parser.RandomSongsParser;
import net.nullsum.audinaut.service.parser.SearchResult2Parser;
import net.nullsum.audinaut.service.parser.UserParser;
import net.nullsum.audinaut.service.ssl.SSLSocketFactory;
import net.nullsum.audinaut.service.ssl.TrustSelfSignedStrategy;
import net.nullsum.audinaut.util.BackgroundTask;
import net.nullsum.audinaut.util.Pair;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.SongDBHandler;
import net.nullsum.audinaut.util.Util;
import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * @author Sindre Mehus
 */
public class RESTMusicService implements MusicService {

    private static OkHttpClient client = new OkHttpClient();
    private static final String TAG = RESTMusicService.class.getSimpleName();

    private static final int SOCKET_CONNECT_TIMEOUT = 10 * 1000;
    private static final int SOCKET_READ_TIMEOUT_DEFAULT = 10 * 1000;
    private static final int SOCKET_READ_TIMEOUT_DOWNLOAD = 30 * 1000;
    private static final int SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS = 60 * 1000;
    private static final int SOCKET_READ_TIMEOUT_GET_PLAYLIST = 60 * 1000;

    // Allow 20 seconds extra timeout per MB offset.
    private static final double TIMEOUT_MILLIS_PER_OFFSET_BYTE = 20000.0 / 1000000.0;

    private static final int HTTP_REQUEST_MAX_ATTEMPTS = 5;
    private static final long REDIRECTION_CHECK_INTERVAL_MILLIS = 60L * 60L * 1000L;

    private final DefaultHttpClient httpClient;
    private long redirectionLastChecked;
    private int redirectionNetworkType = -1;
    private String redirectFrom;
    private String redirectTo;
    private final ThreadSafeClientConnManager connManager;
    private Integer instance;

    public RESTMusicService() {

        // Create and initialize default HTTP parameters
        HttpParams params = new BasicHttpParams();
        ConnManagerParams.setMaxTotalConnections(params, 20);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(20));
        HttpConnectionParams.setConnectionTimeout(params, SOCKET_CONNECT_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_DEFAULT);

        // Turn off stale checking.  Our connections break all the time anyway,
        // and it's not worth it to pay the penalty of checking every time.
        HttpConnectionParams.setStaleCheckingEnabled(params, false);

        // Create and initialize scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", createSSLSocketFactory(), 443));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        connManager = new ThreadSafeClientConnManager(params, schemeRegistry);
        httpClient = new DefaultHttpClient(connManager, params);
    }

    private SocketFactory createSSLSocketFactory() {
        try {
            return new SSLSocketFactory(new TrustSelfSignedStrategy(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        } catch (Throwable x) {
            Log.e(TAG, "Failed to create custom SSL socket factory, using default.", x);
            return org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
        }
    }

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
            return new MusicFoldersParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getArtists");

        Builder builder= new FormBody.Builder();

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
        if(cacheLocn != null && id.indexOf(cacheLocn) != -1) {
            String search = Util.parseOfflineIDSearch(context, id, cacheLocn);
            SearchCritera critera = new SearchCritera(search, 1, 1, 0);
            SearchResult result = search(critera, context, progressListener);
            if(result.getArtists().size() == 1) {
                id = result.getArtists().get(0).getId();
            } else if(result.getAlbums().size() == 1) {
                id = result.getAlbums().get(0).getId();
            }
        }

        MusicDirectory dir = null;
        int index, start = 0;
        while((index = id.indexOf(';', start)) != -1) {
            MusicDirectory extra = getMusicDirectoryImpl(id.substring(start, index), name, refresh, context, progressListener);
            if(dir == null) {
                dir = extra;
            } else {
                dir.addChildren(extra.getChildren());
            }

            start = index + 1;
        }
        MusicDirectory extra = getMusicDirectoryImpl(id.substring(start), name, refresh, context, progressListener);
        if(dir == null) {
            dir = extra;
        } else {
            dir.addChildren(extra.getChildren());
        }

        return dir;
    }

    private MusicDirectory getMusicDirectoryImpl(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getMusicDirectory");

        RequestBody formBody = new FormBody.Builder()
            .add("id", id)
            .build();

        Request request = new Request.Builder()
            .url(url)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new MusicDirectoryParser(context, getInstance(context)).parse(name, response.body().byteStream(), progressListener);
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
            return new MusicDirectoryParser(context, getInstance(context)).parse(name, response.body().byteStream(), progressListener);
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
            return new MusicDirectoryParser(context, getInstance(context)).parse(name, response.body().byteStream(), progressListener);
        }
    }

    @Override
    public SearchResult search(SearchCritera critera, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "search3");

        Builder builder= new FormBody.Builder();

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
            return new SearchResult2Parser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
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
            return new PlaylistParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getPlaylists");

        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new PlaylistsParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "createPlaylist");

        Builder builder= new FormBody.Builder();

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

        Builder builder= new FormBody.Builder();
        builder.add("playlistId", id);
        for(MusicDirectory.Entry song: toAdd) {
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

        Builder builder= new FormBody.Builder();
        builder.add("playlistId", id);

        for(Integer song: toRemove) {
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

        Builder builder= new FormBody.Builder();
        builder.add("playlistId", id);
        builder.add("name", name);

        for(MusicDirectory.Entry song: toAdd) {
            builder.add("songIdToAdd", getOfflineSongId(song.getId(), context, progressListener));
        }

        for(int i = 0; i < toRemove; i++) {
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

        Builder builder= new FormBody.Builder();
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

        Builder builder= new FormBody.Builder();
        builder.add("type", type);
        builder.add("size", Integer.toString(size));
        builder.add("offset", Integer.toString(offset));

        // Add folder if it was set and is non null
        int instance = getInstance(context);
        if(Util.getAlbumListsPerFolder(context, instance)) {
            String folderId = Util.getSelectedMusicFolderId(context, instance);
            if(folderId != null) {
                builder.add("musicFolderId", folderId);
            }
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
            .url(url)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new EntryListParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getAlbumList2");

        Builder builder= new FormBody.Builder();
        builder.add("size", Integer.toString(size));
        builder.add("offset", Integer.toString(offset));

        int instance = getInstance(context);
        if("genres".equals(type)) {
            builder.add("type", "byGenre");
            builder.add("genre", extra);
        } else if("years".equals(type)) {
            int decade = Integer.parseInt(extra);

            builder.add("type", "byYear");
            builder.add("fromYear", Integer.toString(decade + 9));
            builder.add("toYear", Integer.toString(decade));
        }

        // Add folder if it was set and is non null
        if(Util.getAlbumListsPerFolder(context, instance)) {
            String folderId = Util.getSelectedMusicFolderId(context, instance);
            if(folderId != null) {
                builder.add("musicFolderId", folderId);
            }
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
            .url(url)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new EntryListParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
        Builder builder= new FormBody.Builder();
        builder.add("size", Integer.toString(size));
        builder.add("offset", Integer.toString(offset));

        String method;
        switch(type) {
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
            return new EntryListParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String artistId, Context context, ProgressListener progressListener) throws Exception {
        Builder builder= new FormBody.Builder();
        builder.add("id", artistId);
        builder.add("count", Integer.toString(size));

        String url = getRestUrl(context, "getSimilarSongs2");

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
            .url(url)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new RandomSongsParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String musicFolderId, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
        Builder builder= new FormBody.Builder();
        builder.add("size", Integer.toString(size));

        if (musicFolderId != null && !"".equals(musicFolderId) && !Util.isTagBrowsing(context, getInstance(context))) {
            builder.add("musicFolderId", musicFolderId);
        }
        if(genre != null && !"".equals(genre)) {
            builder.add("genre", genre);
        }
        if(startYear != null && !"".equals(startYear)) {
            // Check to make sure user isn't doing 2015 -> 2010 since Subsonic will return no results
            if(endYear != null && !"".equals(endYear)) {
                try {
                    int startYearInt = Integer.parseInt(startYear);
                    int endYearInt = Integer.parseInt(endYear);

                    if(startYearInt > endYearInt) {
                        String tmp = startYear;
                        startYear = endYear;
                        endYear = tmp;
                    }
                } catch(Exception e) {
                    Log.w(TAG, "Failed to convert start/end year into ints", e);
                }
            }

            builder.add("fromYear", startYear);
        }
        if(endYear != null && !"".equals(endYear)) {
            builder.add("toYear", endYear);
        }

        String url = getRestUrl(context, "getRandomSongs");

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
            .url(url)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new RandomSongsParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public String getCoverArtUrl(Context context, MusicDirectory.Entry entry) throws Exception {
        StringBuilder builder = new StringBuilder(getRestUrl(context, "getCoverArt"));
        builder.append("&id=").append(entry.getCoverArt());
        String url = builder.toString();
        url = rewriteUrlWithRedirect(context, url);
        return url;
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

            Builder builder= new FormBody.Builder();
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
                if(task != null && task.isCancelled()) {
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
                if(size == 0) {
                    return null;
                } else {
                    return FileUtil.getSampledBitmap(bytes, size);
                }
            }
        }
    }

    @Override
    public HttpResponse getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {

        String url = getRestUrl(context, "stream");

        // Set socket read timeout. Note: The timeout increases as the offset gets larger. This is
        // to avoid the thrashing effect seen when offset is combined with transcoding/downsampling on the server.
        // In that case, the server uses a long time before sending any data, causing the client to time out.
        HttpParams params = new BasicHttpParams();
        int timeout = (int) (SOCKET_READ_TIMEOUT_DOWNLOAD + offset * TIMEOUT_MILLIS_PER_OFFSET_BYTE);
        HttpConnectionParams.setSoTimeout(params, timeout);

        // Add "Range" header if offset is given.
        List<Header> headers = new ArrayList<Header>();
        if (offset > 0) {
            headers.add(new BasicHeader("Range", "bytes=" + offset + "-"));
        }

        List<String> parameterNames = new ArrayList<String>();
        parameterNames.add("id");
        parameterNames.add("maxBitRate");

        List<Object> parameterValues = new ArrayList<Object>();
        parameterValues.add(song.getId());
        parameterValues.add(maxBitrate);

        HttpResponse response = getResponseForURL(context, url, params, parameterNames, parameterValues, headers, null, task, false);

        // If content type is XML, an error occurred.  Get it.
        String contentType = response.getEntity().getContentType().getValue();
        if (contentType != null && (contentType.startsWith("text/xml") || contentType.startsWith("text/html"))) {
            InputStream in = response.getEntity().getContent();
            Header contentEncoding = response.getEntity().getContentEncoding();
            if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
                in = new GZIPInputStream(in);
            }
            try {
                new ErrorParser(context, getInstance(context)).parse(in);
            } finally {
                Util.close(in);
            }
        }

        return response;
    }


    @Override
    public String getMusicUrl(Context context, MusicDirectory.Entry song, int maxBitrate) throws Exception {
        StringBuilder builder = new StringBuilder(getRestUrl(context, "stream"));
        builder.append("&id=").append(song.getId());

        // Allow user to specify to stream raw formats if available
        builder.append("&maxBitRate=").append(maxBitrate);

        String url = builder.toString();
        url = rewriteUrlWithRedirect(context, url);
        Log.i(TAG, "Using music URL: " + stripUrlInfo(url));
        return url;
    }

    @Override
    public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getGenres");

        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new GenreParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    @Override
    public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
        Builder builder= new FormBody.Builder();
        builder.add("genre", genre);
        builder.add("count", Integer.toString(count));
        builder.add("offset", Integer.toString(offset));

        // Add folder if it was set and is non null
        int instance = getInstance(context);
        if(Util.getAlbumListsPerFolder(context, instance)) {
            String folderId = Util.getSelectedMusicFolderId(context, instance);
            if(folderId != null) {
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
            return new RandomSongsParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
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
            List<User> users = new UserParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
            if(users.size() > 0) {
                // Should only have returned one anyways
                return users.get(0);
            } else {
                return null;
            }
        }
    }

    @Override
    public Bitmap getBitmap(String method, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
        // Synchronize on the url so that we don't download concurrently
        synchronized (method) {
            // Use cached file, if existing.
            Bitmap bitmap = FileUtil.getMiscBitmap(context, method, size);
            if(bitmap != null) {
                return bitmap;
            }

            String url = getRestUrl(context, method);

            Request request = new Request.Builder()
                .url(url)
                .build();

            try (Response response = client.newCall(request).execute()) {
                InputStream in = response.body().byteStream();

                byte[] bytes = Util.toByteArray(in);
                if(task != null && task.isCancelled()) {
                    // Handle case where partial is downloaded and cancelled
                    return null;
                }

                OutputStream out = null;
                try {
                    out = new FileOutputStream(FileUtil.getMiscFile(context, url));
                    out.write(bytes);
                } finally {
                    Util.close(out);
                }

                return FileUtil.getSampledBitmap(bytes, size, false);
            }
        }
    }

    @Override
    public void savePlayQueue(List<MusicDirectory.Entry> songs, MusicDirectory.Entry currentPlaying, int position, Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "savePlayQueue");

        Builder builder= new FormBody.Builder();

        builder.add("current", currentPlaying.getId());
        builder.add("position", Integer.toString(position));

        for(MusicDirectory.Entry song: songs) {
            builder.add("id", song.getId());
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
    public PlayerQueue getPlayQueue(Context context, ProgressListener progressListener) throws Exception {
        String url = getRestUrl(context, "getPlayQueue");

        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return new PlayQueueParser(context, getInstance(context)).parse(response.body().byteStream(), progressListener);
        }
    }

    private String getOfflineSongId(String id, Context context, ProgressListener progressListener) throws Exception {
        SharedPreferences prefs = Util.getPreferences(context);
        String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
        if(cacheLocn != null && id.indexOf(cacheLocn) != -1) {
            Pair<Integer, String> cachedSongId = SongDBHandler.getHandler(context).getIdFromPath(Util.getRestUrlHash(context, getInstance(context)), id);
            if(cachedSongId != null) {
                id = cachedSongId.getSecond();
            } else {
                String searchCriteria = Util.parseOfflineIDSearch(context, id, cacheLocn);
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
    public void setInstance(Integer instance)  throws Exception {
        this.instance = instance;
    }

    private HttpResponse getResponseForURL(Context context, String url, HttpParams requestParams,
                                           List<String> parameterNames, List<Object> parameterValues,
                                           List<Header> headers, ProgressListener progressListener, SilentBackgroundTask task, boolean throwsErrors) throws Exception {
        // If not too many parameters, extract them to the URL rather than relying on the HTTP POST request being
        // received intact. Remember, HTTP POST requests are converted to GET requests during HTTP redirects, thus
        // loosing its entity.
        if (parameterNames != null && parameterNames.size() < 10) {
            StringBuilder builder = new StringBuilder(url);
            for (int i = 0; i < parameterNames.size(); i++) {
                builder.append("&").append(parameterNames.get(i)).append("=");
                String part = URLEncoder.encode(String.valueOf(parameterValues.get(i)), "UTF-8");
                part = part.replaceAll("\\%27", "'");
                builder.append(part);
            }
            url = builder.toString();
            parameterNames = null;
            parameterValues = null;
        }

        String rewrittenUrl = rewriteUrlWithRedirect(context, url);
        return executeWithRetry(context, rewrittenUrl, url, requestParams, parameterNames, parameterValues, headers, progressListener, task, throwsErrors);
    }

    private HttpResponse executeWithRetry(final Context context, String url, String originalUrl, HttpParams requestParams,
                                          List<String> parameterNames, List<Object> parameterValues,
                                          List<Header> headers, ProgressListener progressListener, SilentBackgroundTask task, boolean throwErrors) throws Exception {
        // Strip out sensitive information from log
        if(url.indexOf("scanstatus") == -1) {
            Log.i(TAG, stripUrlInfo(url));
        }

        SharedPreferences prefs = Util.getPreferences(context);
        int networkTimeout = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT, "15000"));
        HttpParams newParams = httpClient.getParams();
        HttpConnectionParams.setSoTimeout(newParams, networkTimeout);
        httpClient.setParams(newParams);

        final AtomicReference<Boolean> isCancelled = new AtomicReference<Boolean>(false);
        int attempts = 0;
        while (true) {
            attempts++;
            HttpContext httpContext = new BasicHttpContext();
            final HttpRequestBase request = (url.indexOf("rest") == -1) ? new HttpGet(url) : new HttpPost(url);

            if (task != null) {
                // Attempt to abort the HTTP request if the task is cancelled.
                task.setOnCancelListener(new BackgroundTask.OnCancelListener() {
                    @Override
                    public void onCancel() {
                        try {
                            isCancelled.set(true);
                            if(Thread.currentThread() == Looper.getMainLooper().getThread()) {
                                new SilentBackgroundTask<Void>(context) {
                                    @Override
                                    protected Void doInBackground() throws Throwable {
                                        request.abort();
                                        return null;
                                    }
                                }.execute();
                            } else {
                                request.abort();
                            }
                        } catch(Exception e) {
                            Log.e(TAG, "Failed to stop http task", e);
                        }
                    }
                });
            }

            if (parameterNames != null && request instanceof HttpPost) {
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                for (int i = 0; i < parameterNames.size(); i++) {
                    params.add(new BasicNameValuePair(parameterNames.get(i), String.valueOf(parameterValues.get(i))));
                }
                ((HttpPost) request).setEntity(new UrlEncodedFormEntity(params, Constants.UTF_8));
            }

            if (requestParams != null) {
                request.setParams(requestParams);
            }

            if (headers != null) {
                for (Header header : headers) {
                    request.addHeader(header);
                }
            }
            if(url.indexOf("getCoverArt") == -1 && url.indexOf("stream") == -1) {
                request.addHeader("Accept-Encoding", "gzip");
            }
            request.addHeader("User-Agent", Constants.REST_CLIENT_ID);

            // Set credentials to get through apache proxies that require authentication.
            int instance = getInstance(context);
            String username = prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
            String password = prefs.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);
            httpClient.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(username, password));

            try {
                HttpResponse response = httpClient.execute(request, httpContext);
                detectRedirect(originalUrl, context, httpContext);
                return response;
            } catch (IOException x) {
                request.abort();
                if (attempts >= HTTP_REQUEST_MAX_ATTEMPTS || isCancelled.get() || throwErrors) {
                    throw x;
                }
                if (progressListener != null) {
                    String msg = context.getResources().getString(R.string.music_service_retry, attempts, HTTP_REQUEST_MAX_ATTEMPTS - 1);
                    progressListener.updateProgress(msg);
                }
                Log.w(TAG, "Got IOException " + x + " (" + attempts + "), will retry");
                increaseTimeouts(requestParams);
                Thread.sleep(2000L);
            }
        }
    }

    private void increaseTimeouts(HttpParams requestParams) {
        if (requestParams != null) {
            int connectTimeout = HttpConnectionParams.getConnectionTimeout(requestParams);
            if (connectTimeout != 0) {
                HttpConnectionParams.setConnectionTimeout(requestParams, (int) (connectTimeout * 1.3F));
            }
            int readTimeout = HttpConnectionParams.getSoTimeout(requestParams);
            if (readTimeout != 0) {
                HttpConnectionParams.setSoTimeout(requestParams, (int) (readTimeout * 1.5F));
            }
        }
    }

    private void detectRedirect(String originalUrl, Context context, HttpContext httpContext) throws Exception {
        HttpUriRequest request = (HttpUriRequest) httpContext.getAttribute(ExecutionContext.HTTP_REQUEST);
        HttpHost host = (HttpHost) httpContext.getAttribute(ExecutionContext.HTTP_TARGET_HOST);

        // Sometimes the request doesn't contain the "http://host" part
        String redirectedUrl;
        if (request.getURI().getScheme() == null) {
            redirectedUrl = host.toURI() + request.getURI();
        } else {
            redirectedUrl = request.getURI().toString();
        }

        int fromIndex = originalUrl.indexOf("/rest/");
        int toIndex = redirectedUrl.indexOf("/rest/");
        if(fromIndex != -1 && toIndex != -1 && !Util.equals(originalUrl, redirectedUrl)) {
            redirectFrom = originalUrl.substring(0, fromIndex);
            redirectTo = redirectedUrl.substring(0, toIndex);

            if (redirectFrom.compareTo(redirectTo) != 0) {
                Log.i(TAG, redirectFrom + " redirects to " + redirectTo);
            }
            redirectionLastChecked = System.currentTimeMillis();
            redirectionNetworkType = getCurrentNetworkType(context);
        }
    }

    private String rewriteUrlWithRedirect(Context context, String url) {

        // Only cache for a certain time.
        if (System.currentTimeMillis() - redirectionLastChecked > REDIRECTION_CHECK_INTERVAL_MILLIS) {
            return url;
        }

        // Ignore cache if network type has changed.
        if (redirectionNetworkType != getCurrentNetworkType(context)) {
            return url;
        }

        if (redirectFrom == null || redirectTo == null) {
            return url;
        }

        return url.replace(redirectFrom, redirectTo);
    }

    private String stripUrlInfo(String url) {
        return url.substring(0, url.indexOf("?u=") + 1) + url.substring(url.indexOf("&v=") + 1);
    }

    private int getCurrentNetworkType(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkInfo == null ? -1 : networkInfo.getType();
    }

    public int getInstance(Context context) {
        if(instance == null) {
            return Util.getActiveServer(context);
        } else {
            return instance;
        }
    }

    public String getRestUrl(Context context, String method) {
        return getRestUrl(context, method, true);
    }

    public String getRestUrl(Context context, String method, boolean allowAltAddress) {
        if(instance == null) {
            return Util.getRestUrl(context, method, allowAltAddress);
        } else {
            return Util.getRestUrl(context, method, instance, allowAltAddress);
        }
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}
