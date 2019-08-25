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
package net.nullsum.audinaut.provider;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.Artist;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.SearchCritera;
import net.nullsum.audinaut.domain.SearchResult;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.service.MusicServiceFactory;
import net.nullsum.audinaut.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides search suggestions based on recent searches.
 *
 * @author Sindre Mehus
 */
public class AudinautSearchProvider extends ContentProvider {

    private static final String RESOURCE_PREFIX = "android.resource://net.nullsum.audinaut/";
    private static final String[] COLUMNS = {"_id",
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
            SearchManager.SUGGEST_COLUMN_ICON_1};

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (selectionArgs[0].isEmpty()) {
            return null;
        }

        String query = selectionArgs[0] + "*";
        SearchResult searchResult = search(query);
        return createCursor(selectionArgs[0], searchResult);
    }

    private SearchResult search(String query) {
        MusicService musicService = MusicServiceFactory.getMusicService(getContext());

        try {
            return musicService.search(new SearchCritera(query, 5, 10, 10), getContext(), null);
        } catch (Exception e) {
            return null;
        }
    }

    private Cursor createCursor(String query, SearchResult searchResult) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        if (searchResult == null) {
            return cursor;
        }

        // Add all results into one pot
        List<Object> results = new ArrayList<>();
        results.addAll(searchResult.getArtists());
        results.addAll(searchResult.getAlbums());
        results.addAll(searchResult.getSongs());

        // For each, calculate its string distance to the query
        for (Object obj : results) {
            if (obj instanceof Artist) {
                Artist artist = (Artist) obj;
                artist.setCloseness(Util.getStringDistance(query, artist.getName()));
            } else {
                MusicDirectory.Entry entry = (MusicDirectory.Entry) obj;
                entry.setCloseness(Util.getStringDistance(query, entry.getTitle()));
            }
        }

        // Sort based on the closeness paramater
        Collections.sort(results, (lhs, rhs) -> {
            // Get the closeness of the two objects
            int left, right;
            boolean leftArtist = lhs instanceof Artist;
            boolean rightArtist = rhs instanceof Artist;
            if (leftArtist) {
                left = ((Artist) lhs).getCloseness();
            } else {
                left = ((MusicDirectory.Entry) lhs).getCloseness();
            }
            if (rightArtist) {
                right = ((Artist) rhs).getCloseness();
            } else {
                right = ((MusicDirectory.Entry) rhs).getCloseness();
            }

            if (left == right) {
                if (leftArtist && rightArtist) {
                    return 0;
                } else if (leftArtist) {
                    return -1;
                } else if (rightArtist) {
                    return 1;
                } else {
                    return 0;
                }
            } else if (left > right) {
                return 1;
            } else {
                return -1;
            }
        });

        // Done sorting, add results to cursor
        for (Object obj : results) {
            if (obj instanceof Artist) {
                Artist artist = (Artist) obj;
                String icon = RESOURCE_PREFIX + R.drawable.ic_action_artist;
                cursor.addRow(new Object[]{artist.getId().hashCode(), artist.getName(), null, "ar-" + artist.getId(), artist.getName(), icon});
            } else {
                MusicDirectory.Entry entry = (MusicDirectory.Entry) obj;
            }
        }
        return cursor;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }

}
