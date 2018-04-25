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

package net.nullsum.audinaut.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.TextView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.User.MusicFolderSetting;

import static net.nullsum.audinaut.domain.User.Setting;

public class SettingView extends UpdateView2<Setting, Boolean> {
    private final TextView titleView;
    private final CheckBox checkBox;

    public SettingView(Context context) {
        super(context, false);
        this.context = context;
        LayoutInflater.from(context).inflate(R.layout.basic_choice_item, this, true);

        titleView = findViewById(R.id.item_name);
        checkBox = findViewById(R.id.item_checkbox);
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (item != null) {
                item.setValue(isChecked);
            }
        });
        checkBox.setClickable(false);
    }

    protected void setObjectImpl(Setting setting, Boolean isEditable) {
        // Can't edit non-role parts
        String name = setting.getName();
        if (!name.contains("Role") && !(setting instanceof MusicFolderSetting)) {
            item2 = false;
        }

        if (setting instanceof MusicFolderSetting) {
            titleView.setText(((MusicFolderSetting) setting).getLabel());
        } else {
            // Last resort to display the raw value
            titleView.setText(name);
        }

        if (setting.getValue()) {
            checkBox.setChecked(setting.getValue());
        } else {
            checkBox.setChecked(false);
        }

        checkBox.setEnabled(item2);
    }

    @Override
    public boolean isCheckable() {
        return item2;
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked);
    }
}
