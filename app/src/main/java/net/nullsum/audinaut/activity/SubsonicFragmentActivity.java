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
package net.nullsum.audinaut.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.PlayerState;
import net.nullsum.audinaut.fragments.DownloadFragment;
import net.nullsum.audinaut.fragments.NowPlayingFragment;
import net.nullsum.audinaut.fragments.SearchFragment;
import net.nullsum.audinaut.fragments.SelectArtistFragment;
import net.nullsum.audinaut.fragments.SelectDirectoryFragment;
import net.nullsum.audinaut.fragments.SelectPlaylistFragment;
import net.nullsum.audinaut.fragments.SubsonicFragment;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.updates.Updater;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.FileUtil;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.Util;

import java.io.File;
import java.util.List;

/**
 * Created by Scott on 10/14/13.
 */
public class SubsonicFragmentActivity extends SubsonicActivity implements DownloadService.OnSongChangedListener {
    private static boolean infoDialogDisplayed;
    private static boolean sessionInitialized = false;
    private SlidingUpPanelLayout slideUpPanel;
    private SlidingUpPanelLayout.PanelSlideListener panelSlideListener;
    private boolean isPanelClosing = false;
    private boolean resuming = false;
    private NowPlayingFragment nowPlayingFragment;
    private SubsonicFragment secondaryFragment;
    private Toolbar mainToolbar;
    private Toolbar nowPlayingToolbar;

    private View bottomBar;
    private ImageView coverArtView;
    private TextView trackView;
    private TextView artistView;
    private ImageButton startButton;
    private DownloadFile currentPlaying;
    private ImageButton previousButton;
    private ImageButton nextButton;
    private ImageButton rewindButton;
    private ImageButton fastforwardButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            String fragmentType = getIntent().getStringExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE);
            boolean firstRun = false;
            if (fragmentType == null) {
                fragmentType = "Library";
                firstRun = true;
            }

            if ("".equals(fragmentType) || firstRun) {
                // Initial startup stuff
                if (!sessionInitialized) {
                    loadSession();
                }
            }
        }

        super.onCreate(savedInstanceState);
        if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_EXIT)) {
            stopService(new Intent(this, DownloadService.class));
            finish();
            getImageLoader().clearCache();
        } else if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW)) {
            getIntent().putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, "Download");
            lastSelectedPosition = R.id.drawer_downloading;
        }
        setContentView(R.layout.abstract_fragment_activity);
        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(true);
        }

        if (findViewById(R.id.fragment_container) != null && savedInstanceState == null) {
            String fragmentType = getIntent().getStringExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE);
            if (fragmentType == null) {
                fragmentType = "Library";
                getIntent().putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, fragmentType);
                lastSelectedPosition = getDrawerItemId(fragmentType);

                MenuItem item = drawerList.getMenu().findItem(lastSelectedPosition);
                if (item != null) {
                    item.setChecked(true);
                }
            } else {
                lastSelectedPosition = getDrawerItemId(fragmentType);
            }

            currentFragment = getNewFragment(fragmentType);
            if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_ID)) {
                Bundle currentArguments = currentFragment.getArguments();
                if (currentArguments == null) {
                    currentArguments = new Bundle();
                }
                currentArguments.putString(Constants.INTENT_EXTRA_NAME_ID, getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID));
                currentFragment.setArguments(currentArguments);
            }
            currentFragment.setPrimaryFragment(true);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, currentFragment, currentFragment.getSupportTag() + "").commit();

            if (getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY) != null) {
                SearchFragment fragment = new SearchFragment();
                replaceFragment(fragment, fragment.getSupportTag());
            }

            // If a album type is set, switch to that album type view
            String albumType = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
            if (albumType != null) {
                SubsonicFragment fragment = new SelectDirectoryFragment();

                Bundle args = new Bundle();
                args.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, albumType);
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 20);
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);

                fragment.setArguments(args);
                replaceFragment(fragment, fragment.getSupportTag());
            }
        }

        slideUpPanel = findViewById(R.id.slide_up_panel);
        panelSlideListener = new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                Util.hideKeyboard(panel);
            }

            @Override
            public void onPanelStateChanged(View panel, PanelState previousState, PanelState newState) {
                if (newState == PanelState.COLLAPSED) {
                    isPanelClosing = false;
                    if (bottomBar.getVisibility() == View.GONE) {
                        bottomBar.setVisibility(View.VISIBLE);
                        nowPlayingToolbar.setVisibility(View.GONE);
                        nowPlayingFragment.setPrimaryFragment(false);
                        setSupportActionBar(mainToolbar);
                        recreateSpinner();
                    }
                } else if (newState == PanelState.EXPANDED) {
                    isPanelClosing = false;
                    currentFragment.stopActionMode();

                    // Disable custom view before switching
                    getSupportActionBar().setDisplayShowCustomEnabled(false);
                    getSupportActionBar().setDisplayShowTitleEnabled(true);

                    bottomBar.setVisibility(View.GONE);
                    nowPlayingToolbar.setVisibility(View.VISIBLE);
                    setSupportActionBar(nowPlayingToolbar);

                    if (secondaryFragment == null) {
                        nowPlayingFragment.setPrimaryFragment(true);
                    } else {
                        secondaryFragment.setPrimaryFragment(true);
                    }

                    drawerToggle.setDrawerIndicatorEnabled(false);
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                }
            }
        };
        slideUpPanel.addPanelSlideListener(panelSlideListener);

        if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD)) {
            // Post this later so it actually runs
            handler.postDelayed(this::openNowPlaying, 200);

            getIntent().removeExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD);
        }

        bottomBar = findViewById(R.id.bottom_bar);
        mainToolbar = findViewById(R.id.main_toolbar);
        nowPlayingToolbar = findViewById(R.id.now_playing_toolbar);
        coverArtView = bottomBar.findViewById(R.id.album_art);
        trackView = bottomBar.findViewById(R.id.track_name);
        artistView = bottomBar.findViewById(R.id.artist_name);

        setSupportActionBar(mainToolbar);

        if (findViewById(R.id.fragment_container) != null && savedInstanceState == null) {
            nowPlayingFragment = new NowPlayingFragment();
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.add(R.id.now_playing_fragment_container, nowPlayingFragment, nowPlayingFragment.getTag() + "");
            trans.commit();
        }

        rewindButton = findViewById(R.id.download_rewind);
        rewindButton.setOnClickListener(v -> new SilentBackgroundTask<Void>(SubsonicFragmentActivity.this) {
            @Override
            protected Void doInBackground() {
                if (getDownloadService() != null) {
                    getDownloadService().rewind();
                }
                return null;
            }
        }.execute());

        previousButton = findViewById(R.id.download_previous);
        previousButton.setOnClickListener(v -> new SilentBackgroundTask<Void>(SubsonicFragmentActivity.this) {
            @Override
            protected Void doInBackground() {
                if (getDownloadService() != null) {
                    getDownloadService().previous();
                }
                return null;
            }
        }.execute());

        startButton = findViewById(R.id.download_start);
        startButton.setOnClickListener(v -> new SilentBackgroundTask<Void>(SubsonicFragmentActivity.this) {
            @Override
            protected Void doInBackground() {
                PlayerState state = getDownloadService().getPlayerState();
                if (state == PlayerState.STARTED) {
                    getDownloadService().pause();
                } else {
                    getDownloadService().start();
                }
                return null;
            }
        }.execute());

        nextButton = findViewById(R.id.download_next);
        nextButton.setOnClickListener(v -> new SilentBackgroundTask<Void>(SubsonicFragmentActivity.this) {
            @Override
            protected Void doInBackground() {
                if (getDownloadService() != null) {
                    getDownloadService().next();
                }
                return null;
            }
        }.execute());

        fastforwardButton = findViewById(R.id.download_fastforward);
        fastforwardButton.setOnClickListener(v -> new SilentBackgroundTask<Void>(SubsonicFragmentActivity.this) {
            @Override
            protected Void doInBackground() {
                if (getDownloadService() == null) {
                    return null;
                }

                getDownloadService().fastForward();
                return null;
            }
        }.execute());

        if (!infoDialogDisplayed) {
            infoDialogDisplayed = true;
            if (Util.getRestUrl(this).contains("demo.subsonic.org")) {
                Util.info(this, R.string.main_welcome_title, R.string.main_welcome_text);
            }
        }

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int ver = Integer.parseInt(version.replace(".", ""));
            Updater updater = new Updater(ver);
            updater.checkUpdates(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (currentFragment != null && intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY) != null) {
            if (isNowPlayingOpen()) {
                closeNowPlaying();
            }

            if (currentFragment instanceof SearchFragment) {
                String query = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY);
                boolean autoplay = intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);

                if (query != null) {
                    ((SearchFragment) currentFragment).search(query, autoplay);
                }
                getIntent().removeExtra(Constants.INTENT_EXTRA_NAME_QUERY);
            } else {
                setIntent(intent);

                SearchFragment fragment = new SearchFragment();
                replaceFragment(fragment, fragment.getSupportTag());
            }
        } else if (intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, false)) {
            if (!isNowPlayingOpen()) {
                openNowPlaying();
            }
        } else {
            setIntent(intent);
        }
        if (drawer != null) {
            drawer.closeDrawers();
        }
    }

    @Override
    public void onResume() {
        resuming = true;
        super.onResume();

        if (getIntent().hasExtra(Constants.INTENT_EXTRA_VIEW_ALBUM)) {
            SubsonicFragment fragment = new SelectDirectoryFragment();
            Bundle args = new Bundle();
            args.putString(Constants.INTENT_EXTRA_NAME_ID, getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID));
            args.putString(Constants.INTENT_EXTRA_NAME_NAME, getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_NAME));
            args.putString(Constants.INTENT_EXTRA_SEARCH_SONG, getIntent().getStringExtra(Constants.INTENT_EXTRA_SEARCH_SONG));
            if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_ARTIST)) {
                args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
            }
            if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID)) {
                args.putString(Constants.INTENT_EXTRA_NAME_CHILD_ID, getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID));
            }
            fragment.setArguments(args);

            replaceFragment(fragment, fragment.getSupportTag());
            getIntent().removeExtra(Constants.INTENT_EXTRA_VIEW_ALBUM);
        }

        createAccount();
        runWhenServiceAvailable(() -> getDownloadService().addOnSongChangedListener(SubsonicFragmentActivity.this));
        resuming = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        DownloadService downloadService = getDownloadService();
        if (downloadService != null) {
            downloadService.removeOnSongChangeListener(this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString(Constants.MAIN_NOW_PLAYING, nowPlayingFragment.getTag());
        if (secondaryFragment != null) {
            savedInstanceState.putString(Constants.MAIN_NOW_PLAYING_SECONDARY, secondaryFragment.getTag());
        }
        savedInstanceState.putInt(Constants.MAIN_SLIDE_PANEL_STATE, slideUpPanel.getPanelState().hashCode());
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        String id = savedInstanceState.getString(Constants.MAIN_NOW_PLAYING);
        FragmentManager fm = getSupportFragmentManager();
        nowPlayingFragment = (NowPlayingFragment) fm.findFragmentByTag(id);

        String secondaryId = savedInstanceState.getString(Constants.MAIN_NOW_PLAYING_SECONDARY);
        if (secondaryId != null) {
            secondaryFragment = (SubsonicFragment) fm.findFragmentByTag(secondaryId);

            nowPlayingFragment.setPrimaryFragment(false);
            secondaryFragment.setPrimaryFragment(true);

            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.hide(nowPlayingFragment);
            trans.commit();
        }

        if (drawerToggle != null && backStack.size() > 0) {
            drawerToggle.setDrawerIndicatorEnabled(false);
        }

        if (savedInstanceState.getInt(Constants.MAIN_SLIDE_PANEL_STATE, -1) == SlidingUpPanelLayout.PanelState.EXPANDED.hashCode()) {
            panelSlideListener.onPanelStateChanged(null, null, PanelState.EXPANDED);
        }
    }

    @Override
    public void onBackPressed() {
        if (isNowPlayingOpen()) {
            if (secondaryFragment == null) {
                closeNowPlaying();
            } else {
                removeCurrent();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public SubsonicFragment getCurrentFragment() {
        if (isNowPlayingOpen()) {
            if (secondaryFragment == null) {
                return nowPlayingFragment;
            } else {
                return secondaryFragment;
            }
        } else {
            return super.getCurrentFragment();
        }
    }

    @Override
    public void replaceFragment(SubsonicFragment fragment, int tag, boolean replaceCurrent) {
        if (slideUpPanel != null && isNowPlayingOpen() && !isPanelClosing) {
            secondaryFragment = fragment;
            nowPlayingFragment.setPrimaryFragment(false);
            secondaryFragment.setPrimaryFragment(true);
            supportInvalidateOptionsMenu();

            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
            trans.hide(nowPlayingFragment);
            trans.add(R.id.now_playing_fragment_container, secondaryFragment, tag + "");
            trans.commit();
        } else {
            super.replaceFragment(fragment, tag, replaceCurrent);
        }
    }

    @Override
    public void removeCurrent() {
        if (isNowPlayingOpen() && secondaryFragment != null) {
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);
            trans.remove(secondaryFragment);
            trans.show(nowPlayingFragment);
            trans.commit();

            secondaryFragment = null;
            nowPlayingFragment.setPrimaryFragment(true);
            supportInvalidateOptionsMenu();
        } else {
            super.removeCurrent();
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        if (isNowPlayingOpen()) {
            getSupportActionBar().setTitle(title);
        } else {
            super.setTitle(title);
        }
    }

    @Override
    protected void drawerItemSelected(String fragmentType) {
        super.drawerItemSelected(fragmentType);

        if (isNowPlayingOpen() && !resuming) {
            closeNowPlaying();
        }
    }

    @Override
    public void startFragmentActivity(String fragmentType) {
        // Create a transaction that does all of this
        FragmentTransaction trans = getSupportFragmentManager().beginTransaction();

        // Clear existing stack
        for (int i = backStack.size() - 1; i >= 0; i--) {
            trans.remove(backStack.get(i));
        }
        trans.remove(currentFragment);
        backStack.clear();

        // Create new stack
        currentFragment = getNewFragment(fragmentType);
        currentFragment.setPrimaryFragment(true);
        trans.add(R.id.fragment_container, currentFragment, currentFragment.getSupportTag() + "");

        // Done, cleanup
        trans.commit();
        supportInvalidateOptionsMenu();
        recreateSpinner();
        if (drawer != null) {
            drawer.closeDrawers();
        }

        if (secondaryContainer != null) {
            secondaryContainer.setVisibility(View.GONE);
        }
        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(true);
        }
    }

    @Override
    public void openNowPlaying() {
        slideUpPanel.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    @Override
    public void closeNowPlaying() {
        slideUpPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        isPanelClosing = true;
    }

    private boolean isNowPlayingOpen() {
        return slideUpPanel.getPanelState() == PanelState.EXPANDED;
    }

    private SubsonicFragment getNewFragment(String fragmentType) {
        switch (fragmentType) {
            case "Playlist":
                return new SelectPlaylistFragment();
            case "Download":
                return new DownloadFragment();
            default:
                return new SelectArtistFragment();
        }
    }

    private void loadSession() {
        PreferenceManager.setDefaultValues(this, R.xml.settings_appearance, false);
        PreferenceManager.setDefaultValues(this, R.xml.settings_cache, false);
        PreferenceManager.setDefaultValues(this, R.xml.settings_playback, false);

        SharedPreferences prefs = Util.getPreferences(this);
        if (!prefs.contains(Constants.PREFERENCES_KEY_CACHE_LOCATION) || prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null) == null) {
            resetCacheLocation(prefs);
        } else {
            String path = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
            File cacheLocation = new File(path);
            if (!FileUtil.verifyCanWrite(cacheLocation)) {
                // Only warn user if there is a difference saved
                if (resetCacheLocation(prefs)) {
                    Util.info(this, R.string.common_warning, R.string.settings_cache_location_reset);
                }
            }
        }

        if (!prefs.contains(Constants.PREFERENCES_KEY_OFFLINE)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Constants.PREFERENCES_KEY_OFFLINE, false);

            editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + 1, "Demo Server");
            editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + 1, "http://demo.subsonic.org");
            editor.putString(Constants.PREFERENCES_KEY_USERNAME + 1, "guest5");
            editor.putString(Constants.PREFERENCES_KEY_PASSWORD + 1, "guest");
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
            editor.apply();
        }
        if (!prefs.contains(Constants.PREFERENCES_KEY_SERVER_COUNT)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_COUNT, 1);
            editor.apply();
        }

        sessionInitialized = true;
    }

    private boolean resetCacheLocation(SharedPreferences prefs) {
        String newDirectory = FileUtil.getDefaultMusicDirectory(this).getPath();
        String oldDirectory = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
        if (oldDirectory != null && newDirectory.equals(oldDirectory)) {
            return false;
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, newDirectory);
            editor.apply();
            return true;
        }
    }

    private void createAccount() {
        final Context context = this;

        new SilentBackgroundTask<Void>(this) {
            @Override
            protected Void doInBackground() {
                AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);
                Account account = new Account(Constants.SYNC_ACCOUNT_NAME, Constants.SYNC_ACCOUNT_TYPE);
                accountManager.addAccountExplicitly(account, null, null);

                SharedPreferences prefs = Util.getPreferences(context);
                boolean syncEnabled = prefs.getBoolean(Constants.PREFERENCES_KEY_SYNC_ENABLED, true);
                int syncInterval = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_SYNC_INTERVAL, "60"));

                // Add enabled/frequency to playlist syncing
                ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY, syncEnabled);
                ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY, new Bundle(), 60L * syncInterval);

                return null;
            }
        }.execute();
    }

    @Override
    public void onSongChanged(DownloadFile currentPlaying, int currentPlayingIndex) {
        this.currentPlaying = currentPlaying;

        MusicDirectory.Entry song = null;
        if (currentPlaying != null) {
            song = currentPlaying.getSong();
            trackView.setText(song.getTitle());

            if (song.getArtist() != null) {
                artistView.setVisibility(View.VISIBLE);
                artistView.setText(song.getArtist());
            } else {
                artistView.setVisibility(View.GONE);
            }
        } else {
            trackView.setText(R.string.main_title);
            artistView.setText(R.string.main_artist);
        }

        if (coverArtView != null) {
            int height = coverArtView.getHeight();
            if (height <= 0) {
                int[] attrs = new int[]{R.attr.actionBarSize};
                TypedArray typedArray = this.obtainStyledAttributes(attrs);
                height = typedArray.getDimensionPixelSize(0, 0);
                typedArray.recycle();
            }
            getImageLoader().loadImage(coverArtView, song, false, height, false);
        }

        previousButton.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.VISIBLE);

        rewindButton.setVisibility(View.GONE);
        fastforwardButton.setVisibility(View.GONE);
    }

    @Override
    public void onSongsChanged(List<DownloadFile> songs, DownloadFile currentPlaying, int currentPlayingIndex) {
        if (this.currentPlaying != currentPlaying || this.currentPlaying == null) {
            onSongChanged(currentPlaying, currentPlayingIndex);
        }
    }

    @Override
    public void onSongProgress(DownloadFile currentPlaying, int millisPlayed, Integer duration, boolean isSeekable) {

    }

    @Override
    public void onStateUpdate(PlayerState playerState) {
        int[] attrs = new int[]{(playerState == PlayerState.STARTED) ? R.attr.actionbar_pause : R.attr.actionbar_start};
        TypedArray typedArray = this.obtainStyledAttributes(attrs);
        startButton.setImageResource(typedArray.getResourceId(0, 0));
        typedArray.recycle();
    }

    @Override
    public void onMetadataUpdate(MusicDirectory.Entry song, int fieldChange) {
        if (song != null && coverArtView != null && fieldChange == DownloadService.METADATA_UPDATED_COVER_ART) {
            int height = coverArtView.getHeight();
            if (height <= 0) {
                int[] attrs = new int[]{R.attr.actionBarSize};
                TypedArray typedArray = this.obtainStyledAttributes(attrs);
                height = typedArray.getDimensionPixelSize(0, 0);
                typedArray.recycle();
            }
            getImageLoader().loadImage(coverArtView, song, false, height, false);

            // We need to update it immediately since it won't update if updater is not running for it
            if (nowPlayingFragment != null && slideUpPanel.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                nowPlayingFragment.onMetadataUpdate(song, fieldChange);
            }
        }
    }
}
