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

package net.nullsum.audinaut.adapter;

import android.content.Context;

import net.nullsum.audinaut.view.BasicListView;
import net.nullsum.audinaut.view.UpdateView;

import java.util.List;

public class BasicListAdapter extends SectionAdapter<String> {
    private static final int VIEW_TYPE_LINE = 1;

    public BasicListAdapter(Context context, List<String> strings, OnItemClickedListener listener) {
        super(context, strings);
        this.onItemClickedListener = listener;
    }

    @Override
    public UpdateView.UpdateViewHolder onCreateSectionViewHolder(int viewType) {
        return new UpdateView.UpdateViewHolder(new BasicListView(context));
    }

    @Override
    public void onBindViewHolder(UpdateView.UpdateViewHolder holder, String item, int viewType) {
        holder.getUpdateView().setObject(item);
    }

    @Override
    public int getItemViewType(String item) {
        return VIEW_TYPE_LINE;
    }
}
