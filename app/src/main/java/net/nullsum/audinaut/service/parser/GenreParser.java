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

 Copyright 2010 (C) Sindre Mehus
 */
package net.nullsum.audinaut.service.parser;

import android.content.Context;
import android.text.Html;

import net.nullsum.audinaut.domain.Genre;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Joshua Bahnsen
 */
public class GenreParser extends AbstractParser {

    public GenreParser(Context context, int instance) {
        super(context, instance);
    }

    public List<Genre> parse(InputStream inputStream) throws Exception {
        init(inputStream);

        List<Genre> result = new ArrayList<>();

        Genre genre = null;

        int eventType;
        do {
            eventType = nextParseEvent();
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                if ("genre".equals(name)) {
                    genre = new Genre();
                    genre.setSongCount(getInteger("songCount"));
                    genre.setAlbumCount(getInteger("albumCount"));
                } else if ("error".equals(name)) {
                    handleError();
                } else {
                    genre = null;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (genre != null) {
                    String value = getText();
                    if (genre != null) {
                        genre.setName(Html.fromHtml(value).toString());
                        genre.setIndex(value.substring(0, 1));
                        result.add(genre);
                        genre = null;
                    }
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();

        Collections.sort(result, (genre1, genre2) -> genre1.getName().compareTo(genre2.getName()));
        return result;
    }
}
