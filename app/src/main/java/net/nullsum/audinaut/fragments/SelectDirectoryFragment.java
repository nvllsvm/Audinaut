package net.nullsum.audinaut.fragments;

import android.annotation.TargetApi;
import android.support.v7.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import net.nullsum.audinaut.R;
import net.nullsum.audinaut.adapter.AlphabeticalAlbumAdapter;
import net.nullsum.audinaut.adapter.EntryInfiniteGridAdapter;
import net.nullsum.audinaut.adapter.EntryGridAdapter;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.service.CachedMusicService;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.util.DrawableTint;
import net.nullsum.audinaut.util.ImageLoader;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.service.MusicServiceFactory;
import net.nullsum.audinaut.service.OfflineException;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.LoadingTask;
import net.nullsum.audinaut.util.Pair;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.TabBackgroundTask;
import net.nullsum.audinaut.util.UpdateHelper;
import net.nullsum.audinaut.util.UserUtil;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.FastScroller;
import net.nullsum.audinaut.view.GridSpacingDecoration;
import net.nullsum.audinaut.view.MyLeadingMarginSpan2;
import net.nullsum.audinaut.view.RecyclingImageView;
import net.nullsum.audinaut.view.UpdateView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static net.nullsum.audinaut.domain.MusicDirectory.Entry;

public class SelectDirectoryFragment extends SubsonicFragment implements SectionAdapter.OnItemClickedListener<Entry> {
	private static final String TAG = SelectDirectoryFragment.class.getSimpleName();

	private RecyclerView recyclerView;
	private FastScroller fastScroller;
	private EntryGridAdapter entryGridAdapter;
	private List<Entry> albums;
	private List<Entry> entries;
	private LoadTask currentTask;

	private SilentBackgroundTask updateCoverArtTask;
	private ImageView coverArtView;
	private Entry coverArtRep;
	private String coverArtId;

	String id;
	String name;
	Entry directory;
	String playlistId;
	String playlistName;
	boolean playlistOwner;
	String albumListType;
	String albumListExtra;
	int albumListSize;
	boolean refreshListing = false;
	boolean restoredInstance = false;
	boolean lookupParent = false;
	boolean largeAlbums = false;
	boolean topTracks = false;
	String lookupEntry;

	public SelectDirectoryFragment() {
		super();
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		if(bundle != null) {
			entries = (List<Entry>) bundle.getSerializable(Constants.FRAGMENT_LIST);
			albums = (List<Entry>) bundle.getSerializable(Constants.FRAGMENT_LIST2);
			if(albums == null) {
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
		if(args != null) {
			id = args.getString(Constants.INTENT_EXTRA_NAME_ID);
			name = args.getString(Constants.INTENT_EXTRA_NAME_NAME);
			directory = (Entry) args.getSerializable(Constants.INTENT_EXTRA_NAME_DIRECTORY);
			playlistId = args.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
			playlistName = args.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
			playlistOwner = args.getBoolean(Constants.INTENT_EXTRA_NAME_PLAYLIST_OWNER, false);
			Object shareObj = args.getSerializable(Constants.INTENT_EXTRA_NAME_SHARE);
			albumListType = args.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
			albumListExtra = args.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_EXTRA);
			albumListSize = args.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
			refreshListing = args.getBoolean(Constants.INTENT_EXTRA_REFRESH_LISTINGS);
			artist = args.getBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, false);
			lookupEntry = args.getString(Constants.INTENT_EXTRA_SEARCH_SONG);
			topTracks = args.getBoolean(Constants.INTENT_EXTRA_TOP_TRACKS);

			String childId = args.getString(Constants.INTENT_EXTRA_NAME_CHILD_ID);
			if(childId != null) {
				id = childId;
				lookupParent = true;
			}
			if(entries == null) {
				entries = (List<Entry>) args.getSerializable(Constants.FRAGMENT_LIST);
				albums = (List<Entry>) args.getSerializable(Constants.FRAGMENT_LIST2);

				if(albums == null) {
					albums = new ArrayList<Entry>();
				}
			}
		}

		rootView = inflater.inflate(R.layout.abstract_recycler_fragment, container, false);

		refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.refresh_layout);
		refreshLayout.setOnRefreshListener(this);

		if(Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_LARGE_ALBUM_ART, true)) {
			largeAlbums = true;
		}

		recyclerView = (RecyclerView) rootView.findViewById(R.id.fragment_recycler);
		recyclerView.setHasFixedSize(true);
		fastScroller = (FastScroller) rootView.findViewById(R.id.fragment_fast_scroller);
		setupScrollList(recyclerView);
		setupLayoutManager(recyclerView, largeAlbums);

		if(entries == null) {
			if(primaryFragment || secondaryFragment) {
				load(false);
			} else {
				invalidated = true;
			}
		} else {
            finishLoading();
		}

		if(name != null) {
			setTitle(name);
		}

		return rootView;
	}

	@Override
	public void setIsOnlyVisible(boolean isOnlyVisible) {
		boolean update = this.isOnlyVisible != isOnlyVisible;
		super.setIsOnlyVisible(isOnlyVisible);
		if(update && entryGridAdapter != null) {
			RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
			if(layoutManager instanceof GridLayoutManager) {
				((GridLayoutManager) layoutManager).setSpanCount(getRecyclerColumnCount());
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		if(albumListType != null) {
			menuInflater.inflate(R.menu.select_album_list, menu);
		} else if(artist) {
			menuInflater.inflate(R.menu.select_album, menu);
		} else {
            if(Util.isOffline(context)) {
                menuInflater.inflate(R.menu.select_song_offline, menu);
            }
            else {
                menuInflater.inflate(R.menu.select_song, menu);

                if(playlistId == null || !playlistOwner) {
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
		if(!Util.isOffline(context) && (playlistId == null || !playlistOwner)) {
			menu.removeItem(R.id.song_menu_remove_playlist);
		}

		recreateContextMenu(menu);
	}
	@Override
	public boolean onContextItemSelected(MenuItem menuItem, UpdateView<Entry> updateView, Entry entry) {
		if(onContextItemSelected(menuItem, entry)) {
			return true;
		}

		switch (menuItem.getItemId()) {
			case R.id.song_menu_remove_playlist:
				removeFromPlaylist(playlistId, playlistName, Arrays.<Integer>asList(entries.indexOf(entry)));
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
			if(!entry.isAlbum()) {
				args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
			}
			fragment.setArguments(args);

			replaceFragment(fragment, true);
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
		if(refreshListing) {
			refresh = true;
		}

		if(currentTask != null) {
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

		new LoadTask(refresh) {
			@Override
			protected MusicDirectory load(MusicService service) throws Exception {
				MusicDirectory dir = getMusicDirectory(id, name, refresh, service, this);

				if(lookupParent && dir.getParent() != null) {
					dir = getMusicDirectory(dir.getParent(), name, refresh, service, this);

					// Update the fragment pointers so other stuff works correctly
					SelectDirectoryFragment.this.id = dir.getId();
					SelectDirectoryFragment.this.name = dir.getName();
				} else if(id != null && directory == null && dir.getParent() != null && !artist) {
					MusicDirectory parentDir = getMusicDirectory(dir.getParent(), name, refresh, true, service, this);
					for(Entry child: parentDir.getChildren()) {
						if(id.equals(child.getId())) {
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

	private void getRecursiveMusicDirectory(final String id, final String name, final boolean refresh) {
		setTitle(name);

		new LoadTask(refresh) {
			@Override
			protected MusicDirectory load(MusicService service) throws Exception {
				MusicDirectory root = getMusicDirectory(id, name, refresh, service, this);
				List<Entry> songs = new ArrayList<Entry>();
				getSongsRecursively(root, songs);
				root.replaceChildren(songs);
				return root;
			}

			private void getSongsRecursively(MusicDirectory parent, List<Entry> songs) throws Exception {
				songs.addAll(parent.getChildren(false, true));
				for (Entry dir : parent.getChildren(true, false)) {
					MusicService musicService = MusicServiceFactory.getMusicService(context);

					MusicDirectory musicDirectory;
					if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
						musicDirectory = musicService.getAlbum(dir.getId(), dir.getTitle(), false, context, this);
					} else {
						musicDirectory = musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, context, this);
					}
					getSongsRecursively(musicDirectory, songs);
				}
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

		new LoadTask(refresh) {
			@Override
			protected MusicDirectory load(MusicService service) throws Exception {
				return service.getPlaylist(refresh, playlistId, playlistName, context, this);
			}
		}.execute();
	}

	private void getTopTracks(final String id, final String name, final boolean refresh) {
		setTitle(name);

		new LoadTask(refresh) {
			@Override
			protected MusicDirectory load(MusicService service) throws Exception {
				return service.getTopTrackSongs(name, 50, context, this);
			}
		}.execute();
	}

	private void getAlbumList(final String albumListType, final int size, final boolean refresh) {
		if ("newest".equals(albumListType)) {
			setTitle(R.string.main_albums_newest);
		} else if ("random".equals(albumListType)) {
			setTitle(R.string.main_albums_random);
		} else if ("recent".equals(albumListType)) {
			setTitle(R.string.main_albums_recent);
		} else if ("frequent".equals(albumListType)) {
			setTitle(R.string.main_albums_frequent);
		} else if("genres".equals(albumListType) || "years".equals(albumListType)) {
			setTitle(albumListExtra);
		} else if("alphabeticalByName".equals(albumListType)) {
			setTitle(R.string.main_albums_alphabetical);
		} if (MainFragment.SONGS_NEWEST.equals(albumListType)) {
			setTitle(R.string.main_songs_newest);
		} else if (MainFragment.SONGS_TOP_PLAYED.equals(albumListType)) {
			setTitle(R.string.main_songs_top_played);
		} else if (MainFragment.SONGS_RECENT.equals(albumListType)) {
			setTitle(R.string.main_songs_recent);
		} else if (MainFragment.SONGS_FREQUENT.equals(albumListType)) {
			setTitle(R.string.main_songs_frequent);
		}

		new LoadTask(true) {
			@Override
			protected MusicDirectory load(MusicService service) throws Exception {
				MusicDirectory result;
				if("genres".equals(albumListType) || "years".equals(albumListType)) {
					result = service.getAlbumList(albumListType, albumListExtra, size, 0, refresh, context, this);
					if(result.getChildrenSize() == 0 && "genres".equals(albumListType)) {
						SelectDirectoryFragment.this.albumListType = "genres-songs";
						result = service.getSongsByGenre(albumListExtra, size, 0, context, this);
					}
				} else if("genres".equals(albumListType) || "genres-songs".equals(albumListType)) {
					result = service.getSongsByGenre(albumListExtra, size, 0, context, this);
				} else if(albumListType.indexOf(MainFragment.SONGS_LIST_PREFIX) != -1) {
					result = service.getSongList(albumListType, size, 0, context, this);
				} else {
					result = service.getAlbumList(albumListType, size, 0, refresh, context, this);
				}
				return result;
			}
		}.execute();
	}

	private abstract class LoadTask extends TabBackgroundTask<Pair<MusicDirectory, Boolean>> {
		private boolean refresh;

		public LoadTask(boolean refresh) {
			super(SelectDirectoryFragment.this);
			this.refresh = refresh;

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
			if(albums.size() == 0) {
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
			if(entryGridAdapter != null && changeCode == CachedMusicService.CACHE_UPDATE_LIST) {
				entryGridAdapter.notifyDataSetChanged();
			} else if(changeCode == CachedMusicService.CACHE_UPDATE_METADATA) {
				if(coverArtView != null && coverArtRep != null && !Util.equals(coverArtRep.getCoverArt(), coverArtId)) {
					synchronized (coverArtRep) {
						if (updateCoverArtTask != null && updateCoverArtTask.isRunning()) {
							updateCoverArtTask.cancel();
						}
						updateCoverArtTask = getImageLoader().loadImage(coverArtView, coverArtRep, false, true);
						coverArtId = coverArtRep.getCoverArt();
					}
				}
			}
		}
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
				if(viewType == EntryGridAdapter.VIEW_TYPE_SONG || viewType == EntryGridAdapter.VIEW_TYPE_HEADER || viewType == EntryInfiniteGridAdapter.VIEW_TYPE_LOADING) {
					return gridLayoutManager.getSpanCount();
				} else {
					return 1;
				}
			}
		};
	}

    private void finishLoading() {
		boolean validData = !entries.isEmpty() || !albums.isEmpty();
		if(!validData) {
			setEmpty(true);
		}

		if(validData) {
			recyclerView.setVisibility(View.VISIBLE);
		}

		if(albumListType == null) {
			entryGridAdapter = new EntryGridAdapter(context, entries, getImageLoader(), largeAlbums);
			entryGridAdapter.setRemoveFromPlaylist(playlistId != null);
		} else {
			if("alphabeticalByName".equals(albumListType)) {
				entryGridAdapter = new AlphabeticalAlbumAdapter(context, entries, getImageLoader(), largeAlbums);
			} else {
				entryGridAdapter = new EntryInfiniteGridAdapter(context, entries, getImageLoader(), largeAlbums);
			}

			// Setup infinite loading based on scrolling
			final EntryInfiniteGridAdapter infiniteGridAdapter = (EntryInfiniteGridAdapter) entryGridAdapter;
			infiniteGridAdapter.setData(albumListType, albumListExtra, albumListSize);

			recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
					super.onScrollStateChanged(recyclerView, newState);
				}

				@Override
				public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
					super.onScrolled(recyclerView, dx, dy);

					RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
					int totalItemCount = layoutManager.getItemCount();
					int lastVisibleItem;
					if(layoutManager instanceof GridLayoutManager) {
						lastVisibleItem = ((GridLayoutManager) layoutManager).findLastVisibleItemPosition();
					} else if(layoutManager instanceof LinearLayoutManager) {
						lastVisibleItem = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
					} else {
						return;
					}

					if(totalItemCount > 0 && lastVisibleItem >= totalItemCount - 2) {
						infiniteGridAdapter.loadMore();
					}
				}
			});
		}
		entryGridAdapter.setOnItemClickedListener(this);
		// Always show artist if this is not a artist we are viewing
		if(!artist) {
			entryGridAdapter.setShowArtist(true);
		}
		if(topTracks) {
			entryGridAdapter.setShowAlbum(true);
		}

		int scrollToPosition = -1;
		if(lookupEntry != null) {
			for(int i = 0; i < entries.size(); i++) {
				if(lookupEntry.equals(entries.get(i).getTitle())) {
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

		if(scrollToPosition != -1) {
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
		if(!songs.isEmpty()) {
			download(songs, append, false, !append, playNext, shuffle);
			entryGridAdapter.clearSelected();
		} else {
			playAll(shuffle, append, playNext);
		}
	}
	private void playAll(final boolean shuffle, final boolean append, final boolean playNext) {
		boolean hasSubFolders = albums != null && !albums.isEmpty();

		if (hasSubFolders && id != null) {
			downloadRecursively(id, false, append, !append, shuffle, false, playNext);
		} else if(hasSubFolders && albumListType != null) {
			downloadRecursively(albums, shuffle, append, playNext);
		} else {
			download(entries, append, false, !append, playNext, shuffle);
		}
	}

	private List<Integer> getSelectedIndexes() {
		List<Entry> selected = entryGridAdapter.getSelected();
		List<Integer> indexes = new ArrayList<Integer>();

		for(Entry entry: selected) {
			indexes.add(entries.indexOf(entry));
		}

		return indexes;
	}

	@Override
	protected void downloadBackground(final boolean save) {
		List<Entry> songs = getSelectedEntries();
		if(playlistId != null) {
			songs = entries;
		}

		if(songs.isEmpty()) {
			// Get both songs and albums
			downloadRecursively(id, save, false, false, false, true);
		} else {
			downloadBackground(save, songs);
		}
	}
	@Override
	protected void downloadBackground(final boolean save, final List<Entry> entries) {
		if (getDownloadService() == null) {
			return;
		}

		warnIfStorageUnavailable();
		RecursiveLoader onValid = new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				getSongsRecursively(entries, true);
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
	protected void download(List<Entry> entries, boolean append, boolean save, boolean autoplay, boolean playNext, boolean shuffle) {
		download(entries, append, save, autoplay, playNext, shuffle, playlistName, playlistId);
	}

	@Override
	protected void delete() {
		List<Entry> songs = getSelectedEntries();
		if(songs.isEmpty()) {
			for(Entry entry: entries) {
				if(entry.isDirectory()) {
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

	public void removeFromPlaylist(final String id, final String name, final List<Integer> indexes) {
		new LoadingTask<Void>(context, true) {
			@Override
			protected Void doInBackground() throws Throwable {				
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				musicService.removeFromPlaylist(id, indexes, context, null);
				return null;
			}

			@Override
			protected void done(Void result) {
				for(Integer index: indexes) {
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
	
	private void showTopTracks() {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle(getArguments());
		args.putBoolean(Constants.INTENT_EXTRA_TOP_TRACKS, true);
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}

	private View createHeader() {
		View header = LayoutInflater.from(context).inflate(R.layout.select_album_header, null, false);

		setupCoverArt(header);
		setupTextDisplay(header);

		return header;
	}

	private void setupCoverArt(View header) {
		setupCoverArtImpl((RecyclingImageView) header.findViewById(R.id.select_album_art));
	}
	private void setupCoverArtImpl(RecyclingImageView coverArtView) {
		final ImageLoader imageLoader = getImageLoader();

		if(entries.size() > 0) {
			coverArtRep = null;
			this.coverArtView = coverArtView;
			for (int i = 0; (i < 3) && (coverArtRep == null || coverArtRep.getCoverArt() == null); i++) {
				coverArtRep = entries.get(random.nextInt(entries.size()));
			}

			coverArtView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (coverArtRep == null || coverArtRep.getCoverArt() == null) {
						return;
					}

					AlertDialog.Builder builder = new AlertDialog.Builder(context);
					ImageView fullScreenView = new ImageView(context);
					imageLoader.loadImage(fullScreenView, coverArtRep, true, true);
					builder.setCancelable(true);

					AlertDialog imageDialog = builder.create();
					// Set view here with unecessary 0's to remove top/bottom border
					imageDialog.setView(fullScreenView, 0, 0, 0, 0);
					imageDialog.show();
				}
			});
			synchronized (coverArtRep) {
				coverArtId = coverArtRep.getCoverArt();
				updateCoverArtTask = imageLoader.loadImage(coverArtView, coverArtRep, false, true);
			}
		}

		coverArtView.setOnInvalidated(new RecyclingImageView.OnInvalidated() {
			@Override
			public void onInvalidated(RecyclingImageView imageView) {
				setupCoverArtImpl(imageView);
			}
		});
	}
	private void setupTextDisplay(final View header) {
		final TextView titleView = (TextView) header.findViewById(R.id.select_album_title);
		if(playlistName != null) {
			titleView.setText(playlistName);
		} else if(name != null) {
			titleView.setText(name);
		}

		int songCount = 0;

		Set<String> artists = new HashSet<String>();
		Set<Integer> years = new HashSet<Integer>();
		Integer totalDuration = 0;
		for (Entry entry : entries) {
			if (!entry.isDirectory()) {
				songCount++;
				if (entry.getArtist() != null) {
					artists.add(entry.getArtist());
				}
				if(entry.getYear() != null) {
					years.add(entry.getYear());
				}
				Integer duration = entry.getDuration();
				if(duration != null) {
					totalDuration += duration;
				}
			}
		}

		final TextView artistView = (TextView) header.findViewById(R.id.select_album_artist);
		if (artists.size() == 1) {
			String artistText = artists.iterator().next();
			if(years.size() == 1) {
				artistText += " - " + years.iterator().next();
			}
			artistView.setText(artistText);
			artistView.setVisibility(View.VISIBLE);
		} else {
			artistView.setVisibility(View.GONE);
		}

		TextView songCountView = (TextView) header.findViewById(R.id.select_album_song_count);
		TextView songLengthView = (TextView) header.findViewById(R.id.select_album_song_length);
        String s = context.getResources().getQuantityString(R.plurals.select_album_n_songs, songCount, songCount);
        songCountView.setText(s.toUpperCase());
        songLengthView.setText(Util.formatDuration(totalDuration));
	}
}
