package net.nullsum.audinaut.fragments;

import android.os.Bundle;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.adapter.AlphabeticalAlbumAdapter;
import net.nullsum.audinaut.adapter.EntryGridAdapter;
import net.nullsum.audinaut.adapter.EntryInfiniteGridAdapter;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.service.CachedMusicService;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.service.MusicServiceFactory;
import net.nullsum.audinaut.service.OfflineException;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.LoadingTask;
import net.nullsum.audinaut.util.Pair;
import net.nullsum.audinaut.util.TabBackgroundTask;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.FastScroller;
import net.nullsum.audinaut.view.UpdateView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.nullsum.audinaut.domain.MusicDirectory.Entry;

public class SelectDirectoryFragment extends SubsonicFragment implements SectionAdapter.OnItemClickedListener<Entry> {
    private String id;
    private String name;
    private Entry directory;
    private String playlistId;
    private String playlistName;
    private boolean playlistOwner;
    private String albumListType;
    private String albumListExtra;
    private int albumListSize;
    private boolean refreshListing = false;
    private boolean restoredInstance = false;
    private boolean lookupParent = false;
    private boolean largeAlbums = false;
    private boolean topTracks = false;
    private String lookupEntry;
    private RecyclerView recyclerView;
    private FastScroller fastScroller;
    private EntryGridAdapter entryGridAdapter;
    private List<Entry> albums;
    private List<Entry> entries;
    private LoadTask currentTask;

    public SelectDirectoryFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle != null) {
            entries = (List<Entry>) bundle.getSerializable(Constants.FRAGMENT_LIST);
            albums = (List<Entry>) bundle.getSerializable(Constants.FRAGMENT_LIST2);
            if (albums == null) {
                albums = new ArrayList<>();
            }
            restoredInstance = true;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(Constants.FRAGMENT_LIST, (Serializable) entries);
        outState.putSerializable(Constants.FRAGMENT_LIST2, (Serializable) albums);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Bundle args = getArguments();
        if (args != null) {
            id = args.getString(Constants.INTENT_EXTRA_NAME_ID);
            name = args.getString(Constants.INTENT_EXTRA_NAME_NAME);
            directory = (Entry) args.getSerializable(Constants.INTENT_EXTRA_NAME_DIRECTORY);
            playlistId = args.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
            playlistName = args.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
            playlistOwner = args.getBoolean(Constants.INTENT_EXTRA_NAME_PLAYLIST_OWNER, false);
            albumListType = args.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
            albumListExtra = args.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_EXTRA);
            albumListSize = args.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
            refreshListing = args.getBoolean(Constants.INTENT_EXTRA_REFRESH_LISTINGS);
            artist = args.getBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, false);
            lookupEntry = args.getString(Constants.INTENT_EXTRA_SEARCH_SONG);
            topTracks = args.getBoolean(Constants.INTENT_EXTRA_TOP_TRACKS);

            String childId = args.getString(Constants.INTENT_EXTRA_NAME_CHILD_ID);
            if (childId != null) {
                id = childId;
                lookupParent = true;
            }
            if (entries == null) {
                entries = (List<Entry>) args.getSerializable(Constants.FRAGMENT_LIST);
                albums = (List<Entry>) args.getSerializable(Constants.FRAGMENT_LIST2);

                if (albums == null) {
                    albums = new ArrayList<>();
                }
            }
        }

        rootView = inflater.inflate(R.layout.abstract_recycler_fragment, container, false);

        refreshLayout = rootView.findViewById(R.id.refresh_layout);
        refreshLayout.setOnRefreshListener(this);

        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_LARGE_ALBUM_ART, true)) {
            largeAlbums = true;
        }

        recyclerView = rootView.findViewById(R.id.fragment_recycler);
        recyclerView.setHasFixedSize(true);
        fastScroller = rootView.findViewById(R.id.fragment_fast_scroller);
        setupScrollList(recyclerView);
        setupLayoutManager(recyclerView, largeAlbums);

        if (entries == null) {
            if (primaryFragment || secondaryFragment) {
                load(false);
            } else {
                invalidated = true;
            }
        } else {
            finishLoading();
        }

        if (name != null) {
            setTitle(name);
        }

        return rootView;
    }

    @Override
    public void setIsOnlyVisible(boolean isOnlyVisible) {
        boolean update = this.isOnlyVisible != isOnlyVisible;
        super.setIsOnlyVisible(isOnlyVisible);
        if (update && entryGridAdapter != null) {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof GridLayoutManager) {
                ((GridLayoutManager) layoutManager).setSpanCount(getRecyclerColumnCount());
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        if (albumListType != null) {
            menuInflater.inflate(R.menu.select_album_list, menu);
        } else if (artist) {
            menuInflater.inflate(R.menu.select_album, menu);
        } else {
            if (Util.isOffline(context)) {
                menuInflater.inflate(R.menu.select_song_offline, menu);
            } else {
                menuInflater.inflate(R.menu.select_song, menu);

                if (playlistId == null || !playlistOwner) {
                    menu.removeItem(R.id.menu_remove_playlist);
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_remove_playlist:
                removeFromPlaylist(playlistId, playlistName, getSelectedIndexes());
                return true;
        }

        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onCreateContextMenu(Menu menu, MenuInflater menuInflater, UpdateView updateView, Entry entry) {
        onCreateContextMenuSupport(menu, menuInflater, updateView, entry);
        if (!Util.isOffline(context) && (playlistId == null || !playlistOwner)) {
            menu.removeItem(R.id.song_menu_remove_playlist);
        }

        recreateContextMenu(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem, UpdateView<Entry> updateView, Entry entry) {
        if (onContextItemSelected(menuItem, entry)) {
            return true;
        }

        switch (menuItem.getItemId()) {
            case R.id.song_menu_remove_playlist:
                removeFromPlaylist(playlistId, playlistName, Collections.singletonList(entries.indexOf(entry)));
                break;
        }

        return true;
    }

    @Override
    public void onItemClicked(UpdateView<Entry> updateView, Entry entry) {
        if (entry.isDirectory()) {
            SubsonicFragment fragment = new SelectDirectoryFragment();
            Bundle args = new Bundle();
            args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getId());
            args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getTitle());
            args.putSerializable(Constants.INTENT_EXTRA_NAME_DIRECTORY, entry);
            if ("newest".equals(albumListType)) {
                args.putBoolean(Constants.INTENT_EXTRA_REFRESH_LISTINGS, true);
            }
            if (!entry.isAlbum()) {
                args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
            }
            fragment.setArguments(args);

            replaceFragment(fragment);
        } else {
            onSongPress(entries, entry, albumListType == null);
        }
    }

    @Override
    protected void refresh(boolean refresh) {
        load(refresh);
    }

    @Override
    protected boolean isShowArtistEnabled() {
        return albumListType != null;
    }

    private void load(boolean refresh) {
        if (refreshListing) {
            refresh = true;
        }

        if (currentTask != null) {
            currentTask.cancel();
        }

        recyclerView.setVisibility(View.INVISIBLE);
        if (playlistId != null) {
            getPlaylist(playlistId, playlistName, refresh);
        } else if (albumListType != null) {
            getAlbumList(albumListType, albumListSize, refresh);
        } else {
            getMusicDirectory(id, name, refresh);
        }
    }

    private void getMusicDirectory(final String id, final String name, final boolean refresh) {
        setTitle(name);

        new LoadTask() {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception {
                MusicDirectory dir = getMusicDirectory(id, name, refresh, service, this);

                if (lookupParent && dir.getParent() != null) {
                    dir = getMusicDirectory(dir.getParent(), name, refresh, service, this);

                    // Update the fragment pointers so other stuff works correctly
                    SelectDirectoryFragment.this.id = dir.getId();
                    SelectDirectoryFragment.this.name = dir.getName();
                } else if (id != null && directory == null && dir.getParent() != null && !artist) {
                    MusicDirectory parentDir = getMusicDirectory(dir.getParent(), name, refresh, true, service, this);
                    for (Entry child : parentDir.getChildren()) {
                        if (id.equals(child.getId())) {
                            directory = child;
                            break;
                        }
                    }
                }

                return dir;
            }

            @Override
            protected void done(Pair<MusicDirectory, Boolean> result) {
                SelectDirectoryFragment.this.name = result.getFirst().getName();
                setTitle(SelectDirectoryFragment.this.name);
                super.done(result);
            }
        }.execute();
    }

    private void getPlaylist(final String playlistId, final String playlistName, final boolean refresh) {
        setTitle(playlistName);

        new LoadTask() {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception {
                return service.getPlaylist(refresh, playlistId, playlistName, context, this);
            }
        }.execute();
    }

    private void getAlbumList(final String albumListType, final int size, final boolean refresh) {
        switch (albumListType) {
            case "random":
                setTitle(R.string.main_albums_random);
                break;
            case "recent":
                setTitle(R.string.main_albums_recent);
                break;
            case "frequent":
                setTitle(R.string.main_albums_frequent);
                break;
            case "genres":
            case "years":
                setTitle(albumListExtra);
                break;
            case "alphabeticalByName":
                setTitle(R.string.main_albums_alphabetical);
                break;
        }
        switch (albumListType) {
            case MainFragment.SONGS_NEWEST:
                setTitle(R.string.main_songs_newest);
                break;
            case MainFragment.SONGS_TOP_PLAYED:
                setTitle(R.string.main_songs_top_played);
                break;
            case MainFragment.SONGS_RECENT:
                setTitle(R.string.main_songs_recent);
                break;
            case MainFragment.SONGS_FREQUENT:
                setTitle(R.string.main_songs_frequent);
                break;
        }

        new LoadTask() {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception {
                MusicDirectory result;
                if ("genres".equals(albumListType) || "years".equals(albumListType)) {
                    result = service.getAlbumList(albumListType, albumListExtra, size, 0, refresh, context, this);
                    if (result.getChildrenSize() == 0 && "genres".equals(albumListType)) {
                        SelectDirectoryFragment.this.albumListType = "genres-songs";
                        result = service.getSongsByGenre(albumListExtra, size, 0, context, this);
                    }
                } else if ("genres".equals(albumListType) || "genres-songs".equals(albumListType)) {
                    result = service.getSongsByGenre(albumListExtra, size, 0, context, this);
                } else if (albumListType.contains(MainFragment.SONGS_LIST_PREFIX)) {
                    result = service.getSongList(albumListType, size, 0, context, this);
                } else {
                    result = service.getAlbumList(albumListType, size, 0, refresh, context, this);
                }
                return result;
            }
        }.execute();
    }

    @Override
    public SectionAdapter<Entry> getCurrentAdapter() {
        return entryGridAdapter;
    }

    @Override
    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final GridLayoutManager gridLayoutManager) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int viewType = entryGridAdapter.getItemViewType(position);
                if (viewType == EntryGridAdapter.VIEW_TYPE_SONG || viewType == EntryGridAdapter.VIEW_TYPE_HEADER || viewType == EntryInfiniteGridAdapter.VIEW_TYPE_LOADING) {
                    return gridLayoutManager.getSpanCount();
                } else {
                    return 1;
                }
            }
        };
    }

    private void finishLoading() {
        boolean validData = !entries.isEmpty() || !albums.isEmpty();
        if (!validData) {
            setEmpty(true);
        }

        if (validData) {
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (albumListType == null) {
            entryGridAdapter = new EntryGridAdapter(context, entries, getImageLoader(), largeAlbums);
            entryGridAdapter.setRemoveFromPlaylist(playlistId != null);
        } else {
            if ("alphabeticalByName".equals(albumListType)) {
                entryGridAdapter = new AlphabeticalAlbumAdapter(context, entries, getImageLoader(), largeAlbums);
            } else {
                entryGridAdapter = new EntryInfiniteGridAdapter(context, entries, getImageLoader(), largeAlbums);
            }

            // Setup infinite loading based on scrolling
            final EntryInfiniteGridAdapter infiniteGridAdapter = (EntryInfiniteGridAdapter) entryGridAdapter;
            infiniteGridAdapter.setData(albumListType, albumListExtra, albumListSize);

            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                    int totalItemCount = layoutManager.getItemCount();
                    int lastVisibleItem;
                    if (layoutManager instanceof GridLayoutManager) {
                        lastVisibleItem = ((GridLayoutManager) layoutManager).findLastVisibleItemPosition();
                    } else if (layoutManager instanceof LinearLayoutManager) {
                        lastVisibleItem = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                    } else {
                        return;
                    }

                    if (totalItemCount > 0 && lastVisibleItem >= totalItemCount - 2) {
                        infiniteGridAdapter.loadMore();
                    }
                }
            });
        }
        entryGridAdapter.setOnItemClickedListener(this);
        // Always show artist if this is not a artist we are viewing
        if (!artist) {
            entryGridAdapter.setShowArtist();
        }
        if (topTracks) {
            entryGridAdapter.setShowAlbum();
        }

        int scrollToPosition = -1;
        if (lookupEntry != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (lookupEntry.equals(entries.get(i).getTitle())) {
                    scrollToPosition = i;
                    entryGridAdapter.addSelected(entries.get(i));
                    lookupEntry = null;
                    break;
                }
            }
        }

        recyclerView.setAdapter(entryGridAdapter);
        fastScroller.attachRecyclerView(recyclerView);
        context.supportInvalidateOptionsMenu();

        if (scrollToPosition != -1) {
            recyclerView.scrollToPosition(scrollToPosition);
        }

        Bundle args = getArguments();
        boolean playAll = args.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);
        if (playAll && !restoredInstance) {
            playAll(args.getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false), false, false);
        }
    }

    @Override
    protected void playNow(final boolean shuffle, final boolean append, final boolean playNext) {
        List<Entry> songs = getSelectedEntries();
        if (!songs.isEmpty()) {
            download(songs, append, !append, playNext, shuffle);
            entryGridAdapter.clearSelected();
        } else {
            playAll(shuffle, append, playNext);
        }
    }

    private void playAll(final boolean shuffle, final boolean append, final boolean playNext) {
        boolean hasSubFolders = albums != null && !albums.isEmpty();

        if (hasSubFolders && id != null) {
            downloadRecursively(id, false, append, !append, shuffle, false, playNext);
        } else if (hasSubFolders && albumListType != null) {
            downloadRecursively(albums, shuffle, append, playNext);
        } else {
            download(entries, append, !append, playNext, shuffle);
        }
    }

    private List<Integer> getSelectedIndexes() {
        List<Entry> selected = entryGridAdapter.getSelected();
        List<Integer> indexes = new ArrayList<>();

        for (Entry entry : selected) {
            indexes.add(entries.indexOf(entry));
        }

        return indexes;
    }

    @Override
    protected void downloadBackground(final boolean save) {
        List<Entry> songs = getSelectedEntries();
        if (playlistId != null) {
            songs = entries;
        }

        if (songs.isEmpty()) {
            // Get both songs and albums
            downloadRecursively(id, save, false, false, false, true, false);
        } else {
            downloadBackground(save, songs);
        }
    }

    @Override
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
        };
    }

    @Override
    void download(List<Entry> entries, boolean append, boolean autoplay, boolean playNext, boolean shuffle) {
        download(entries, append, autoplay, playNext, shuffle, playlistName, playlistId);
    }

    @Override
    protected void delete() {
        List<Entry> songs = getSelectedEntries();
        if (songs.isEmpty()) {
            for (Entry entry : entries) {
                if (entry.isDirectory()) {
                    deleteRecursively(entry);
                } else {
                    songs.add(entry);
                }
            }
        }
        if (getDownloadService() != null) {
            getDownloadService().delete(songs);
        }
    }

    private void removeFromPlaylist(final String id, final String name, final List<Integer> indexes) {
        new LoadingTask<Void>(context, true) {
            @Override
            protected Void doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(context);
                musicService.removeFromPlaylist(id, indexes, context, null);
                return null;
            }

            @Override
            protected void done(Void result) {
                for (Integer index : indexes) {
                    entryGridAdapter.removeAt(index);
                }
                Util.toast(context, context.getResources().getString(R.string.removed_playlist, indexes.size(), name));
            }

            @Override
            protected void error(Throwable error) {
                String msg;
                if (error instanceof OfflineException) {
                    msg = getErrorMessage(error);
                } else {
                    msg = context.getResources().getString(R.string.updated_playlist_error, name) + " " + getErrorMessage(error);
                }

                Util.toast(context, msg, false);
            }
        }.execute();
    }

    private abstract class LoadTask extends TabBackgroundTask<Pair<MusicDirectory, Boolean>> {

        public LoadTask() {
            super(SelectDirectoryFragment.this);

            currentTask = this;
        }

        protected abstract MusicDirectory load(MusicService service) throws Exception;

        @Override
        protected Pair<MusicDirectory, Boolean> doInBackground() throws Throwable {
            MusicService musicService = MusicServiceFactory.getMusicService(context);
            MusicDirectory dir = load(musicService);

            albums = dir.getChildren(true, false);
            entries = dir.getChildren();

            // This isn't really an artist if no albums on it!
            if (albums.size() == 0) {
                artist = false;
            }

            return new Pair<>(dir, true);
        }

        @Override
        protected void done(Pair<MusicDirectory, Boolean> result) {
            finishLoading();
            currentTask = null;
        }

        @Override
        public void updateCache(int changeCode) {
            if (entryGridAdapter != null && changeCode == CachedMusicService.CACHE_UPDATE_LIST) {
                entryGridAdapter.notifyDataSetChanged();
            }
        }
    }
}
