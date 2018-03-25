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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.nullsum.audinaut.domain.MusicDirectory;

import java.util.ArrayList;
import java.util.List;

public class SongDBHandler extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "SongsDB";
    private static final String TABLE_SONGS = "RegisteredSongs";
    private static final String SONGS_ID = "id";
    private static final String SONGS_SERVER_KEY = "serverKey";
    private static final String SONGS_SERVER_ID = "serverId";
    private static final String SONGS_COMPLETE_PATH = "completePath";
    private static final String SONGS_LAST_PLAYED = "lastPlayed";
    private static final String SONGS_LAST_COMPLETED = "lastCompleted";
    private static final int DATABASE_VERSION = 2;
    private static SongDBHandler dbHandler;
    private final Context context;

    private SongDBHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    public static SongDBHandler getHandler(Context context) {
        if (dbHandler == null) {
            dbHandler = new SongDBHandler(context);
        }

        return dbHandler;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SONGS + " ( " +
                SONGS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                SONGS_SERVER_KEY + " INTEGER NOT NULL, " +
                SONGS_SERVER_ID + " TEXT NOT NULL, " +
                SONGS_COMPLETE_PATH + " TEXT NOT NULL, " +
                SONGS_LAST_PLAYED + " INTEGER, " +
                SONGS_LAST_COMPLETED + " INTEGER, " +
                "UNIQUE(" + SONGS_SERVER_KEY + ", " + SONGS_SERVER_ID + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
        this.onCreate(db);
    }

    public synchronized void addSongs(int instance, List<MusicDirectory.Entry> entries) {
        SQLiteDatabase db = this.getWritableDatabase();

        List<Pair<String, String>> pairs = new ArrayList<>();
        for (MusicDirectory.Entry entry : entries) {
            pairs.add(new Pair<>(entry.getId(), FileUtil.getSongFile(context, entry).getAbsolutePath()));
        }
        addSongs(db, instance, pairs);

        db.close();
    }

    private synchronized void addSongs(SQLiteDatabase db, int instance, List<Pair<String, String>> entries) {
        addSongsImpl(db, Util.getRestUrlHash(context, instance), entries);
    }

    private synchronized void addSongsImpl(SQLiteDatabase db, int serverKey, List<Pair<String, String>> entries) {
        db.beginTransaction();
        try {
            for (Pair<String, String> entry : entries) {
                ContentValues values = new ContentValues();
                values.put(SONGS_SERVER_KEY, serverKey);
                values.put(SONGS_SERVER_ID, entry.getFirst());
                values.put(SONGS_COMPLETE_PATH, entry.getSecond());
                // Util.sleepQuietly(10000);

                db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }

            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        }

        db.endTransaction();
    }

    public synchronized Long[] getLastPlayed(MusicDirectory.Entry entry) {
        return getLastPlayed(getOnlineSongId(entry));
    }

    private synchronized Long[] getLastPlayed(Pair<Integer, String> pair) {
        if (pair == null) {
            return null;
        } else {
            return getLastPlayed(pair.getFirst(), pair.getSecond());
        }
    }

    private synchronized Long[] getLastPlayed(int serverKey, String id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SONGS_LAST_PLAYED, SONGS_LAST_COMPLETED};
        Cursor cursor = db.query(TABLE_SONGS, columns, SONGS_SERVER_KEY + " = ? AND " + SONGS_SERVER_ID + " = ?", new String[]{Integer.toString(serverKey), id}, null, null, null, null);

        try {
            cursor.moveToFirst();

            Long[] dates = new Long[2];
            dates[0] = cursor.getLong(0);
            dates[1] = cursor.getLong(1);
            return dates;
        } catch (Exception e) {
            return null;
        } finally {
            cursor.close();
            db.close();
        }
    }

    private synchronized Pair<Integer, String> getOnlineSongId(MusicDirectory.Entry entry) {
        return getOnlineSongId(Util.getRestUrlHash(context), entry.getId(), FileUtil.getSongFile(context, entry).getAbsolutePath(), !Util.isOffline(context));
    }

    private synchronized Pair<Integer, String> getOnlineSongId(int serverKey, String id, String savePath, boolean requireServerKey) {
        SharedPreferences prefs = Util.getPreferences(context);
        String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
        if (cacheLocn != null && id.contains(cacheLocn)) {
            if (requireServerKey) {
                return getIdFromPath(serverKey, savePath);
            } else {
                return getIdFromPath(savePath);
            }
        } else {
            return new Pair<>(serverKey, id);
        }
    }

    private synchronized Pair<Integer, String> getIdFromPath(String path) {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SONGS_SERVER_KEY, SONGS_SERVER_ID};
        Cursor cursor = db.query(TABLE_SONGS, columns, SONGS_COMPLETE_PATH + " = ?", new String[]{path}, null, null, SONGS_LAST_PLAYED + " DESC", null);

        try {
            cursor.moveToFirst();
            return new Pair(cursor.getInt(0), cursor.getString(1));
        } catch (Exception e) {
            return null;
        } finally {
            cursor.close();
            db.close();
        }
    }

    public synchronized Pair<Integer, String> getIdFromPath(int serverKey, String path) {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SONGS_SERVER_KEY, SONGS_SERVER_ID};
        Cursor cursor = db.query(TABLE_SONGS, columns, SONGS_SERVER_KEY + " = ? AND " + SONGS_COMPLETE_PATH + " = ?", new String[]{Integer.toString(serverKey), path}, null, null, null, null);

        try {
            cursor.moveToFirst();
            return new Pair(cursor.getInt(0), cursor.getString(1));
        } catch (Exception e) {
            return null;
        } finally {
            cursor.close();
            db.close();
        }
    }
}
