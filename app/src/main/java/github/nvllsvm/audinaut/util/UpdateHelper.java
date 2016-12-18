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

package github.nvllsvm.audinaut.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import github.nvllsvm.audinaut.R;
import github.nvllsvm.audinaut.domain.Artist;
import github.nvllsvm.audinaut.domain.MusicDirectory;
import github.nvllsvm.audinaut.domain.MusicDirectory.Entry;
import github.nvllsvm.audinaut.fragments.SubsonicFragment;
import github.nvllsvm.audinaut.service.DownloadFile;
import github.nvllsvm.audinaut.service.DownloadService;
import github.nvllsvm.audinaut.service.MusicService;
import github.nvllsvm.audinaut.service.MusicServiceFactory;
import github.nvllsvm.audinaut.service.OfflineException;
import github.nvllsvm.audinaut.view.UpdateView;

public final class UpdateHelper {
	private static final String TAG = UpdateHelper.class.getSimpleName();

	public static abstract class EntryInstanceUpdater {
		private Entry entry;
		protected int metadataUpdate = DownloadService.METADATA_UPDATED_ALL;

		public EntryInstanceUpdater(Entry entry) {
			this.entry = entry;
		}
		public EntryInstanceUpdater(Entry entry, int metadataUpdate) {
			this.entry = entry;
			this.metadataUpdate = metadataUpdate;
		}

		public abstract void update(Entry found);

		public void execute() {
			DownloadService downloadService = DownloadService.getInstance();
			if(downloadService != null && !entry.isDirectory()) {
				boolean serializeChanges = false;
				List<DownloadFile> downloadFiles = downloadService.getDownloads();
				DownloadFile currentPlaying = downloadService.getCurrentPlaying();

				for(DownloadFile file: downloadFiles) {
					Entry check = file.getSong();
					if(entry.getId().equals(check.getId())) {
						update(check);
						serializeChanges = true;

						if(currentPlaying != null && currentPlaying.getSong() != null && currentPlaying.getSong().getId().equals(entry.getId())) {
							downloadService.onMetadataUpdate(metadataUpdate);
						}
					}
				}

				if(serializeChanges) {
					downloadService.serializeQueue();
				}
			}

			Entry find = UpdateView.findEntry(entry);
			if(find != null) {
				update(find);
			}
		}
	}
}
