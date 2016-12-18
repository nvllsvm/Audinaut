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
package github.nvllsvm.audinaut.service;

import java.util.List;

import org.apache.http.HttpResponse;

import android.content.Context;
import android.graphics.Bitmap;

import github.nvllsvm.audinaut.domain.Genre;
import github.nvllsvm.audinaut.domain.Indexes;
import github.nvllsvm.audinaut.domain.PlayerQueue;
import github.nvllsvm.audinaut.domain.RemoteStatus;
import github.nvllsvm.audinaut.domain.MusicDirectory;
import github.nvllsvm.audinaut.domain.MusicFolder;
import github.nvllsvm.audinaut.domain.Playlist;
import github.nvllsvm.audinaut.domain.SearchCritera;
import github.nvllsvm.audinaut.domain.SearchResult;
import github.nvllsvm.audinaut.domain.User;
import github.nvllsvm.audinaut.domain.Version;
import github.nvllsvm.audinaut.util.SilentBackgroundTask;
import github.nvllsvm.audinaut.util.ProgressListener;

/**
 * @author Sindre Mehus
 */
public interface MusicService {

    void ping(Context context, ProgressListener progressListener) throws Exception;

    List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void startRescan(Context context, ProgressListener listener) throws Exception;

    Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

    MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

    SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception;

    MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception;

    List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

    void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception;
	
	void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception;
	
	void addToPlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception;
	
	void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception;
	
	void overwritePlaylist(String id, String name, int toRemove, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception;
	
	void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception;

    MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getRandomSongs(int size, String artistId, Context context, ProgressListener progressListener) throws Exception;
    MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception;

	String getCoverArtUrl(Context context, MusicDirectory.Entry entry) throws Exception;

    Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception;

    HttpResponse getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception;

	String getMusicUrl(Context context, MusicDirectory.Entry song, int maxBitrate) throws Exception;

	List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception;
	
	MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getTopTrackSongs(String artist, int size, Context context, ProgressListener progressListener) throws Exception;
	
	User getUser(boolean refresh, String username, Context context, ProgressListener progressListener) throws Exception;

	List<User> getUsers(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void createUser(User user, Context context, ProgressListener progressListener) throws Exception;

	void updateUser(User user, Context context, ProgressListener progressListener) throws Exception;

	void deleteUser(String username, Context context, ProgressListener progressListener) throws Exception;

	void changeEmail(String username, String email, Context context, ProgressListener progressListener) throws Exception;

	void changePassword(String username, String password, Context context, ProgressListener progressListener) throws Exception;

	Bitmap getBitmap(String url, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception;

	void savePlayQueue(List<MusicDirectory.Entry> songs, MusicDirectory.Entry currentPlaying, int position, Context context, ProgressListener progressListener) throws Exception;

	PlayerQueue getPlayQueue(Context context, ProgressListener progressListener) throws Exception;

	void setInstance(Integer instance) throws Exception;
}
