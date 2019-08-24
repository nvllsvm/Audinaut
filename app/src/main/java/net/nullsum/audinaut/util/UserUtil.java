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
    Copyright 2014 (C) Scott Jackson
*/

package net.nullsum.audinaut.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import net.nullsum.audinaut.domain.User;
import net.nullsum.audinaut.service.MusicServiceFactory;

public final class UserUtil {
    private static final String TAG = UserUtil.class.getSimpleName();

    private static int instance = -1;
    private static int instanceHash = -1;
    private static User currentUser;

    public static void refreshCurrentUser(Context context) {
        currentUser = null;
        seedCurrentUser(context);
    }

    public static void seedCurrentUser(Context context) {
        // Only try to seed if online
        if (Util.isOffline(context)) {
            currentUser = null;
            return;
        }

        final int instance = Util.getActiveServer(context);
        final int instanceHash = (instance == 0) ? 0 : Util.getRestUrl(context).hashCode();
        if (UserUtil.instance == instance && UserUtil.instanceHash == instanceHash && currentUser != null) {
            return;
        } else {
            UserUtil.instance = instance;
            UserUtil.instanceHash = instanceHash;
        }

        new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() throws Throwable {
                currentUser = MusicServiceFactory.getMusicService(context).getUser(false, getCurrentUsername(context, instance), context, null);
                return null;
            }

            @Override
            protected void done(Void result) {
                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).supportInvalidateOptionsMenu();
                }
            }

            @Override
            protected void error(Throwable error) {
                // Don't do anything, supposed to be background pull
                Log.e(TAG, "Failed to seed user information");
            }
        }.execute();
    }

    private static String getCurrentUsername(Context context, int instance) {
        SharedPreferences prefs = Util.getPreferences(context);
        return prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
    }

    public static String getCurrentUsername(Context context) {
        return getCurrentUsername(context, Util.getActiveServer(context));
    }

}
