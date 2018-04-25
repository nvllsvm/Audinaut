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

package net.nullsum.audinaut.util;

import android.content.Context;
import android.util.Log;
import android.view.Menu;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.view.AlbumView;
import net.nullsum.audinaut.view.ArtistEntryView;
import net.nullsum.audinaut.view.ArtistView;
import net.nullsum.audinaut.view.SongView;
import net.nullsum.audinaut.view.UpdateView;

import java.io.File;

public final class MenuUtil {
    private final static String TAG = MenuUtil.class.getSimpleName();

    public static void hideMenuItems(Context context, Menu menu, UpdateView updateView) {
        if (!Util.isOffline(context)) {
            // If we are looking at a standard song view, get downloadFile to cache what options to show
            if (updateView instanceof SongView) {
                SongView songView = (SongView) updateView;
                DownloadFile downloadFile = songView.getDownloadFile();

                try {
                    if (downloadFile != null) {
                        if (downloadFile.isWorkDone()) {
                            // Remove permanent cache menu if already perma cached
                            if (downloadFile.isSaved()) {
                                menu.setGroupVisible(R.id.hide_pin, false);
                            }

                            // Remove cache option no matter what if already downloaded
                            menu.setGroupVisible(R.id.hide_download, false);
                        } else {
                            // Remove delete option if nothing to delete
                            menu.setGroupVisible(R.id.hide_delete, false);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to lookup downloadFile info", e);
                }
            }
            // Apply similar logic to album views
            else if (updateView instanceof AlbumView || updateView instanceof ArtistView || updateView instanceof ArtistEntryView) {
                File folder = null;
                if (updateView instanceof AlbumView) {
                    folder = ((AlbumView) updateView).getFile();
                } else if (updateView instanceof ArtistView) {
                    folder = ((ArtistView) updateView).getFile();
                } else {
                    folder = ((ArtistEntryView) updateView).getFile();
                }

                try {
                    if (folder != null && !folder.exists()) {
                        menu.setGroupVisible(R.id.hide_delete, false);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to lookup album directory info", e);
                }
            }
        }
    }
}
