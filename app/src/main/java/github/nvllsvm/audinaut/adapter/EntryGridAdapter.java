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
	Copyright 2015 (C) Scott Jackson
*/

package github.nvllsvm.audinaut.adapter;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import github.nvllsvm.audinaut.R;
import github.nvllsvm.audinaut.domain.MusicDirectory;
import github.nvllsvm.audinaut.domain.MusicDirectory.Entry;
import github.nvllsvm.audinaut.util.ImageLoader;
import github.nvllsvm.audinaut.util.Util;
import github.nvllsvm.audinaut.view.AlbumView;
import github.nvllsvm.audinaut.view.SongView;
import github.nvllsvm.audinaut.view.UpdateView;
import github.nvllsvm.audinaut.view.UpdateView.UpdateViewHolder;

public class EntryGridAdapter extends SectionAdapter<Entry> {
	private static String TAG = EntryGridAdapter.class.getSimpleName();

	public static int VIEW_TYPE_ALBUM_CELL = 1;
	public static int VIEW_TYPE_ALBUM_LINE = 2;
	public static int VIEW_TYPE_SONG = 3;

	private ImageLoader imageLoader;
	private boolean largeAlbums;
	private boolean showArtist = false;
	private boolean showAlbum = false;
	private boolean removeFromPlaylist = false;
	private View header;

	public EntryGridAdapter(Context context, List<Entry> entries, ImageLoader imageLoader, boolean largeCell) {
		super(context, entries);
		this.imageLoader = imageLoader;
		this.largeAlbums = largeCell;

		// Always show artist if they aren't all the same
		String artist = null;
		for(MusicDirectory.Entry entry: entries) {
			if(artist == null) {
				artist = entry.getArtist();
			}

			if(artist != null && !artist.equals(entry.getArtist())) {
				showArtist = true;
			}
		}
		checkable = true;
	}

	@Override
	public UpdateViewHolder onCreateSectionViewHolder(ViewGroup parent, int viewType) {
		UpdateView updateView = null;
		if(viewType == VIEW_TYPE_ALBUM_LINE || viewType == VIEW_TYPE_ALBUM_CELL) {
			updateView = new AlbumView(context, viewType == VIEW_TYPE_ALBUM_CELL);
		} else if(viewType == VIEW_TYPE_SONG) {
			updateView = new SongView(context);
		}

		return new UpdateViewHolder(updateView);
	}

	@Override
	public void onBindViewHolder(UpdateViewHolder holder, Entry entry, int viewType) {
		UpdateView view = holder.getUpdateView();
		if(viewType == VIEW_TYPE_ALBUM_CELL || viewType == VIEW_TYPE_ALBUM_LINE) {
			AlbumView albumView = (AlbumView) view;
			albumView.setShowArtist(showArtist);
			albumView.setObject(entry, imageLoader);
		} else if(viewType == VIEW_TYPE_SONG) {
			SongView songView = (SongView) view;
			songView.setShowAlbum(showAlbum);
			songView.setObject(entry, checkable);
		}
	}

	public UpdateViewHolder onCreateHeaderHolder(ViewGroup parent) {
		return new UpdateViewHolder(header, false);
	}
	public void onBindHeaderHolder(UpdateViewHolder holder, String header, int sectionIndex) {

	}

	@Override
	public int getItemViewType(Entry entry) {
		if(entry.isDirectory()) {
			if (largeAlbums) {
				return VIEW_TYPE_ALBUM_CELL;
			} else {
				return VIEW_TYPE_ALBUM_LINE;
			}
		} else {
			return VIEW_TYPE_SONG;
		}
	}

	public void setHeader(View header) {
		this.header = header;
		this.singleSectionHeader = true;
	}
	public View getHeader() {
		return header;
	}

	public void setShowArtist(boolean showArtist) {
		this.showArtist = showArtist;
	}

	public void setShowAlbum(boolean showAlbum) {
		this.showAlbum = showAlbum;
	}

	public void removeAt(int index) {
		sections.get(0).remove(index);
		if(header != null) {
			index++;
		}
		notifyItemRemoved(index);
	}

	public void setRemoveFromPlaylist(boolean removeFromPlaylist) {
		this.removeFromPlaylist = removeFromPlaylist;
	}

	@Override
	public void onCreateActionModeMenu(Menu menu, MenuInflater menuInflater) {
		if(Util.isOffline(context)) {
			menuInflater.inflate(R.menu.multiselect_media_offline, menu);
		} else {
			menuInflater.inflate(R.menu.multiselect_media, menu);
		}

		if(!removeFromPlaylist) {
			menu.removeItem(R.id.menu_remove_playlist);
		}
	}
}
