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

import net.nullsum.audinaut.domain.MusicDirectory.Entry;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.view.UpdateView;

import java.util.List;

public final class UpdateHelper {

    public static abstract class EntryInstanceUpdater {
        private final Entry entry;
        protected int metadataUpdate = DownloadService.METADATA_UPDATED_ALL;

        public EntryInstanceUpdater(Entry entry) {
            this.entry = entry;
        }

        public abstract void update(Entry found);

        public void execute() {
            DownloadService downloadService = DownloadService.getInstance();
            if (downloadService != null && !entry.isDirectory()) {
                boolean serializeChanges = false;
                List<DownloadFile> downloadFiles = downloadService.getDownloads();
                DownloadFile currentPlaying = downloadService.getCurrentPlaying();

                for (DownloadFile file : downloadFiles) {
                    Entry check = file.getSong();
                    if (entry.getId().equals(check.getId())) {
                        update(check);
                        serializeChanges = true;

                        if (currentPlaying != null && currentPlaying.getSong() != null && currentPlaying.getSong().getId().equals(entry.getId())) {
                            downloadService.onMetadataUpdate(metadataUpdate);
                        }
                    }
                }

                if (serializeChanges) {
                    downloadService.serializeQueue();
                }
            }

            Entry find = UpdateView.findEntry(entry);
            if (find != null) {
                update(find);
            }
        }
    }
}
