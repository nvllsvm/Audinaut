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
package net.nullsum.audinaut.view;

import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.activity.SubsonicFragmentActivity;
import net.nullsum.audinaut.util.Util;

/**
 * @author Sindre Mehus
 */
public class ErrorDialog {

    public ErrorDialog(AppCompatActivity activity, int messageId) {
        this(activity, activity.getResources().getString(messageId), false);
    }

    public ErrorDialog(final AppCompatActivity activity, String message, final boolean finishActivityOnClose) {

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.error_label);
        builder.setMessage(message);
        builder.setCancelable(true);
        builder.setOnCancelListener(dialogInterface -> {
            if (finishActivityOnClose) {
                restart(activity);
            }
        });
        builder.setPositiveButton(R.string.common_ok, (dialogInterface, i) -> {
            if (finishActivityOnClose) {
                restart(activity);
            }
        });

        try {
            builder.create().show();
        } catch (Exception e) {
            // Don't care, just means no activity to attach to
        }
    }

    private void restart(AppCompatActivity activity) {
        Intent intent = new Intent(activity, SubsonicFragmentActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Util.startActivityWithoutTransition(activity, intent);
    }
}
