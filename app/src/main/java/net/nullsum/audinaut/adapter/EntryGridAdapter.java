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

package net.nullsum.audinaut.adapter;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.MusicDirectory.Entry;
import net.nullsum.audinaut.util.ImageLoader;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.AlbumView;
import net.nullsum.audinaut.view.SongView;
import net.nullsum.audinaut.view.UpdateView;
import net.nullsum.audinaut.view.UpdateView.UpdateViewHolder;

import java.util.List;

public class EntryGridAdapter extends SectionAdapter<Entry> {
    public static final int VIEW_TYPE_ALBUM_CELL = 1;
    public static final int VIEW_TYPE_ALBUM_LINE = 2;
    public static final int VIEW_TYPE_SONG = 3;
    private final ImageLoader imageLoader;
    private final boolean largeAlbums;
    private boolean showArtist = false;
    private boolean showAlbum = false;
    private boolean removeFromPlaylist = false;

    public EntryGridAdapter(Context context, List<Entry> entries, ImageLoader imageLoader, boolean largeCell) {
        super(context, entries);
        this.imageLoader = imageLoader;
        this.largeAlbums = largeCell;

        // Always show artist if they aren't all the same
        String artist = null;
        for (MusicDirectory.Entry entry : entries) {
            if (artist == null) {
                artist = entry.getArtist();
            }

            if (artist != null && !artist.equals(entry.getArtist())) {
                showArtist = true;
            }
        }
        checkable = true;
    }

    @Override
    public UpdateViewHolder onCreateSectionViewHolder(int viewType) {
        UpdateView updateView = null;
        if (viewType == VIEW_TYPE_ALBUM_LINE || viewType == VIEW_TYPE_ALBUM_CELL) {
            updateView = new AlbumView(context, viewType == VIEW_TYPE_ALBUM_CELL);
        } else if (viewType == VIEW_TYPE_SONG) {
            updateView = new SongView(context);
        }

        return new UpdateViewHolder(updateView);
    }

    @Override
    public void onBindViewHolder(UpdateViewHolder holder, Entry entry, int viewType) {
        UpdateView view = holder.getUpdateView();
        if (viewType == VIEW_TYPE_ALBUM_CELL || viewType == VIEW_TYPE_ALBUM_LINE) {
            AlbumView albumView = (AlbumView) view;
            albumView.setShowArtist(showArtist);
            albumView.setObject(entry, imageLoader);
        } else if (viewType == VIEW_TYPE_SONG) {
            SongView songView = (SongView) view;
            songView.setShowAlbum(showAlbum);
            songView.setObject(entry, checkable);
        }
    }

    public void onBindHeaderHolder(UpdateViewHolder holder, String header, int sectionIndex) {

    }

    @Override
    public int getItemViewType(Entry entry) {
        if (entry.isDirectory()) {
            if (largeAlbums) {
                return VIEW_TYPE_ALBUM_CELL;
            } else {
                return VIEW_TYPE_ALBUM_LINE;
            }
        } else {
            return VIEW_TYPE_SONG;
        }
    }

    public void setShowArtist() {
        this.showArtist = true;
    }

    public void setShowAlbum() {
        this.showAlbum = true;
    }

    public void removeAt(int index) {
        sections.get(0).remove(index);
        notifyItemRemoved(index);
    }

    public void setRemoveFromPlaylist(boolean removeFromPlaylist) {
        this.removeFromPlaylist = removeFromPlaylist;
    }

    @Override
    public void onCreateActionModeMenu(Menu menu, MenuInflater menuInflater) {
        if (Util.isOffline(context)) {
            menuInflater.inflate(R.menu.multiselect_media_offline, menu);
        } else {
            menuInflater.inflate(R.menu.multiselect_media, menu);
        }

        if (!removeFromPlaylist) {
            menu.removeItem(R.id.menu_remove_playlist);
        }
    }
}
