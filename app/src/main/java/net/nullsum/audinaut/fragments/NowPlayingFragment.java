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
package net.nullsum.audinaut.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.activity.SubsonicFragmentActivity;
import net.nullsum.audinaut.adapter.DownloadFileAdapter;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.audiofx.EqualizerController;
import net.nullsum.audinaut.domain.PlayerState;
import net.nullsum.audinaut.domain.RepeatMode;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.service.DownloadService.OnSongChangedListener;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.DownloadFileItemHelperCallback;
import net.nullsum.audinaut.util.DrawableTint;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.MenuUtil;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.AutoRepeatButton;
import net.nullsum.audinaut.view.FastScroller;
import net.nullsum.audinaut.view.UpdateView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static net.nullsum.audinaut.domain.MusicDirectory.Entry;
import static net.nullsum.audinaut.domain.PlayerState.COMPLETED;
import static net.nullsum.audinaut.domain.PlayerState.IDLE;
import static net.nullsum.audinaut.domain.PlayerState.PAUSED;
import static net.nullsum.audinaut.domain.PlayerState.STOPPED;

public class NowPlayingFragment extends SubsonicFragment implements OnGestureListener, SectionAdapter.OnItemClickedListener<DownloadFile>, OnSongChangedListener {
    private static final int PERCENTAGE_OF_SCREEN_FOR_SWIPE = 10;

    private static final int ACTION_PREVIOUS = 1;
    private static final int ACTION_NEXT = 2;
    private static final int ACTION_REWIND = 3;
    private static final int ACTION_FORWARD = 4;

    private ViewFlipper playlistFlipper;
    private TextView emptyTextView;
    private TextView songTitleTextView;
    private ImageView albumArtImageView;
    private View albumArtBackgroundView;
    private ImageView albumArtBackgroundImageView;
    private View nowPlayingView;
    private RecyclerView playlistView;
    private TextView positionTextView;
    private TextView durationTextView;
    private TextView statusTextView;
    private SeekBar progressBar;
    private AutoRepeatButton previousButton;
    private AutoRepeatButton nextButton;
    private AutoRepeatButton rewindButton;
    private AutoRepeatButton fastforwardButton;
    private View pauseButton;
    private View stopButton;
    private View startButton;
    private ImageButton repeatButton;
    private View toggleListButton;

    private DownloadFile currentPlaying;
    private int swipeDistance;
    private int swipeVelocity;
    private List<DownloadFile> songList;
    private DownloadFileAdapter songListAdapter;
    private boolean seekInProgress = false;
    private boolean startFlipped = false;
    private boolean scrollWhenLoaded = false;
    private int lastY = 0;
    private int currentPlayingSize = 0;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            if (savedInstanceState.getInt(Constants.FRAGMENT_DOWNLOAD_FLIPPER) == 1) {
                startFlipped = true;
            }
        }
        primaryFragment = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(Constants.FRAGMENT_DOWNLOAD_FLIPPER, playlistFlipper.getDisplayedChild());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        rootView = inflater.inflate(R.layout.download, container, false);
        setTitle(R.string.button_bar_now_playing);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int screenWidth = displaymetrics.widthPixels;
        int screenHeight = displaymetrics.heightPixels;
        swipeDistance = (screenWidth + screenHeight) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
        swipeVelocity = (screenWidth + screenHeight) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
        gestureScanner = new GestureDetector(context, this);

        playlistFlipper = rootView.findViewById(R.id.download_playlist_flipper);
        emptyTextView = rootView.findViewById(R.id.download_empty);
        songTitleTextView = rootView.findViewById(R.id.download_song_title);
        albumArtImageView = rootView.findViewById(R.id.download_album_art_image);
        albumArtBackgroundView = rootView.findViewById(R.id.download_album_art_background);
        albumArtBackgroundImageView = rootView.findViewById(R.id.download_album_art_background_image);
        nowPlayingView = rootView.findViewById(R.id.now_playing_top);
        positionTextView = rootView.findViewById(R.id.download_position);
        durationTextView = rootView.findViewById(R.id.download_duration);
        statusTextView = rootView.findViewById(R.id.download_status);
        progressBar = rootView.findViewById(R.id.download_progress_bar);
        previousButton = rootView.findViewById(R.id.download_previous);
        nextButton = rootView.findViewById(R.id.download_next);
        rewindButton = rootView.findViewById(R.id.download_rewind);
        fastforwardButton = rootView.findViewById(R.id.download_fastforward);
        pauseButton = rootView.findViewById(R.id.download_pause);
        stopButton = rootView.findViewById(R.id.download_stop);
        startButton = rootView.findViewById(R.id.download_start);
        repeatButton = rootView.findViewById(R.id.download_repeat);
        toggleListButton = rootView.findViewById(R.id.download_toggle_list);

        playlistView = rootView.findViewById(R.id.download_list);
        FastScroller fastScroller = rootView.findViewById(R.id.download_fast_scroller);
        fastScroller.attachRecyclerView(playlistView);
        setupLayoutManager(playlistView, false);
        ItemTouchHelper touchHelper = new ItemTouchHelper(new DownloadFileItemHelperCallback(this, true));
        touchHelper.attachToRecyclerView(playlistView);

        View.OnTouchListener touchListener = (v, me) -> gestureScanner.onTouchEvent(me);
        pauseButton.setOnTouchListener(touchListener);
        stopButton.setOnTouchListener(touchListener);
        startButton.setOnTouchListener(touchListener);
        emptyTextView.setOnTouchListener(touchListener);
        albumArtImageView.setOnTouchListener((v, me) -> {
            if (me.getAction() == MotionEvent.ACTION_DOWN) {
                lastY = (int) me.getRawY();
            }
            return gestureScanner.onTouchEvent(me);
        });

        previousButton.setOnClickListener(view -> {
            warnIfStorageUnavailable();
            new SilentBackgroundTask<Void>(context) {
                @Override
                protected Void doInBackground() {
                    getDownloadService().previous();
                    return null;
                }
            }.execute();
        });
        previousButton.setOnRepeatListener(() -> changeProgress(true));

        nextButton.setOnClickListener(view -> {
            warnIfStorageUnavailable();
            new SilentBackgroundTask<Boolean>(context) {
                @Override
                protected Boolean doInBackground() {
                    getDownloadService().next();
                    return true;
                }
            }.execute();
        });
        nextButton.setOnRepeatListener(() -> changeProgress(false));

        rewindButton.setOnClickListener(view -> changeProgress(true));
        rewindButton.setOnRepeatListener(() -> changeProgress(true));

        fastforwardButton.setOnClickListener(view -> changeProgress(false));
        fastforwardButton.setOnRepeatListener(() -> changeProgress(false));


        pauseButton.setOnClickListener(view -> new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() {
                getDownloadService().pause();
                return null;
            }
        }.execute());

        stopButton.setOnClickListener(view -> new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() {
                getDownloadService().reset();
                return null;
            }
        }.execute());

        startButton.setOnClickListener(view -> {
            warnIfStorageUnavailable();
            new SilentBackgroundTask<Void>(context) {
                @Override
                protected Void doInBackground() {
                    start();
                    return null;
                }
            }.execute();
        });

        repeatButton.setOnClickListener(view -> {
            RepeatMode repeatMode = getDownloadService().getRepeatMode().next();
            getDownloadService().setRepeatMode(repeatMode);
            switch (repeatMode) {
                case OFF:
                    Util.toast(context, R.string.download_repeat_off);
                    break;
                case ALL:
                    Util.toast(context, R.string.download_repeat_all);
                    break;
                case SINGLE:
                    Util.toast(context, R.string.download_repeat_single);
                    break;
                default:
                    break;
            }
            updateRepeatButton();
        });

        toggleListButton.setOnClickListener(view -> {
            toggleFullscreenAlbumArt();
        });

        View overlay = rootView.findViewById(R.id.download_overlay_buttons);
        final int overlayHeight = overlay != null ? overlay.getHeight() : -1;
        // seems pointless, but allows swipe gestures to work
        albumArtImageView.setOnClickListener(view -> {});

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                new SilentBackgroundTask<Void>(context) {
                    @Override
                    protected Void doInBackground() {
                        getDownloadService().seekTo(progressBar.getProgress());
                        return null;
                    }

                    @Override
                    protected void done(Void result) {
                        seekInProgress = false;
                    }
                }.execute();
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
                seekInProgress = true;
            }

            @Override
            public void onProgressChanged(final SeekBar seekBar, final int position, final boolean fromUser) {
                if (fromUser) {
                    positionTextView.setText(Util.formatDuration(position / 1000));
                }
            }
        });

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        DownloadService downloadService = getDownloadService();
        menuInflater.inflate(R.menu.nowplaying, menu);

        if (Util.isOffline(context)) {
            menu.findItem(R.id.menu_save_playlist).setEnabled(false);
        }

        if (downloadService != null) {
            if (downloadService.isRemovePlayed()) {
                menu.findItem(R.id.menu_remove_played).setChecked(true);
            }
            SharedPreferences prefs = Util.getPreferences(context);
            boolean equalizerOn = prefs.getBoolean(Constants.PREFERENCES_EQUALIZER_ON, false);
            if (equalizerOn) {
                if (downloadService.getEqualizerController() != null && downloadService.getEqualizerController().isEnabled()) {
                    menu.findItem(R.id.menu_equalizer).setChecked(true);
                }
            }
        } else {
            menu.removeItem(R.id.menu_equalizer);
        }

        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_BATCH_MODE, false)) {
            menu.findItem(R.id.menu_batch_mode).setChecked(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        return menuItemSelected(menuItem.getItemId(), null) || super.onOptionsItemSelected(menuItem);

    }

    @Override
    public void onCreateContextMenu(Menu menu, MenuInflater menuInflater, UpdateView<DownloadFile> updateView, DownloadFile downloadFile) {
        if (Util.isOffline(context)) {
            menuInflater.inflate(R.menu.nowplaying_context_offline, menu);
        } else {
            menuInflater.inflate(R.menu.nowplaying_context, menu);
        }

        if (downloadFile.getSong().getParent() == null) {
            menu.findItem(R.id.menu_show_album).setVisible(false);
            menu.findItem(R.id.menu_show_artist).setVisible(false);
        }

        MenuUtil.hideMenuItems(context, menu, updateView);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem, UpdateView<DownloadFile> updateView, DownloadFile downloadFile) {
        return onContextItemSelected(menuItem, downloadFile.getSong()) || menuItemSelected(menuItem.getItemId(), downloadFile);

    }

    private boolean menuItemSelected(int menuItemId, final DownloadFile song) {
        switch (menuItemId) {
            case R.id.menu_show_album:
            case R.id.menu_show_artist:
                Entry entry = song.getSong();

                Intent intent = new Intent(context, SubsonicFragmentActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_VIEW_ALBUM, true);
                String albumId;
                String albumName;
                if (menuItemId == R.id.menu_show_album) {
                    albumId = entry.getAlbumId();
                    albumName = entry.getAlbum();
                } else {
                    albumId = entry.getArtistId();
                    albumName = entry.getArtist();
                    intent.putExtra(Constants.INTENT_EXTRA_NAME_ARTIST, true);
                }
                intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, albumId);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, albumName);
                intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, "Artist");

                if (Util.isOffline(context)) {
                    try {
                        // This should only be successful if this is a online song in offline mode
                        Integer.parseInt(entry.getParent());
                        String root = FileUtil.getMusicDirectory(context).getPath();
                        String id = root + "/" + entry.getPath();
                        id = id.substring(0, id.lastIndexOf("/"));
                        if (menuItemId == R.id.menu_show_album) {
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, id);
                        }
                        id = id.substring(0, id.lastIndexOf("/"));
                        if (menuItemId != R.id.menu_show_album) {
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, id);
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
                            intent.removeExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID);
                        }
                    } catch (Exception e) {
                        // Do nothing, entry.getParent() is fine
                    }
                }

                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Util.startActivityWithoutTransition(context, intent);
                return true;
            case R.id.menu_remove_all:
                Util.confirmDialog(context, R.string.download_menu_remove_all, "", (dialog, which) -> new SilentBackgroundTask<Void>(context) {
                    @Override
                    protected Void doInBackground() {
                        getDownloadService().setShufflePlayEnabled(false);
                        getDownloadService().clear();
                        return null;
                    }

                    @Override
                    protected void done(Void result) {
                        context.closeNowPlaying();
                    }
                }.execute());
                return true;
            case R.id.menu_remove_played:
                if (getDownloadService().isRemovePlayed()) {
                    getDownloadService().setRemovePlayed(false);
                } else {
                    getDownloadService().setRemovePlayed(true);
                }
                context.supportInvalidateOptionsMenu();
                return true;
            case R.id.menu_shuffle:
                new SilentBackgroundTask<Void>(context) {
                    @Override
                    protected Void doInBackground() {
                        getDownloadService().shuffle();
                        return null;
                    }

                    @Override
                    protected void done(Void result) {
                        Util.toast(context, R.string.download_menu_shuffle_notification);
                    }
                }.execute();
                return true;
            case R.id.menu_save_playlist:
                List<Entry> entries = new LinkedList<>();
                for (DownloadFile downloadFile : getDownloadService().getSongs()) {
                    entries.add(downloadFile.getSong());
                }
                createNewPlaylist(entries, true);
                return true;
            case R.id.menu_info:
                displaySongInfo(song.getSong());
                return true;
            case R.id.menu_equalizer: {
                DownloadService downloadService = getDownloadService();
                if (downloadService != null) {
                    EqualizerController controller = downloadService.getEqualizerController();
                    if (controller != null) {
                        SubsonicFragment fragment = new EqualizerFragment();
                        replaceFragment(fragment);

                        return true;
                    }
                }

                // Any failed condition will get here
                Util.toast(context, "Failed to start equalizer.  Try restarting.");
                return true;
            }
            case R.id.menu_batch_mode:
                if (Util.isBatchMode(context)) {
                    Util.setBatchMode(context, false);
                    songListAdapter.notifyDataSetChanged();
                } else {
                    Util.setBatchMode(context, true);
                    songListAdapter.notifyDataSetChanged();
                }
                context.supportInvalidateOptionsMenu();

                return true;
            default:
                return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.primaryFragment) {
            onResumeHandlers();
        } else {
            update();
        }
    }

    private void onResumeHandlers() {
        context.runWhenServiceAvailable(() -> {
            if (primaryFragment) {
                DownloadService downloadService1 = getDownloadService();
                downloadService1.addOnSongChangedListener(NowPlayingFragment.this);
            }
            updateRepeatButton();
            updateTitle();
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        onPauseHandlers();
    }

    private void onPauseHandlers() {
        DownloadService downloadService = getDownloadService();
        if (downloadService != null) {
            downloadService.removeOnSongChangeListener(this);
        }
    }

    @Override
    public void setPrimaryFragment(boolean primary) {
        super.setPrimaryFragment(primary);
        if (rootView != null) {
            if (primary) {
                onResumeHandlers();
            } else {
                onPauseHandlers();
            }
        }
    }

    @Override
    void setTitle(int title) {
        this.title = context.getResources().getString(title);
        if (this.primaryFragment) {
            context.setTitle(this.title);
        }
    }

    @Override
    void setSubtitle(CharSequence title) {
        this.subtitle = title;
        if (this.primaryFragment) {
            context.setSubtitle(title);
        }
    }

    @Override
    public SectionAdapter getCurrentAdapter() {
        return songListAdapter;
    }

    // Scroll to current playing/downloading.
    private void scrollToCurrent() {
        if (getDownloadService() == null || songListAdapter == null) {
            scrollWhenLoaded = true;
            return;
        }

        // Try to get position of current playing/downloading
        int position = songListAdapter.getItemPosition(currentPlaying);
        if (position == -1) {
            DownloadFile currentDownloading = getDownloadService().getCurrentDownloading();
            position = songListAdapter.getItemPosition(currentDownloading);
        }

        // If found, scroll to it
        if (position != -1) {
            // RecyclerView.scrollToPosition just puts it on the screen (ie: bottom if scrolled below it)
            LinearLayoutManager layoutManager = (LinearLayoutManager) playlistView.getLayoutManager();
            layoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private void update() {
        if (startFlipped) {
            startFlipped = false;
            scrollToCurrent();
        }
    }

    private void toggleFullscreenAlbumArt() {
        if (playlistFlipper.getDisplayedChild() == 1) {
            playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(context, R.anim.push_down_in));
            playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(context, R.anim.push_down_out));
            playlistFlipper.setDisplayedChild(0);
        } else {
            scrollToCurrent();
            playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(context, R.anim.push_up_in));
            playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(context, R.anim.push_up_out));
            playlistFlipper.setDisplayedChild(1);

            UpdateView.triggerUpdate();
        }
    }

    private void start() {
        DownloadService service = getDownloadService();
        PlayerState state = service.getPlayerState();
        if (state == PAUSED || state == COMPLETED || state == STOPPED) {
            service.start();
        } else if (state == IDLE) {
            warnIfStorageUnavailable();
            int current = service.getCurrentPlayingIndex();
            // TODO: Use play() method.
            if (current == -1) {
                service.play(0);
            } else {
                service.play(current);
            }
        }
    }

    private void changeProgress(final boolean rewind) {
        final DownloadService downloadService = getDownloadService();
        if (downloadService == null) {
            return;
        }

        new SilentBackgroundTask<Void>(context) {
            int seekTo;

            @Override
            protected Void doInBackground() {
                if (rewind) {
                    seekTo = downloadService.rewind();
                } else {
                    seekTo = downloadService.fastForward();
                }
                return null;
            }

            @Override
            protected void done(Void result) {
                progressBar.setProgress(seekTo);
            }
        }.execute();
    }

    @Override
    public boolean onDown(MotionEvent me) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        final DownloadService downloadService = getDownloadService();
        if (downloadService == null || e1 == null || e2 == null) {
            return false;
        }

        // Right to Left swipe
        int action = 0;
        if (e1.getX() - e2.getX() > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
            action = ACTION_NEXT;
        }
        // Left to Right swipe
        else if (e2.getX() - e1.getX() > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
            action = ACTION_PREVIOUS;
        }
        /*
         * too finnicky, no visual feedback
        // Top to Bottom swipe
        else if (e2.getY() - e1.getY() > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
            action = ACTION_FORWARD;
        }
        // Bottom to Top swipe
        else if (e1.getY() - e2.getY() > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
            action = ACTION_REWIND;
        }
        */

        if (action > 0) {
            final int performAction = action;
            warnIfStorageUnavailable();
            new SilentBackgroundTask<Void>(context) {
                @Override
                protected Void doInBackground() {
                    switch (performAction) {
                        case ACTION_NEXT:
                            downloadService.next();
                            break;
                        case ACTION_PREVIOUS:
                            downloadService.previous();
                            break;
                        case ACTION_FORWARD:
                            downloadService.seekTo(downloadService.getPlayerPosition() + DownloadService.FAST_FORWARD);
                            break;
                        case ACTION_REWIND:
                            downloadService.seekTo(downloadService.getPlayerPosition() - DownloadService.REWIND);
                            break;
                    }
                    return null;
                }
            }.execute();

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public void onItemClicked(UpdateView<DownloadFile> updateView, final DownloadFile item) {
        warnIfStorageUnavailable();
        new SilentBackgroundTask<Void>(context) {
            @Override
            protected Void doInBackground() {
                getDownloadService().play(item);
                return null;
            }
        }.execute();
    }

    @Override
    public void onSongChanged(DownloadFile currentPlaying, int currentPlayingIndex) {
        this.currentPlaying = currentPlaying;
        setupSubtitle(currentPlayingIndex);

        if (currentPlaying != null && !currentPlaying.isSong()) {
            previousButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.GONE);

            rewindButton.setVisibility(View.VISIBLE);
            fastforwardButton.setVisibility(View.VISIBLE);
        } else {
            previousButton.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.VISIBLE);

            rewindButton.setVisibility(View.GONE);
            fastforwardButton.setVisibility(View.GONE);
        }
        updateTitle();
    }

    private void setupSubtitle(int currentPlayingIndex) {
        if (currentPlaying != null) {
            Entry song = currentPlaying.getSong();
            songTitleTextView.setText(song.getTitle());
            setAlbumArt(song, true);

            DownloadService downloadService = getDownloadService();
        } else {
            songTitleTextView.setText(null);
            setAlbumArt(null, false);
            setSubtitle(null);
        }
    }

    @Override
    public void onSongsChanged(List<DownloadFile> songs, DownloadFile currentPlaying, int currentPlayingIndex) {
        currentPlayingSize = songs.size();

        DownloadService downloadService = getDownloadService();
        if (downloadService.isShufflePlayEnabled()) {
            emptyTextView.setText(R.string.download_shuffle_loading);
        } else {
            emptyTextView.setText(R.string.download_empty);
        }

        if (songListAdapter == null) {
            songList = new ArrayList<>();
            songList.addAll(songs);
            playlistView.setAdapter(songListAdapter = new DownloadFileAdapter(context, songList, NowPlayingFragment.this));
        } else {
            songList.clear();
            songList.addAll(songs);
            songListAdapter.notifyDataSetChanged();
        }

        emptyTextView.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);

        if (scrollWhenLoaded) {
            scrollToCurrent();
            scrollWhenLoaded = false;
        }

        if (this.currentPlaying != currentPlaying) {
            onSongChanged(currentPlaying, currentPlayingIndex);
            onMetadataUpdate(currentPlaying != null ? currentPlaying.getSong() : null, DownloadService.METADATA_UPDATED_ALL);
        } else {
            setupSubtitle(currentPlayingIndex);
        }

        toggleListButton.setVisibility(View.VISIBLE);
        repeatButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSongProgress(DownloadFile currentPlaying, int millisPlayed, Integer duration, boolean isSeekable) {
        if (currentPlaying != null) {
            int millisTotal = duration == null ? 0 : duration;

            positionTextView.setText(Util.formatDuration(millisPlayed / 1000));
            if (millisTotal > 0) {
                durationTextView.setText(Util.formatDuration(millisTotal / 1000));
            } else {
                durationTextView.setText("-:--");
            }
            progressBar.setMax(millisTotal == 0 ? 100 : millisTotal); // Work-around for apparent bug.
            if (!seekInProgress) {
                progressBar.setProgress(millisPlayed);
            }
            progressBar.setEnabled(isSeekable);
        } else {
            positionTextView.setText("0:00");
            durationTextView.setText("-:--");
            progressBar.setProgress(0);
            progressBar.setEnabled(false);
        }
    }

    @Override
    public void onStateUpdate(PlayerState playerState) {
        switch (playerState) {
            case DOWNLOADING:
                if (currentPlaying != null) {
                    if (Util.isWifiRequiredForDownload(context)) {
                        statusTextView.setText(context.getResources().getString(R.string.download_playerstate_mobile_disabled));
                    } else {
                        long bytes = currentPlaying.getPartialFile().length();
                        statusTextView.setText(context.getResources().getString(R.string.download_playerstate_downloading, Util.formatLocalizedBytes(bytes, context)));
                    }
                }
                break;
            case PREPARING:
                statusTextView.setText(R.string.download_playerstate_buffering);
                break;
            default:
                if (currentPlaying != null) {
                    Entry entry = currentPlaying.getSong();
                    if (entry.getAlbum() != null) {
                        String artist = "";
                        if (entry.getArtist() != null) {
                            artist = currentPlaying.getSong().getArtist().trim() + " - ";
                        }
                        statusTextView.setText(artist + entry.getAlbum().trim());
                    } else {
                        statusTextView.setText(null);
                    }
                } else {
                    statusTextView.setText(null);
                }
                break;
        }

        switch (playerState) {
            case STARTED:
                pauseButton.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.INVISIBLE);
                startButton.setVisibility(View.INVISIBLE);
                break;
            case DOWNLOADING:
            case PREPARING:
                pauseButton.setVisibility(View.INVISIBLE);
                stopButton.setVisibility(View.VISIBLE);
                startButton.setVisibility(View.INVISIBLE);
                break;
            default:
                pauseButton.setVisibility(View.INVISIBLE);
                stopButton.setVisibility(View.INVISIBLE);
                startButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void setAlbumArt(Entry song, Boolean crossfade) {
        getImageLoader().loadImage(albumArtImageView, song, true, crossfade);
        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_BLURRED_BACKGROUND, true)) {
            albumArtBackgroundView.setVisibility(ImageView.VISIBLE);
            getImageLoader().loadBlurImage(albumArtBackgroundImageView, song, true, crossfade);
        } else {
            albumArtBackgroundView.setVisibility(ImageView.GONE);
        }
    }

    @Override
    public void onMetadataUpdate(Entry song, int fieldChange) {
        if (song != null && albumArtImageView != null && fieldChange == DownloadService.METADATA_UPDATED_COVER_ART) {
            setAlbumArt(song, true);
        }
    }

    private void updateRepeatButton() {
        DownloadService downloadService = getDownloadService();
        switch (downloadService.getRepeatMode()) {
            case OFF:
                repeatButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.media_button_repeat_off));
                break;
            case ALL:
                repeatButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.media_button_repeat_all));
                break;
            case SINGLE:
                repeatButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.media_button_repeat_single));
                break;
            default:
                break;
        }
    }

    private void updateTitle() {
        String title = context.getResources().getString(R.string.button_bar_now_playing);

        setTitle(title);
    }

    @Override
    protected List<Entry> getSelectedEntries() {
        List<DownloadFile> selected = getCurrentAdapter().getSelected();
        List<Entry> entries = new ArrayList<>();

        for (DownloadFile downloadFile : selected) {
            if (downloadFile.getSong() != null) {
                entries.add(downloadFile.getSong());
            }
        }

        return entries;
    }
}
