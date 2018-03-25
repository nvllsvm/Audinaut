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
package net.nullsum.audinaut.service.parser;

import android.content.Context;

import net.nullsum.audinaut.domain.MusicDirectory;

/**
 * @author Sindre Mehus
 */
class MusicDirectoryEntryParser extends AbstractParser {
    MusicDirectoryEntryParser(Context context, int instance) {
        super(context, instance);
    }

    MusicDirectory.Entry parseEntry(String artist) {
        MusicDirectory.Entry entry = new MusicDirectory.Entry();
        entry.setId(get("id"));
        entry.setParent(get("parent"));
        entry.setArtistId(get("artistId"));
        entry.setTitle(get("title"));
        if (entry.getTitle() == null) {
            entry.setTitle(get("name"));
        }
        entry.setDirectory(getBoolean());
        entry.setCoverArt(get("coverArt"));
        entry.setArtist(get("artist"));
        entry.setYear(getInteger("year"));
        entry.setGenre(get("genre"));
        entry.setAlbum(get("album"));

        if (!entry.isDirectory()) {
            entry.setAlbumId(get("albumId"));
            entry.setTrack(getInteger("track"));
            entry.setContentType(get("contentType"));
            entry.setSuffix(get("suffix"));
            entry.setTranscodedContentType(get("transcodedContentType"));
            entry.setTranscodedSuffix(get("transcodedSuffix"));
            entry.setDuration(getInteger("duration"));
            entry.setBitRate(getInteger("bitRate"));
            entry.setPath(get("path"));
            entry.setDiscNumber(getInteger("discNumber"));
        } else if (!"".equals(artist)) {
            entry.setPath(artist + "/" + entry.getTitle());
        }
        return entry;
    }

}
