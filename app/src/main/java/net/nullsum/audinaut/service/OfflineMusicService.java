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
import net.nullsum.audinaut.domain.MusicDirectory.Entry;
import net.nullsum.audinaut.domain.MusicFolder;
import net.nullsum.audinaut.domain.Playlist;
import net.nullsum.audinaut.domain.SearchCritera;
import net.nullsum.audinaut.domain.SearchResult;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;

import okhttp3.Response;

/**
 * @author Sindre Mehus
 */
public class OfflineMusicService implements MusicService {
    public static final String ERRORMSG = "Not available in offline mode";
    private static final String TAG = OfflineMusicService.class.getSimpleName();
    private static final Random random = new Random();

    @Override
    public void ping(Context context, ProgressListener progressListener) {

    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) {
        List<Artist> artists = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();

        File root = FileUtil.getMusicDirectory(context);
        for (File file : FileUtil.listFiles(root)) {
            if (file.isDirectory()) {
                Artist artist = new Artist();
                artist.setId(file.getPath());
                artist.setIndex(file.getName().substring(0, 1));
                artist.setName(file.getName());
                artists.add(artist);
            } else if (!file.getName().equals("albumart.jpg") && !file.getName().equals(".nomedia")) {
                entries.add(createEntry(context, file));
            }
        }

        return new Indexes(Collections.emptyList(), artists, entries);
    }

    @Override
    public MusicDirectory getMusicDirectory(String id, String artistName, boolean refresh, Context context, ProgressListener progressListener) {
        return getMusicDirectory(id, context);
    }

    private MusicDirectory getMusicDirectory(String id, Context context) {
        File dir = new File(id);
        MusicDirectory result = new MusicDirectory();
        result.setName(dir.getName());

        Set<String> names = new HashSet<>();

        for (File file : FileUtil.listMediaFiles(dir)) {
            String name = getName(file);
            if (name != null & !names.contains(name)) {
                names.add(name);
                result.addChild(createEntry(context, file, name, true));
            }
        }
        result.sortChildren(Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_CUSTOM_SORT_ENABLED, true));
        return result;
    }

    @Override
    public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    private String getName(File file) {
        String name = file.getName();
        if (file.isDirectory()) {
            return name;
        }

        if (name.endsWith(".partial") || name.contains(".partial.") || name.equals(Constants.ALBUM_ART_FILE)) {
            return null;
        }

        name = name.replace(".complete", "");
        return FileUtil.getBaseName(name);
    }

    private Entry createEntry(Context context, File file) {
        return createEntry(context, file, getName(file));
    }

    private Entry createEntry(Context context, File file, String name) {
        return createEntry(context, file, name, true);
    }

    private Entry createEntry(Context context, File file, String name, boolean load) {
        Entry entry;
        entry = new Entry();
        entry.setDirectory(file.isDirectory());
        entry.setId(file.getPath());
        entry.setParent(file.getParent());
        String root = FileUtil.getMusicDirectory(context).getPath();
        entry.setPath(file.getPath().replaceFirst("^" + root + "/", ""));
        String title = name;
        if (file.isFile()) {
            File artistFolder = file.getParentFile().getParentFile();
            File albumFolder = file.getParentFile();
            if (artistFolder.getPath().equals(root)) {
                entry.setArtist(albumFolder.getName());
            } else {
                entry.setArtist(artistFolder.getName());
            }
            entry.setAlbum(albumFolder.getName());

            int index = name.indexOf('-');
            if (index != -1) {
                try {
                    entry.setTrack(Integer.parseInt(name.substring(0, index)));
                    title = title.substring(index + 1);
                } catch (Exception e) {
                    // Failed parseInt, just means track filled out
                }
            }

            if (load) {
                entry.loadMetadata(file);
            }
        }

        entry.setTitle(title);
        entry.setSuffix(FileUtil.getExtension(file.getName().replace(".complete", "")));

        File albumArt = FileUtil.getAlbumArtFile(context, entry);
        if (albumArt.exists()) {
            entry.setCoverArt(albumArt.getPath());
        }
        return entry;
    }

    @Override
    public Bitmap getCoverArt(Context context, Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) {
        try {
            return FileUtil.getAlbumArtBitmap(context, entry, size);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Response getDownloadInputStream(Context context, Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {
        throw new OfflineException();
    }

    @Override
    public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) {
        List<Artist> artists = new ArrayList<>();
        List<Entry> albums = new ArrayList<>();
        List<Entry> songs = new ArrayList<>();
        File root = FileUtil.getMusicDirectory(context);
        int closeness;
        for (File artistFile : FileUtil.listFiles(root)) {
            String artistName = artistFile.getName();
            if (artistFile.isDirectory()) {
                if ((closeness = matchCriteria(criteria, artistName)) > 0) {
                    Artist artist = new Artist();
                    artist.setId(artistFile.getPath());
                    artist.setIndex(artistFile.getName().substring(0, 1));
                    artist.setName(artistName);
                    artist.setCloseness(closeness);
                    artists.add(artist);
                }

                recursiveAlbumSearch(artistName, artistFile, criteria, context, albums, songs);
            }
        }

        Collections.sort(artists, (lhs, rhs) -> Integer.compare(rhs.getCloseness(), lhs.getCloseness()));
        Collections.sort(albums, (lhs, rhs) -> Integer.compare(rhs.getCloseness(), lhs.getCloseness()));
        Collections.sort(songs, (lhs, rhs) -> Integer.compare(rhs.getCloseness(), lhs.getCloseness()));

        // Respect counts in search criteria
        int artistCount = Math.min(artists.size(), criteria.getArtistCount());
        int albumCount = Math.min(albums.size(), criteria.getAlbumCount());
        int songCount = Math.min(songs.size(), criteria.getSongCount());
        artists = artists.subList(0, artistCount);
        albums = albums.subList(0, albumCount);
        songs = songs.subList(0, songCount);

        return new SearchResult(artists, albums, songs);
    }

    private void recursiveAlbumSearch(String artistName, File file, SearchCritera criteria, Context context, List<Entry> albums, List<Entry> songs) {
        int closeness;
        for (File albumFile : FileUtil.listMediaFiles(file)) {
            if (albumFile.isDirectory()) {
                String albumName = getName(albumFile);
                if ((closeness = matchCriteria(criteria, albumName)) > 0) {
                    Entry album = createEntry(context, albumFile, albumName);
                    album.setArtist(artistName);
                    album.setCloseness(closeness);
                    albums.add(album);
                }

                for (File songFile : FileUtil.listMediaFiles(albumFile)) {
                    String songName = getName(songFile);
                    if (songName == null) {
                        continue;
                    }

                    if (songFile.isDirectory()) {
                        recursiveAlbumSearch(artistName, songFile, criteria, context, albums, songs);
                    } else if ((closeness = matchCriteria(criteria, songName)) > 0) {
                        Entry song = createEntry(context, albumFile, songName);
                        song.setArtist(artistName);
                        song.setAlbum(albumName);
                        song.setCloseness(closeness);
                        songs.add(song);
                    }
                }
            } else {
                String songName = getName(albumFile);
                if ((closeness = matchCriteria(criteria, songName)) > 0) {
                    Entry song = createEntry(context, albumFile, songName);
                    song.setArtist(artistName);
                    song.setAlbum(songName);
                    song.setCloseness(closeness);
                    songs.add(song);
                }
            }
        }
    }

    private int matchCriteria(SearchCritera criteria, String name) {
        if (criteria.getPattern().matcher(name).matches()) {
            return Util.getStringDistance(
                    criteria.getQuery().toLowerCase(),
                    name.toLowerCase());
        } else {
            return 0;
        }
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) {
        List<Playlist> playlists = new ArrayList<>();
        File root = FileUtil.getPlaylistDirectory(context);
        String lastServer = null;
        boolean removeServer = true;
        for (File folder : FileUtil.listFiles(root)) {
            if (folder.isDirectory()) {
                String server = folder.getName();
                SortedSet<File> fileList = FileUtil.listFiles(folder);
                for (File file : fileList) {
                    if (FileUtil.isPlaylistFile(file)) {
                        String id = file.getName();
                        String filename = FileUtil.getBaseName(id);
                        String name = server + ": " + filename;
                        Playlist playlist = new Playlist(server, name);
                        playlist.setComment(filename);

                        Reader reader = null;
                        BufferedReader buffer = null;
                        int songCount = 0;
                        try {
                            reader = new FileReader(file);
                            buffer = new BufferedReader(reader);

                            String line = buffer.readLine();
                            while ((line = buffer.readLine()) != null) {
                                // No matter what, end file can't have .complete in it
                                line = line.replace(".complete", "");
                                File entryFile = new File(line);

                                // Don't add file to playlist if it doesn't exist as cached or pinned!
                                File checkFile = entryFile;
                                if (!checkFile.exists()) {
                                    // If normal file doens't exist, check if .complete version does
                                    checkFile = new File(entryFile.getParent(), FileUtil.getBaseName(entryFile.getName())
                                            + ".complete." + FileUtil.getExtension(entryFile.getName()));
                                }

                                String entryName = getName(entryFile);
                                if (checkFile.exists() && entryName != null) {
                                    songCount++;
                                }
                            }

                            playlist.setSongCount(Integer.toString(songCount));
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to count songs in playlist", e);
                        } finally {
                            Util.close(buffer);
                            Util.close(reader);
                        }

                        if (songCount > 0) {
                            playlists.add(playlist);
                        }
                    }
                }

                if (!server.equals(lastServer) && fileList.size() > 0) {
                    if (lastServer != null) {
                        removeServer = false;
                    }
                    lastServer = server;
                }
            } else {
                // Delete legacy playlist files
                try {
                    folder.delete();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete old playlist file: " + folder.getName());
                }
            }
        }

        if (removeServer) {
            for (Playlist playlist : playlists) {
                playlist.setName(playlist.getName().substring(playlist.getId().length() + 2));
            }
        }
        return playlists;
    }

    @Override
    public MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception {
        DownloadService downloadService = DownloadService.getInstance();
        if (downloadService == null) {
            return new MusicDirectory();
        }

        Reader reader = null;
        BufferedReader buffer = null;
        try {
            int firstIndex = name.indexOf(id);
            if (firstIndex != -1) {
                name = name.substring(id.length() + 2);
            }

            File playlistFile = FileUtil.getPlaylistFile(context, id, name);
            reader = new FileReader(playlistFile);
            buffer = new BufferedReader(reader);

            MusicDirectory playlist = new MusicDirectory();
            String line = buffer.readLine();
            if (!"#EXTM3U".equals(line)) return playlist;

            while ((line = buffer.readLine()) != null) {
                // No matter what, end file can't have .complete in it
                line = line.replace(".complete", "");
                File entryFile = new File(line);

                // Don't add file to playlist if it doesn't exist as cached or pinned!
                File checkFile = entryFile;
                if (!checkFile.exists()) {
                    // If normal file doens't exist, check if .complete version does
                    checkFile = new File(entryFile.getParent(), FileUtil.getBaseName(entryFile.getName())
                            + ".complete." + FileUtil.getExtension(entryFile.getName()));
                }

                String entryName = getName(entryFile);
                if (checkFile.exists() && entryName != null) {
                    playlist.addChild(createEntry(context, entryFile, entryName, false));
                }
            }

            return playlist;
        } finally {
            Util.close(buffer);
            Util.close(reader);
        }
    }

    @Override
    public void createPlaylist(String id, String name, List<Entry> entries, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public void addToPlaylist(String id, List<Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public void overwritePlaylist(String id, String name, int toRemove, List<Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException();
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) {
        File root = FileUtil.getMusicDirectory(context);
        List<File> children = new LinkedList<>();
        listFilesRecursively(root, children);
        MusicDirectory result = new MusicDirectory();

        if (children.isEmpty()) {
            return result;
        }
        for (int i = 0; i < size; i++) {
            File file = children.get(random.nextInt(children.size()));
            result.addChild(createEntry(context, file, getName(file)));
        }

        return result;
    }

    @Override
    public void startScan(Context c) throws Exception {
        throw new OfflineException();
    }

    @Override
    public void setInstance(Integer instance) throws Exception {
        throw new OfflineException();
    }

    private void listFilesRecursively(File parent, List<File> children) {
        for (File file : FileUtil.listMediaFiles(parent)) {
            if (file.isFile()) {
                children.add(file);
            } else {
                listFilesRecursively(file, children);
            }
        }
    }
}
