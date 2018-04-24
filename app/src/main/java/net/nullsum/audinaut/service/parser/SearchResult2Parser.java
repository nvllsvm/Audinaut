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

import net.nullsum.audinaut.domain.Artist;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.SearchResult;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class SearchResult2Parser extends MusicDirectoryEntryParser {

    public SearchResult2Parser(Context context, int instance) {
        super(context, instance);
    }

    public SearchResult parse(InputStream inputStream) throws Exception {
        init(inputStream);

        List<Artist> artists = new ArrayList<>();
        List<MusicDirectory.Entry> albums = new ArrayList<>();
        List<MusicDirectory.Entry> songs = new ArrayList<>();
        int eventType;
        do {
            eventType = nextParseEvent();
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                switch (name) {
                    case "artist":
                        Artist artist = new Artist();
                        artist.setId(get("id"));
                        artist.setName(get("name"));
                        artists.add(artist);
                        break;
                    case "album":
                        MusicDirectory.Entry entry = parseEntry("");
                        entry.setDirectory(true);
                        albums.add(entry);
                        break;
                    case "song":
                        songs.add(parseEntry(""));
                        break;
                    case "error":
                        handleError();
                        break;
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();

        return new SearchResult(artists, albums, songs);
    }

}
