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
package net.nullsum.audinaut.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.activity.SubsonicActivity;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.domain.Artist;
import net.nullsum.audinaut.domain.Genre;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.Playlist;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.service.MediaStoreService;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.service.MusicServiceFactory;
import net.nullsum.audinaut.service.OfflineException;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.ImageLoader;
import net.nullsum.audinaut.util.LoadingTask;
import net.nullsum.audinaut.util.MenuUtil;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.SongDBHandler;
import net.nullsum.audinaut.util.UserUtil;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.GridSpacingDecoration;
import net.nullsum.audinaut.view.PlaylistSongView;
import net.nullsum.audinaut.view.UpdateView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static net.nullsum.audinaut.domain.MusicDirectory.Entry;

public class SubsonicFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = SubsonicFragment.class.getSimpleName();
    private static int TAG_INC = 10;
    SubsonicActivity context;
    CharSequence title = null;
    CharSequence subtitle = null;
    View rootView;
    boolean primaryFragment = false;
    boolean secondaryFragment = false;
    boolean isOnlyVisible = true;
    boolean alwaysFullscreen = false;
    boolean alwaysStartFullscreen = false;
    boolean invalidated = false;
    GestureDetector gestureScanner;
    boolean artist = false;
    SwipeRefreshLayout refreshLayout;
    MenuItem searchItem;
    private boolean artistOverride = false;
    private boolean firstRun;
    private SearchView searchView;
    private int tag;

    public SubsonicFragment() {
        super();
        tag = TAG_INC++;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        if (bundle != null) {
            String name = bundle.getString(Constants.FRAGMENT_NAME);
            if (name != null) {
                title = name;
            }
        }
        firstRun = true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (title != null) {
            outState.putString(Constants.FRAGMENT_NAME, title.toString());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstRun) {
            firstRun = false;
        } else {
            UpdateView.triggerUpdate();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = (SubsonicActivity) getActivity();
    }

    void onFinishSetupOptionsMenu(final Menu menu) {
        searchItem = menu.findItem(R.id.menu_global_search);
        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();
            SearchManager searchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
            SearchableInfo searchableInfo = searchManager.getSearchableInfo(context.getComponentName());
            if (searchableInfo == null) {
                Log.w(TAG, "Failed to get SearchableInfo");
            } else {
                searchView.setSearchableInfo(searchableInfo);
            }

            String currentQuery = getCurrentQuery();
            if (currentQuery != null) {
                searchView.setOnSearchClickListener(v -> searchView.setQuery(getCurrentQuery(), false));
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_global_shuffle:
                onShuffleRequested();
                return true;
            case R.id.menu_refresh:
                refresh(true);
                return true;
            case R.id.menu_play_now:
                playNow(false, false, false);
                return true;
            case R.id.menu_play_last:
                playNow(false, true, false);
                return true;
            case R.id.menu_play_next:
                playNow(false, true, true);
                return true;
            case R.id.menu_shuffle:
                playNow(true, false, false);
                return true;
            case R.id.menu_download:
                downloadBackground(false);
                clearSelected();
                return true;
            case R.id.menu_cache:
                downloadBackground(true);
                clearSelected();
                return true;
            case R.id.menu_delete:
                delete();
                clearSelected();
                return true;
            case R.id.menu_add_playlist:
                List<Entry> songs = getSelectedEntries();
                addToPlaylist(songs);
                clearSelected();
                return true;
        }

        return false;
    }

    void onCreateContextMenuSupport(Menu menu, MenuInflater menuInflater, UpdateView updateView, Object selected) {
        if (selected instanceof Entry) {
            Entry entry = (Entry) selected;
            if (entry.isDirectory()) {
                if (Util.isOffline(context)) {
                    menuInflater.inflate(R.menu.select_album_context_offline, menu);
                } else {
                    menuInflater.inflate(R.menu.select_album_context, menu);
                }
            } else {
                if (Util.isOffline(context)) {
                    menuInflater.inflate(R.menu.select_song_context_offline, menu);
                } else {
                    menuInflater.inflate(R.menu.select_song_context, menu);


                    String songPressAction = Util.getSongPressAction(context);
                    if (!"next".equals(songPressAction) && !"last".equals(songPressAction)) {
                        menu.setGroupVisible(R.id.hide_play_now, false);
                    }
                }
            }

            if (!isShowArtistEnabled() || (entry.getArtistId() == null)) {
                menu.setGroupVisible(R.id.hide_show_artist, false);
            }
        } else if (selected instanceof Artist) {
            if (Util.isOffline(context)) {
                menuInflater.inflate(R.menu.select_artist_context_offline, menu);
            } else {
                menuInflater.inflate(R.menu.select_artist_context, menu);
            }
        }

        MenuUtil.hideMenuItems(context, menu, updateView);
    }

    void recreateContextMenu(Menu menu) {
        List<MenuItem> menuItems = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.isVisible()) {
                menuItems.add(item);
            }
        }
        menu.clear();
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);
            menu.add(tag, item.getItemId(), Menu.NONE, item.getTitle());
        }
    }

    // For reverting specific removals: https://github.com/daneren2005/Subsonic/commit/fbd1a68042dfc3601eaa0a9e37b3957bbdd51420
    boolean onContextItemSelected(MenuItem menuItem, Object selectedItem) {
        Artist artist = selectedItem instanceof Artist ? (Artist) selectedItem : null;
        Entry entry = selectedItem instanceof Entry ? (Entry) selectedItem : null;
        if (selectedItem instanceof DownloadFile) {
            entry = ((DownloadFile) selectedItem).getSong();
        }
        List<Entry> songs = new ArrayList<>(1);
        songs.add(entry);

        switch (menuItem.getItemId()) {
            case R.id.artist_menu_play_now:
                downloadRecursively(artist.getId(), false, false, true, false, false, false);
                break;
            case R.id.artist_menu_play_shuffled:
                downloadRecursively(artist.getId(), false, false, true, true, false, false);
                break;
            case R.id.artist_menu_play_next:
                downloadRecursively(artist.getId(), false, true, false, false, false, true);
                break;
            case R.id.artist_menu_play_last:
                downloadRecursively(artist.getId(), false, true, false, false, false, false);
                break;
            case R.id.artist_menu_download:
                downloadRecursively(artist.getId(), false, true, false, false, true, false);
                break;
            case R.id.artist_menu_pin:
                downloadRecursively(artist.getId(), true, true, false, false, true, false);
                break;
            case R.id.artist_menu_delete:
                deleteRecursively(artist);
                break;
            case R.id.album_menu_play_now:
                artistOverride = true;
                downloadRecursively(entry.getId(), false, false, true, false, false, false);
                break;
            case R.id.album_menu_play_shuffled:
                artistOverride = true;
                downloadRecursively(entry.getId(), false, false, true, true, false, false);
                break;
            case R.id.album_menu_play_next:
                artistOverride = true;
                downloadRecursively(entry.getId(), false, true, false, false, false, true);
                break;
            case R.id.album_menu_play_last:
                artistOverride = true;
                downloadRecursively(entry.getId(), false, true, false, false, false, false);
                break;
            case R.id.album_menu_download:
                artistOverride = true;
                downloadRecursively(entry.getId(), false, true, false, false, true, false);
                break;
            case R.id.album_menu_pin:
                artistOverride = true;
                downloadRecursively(entry.getId(), true, true, false, false, true, false);
                break;
            case R.id.album_menu_delete:
                deleteRecursively(entry);
                break;
            case R.id.album_menu_info:
                displaySongInfo(entry);
                break;
            case R.id.album_menu_show_artist:
                showAlbumArtist((Entry) selectedItem);
                break;
            case R.id.song_menu_play_now:
                playNow(songs);
                break;
            case R.id.song_menu_play_next:
                getDownloadService().download(songs, false, false, true, false);
                break;
            case R.id.song_menu_play_last:
                getDownloadService().download(songs, false, false, false, false);
                break;
            case R.id.song_menu_download:
                getDownloadService().downloadBackground(songs, false);
                break;
            case R.id.song_menu_pin:
                getDownloadService().downloadBackground(songs, true);
                break;
            case R.id.song_menu_delete:
                deleteSongs(songs);
                break;
            case R.id.song_menu_add_playlist:
                addToPlaylist(songs);
                break;
            case R.id.song_menu_info:
                displaySongInfo(entry);
                break;
            case R.id.song_menu_show_album:
                showAlbum((Entry) selectedItem);
                break;
            case R.id.song_menu_show_artist:
                showArtist((Entry) selectedItem);
                break;
            default:
                return false;
        }

        return true;
    }

    void replaceFragment(SubsonicFragment fragment) {
        context.replaceFragment(fragment, fragment.getSupportTag(), secondaryFragment);
    }

    void removeCurrent() {
        context.removeCurrent();
    }

    public int getRootId() {
        return rootView.getId();
    }

    public int getSupportTag() {
        return tag;
    }

    public void setSupportTag(String tag) {
        this.tag = Integer.parseInt(tag);
    }

    public void setPrimaryFragment(boolean primary) {
        primaryFragment = primary;
        if (primary) {
            if (context != null && title != null) {
                context.setTitle(title);
                context.setSubtitle(subtitle);
            }
            if (invalidated) {
                invalidated = false;
                refresh(false);
            }
        }
    }

    public void setPrimaryFragment(boolean primary, boolean secondary) {
        setPrimaryFragment(primary);
        secondaryFragment = secondary;
    }

    public void setSecondaryFragment(boolean secondary) {
        secondaryFragment = secondary;
    }

    public void setIsOnlyVisible(boolean isOnlyVisible) {
        this.isOnlyVisible = isOnlyVisible;
    }

    public boolean isAlwaysFullscreen() {
        return alwaysFullscreen;
    }

    public boolean isAlwaysStartFullscreen() {
        return alwaysStartFullscreen;
    }

    public void invalidate() {
        if (primaryFragment) {
            refresh(true);
        } else {
            invalidated = true;
        }
    }

    public DownloadService getDownloadService() {
        return context != null ? context.getDownloadService() : null;
    }

    void refresh(boolean refresh) {
    }

    @Override
    public void onRefresh() {
        refreshLayout.setRefreshing(false);
        refresh(true);
    }

    public void setProgressVisible(boolean visible) {
        View view = rootView.findViewById(R.id.tab_progress);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);

            if (visible) {
                View progress = rootView.findViewById(R.id.tab_progress_spinner);
                progress.setVisibility(View.VISIBLE);
            }
        }
    }

    public void updateProgress(String message) {
        TextView view = rootView.findViewById(R.id.tab_progress_message);
        if (view != null) {
            view.setText(message);
        }
    }

    void setEmpty(boolean empty) {
        View view = rootView.findViewById(R.id.tab_progress);
        if (empty) {
            view.setVisibility(View.VISIBLE);

            View progress = view.findViewById(R.id.tab_progress_spinner);
            progress.setVisibility(View.GONE);

            TextView text = view.findViewById(R.id.tab_progress_message);
            text.setText(R.string.common_empty);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    synchronized ImageLoader getImageLoader() {
        return context.getImageLoader();
    }

    void setTitle(CharSequence title) {
        this.title = title;
        context.setTitle(title);
    }

    void setSubtitle(CharSequence title) {
        this.subtitle = title;
        context.setSubtitle(title);
    }

    public CharSequence getTitle() {
        return this.title;
    }

    void setTitle(int title) {
        this.title = context.getResources().getString(title);
        context.setTitle(this.title);
    }

    void setupScrollList(final RecyclerView recyclerView) {
        if (!context.isTouchscreen()) {
            refreshLayout.setEnabled(false);
        } else {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    refreshLayout.setEnabled(!recyclerView.canScrollVertically(-1));
                }
            });
        }
    }

    void setupLayoutManager(RecyclerView recyclerView, boolean largeAlbums) {
        recyclerView.setLayoutManager(getLayoutManager(recyclerView, largeAlbums));
    }

    private RecyclerView.LayoutManager getLayoutManager(RecyclerView recyclerView, boolean largeCells) {
        if (largeCells) {
            return getGridLayoutManager(recyclerView);
        } else {
            return getLinearLayoutManager();
        }
    }

    private GridLayoutManager getGridLayoutManager(RecyclerView recyclerView) {
        final int columns = getRecyclerColumnCount();
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, columns);

        GridLayoutManager.SpanSizeLookup spanSizeLookup = getSpanSizeLookup(gridLayoutManager);
        if (spanSizeLookup != null) {
            gridLayoutManager.setSpanSizeLookup(spanSizeLookup);
        }
        RecyclerView.ItemDecoration itemDecoration = getItemDecoration();
        recyclerView.addItemDecoration(itemDecoration);
        return gridLayoutManager;
    }

    private LinearLayoutManager getLinearLayoutManager() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        return layoutManager;
    }

    GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final GridLayoutManager gridLayoutManager) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                SectionAdapter adapter = getCurrentAdapter();
                if (adapter != null) {
                    int viewType = adapter.getItemViewType(position);
                    if (viewType == SectionAdapter.VIEW_TYPE_HEADER) {
                        return gridLayoutManager.getSpanCount();
                    } else {
                        return 1;
                    }
                } else {
                    return 1;
                }
            }
        };
    }

    private RecyclerView.ItemDecoration getItemDecoration() {
        return new GridSpacingDecoration();
    }

    int getRecyclerColumnCount() {
        if (isOnlyVisible) {
            return context.getResources().getInteger(R.integer.Grid_FullScreen_Columns);
        } else {
            return context.getResources().getInteger(R.integer.Grid_Columns);
        }
    }

    void warnIfStorageUnavailable() {
        if (!Util.isExternalStoragePresent()) {
            Util.toast(context, R.string.select_album_no_sdcard);
        }

        try {
            StatFs stat = new StatFs(FileUtil.getMusicDirectory(context).getPath());
            long bytesAvailableFs = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            if (bytesAvailableFs < 50000000L) {
                Util.toast(context, context.getResources().getString(R.string.select_album_no_room, Util.formatBytes(bytesAvailableFs)));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error while checking storage space for music directory", e);
        }
    }

    private void onShuffleRequested() {
        if (Util.isOffline(context)) {
            DownloadService downloadService = getDownloadService();
            if (downloadService == null) {
                return;
            }
            downloadService.clear();
            downloadService.setShufflePlayEnabled(true);
            context.openNowPlaying();
            return;
        }

        View dialogView = context.getLayoutInflater().inflate(R.layout.shuffle_dialog, null);
        final EditText startYearBox = dialogView.findViewById(R.id.start_year);
        final EditText endYearBox = dialogView.findViewById(R.id.end_year);
        final EditText genreBox = dialogView.findViewById(R.id.genre);
        final Button genreCombo = dialogView.findViewById(R.id.genre_combo);

        final SharedPreferences prefs = Util.getPreferences(context);
        final String oldStartYear = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, "");
        final String oldEndYear = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, "");
        final String oldGenre = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, "");

        genreBox.setVisibility(View.GONE);
        genreCombo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new LoadingTask<List<Genre>>(context, true) {
                    @Override
                    protected List<Genre> doInBackground() throws Throwable {
                        MusicService musicService = MusicServiceFactory.getMusicService(context);
                        return musicService.getGenres(false, context, this);
                    }

                    @Override
                    protected void done(final List<Genre> genres) {
                        List<String> names = new ArrayList<>();
                        String blank = context.getResources().getString(R.string.select_genre_blank);
                        names.add(blank);
                        for (Genre genre : genres) {
                            names.add(genre.getName());
                        }
                        final List<String> finalNames = names;

                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.shuffle_pick_genre)
                                .setItems(names.toArray(new CharSequence[names.size()]), (dialog, which) -> {
                                    if (which == 0) {
                                        genreCombo.setText("");
                                    } else {
                                        genreCombo.setText(finalNames.get(which));
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }

                    @Override
                    protected void error(Throwable error) {
                        String msg;
                        if (error instanceof OfflineException) {
                            msg = getErrorMessage(error);
                        } else {
                            msg = context.getResources().getString(R.string.playlist_error) + " " + getErrorMessage(error);
                        }

                        Util.toast(context, msg, false);
                    }
                }.execute();
            }
        });

        startYearBox.setText(oldStartYear);
        endYearBox.setText(oldEndYear);
        genreBox.setText(oldGenre);
        genreCombo.setText(oldGenre);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.shuffle_title)
                .setView(dialogView)
                .setPositiveButton(R.string.common_ok, (dialog, id) -> {
                    String genre;
                    genre = genreCombo.getText().toString();
                    String startYear = startYearBox.getText().toString();
                    String endYear = endYearBox.getText().toString();

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, startYear);
                    editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, endYear);
                    editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, genre);
                    editor.apply();

                    DownloadService downloadService = getDownloadService();
                    if (downloadService == null) {
                        return;
                    }

                    downloadService.clear();
                    downloadService.setShufflePlayEnabled(true);
                    context.openNowPlaying();
                })
                .setNegativeButton(R.string.common_cancel, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
        downloadRecursively(id, "", true, save, append, autoplay, shuffle, background, playNext);
    }

    void downloadPlaylist(final String id, final String name) {
        downloadRecursively(id, name, false, false, true, false, false, true, false);
    }

    private void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
        new RecursiveLoader(context) {
            @Override
            protected Boolean doInBackground() throws Throwable {
                musicService = MusicServiceFactory.getMusicService(context);
                MusicDirectory root;
                if (isDirectory && id != null) {
                    root = getMusicDirectory(id, name, false, musicService, this);
                } else {
                    root = musicService.getPlaylist(true, id, name, context, this);
                }

                boolean shuffleByAlbum = Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_SHUFFLE_BY_ALBUM, true);
                if (shuffle && shuffleByAlbum) {
                    Collections.shuffle(root.getChildren());
                }

                songs = new LinkedList<>();
                getSongsRecursively(root, songs);

                if (shuffle && !shuffleByAlbum) {
                    Collections.shuffle(songs);
                }

                DownloadService downloadService = getDownloadService();
                boolean transition = false;
                if (!songs.isEmpty() && downloadService != null) {
                    // Conditions for a standard play now operation
                    if (!append && !save && autoplay && !playNext && !shuffle && !background) {
                        playNowOverride = true;
                        return false;
                    }

                    if (!append && !background) {
                        downloadService.clear();
                    }
                    if (!background) {
                        downloadService.download(songs, save, autoplay, playNext, false);
                        if (!append) {
                            transition = true;
                        }
                    } else {
                        downloadService.downloadBackground(songs, save);
                    }
                }
                artistOverride = false;

                return transition;
            }
        }.execute();
    }

    void downloadRecursively(final List<Entry> albums, final boolean shuffle, final boolean append, final boolean playNext) {
        new RecursiveLoader(context) {
            @Override
            protected Boolean doInBackground() throws Throwable {
                musicService = MusicServiceFactory.getMusicService(context);

                if (shuffle) {
                    Collections.shuffle(albums);
                }

                songs = new LinkedList<>();
                MusicDirectory root = new MusicDirectory();
                root.addChildren(albums);
                getSongsRecursively(root, songs);

                DownloadService downloadService = getDownloadService();
                boolean transition = false;
                if (!songs.isEmpty() && downloadService != null) {
                    // Conditions for a standard play now operation
                    if (!append && !shuffle) {
                        playNowOverride = true;
                        return false;
                    }

                    if (!append) {
                        downloadService.clear();
                    }

                    downloadService.download(songs, false, true, playNext, false);
                    if (!append) {
                        transition = true;
                    }
                }
                artistOverride = false;

                return transition;
            }
        }.execute();
    }

    MusicDirectory getMusicDirectory(String id, String name, boolean refresh, MusicService service, ProgressListener listener) throws Exception {
        return getMusicDirectory(id, name, refresh, false, service, listener);
    }

    MusicDirectory getMusicDirectory(String id, String name, boolean refresh, boolean forceArtist, MusicService service, ProgressListener listener) throws Exception {
        if (!Util.isOffline(context)) {
            if (artist && !artistOverride || forceArtist) {
                return service.getArtist(id, name, refresh, context, listener);
            } else {
                return service.getAlbum(id, name, refresh, context, listener);
            }
        } else {
            return service.getMusicDirectory(id, name, refresh, context, listener);
        }
    }

    private void addToPlaylist(final List<Entry> songs) {
        Iterator<Entry> it = songs.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.isDirectory()) {
                it.remove();
            }
        }

        if (songs.isEmpty()) {
            Util.toast(context, "No songs selected");
            return;
        }

        new LoadingTask<List<Playlist>>(context, true) {
            @Override
            protected List<Playlist> doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(context);
                List<Playlist> playlists = new ArrayList<>(musicService.getPlaylists(false, context, this));

                // Iterate through and remove all non owned public playlists
                Iterator<Playlist> it = playlists.iterator();
                while (it.hasNext()) {
                    Playlist playlist = it.next();
                    if (playlist.getPublic() && !playlist.getId().contains(".m3u") && !UserUtil.getCurrentUsername(context).equals(playlist.getOwner())) {
                        it.remove();
                    }
                }

                return playlists;
            }

            @Override
            protected void done(final List<Playlist> playlists) {
                // Create adapter to show playlists
                Playlist createNew = new Playlist("-1", context.getResources().getString(R.string.playlist_create_new));
                playlists.add(0, createNew);
                ArrayAdapter playlistAdapter = new ArrayAdapter<Playlist>(context, R.layout.basic_count_item, playlists) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        Playlist playlist = getItem(position);

                        // Create new if not getting a convert view to use
                        PlaylistSongView view;
                        if (convertView instanceof PlaylistSongView) {
                            view = (PlaylistSongView) convertView;
                        } else {
                            view = new PlaylistSongView(context);
                        }

                        view.setObject(playlist, songs);

                        return view;
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.playlist_add_to)
                        .setAdapter(playlistAdapter, (dialog, which) -> {
                            if (which > 0) {
                                addToPlaylist(playlists.get(which), songs);
                            } else {
                                createNewPlaylist(songs, false);
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }

            @Override
            protected void error(Throwable error) {
                String msg;
                if (error instanceof OfflineException) {
                    msg = getErrorMessage(error);
                } else {
                    msg = context.getResources().getString(R.string.playlist_error) + " " + getErrorMessage(error);
                }

                Util.toast(context, msg, false);
            }
        }.execute();
    }

    private void addToPlaylist(final Playlist playlist, final List<Entry> songs) {
        new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(context);
                musicService.addToPlaylist(playlist.getId(), songs, context, null);
                return null;
            }

            @Override
            protected void done(Void result) {
                Util.toast(context, context.getResources().getString(R.string.updated_playlist, songs.size(), playlist.getName()));
            }

            @Override
            protected void error(Throwable error) {
                String msg;
                if (error instanceof OfflineException) {
                    msg = getErrorMessage(error);
                } else {
                    msg = context.getResources().getString(R.string.updated_playlist_error, playlist.getName()) + " " + getErrorMessage(error);
                }

                Util.toast(context, msg, false);
            }
        }.execute();
    }

    void createNewPlaylist(final List<Entry> songs, final boolean getSuggestion) {
        View layout = context.getLayoutInflater().inflate(R.layout.save_playlist, null);
        final EditText playlistNameView = layout.findViewById(R.id.save_playlist_name);
        final CheckBox overwriteCheckBox = layout.findViewById(R.id.save_playlist_overwrite);
        if (getSuggestion) {
            String playlistName = (getDownloadService() != null) ? getDownloadService().getSuggestedPlaylistName() : null;
            if (playlistName != null) {
                playlistNameView.setText(playlistName);
                try {
                    if (Integer.parseInt(getDownloadService().getSuggestedPlaylistId()) != -1) {
                        overwriteCheckBox.setChecked(true);
                        overwriteCheckBox.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.i(TAG, "Playlist id isn't a integer, probably MusicCabinet");
                }
            } else {
                DateFormat dateFormat = DateFormat.getDateInstance();
                playlistNameView.setText(dateFormat.format(new Date()));
            }
        } else {
            DateFormat dateFormat = DateFormat.getDateInstance();
            playlistNameView.setText(dateFormat.format(new Date()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.download_playlist_title)
                .setMessage(R.string.download_playlist_name)
                .setView(layout)
                .setPositiveButton(R.string.common_save, (dialog, id) -> {
                    String playlistName = String.valueOf(playlistNameView.getText());
                    if (overwriteCheckBox.isChecked()) {
                        overwritePlaylist(songs, playlistName, getDownloadService().getSuggestedPlaylistId());
                    } else {
                        createNewPlaylist(songs, playlistName);

                        if (getSuggestion) {
                            DownloadService downloadService = getDownloadService();
                            if (downloadService != null) {
                                downloadService.setSuggestedPlaylistName(playlistName, null);
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.common_cancel, (dialog, id) -> dialog.cancel())
                .setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void createNewPlaylist(final List<Entry> songs, final String name) {
        new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(context);
                musicService.createPlaylist(null, name, songs, context, null);
                return null;
            }

            @Override
            protected void done(Void result) {
                Util.toast(context, R.string.download_playlist_done);
            }

            @Override
            protected void error(Throwable error) {
                String msg = context.getResources().getString(R.string.download_playlist_error) + " " + getErrorMessage(error);
                Util.toast(context, msg);
            }
        }.execute();
    }

    private void overwritePlaylist(final List<Entry> songs, final String name, final String id) {
        new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(context);
                MusicDirectory playlist = musicService.getPlaylist(true, id, name, context, null);
                List<Entry> toDelete = playlist.getChildren();
                musicService.overwritePlaylist(id, name, toDelete.size(), songs, context, null);
                return null;
            }

            @Override
            protected void done(Void result) {
                Util.toast(context, R.string.download_playlist_done);
            }

            @Override
            protected void error(Throwable error) {
                String msg;
                if (error instanceof OfflineException) {
                    msg = getErrorMessage(error);
                } else {
                    msg = context.getResources().getString(R.string.download_playlist_error) + " " + getErrorMessage(error);
                }

                Util.toast(context, msg, false);
            }
        }.execute();
    }

    void displaySongInfo(final Entry song) {
        Integer duration = null;
        Integer bitrate = null;
        String format = null;
        long size = 0;
        if (!song.isDirectory()) {
            try {
                DownloadFile downloadFile = new DownloadFile(context, song, false);
                File file = downloadFile.getCompleteFile();
                if (file.exists()) {
                    MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                    metadata.setDataSource(file.getAbsolutePath());

                    String tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    duration = Integer.parseInt((tmp != null) ? tmp : "0") / 1000;
                    format = FileUtil.getExtension(file.getName());
                    size = file.length();

                    // If no duration try to read bitrate tag
                    // Otherwise do a calculation for it
                    // Divide by 1000 so in kbps
                    bitrate = (int) (size / duration) / 1000 * 8;

                    if (Util.isOffline(context)) {
                        song.setGenre(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
                        String year = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
                        song.setYear(Integer.parseInt((year != null) ? year : "0"));
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "Device doesn't properly support MediaMetadataRetreiver");
            }
        }
        if (duration == null) {
            duration = song.getDuration();
        }

        List<Integer> headers = new ArrayList<>();
        List<String> details = new ArrayList<>();

        if (!song.isDirectory()) {
            headers.add(R.string.details_title);
            details.add(song.getTitle());
        }

        if (song.getArtist() != null && !"".equals(song.getArtist())) {
            headers.add(R.string.details_artist);
            details.add(song.getArtist());
        }
        if (song.getAlbum() != null && !"".equals(song.getAlbum())) {
            headers.add(R.string.details_album);
            details.add(song.getAlbum());
        }
        if (song.getTrack() != null && song.getTrack() != 0) {
            headers.add(R.string.details_track);
            details.add(Integer.toString(song.getTrack()));
        }
        if (song.getGenre() != null && !"".equals(song.getGenre())) {
            headers.add(R.string.details_genre);
            details.add(song.getGenre());
        }
        if (song.getYear() != null && song.getYear() != 0) {
            headers.add(R.string.details_year);
            details.add(Integer.toString(song.getYear()));
        }
        if (!Util.isOffline(context) && song.getSuffix() != null) {
            headers.add(R.string.details_server_format);
            details.add(song.getSuffix());

            if (song.getBitRate() != null && song.getBitRate() != 0) {
                headers.add(R.string.details_server_bitrate);
                details.add(song.getBitRate() + " kbps");
            }
        }
        if (format != null && !"".equals(format)) {
            headers.add(R.string.details_cached_format);
            details.add(format);
        }
        if (bitrate != null && bitrate != 0) {
            headers.add(R.string.details_cached_bitrate);
            details.add(bitrate + " kbps");
        }
        if (size != 0) {
            headers.add(R.string.details_size);
            details.add(Util.formatLocalizedBytes(size, context));
        }
        if (duration != null && duration != 0) {
            headers.add(R.string.details_length);
            details.add(Util.formatDuration(duration));
        }

        try {
            Long[] dates = SongDBHandler.getHandler(context).getLastPlayed(song);
            if (dates != null && dates[0] != null && dates[0] > 0) {
                headers.add(R.string.details_last_played);
                Date date = new Date((dates[1] != null && dates[1] > dates[0]) ? dates[1] : dates[0]);
                DateFormat dateFormat = DateFormat.getDateInstance();
                details.add(dateFormat.format(date));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get last played", e);
        }

        int title;
        if (song.isDirectory()) {
            title = R.string.details_title_album;
        } else {
            title = R.string.details_title_song;
        }
        Util.showDetailsDialog(context, title, headers, details);
    }


    private void deleteRecursively(Artist artist) {
        deleteRecursively(artist, FileUtil.getArtistDirectory(context, artist));
    }

    void deleteRecursively(Entry album) {
        deleteRecursively(album, FileUtil.getAlbumDirectory(context, album));
    }

    private void deleteRecursively(final Object remove, final File dir) {
        if (dir == null) {
            return;
        }

        new LoadingTask<Void>(context) {
            @Override
            protected Void doInBackground() {
                MediaStoreService mediaStore = new MediaStoreService(context);
                FileUtil.recursiveDelete(dir, mediaStore);
                return null;
            }

            @Override
            protected void done(Void result) {
                if (Util.isOffline(context)) {
                    SectionAdapter adapter = getCurrentAdapter();
                    if (adapter != null) {
                        adapter.removeItem(remove);
                    } else {
                        refresh(true);
                    }
                } else {
                    UpdateView.triggerUpdate();
                }
            }
        }.execute();
    }

    private void deleteSongs(final List<Entry> songs) {
        new LoadingTask<Void>(context) {
            @Override
            protected Void doInBackground() {
                getDownloadService().delete(songs);
                return null;
            }

            @Override
            protected void done(Void result) {
                if (Util.isOffline(context)) {
                    SectionAdapter adapter = getCurrentAdapter();
                    if (adapter != null) {
                        for (Entry song : songs) {
                            adapter.removeItem(song);
                        }
                    } else {
                        refresh(true);
                    }
                } else {
                    UpdateView.triggerUpdate();
                }
            }
        }.execute();
    }

    private void showAlbumArtist(Entry entry) {
        SubsonicFragment fragment = new SelectDirectoryFragment();
        Bundle args = new Bundle();
        args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
        args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
        args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
        fragment.setArguments(args);

        replaceFragment(fragment);
    }

    private void showArtist(Entry entry) {
        SubsonicFragment fragment = new SelectDirectoryFragment();
        Bundle args = new Bundle();
        args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
        args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
        args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
        fragment.setArguments(args);

        replaceFragment(fragment);
    }

    private void showAlbum(Entry entry) {
        SubsonicFragment fragment = new SelectDirectoryFragment();
        Bundle args = new Bundle();
        args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getAlbumId());
        args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getAlbum());
        fragment.setArguments(args);

        replaceFragment(fragment);
    }

    public GestureDetector getGestureDetector() {
        return gestureScanner;
    }

    void onSongPress(List<Entry> entries, Entry entry) {
        onSongPress(entries, entry, true);
    }

    void onSongPress(List<Entry> entries, Entry entry, boolean allowPlayAll) {
        List<Entry> songs = new ArrayList<>();

        String songPressAction = Util.getSongPressAction(context);
        if ("all".equals(songPressAction) && allowPlayAll) {
            for (Entry song : entries) {
                if (!song.isDirectory()) {
                    songs.add(song);
                }
            }
            playNow(songs, entry);
        } else if ("next".equals(songPressAction)) {
            getDownloadService().download(Collections.singletonList(entry), false, false, true, false);
        } else if ("last".equals(songPressAction)) {
            getDownloadService().download(Collections.singletonList(entry), false, false, false, false);
        } else {
            songs.add(entry);
            playNow(songs);
        }
    }

    private void playNow(List<Entry> entries) {
        playNow(entries, null, null);
    }

    private void playNow(final List<Entry> entries, final String playlistName, final String playlistId) {
        new RecursiveLoader(context) {
            @Override
            protected Boolean doInBackground() throws Throwable {
                getSongsRecursively(entries, songs);
                return null;
            }

            @Override
            protected void done(Boolean result) {
                Entry selected = songs.isEmpty() ? null : songs.get(0);
                playNow(songs, selected, playlistName, playlistId);
            }
        }.execute();
    }

    private void playNow(List<Entry> entries, Entry song) {
        playNow(entries, song, null, null);
    }

    private void playNow(final List<Entry> entries, final Entry song, final String playlistName, final String playlistId) {
        new LoadingTask<Void>(context) {
            @Override
            protected Void doInBackground() {
                playNowInTask(entries, song, playlistName, playlistId);
                return null;
            }

            @Override
            protected void done(Void result) {
                context.openNowPlaying();
            }
        }.execute();
    }

    private void playNowInTask(final List<Entry> entries, final Entry song, final String playlistName, final String playlistId) {
        DownloadService downloadService = getDownloadService();
        if (downloadService == null) {
            return;
        }

        downloadService.clear();
        downloadService.download(entries, false, true, true, false, entries.indexOf(song));
        downloadService.setSuggestedPlaylistName(playlistName, playlistId);
    }

    public SectionAdapter getCurrentAdapter() {
        return null;
    }

    public void stopActionMode() {
        SectionAdapter adapter = getCurrentAdapter();
        if (adapter != null) {
            adapter.stopActionMode();
        }
    }

    private void clearSelected() {
        if (getCurrentAdapter() != null) {
            getCurrentAdapter().clearSelected();
        }
    }

    List<Entry> getSelectedEntries() {
        return getCurrentAdapter().getSelected();
    }

     void playNow(final boolean shuffle, final boolean append, final boolean playNext) {
        List<Entry> songs = getSelectedEntries();
        if (!songs.isEmpty()) {
            download(songs, append, !append, playNext, shuffle);
            clearSelected();
        }
    }

    void download(List<Entry> entries, boolean append, boolean autoplay, boolean playNext, boolean shuffle) {
        download(entries, append, autoplay, playNext, shuffle, null, null);
    }

    void download(final List<Entry> entries, final boolean append, final boolean autoplay, final boolean playNext, final boolean shuffle, final String playlistName, final String playlistId) {
        final DownloadService downloadService = getDownloadService();
        if (downloadService == null) {
            return;
        }
        warnIfStorageUnavailable();

        // Conditions for using play now button
        if (!append && autoplay && !playNext && !shuffle) {
            // Call playNow which goes through and tries to use information
            playNow(entries, playlistName, playlistId);
            return;
        }

        RecursiveLoader onValid = new RecursiveLoader(context) {
            @Override
            protected Boolean doInBackground() throws Throwable {
                if (!append) {
                    getDownloadService().clear();
                }
                getSongsRecursively(entries, songs);

                downloadService.download(songs, false, autoplay, playNext, shuffle);
                if (playlistName != null) {
                    downloadService.setSuggestedPlaylistName(playlistName, playlistId);
                } else {
                    downloadService.setSuggestedPlaylistName(null, null);
                }
                return null;
            }

            @Override
            protected void done(Boolean result) {
                if (autoplay) {
                    context.openNowPlaying();
                } else if (append) {
                    Util.toast(context,
                            context.getResources().getQuantityString(R.plurals.select_album_n_songs_added, songs.size(), songs.size()));
                }
            }
        };

        executeOnValid(onValid);
    }

    private void executeOnValid(RecursiveLoader onValid) {
        onValid.execute();
    }

    void downloadBackground(final boolean save) {
        List<Entry> songs = getSelectedEntries();
        if (!songs.isEmpty()) {
            downloadBackground(save, songs);
        }
    }

    void downloadBackground(final boolean save, final List<Entry> entries) {
        if (getDownloadService() == null) {
            return;
        }

        warnIfStorageUnavailable();
        new RecursiveLoader(context) {
            @Override
            protected Boolean doInBackground() throws Throwable {
                getSongsRecursively(entries);
                getDownloadService().downloadBackground(songs, save);
                return null;
            }

            @Override
            protected void done(Boolean result) {
                Util.toast(context, context.getResources().getQuantityString(R.plurals.select_album_n_songs_downloading, songs.size(), songs.size()));
            }
        }.execute();
    }

    void delete() {
        List<Entry> songs = getSelectedEntries();
        if (!songs.isEmpty()) {
            DownloadService downloadService = getDownloadService();
            if (downloadService != null) {
                downloadService.delete(songs);
            }
        }
    }

    boolean isShowArtistEnabled() {
        return false;
    }

    String getCurrentQuery() {
        return null;
    }

    public abstract class RecursiveLoader extends LoadingTask<Boolean> {
        static final int MAX_SONGS = 500;
        MusicService musicService;
        boolean playNowOverride = false;
        List<Entry> songs = new ArrayList<>();

        public RecursiveLoader(Activity context) {
            super(context);
            musicService = MusicServiceFactory.getMusicService(context);
        }

        void getSongsRecursively(List<Entry> entry) throws Exception {
            getSongsRecursively(entry, songs);
        }

        void getSongsRecursively(List<Entry> entry, List<Entry> songs) throws Exception {
            MusicDirectory dir = new MusicDirectory();
            dir.addChildren(entry);
            getSongsRecursively(dir, songs);
        }

        void getSongsRecursively(MusicDirectory parent, List<Entry> songs) throws Exception {
            if (songs.size() > MAX_SONGS) {
                return;
            }

            for (Entry dir : parent.getChildren(true, false)) {
                MusicDirectory musicDirectory;
                if (!Util.isOffline(context)) {
                    musicDirectory = musicService.getAlbum(dir.getId(), dir.getTitle(), false, context, this);
                } else {
                    musicDirectory = musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, context, this);
                }
                getSongsRecursively(musicDirectory, songs);
            }

            songs.addAll(parent.getChildren(false, true));
        }

        @Override
        protected void done(Boolean result) {
            warnIfStorageUnavailable();

            if (playNowOverride) {
                playNow(songs);
                return;
            }

            if (result) {
                context.openNowPlaying();
            }
        }
    }
}
