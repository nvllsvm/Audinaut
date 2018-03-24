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

package net.nullsum.audinaut.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.User;
import net.nullsum.audinaut.util.ImageLoader;
import net.nullsum.audinaut.util.UserUtil;
import net.nullsum.audinaut.view.BasicHeaderView;
import net.nullsum.audinaut.view.RecyclingImageView;
import net.nullsum.audinaut.view.SettingView;
import net.nullsum.audinaut.view.UpdateView;

import static net.nullsum.audinaut.domain.User.Setting;

public class SettingsAdapter extends SectionAdapter<Setting> {
    private static final String TAG = SettingsAdapter.class.getSimpleName();
    public final int VIEW_TYPE_SETTING = 1;
    public final int VIEW_TYPE_SETTING_HEADER = 2;

    private final User user;
    private final boolean editable;
    private final ImageLoader imageLoader;

    public SettingsAdapter(Context context, User user, List<String> headers, List<List<User.Setting>> settingSections, ImageLoader imageLoader, boolean editable, OnItemClickedListener<Setting> onItemClickedListener) {
        super(context, headers, settingSections, imageLoader != null);
        this.user = user;
        this.imageLoader = imageLoader;
        this.editable = editable;
        this.onItemClickedListener = onItemClickedListener;

        for(List<Setting> settings: sections) {
            for (Setting setting : settings) {
                if (setting.getValue()) {
                    addSelected(setting);
                }
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        int viewType = super.getItemViewType(position);
        if(viewType == SectionAdapter.VIEW_TYPE_HEADER) {
            if(position == 0 && imageLoader != null) {
                return VIEW_TYPE_HEADER;
            } else {
                return VIEW_TYPE_SETTING_HEADER;
            }
        } else {
            return viewType;
        }
    }

    public void onBindHeaderHolder(UpdateView.UpdateViewHolder holder, String description, int sectionIndex) {
        View header = holder.getView();
    }

    @Override
    public UpdateView.UpdateViewHolder onCreateSectionViewHolder(ViewGroup parent, int viewType) {
        if(viewType == VIEW_TYPE_SETTING_HEADER) {
            return new UpdateView.UpdateViewHolder(new BasicHeaderView(context));
        } else {
            return new UpdateView.UpdateViewHolder(new SettingView(context));
        }
    }

    @Override
    public void onBindViewHolder(UpdateView.UpdateViewHolder holder, Setting item, int viewType) {
        holder.getUpdateView().setObject(item, editable);
    }

    @Override
    public int getItemViewType(Setting item) {
        return VIEW_TYPE_SETTING;
    }

    @Override
    public void setChecked(UpdateView updateView, boolean checked) {
        if(updateView instanceof SettingView) {
            updateView.setChecked(checked);
        }
    }

    public static SettingsAdapter getSettingsAdapter(Context context, User user, ImageLoader imageLoader, OnItemClickedListener<Setting> onItemClickedListener) {
        return getSettingsAdapter(context, user, imageLoader, true, onItemClickedListener);
    }
    public static SettingsAdapter getSettingsAdapter(Context context, User user, ImageLoader imageLoader, boolean isEditable, OnItemClickedListener<Setting> onItemClickedListener) {
        List<String> headers = new ArrayList<>();
        List<List<User.Setting>> settingsSections = new ArrayList<>();
        settingsSections.add(user.getSettings());

        if(user.getMusicFolderSettings() != null) {
            settingsSections.add(user.getMusicFolderSettings());
        }

        return new SettingsAdapter(context, user, headers, settingsSections, imageLoader, isEditable, onItemClickedListener);
    }
}
