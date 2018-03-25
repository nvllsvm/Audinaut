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
package net.nullsum.audinaut.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.Playlist;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.service.MusicServiceFactory;

/**
 * Asynchronous loading of images, with caching.
 * <p/>
 * There should normally be only one instance of this class.
 *
 * @author Sindre Mehus
 */
public class ImageLoader {
    public static final String PLAYLIST_PREFIX = "pl-";
    private static final String TAG = ImageLoader.class.getSimpleName();
    private final static int[] COLORS = {0xFF33B5E5, 0xFFAA66CC, 0xFF99CC00, 0xFFFFBB33, 0xFFFF4444};
    private final int imageSizeDefault;
    private final int imageSizeLarge;
    private final int cacheSize;
    private final Context context;
    private final Handler handler;
    private LruCache<String, Bitmap> cache;
    private Bitmap nowPlaying;
    private Bitmap nowPlayingSmall;
    private boolean clearingCache = false;

    public ImageLoader(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        cacheSize = maxMemory / 4;

        // Determine the density-dependent image sizes.
        imageSizeDefault = context.getResources().getDrawable(R.drawable.unknown_album).getIntrinsicHeight();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        imageSizeLarge = Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));

        cache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldBitmap, Bitmap newBitmap) {
                if (evicted) {
                    if ((oldBitmap != nowPlaying && oldBitmap != nowPlayingSmall) || clearingCache) {
                        oldBitmap.recycle();
                    } else if (oldBitmap != newBitmap) {
                        cache.put(key, oldBitmap);
                    }
                }
            }
        };
    }

    public void clearCache() {
        nowPlaying = null;
        nowPlayingSmall = null;
        new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() throws Throwable {
                clearingCache = true;
                cache.evictAll();
                clearingCache = false;
                return null;
            }
        }.execute();
    }

    public void onLowMemory(float percent) {
        Log.i(TAG, "Cache size: " + cache.size() + " => " + Math.round(cacheSize * (1 - percent)) + " out of " + cache.maxSize());
        cache.resize(Math.round(cacheSize * (1 - percent)));
    }

    public void onUIVisible() {
        if (cache.maxSize() != cacheSize) {
            Log.i(TAG, "Returned to full cache size");
            cache.resize(cacheSize);
        }
    }

    public void setNowPlayingSmall(Bitmap bitmap) {
        nowPlayingSmall = bitmap;
    }

    private Bitmap getUnknownImage(MusicDirectory.Entry entry, int size) {
        String key;
        int color;
        if (entry == null) {
            key = getKey("unknown", size);
            color = COLORS[0];

            return getUnknownImage(key, size, color, null, null);
        } else {
            key = getKey(entry.getId() + "unknown", size);
            String hash;
            if (entry.getAlbum() != null) {
                hash = entry.getAlbum();
            } else if (entry.getArtist() != null) {
                hash = entry.getArtist();
            } else {
                hash = entry.getId();
            }
            color = COLORS[Math.abs(hash.hashCode()) % COLORS.length];

            return getUnknownImage(key, size, color, entry.getAlbum(), entry.getArtist());
        }
    }

    private Bitmap getUnknownImage(String key, int size, int color, String topText, String bottomText) {
        Bitmap bitmap = cache.get(key);
        if (bitmap == null) {
            bitmap = createUnknownImage(size, color, topText, bottomText);
            cache.put(key, bitmap);
        }

        return bitmap;
    }

    private Bitmap createUnknownImage(int size, int primaryColor, String topText, String bottomText) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint color = new Paint();
        color.setColor(primaryColor);
        canvas.drawRect(0, 0, size, size * 2.0f / 3.0f, color);

        color.setShader(new LinearGradient(0, 0, 0, size / 3.0f, Color.rgb(82, 82, 82), Color.BLACK, Shader.TileMode.MIRROR));
        canvas.drawRect(0, size * 2.0f / 3.0f, size, size, color);

        if (topText != null || bottomText != null) {
            Paint font = new Paint();
            font.setFlags(Paint.ANTI_ALIAS_FLAG);
            font.setColor(Color.WHITE);
            font.setTextSize(3.0f + size * 0.07f);

            if (topText != null) {
                canvas.drawText(topText, size * 0.05f, size * 0.6f, font);
            }

            if (bottomText != null) {
                canvas.drawText(bottomText, size * 0.05f, size * 0.8f, font);
            }
        }

        return bitmap;
    }

    public Bitmap getCachedImage(Context context, MusicDirectory.Entry entry, boolean large) {
        int size = large ? imageSizeLarge : imageSizeDefault;
        if (entry == null || entry.getCoverArt() == null) {
            return getUnknownImage(entry, size);
        }

        Bitmap bitmap = cache.get(getKey(entry.getCoverArt(), size));
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = FileUtil.getAlbumArtBitmap(context, entry, size);
            String key = getKey(entry.getCoverArt(), size);
            cache.put(key, bitmap);
            cache.get(key);
        }

        if (bitmap != null && bitmap.isRecycled()) {
            bitmap = null;
        }
        return bitmap;
    }

    public SilentBackgroundTask loadImage(View view, MusicDirectory.Entry entry, boolean large, boolean crossfade) {
        int size = large ? imageSizeLarge : imageSizeDefault;
        return loadImage(view, entry, large, size, crossfade);
    }

    public SilentBackgroundTask loadImage(View view, MusicDirectory.Entry entry, boolean large, int size, boolean crossfade) {
        // If we know this a artist, try to load artist info instead
        if (entry != null && !entry.isAlbum() && !Util.isOffline(context)) {
            SilentBackgroundTask task = new ArtistImageTask(view.getContext(), entry, size, large, view, crossfade);
            task.execute();
            return task;
        } else if (entry != null && entry.getCoverArt() == null && entry.isDirectory() && !Util.isOffline(context)) {
            // Try to lookup child cover art
            MusicDirectory.Entry firstChild = FileUtil.lookupChild(context, entry, true);
            if (firstChild != null) {
                entry.setCoverArt(firstChild.getCoverArt());
            }
        }

        Bitmap bitmap;
        if (entry == null || entry.getCoverArt() == null) {
            bitmap = getUnknownImage(entry, size);
            setImage(view, Util.createDrawableFromBitmap(context, bitmap), crossfade);
            return null;
        }

        bitmap = cache.get(getKey(entry.getCoverArt(), size));
        if (bitmap != null && !bitmap.isRecycled()) {
            final Drawable drawable = Util.createDrawableFromBitmap(this.context, bitmap);
            setImage(view, drawable, crossfade);
            if (large) {
                nowPlaying = bitmap;
            }
            return null;
        }

        if (!large) {
            setImage(view, null, false);
        }
        ImageTask task = new ViewImageTask(view.getContext(), entry, size, large, view, crossfade);
        task.execute();
        return task;
    }

    public SilentBackgroundTask loadImage(View view, Playlist playlist) {
        MusicDirectory.Entry entry = new MusicDirectory.Entry();
        String id;
        if (Util.isOffline(context)) {
            id = PLAYLIST_PREFIX + playlist.getName();
            entry.setTitle(playlist.getComment());
        } else {
            id = PLAYLIST_PREFIX + playlist.getId();
            entry.setTitle(playlist.getName());
        }
        entry.setId(id);
        entry.setCoverArt(id);
        // So this isn't treated as a artist
        entry.setParent("");

        return loadImage(view, entry, false, true);
    }

    private String getKey(String coverArtId, int size) {
        return coverArtId + size;
    }

    private void setImage(View view, final Drawable drawable, boolean crossfade) {
        if (view instanceof TextView) {
            // Cross-fading is not implemented for TextView since it's not in use.  It would be easy to add it, though.
            TextView textView = (TextView) view;
            textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        } else if (view instanceof ImageView) {
            final ImageView imageView = (ImageView) view;
            if (crossfade && drawable != null) {
                Drawable existingDrawable = imageView.getDrawable();
                if (existingDrawable == null) {
                    Bitmap emptyImage;
                    if (drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
                        emptyImage = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                    } else {
                        emptyImage = Bitmap.createBitmap(imageSizeDefault, imageSizeDefault, Bitmap.Config.ARGB_8888);
                    }
                    existingDrawable = new BitmapDrawable(context.getResources(), emptyImage);
                } else if (existingDrawable instanceof TransitionDrawable) {
                    // This should only ever be used if user is skipping through many songs quickly
                    TransitionDrawable tmp = (TransitionDrawable) existingDrawable;
                    existingDrawable = tmp.getDrawable(tmp.getNumberOfLayers() - 1);
                }
                if (existingDrawable != null && drawable != null) {
                    Drawable[] layers = new Drawable[]{existingDrawable, drawable};
                    final TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
                    imageView.setImageDrawable(transitionDrawable);
                    transitionDrawable.startTransition(250);

                    // Get rid of transition drawable after transition occurs
                    handler.postDelayed(() -> {
                        // Only execute if still on same transition drawable
                        if (imageView.getDrawable() == transitionDrawable) {
                            imageView.setImageDrawable(drawable);
                        }
                    }, 500L);
                } else {
                    imageView.setImageDrawable(drawable);
                }
            } else {
                imageView.setImageDrawable(drawable);
            }
        }
    }

    public abstract class ImageTask extends SilentBackgroundTask<Void> {
        final MusicDirectory.Entry mEntry;
        private final Context mContext;
        private final int mSize;
        private final boolean mIsNowPlaying;
        Drawable mDrawable;

        public ImageTask(Context context, MusicDirectory.Entry entry, int size, boolean isNowPlaying) {
            super(context);
            mContext = context;
            mEntry = entry;
            mSize = size;
            mIsNowPlaying = isNowPlaying;
        }

        @Override
        protected Void doInBackground() throws Throwable {
            try {
                MusicService musicService = MusicServiceFactory.getMusicService(mContext);
                Bitmap bitmap = musicService.getCoverArt(mContext, mEntry, mSize, null, this);
                if (bitmap != null) {
                    String key = getKey(mEntry.getCoverArt(), mSize);
                    cache.put(key, bitmap);
                    // Make sure key is the most recently "used"
                    cache.get(key);
                    if (mIsNowPlaying) {
                        nowPlaying = bitmap;
                    }
                } else {
                    bitmap = getUnknownImage(mEntry, mSize);
                }

                mDrawable = Util.createDrawableFromBitmap(mContext, bitmap);
            } catch (Throwable x) {
                Log.e(TAG, "Failed to download album art.", x);
                cancelled.set(true);
            }

            return null;
        }
    }

    private class ViewImageTask extends ImageTask {
        final boolean mCrossfade;
        private final View mView;

        public ViewImageTask(Context context, MusicDirectory.Entry entry, int size, boolean isNowPlaying, View view, boolean crossfade) {
            super(context, entry, size, isNowPlaying);

            mView = view;
            mCrossfade = crossfade;
        }

        @Override
        protected void done(Void result) {
            setImage(mView, mDrawable, mCrossfade);
        }
    }

    private class ArtistImageTask extends SilentBackgroundTask<Void> {
        private final Context mContext;
        private final MusicDirectory.Entry mEntry;
        private final int mSize;
        private final boolean mIsNowPlaying;
        private final boolean mCrossfade;
        private final View mView;
        private Drawable mDrawable;
        private SilentBackgroundTask subTask;

        public ArtistImageTask(Context context, MusicDirectory.Entry entry, int size, boolean isNowPlaying, View view, boolean crossfade) {
            super(context);
            mContext = context;
            mEntry = entry;
            mSize = size;
            mIsNowPlaying = isNowPlaying;
            mView = view;
            mCrossfade = crossfade;
        }

        @Override
        protected Void doInBackground() throws Throwable {
            try {
                // Figure out whether we are going to get a artist image or the standard image
                if (mEntry != null && mEntry.getCoverArt() == null && mEntry.isDirectory() && !Util.isOffline(context)) {
                    // Try to lookup child cover art
                    MusicDirectory.Entry firstChild = FileUtil.lookupChild(context, mEntry, true);
                    if (firstChild != null) {
                        mEntry.setCoverArt(firstChild.getCoverArt());
                    }
                }

                if (mEntry != null && mEntry.getCoverArt() != null) {
                    subTask = new ViewImageTask(mContext, mEntry, mSize, mIsNowPlaying, mView, mCrossfade);
                } else {
                    // If entry is null as well, we need to just set as a blank image
                    Bitmap bitmap = getUnknownImage(mEntry, mSize);
                    mDrawable = Util.createDrawableFromBitmap(mContext, bitmap);
                    return null;
                }

                // Execute whichever way we decided to go
                subTask.doInBackground();
            } catch (Throwable x) {
                Log.e(TAG, "Failed to get artist info", x);
                cancelled.set(true);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            if (subTask != null) {
                subTask.done(result);
            } else if (mDrawable != null) {
                setImage(mView, mDrawable, mCrossfade);
            }
        }
    }
}
