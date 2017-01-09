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

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.domain.User;
import net.nullsum.audinaut.fragments.SubsonicFragment;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.service.MusicServiceFactory;
import net.nullsum.audinaut.service.OfflineException;
import net.nullsum.audinaut.adapter.SettingsAdapter;
import net.nullsum.audinaut.view.UpdateView;

public final class UserUtil {
	private static final String TAG = UserUtil.class.getSimpleName();
	private static final long MIN_VERIFY_DURATION = 1000L * 60L * 60L;
	
	private static int instance = -1;
	private static int instanceHash = -1;
	private static User currentUser;
	private static long lastVerifiedTime = 0;

	public static void refreshCurrentUser(Context context, boolean forceRefresh) {
		refreshCurrentUser(context, forceRefresh, false);
	}
	public static void refreshCurrentUser(Context context, boolean forceRefresh, boolean unAuth) {
		currentUser = null;
		if(unAuth) {
			lastVerifiedTime = 0;
		}
		seedCurrentUser(context, forceRefresh);
	}

	public static void seedCurrentUser(Context context) {
		seedCurrentUser(context, false);
	}
	public static void seedCurrentUser(final Context context, final boolean refresh) {
		// Only try to seed if online
		if(Util.isOffline(context)) {
			currentUser = null;
			return;
		}
		
		final int instance = Util.getActiveServer(context);
		final int instanceHash = (instance == 0) ? 0 : Util.getRestUrl(context, null).hashCode();
		if(UserUtil.instance == instance && UserUtil.instanceHash == instanceHash && currentUser != null) {
			return;
		} else {
			UserUtil.instance = instance;
			UserUtil.instanceHash = instanceHash;
		}

		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				currentUser = MusicServiceFactory.getMusicService(context).getUser(refresh, getCurrentUsername(context, instance), context, null);

				// If running, redo cast selector
				DownloadService downloadService = DownloadService.getInstance();

				return null;
			}

			@Override
			protected void done(Void result) {
				if(context instanceof AppCompatActivity) {
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

	public static User getCurrentUser() {
		return currentUser;
	}
    	public static String getCurrentUsername(Context context, int instance) {
		SharedPreferences prefs = Util.getPreferences(context);
		return prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
	}

	public static String getCurrentUsername(Context context) {
		return getCurrentUsername(context, Util.getActiveServer(context));
	}

}
