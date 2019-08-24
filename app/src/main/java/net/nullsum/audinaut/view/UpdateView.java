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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.util.DrawableTint;
import net.nullsum.audinaut.util.SilentBackgroundTask;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public abstract class UpdateView<T> extends LinearLayout {
    private static final String TAG = UpdateView.class.getSimpleName();
    private static final WeakHashMap<UpdateView, ?> INSTANCES = new WeakHashMap<>();

    static Handler backgroundHandler;
    static Handler uiHandler;
    private static Runnable updateRunnable;
    private static int activeActivities = 0;
    Context context;
    T item;
    ImageView moreButton;
    View coverArtView;
    boolean exists = false;
    boolean pinned = false;
    SilentBackgroundTask<Void> imageTask = null;
    boolean checkable;
    private boolean shaded = false;
    private Drawable startBackgroundDrawable;

    UpdateView(Context context, boolean autoUpdate) {
        super(context);
        this.context = context;

        setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (autoUpdate) {
            INSTANCES.put(this, null);
        }
        startUpdater();
    }

    private static synchronized void startUpdater() {
        if (uiHandler != null) {
            return;
        }

        uiHandler = new Handler();
        // Needed so handler is never null until thread creates it
        backgroundHandler = uiHandler;
        updateRunnable = UpdateView::updateAll;

        new Thread(() -> {
            Looper.prepare();
            backgroundHandler = new Handler(Looper.myLooper());
            uiHandler.post(updateRunnable);
            Looper.loop();
        }, "UpdateView").start();
    }

    public static synchronized void triggerUpdate() {
        if (backgroundHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
            backgroundHandler.removeCallbacksAndMessages(null);
            uiHandler.post(updateRunnable);
        }
    }

    private static void updateAll() {
        try {
            // If nothing can see this, stop updating
            if (activeActivities == 0) {
                activeActivities--;
                return;
            }

            List<UpdateView> views = new ArrayList<>();
            for (UpdateView view : INSTANCES.keySet()) {
                if (view.isShown()) {
                    views.add(view);
                }
            }
            if (views.size() > 0) {
                updateAllLive(views);
            } else {
                uiHandler.postDelayed(updateRunnable, 2000L);
            }
        } catch (Throwable x) {
            Log.w(TAG, "Error when updating song views.", x);
        }
    }

    private static void updateAllLive(final List<UpdateView> views) {
        final Runnable runnable = () -> {
            try {
                for (UpdateView view : views) {
                    view.update();
                }
            } catch (Throwable x) {
                Log.w(TAG, "Error when updating song views.", x);
            }
            uiHandler.postDelayed(updateRunnable, 1000L);
        };

        backgroundHandler.post(() -> {
            try {
                for (UpdateView view : views) {
                    view.updateBackground();
                }
                uiHandler.post(runnable);
            } catch (Throwable x) {
                Log.w(TAG, "Error when updating song views.", x);
            }
        });
    }

    public static void addActiveActivity() {
        activeActivities++;

        if (activeActivities == 0 && uiHandler != null && updateRunnable != null) {
            activeActivities++;
            uiHandler.post(updateRunnable);
        }
    }

    public static void removeActiveActivity() {
        activeActivities--;
    }

    public static MusicDirectory.Entry findEntry(MusicDirectory.Entry entry) {
        for (UpdateView view : INSTANCES.keySet()) {
            MusicDirectory.Entry check = null;
            if (view instanceof SongView) {
                check = ((SongView) view).getEntry();
            } else if (view instanceof AlbumView) {
                check = ((AlbumView) view).getEntry();
            }

            if (check != null && entry != check && check.getId().equals(entry.getId())) {
                return check;
            }
        }

        return null;
    }

    @Override
    public void setPressed(boolean pressed) {

    }

    public void setObject(T obj) {
        if (item == obj) {
            return;
        }

        item = obj;
        if (imageTask != null) {
            imageTask.cancel();
            imageTask = null;
        }
        if (coverArtView != null && coverArtView instanceof ImageView) {
            ((ImageView) coverArtView).setImageDrawable(null);
        }
        setObjectImpl(obj);
        updateBackground();
        update();
    }

    protected abstract void setObjectImpl(T obj);

    void updateBackground() {

    }

    void update() {
        if (moreButton != null) {
            if (exists || pinned) {
                if (!shaded) {
                    moreButton.setImageResource(exists ? R.drawable.download_cached : R.drawable.download_pinned);
                    shaded = true;
                }
            } else {
                if (shaded) {
                    moreButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.download_none));
                    shaded = false;
                }
            }
        }

        if (coverArtView != null && coverArtView instanceof RecyclingImageView) {
            RecyclingImageView recyclingImageView = (RecyclingImageView) coverArtView;
            if (recyclingImageView.isInvalidated()) {
                onUpdateImageView();
            }
        }
    }

    public boolean isCheckable() {
        return checkable;
    }

    public void setChecked(boolean checked) {
        View child = getChildAt(0);
        if (checked && startBackgroundDrawable == null) {
            startBackgroundDrawable = child.getBackground();
            child.setBackgroundColor(DrawableTint.getColorRes(context, R.attr.colorPrimary));
        } else if (!checked && startBackgroundDrawable != null) {
            child.setBackground(startBackgroundDrawable);
            startBackgroundDrawable = null;
        }
    }

    void onUpdateImageView() {

    }

    public static class UpdateViewHolder<T> extends RecyclerView.ViewHolder {
        private final View view;
        private UpdateView updateView;
        private T item;

        public UpdateViewHolder(UpdateView itemView) {
            super(itemView);

            this.updateView = itemView;
            this.view = itemView;
        }

        // Different is so that call is not ambiguous
        public UpdateViewHolder(View view) {
            super(view);
            this.view = view;
        }

        public UpdateView<T> getUpdateView() {
            return updateView;
        }

        public View getView() {
            return view;
        }

        public T getItem() {
            return item;
        }

        public void setItem(T item) {
            this.item = item;
        }
    }
}

