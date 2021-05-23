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
    Copyright 2016 (C) Scott Jackson
*/

package net.nullsum.audinaut.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import net.nullsum.audinaut.R;

public final class ThemeUtil {
    private static final String THEME_DARK = "dark";
    private static final String THEME_BLACK = "black";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DAY_NIGHT = "day/night";
    private static final String THEME_DAY_BLACK_NIGHT = "day/black";

    public static String getTheme(Context context) {
        SharedPreferences prefs = Util.getPreferences(context);
        String theme;

        if (Build.VERSION.SDK_INT<29) {
            // If Android Pie or older, default to null (handled below as light)
            theme = prefs.getString(Constants.PREFERENCES_KEY_THEME, null);
        } else {
            // Else, for Android 10+, default to follow system dark mode setting
            theme = prefs.getString(Constants.PREFERENCES_KEY_THEME, THEME_DAY_NIGHT);
        }

        if (THEME_DAY_NIGHT.equals(theme)) {
            int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                theme = THEME_DARK;
            } else {
                theme = THEME_LIGHT;
            }
        } else if (THEME_DAY_BLACK_NIGHT.equals(theme)) {
            int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                theme = THEME_BLACK;
            } else {
                theme = THEME_LIGHT;
            }
        }

        return theme;
    }

    public static int getThemeRes(Context context) {
        return getThemeRes(context, getTheme(context));
    }

    private static int getThemeRes(Context context, String theme) {
        if(theme == null)
            return  R.style.Theme_Audinaut_Light;

        switch (theme) {
            case THEME_DARK:
                return R.style.Theme_Audinaut_Dark;
            case THEME_BLACK:
                return R.style.Theme_Audinaut_Black;
            default:
                return R.style.Theme_Audinaut_Light;
        }
    }

    public static void setTheme(Context context, String theme) {
        SharedPreferences.Editor editor = Util.getPreferences(context).edit();
        editor.putString(Constants.PREFERENCES_KEY_THEME, theme);
        editor.apply();
    }

    public static void applyTheme(Context context, String theme) {
        context.setTheme(getThemeRes(context, theme));
    }
}
