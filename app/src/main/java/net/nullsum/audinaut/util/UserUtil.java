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

public final class UserUtil {
    private static String getCurrentUsername(Context context, int instance) {
        SharedPreferences prefs = Util.getPreferences(context);
        return prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
    }

    public static String getCurrentUsername(Context context) {
        return getCurrentUsername(context, Util.getActiveServer(context));
    }

}
