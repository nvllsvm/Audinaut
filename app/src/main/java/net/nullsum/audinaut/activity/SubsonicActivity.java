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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.fragments.SubsonicFragment;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.service.HeadphoneListenerService;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.DrawableTint;
import net.nullsum.audinaut.util.ImageLoader;
import net.nullsum.audinaut.util.SilentBackgroundTask;
import net.nullsum.audinaut.util.ThemeUtil;
import net.nullsum.audinaut.util.UserUtil;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.UpdateView;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission;

public class SubsonicActivity extends AppCompatActivity implements OnItemSelectedListener {
    private static final String TAG = SubsonicActivity.class.getSimpleName();
    private static final int MENU_GROUP_SERVER = 10;
    private static final int MENU_ITEM_SERVER_BASE = 100;
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static String theme;
    private static boolean fullScreen;
    private static ImageLoader IMAGE_LOADER;

    static {
        // If Android Pie or older, set night mode by system clock
        if (Build.VERSION.SDK_INT<29) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
        } else {
            // Else, for Android 10+, follow system dark mode setting
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    final List<SubsonicFragment> backStack = new ArrayList<>();
    final Handler handler = new Handler();
    private final List<Runnable> afterServiceAvailable = new ArrayList<>();
    SubsonicFragment currentFragment;
    View secondaryContainer;
    DrawerLayout drawer;
    ActionBarDrawerToggle drawerToggle;
    NavigationView drawerList;
    int lastSelectedPosition = 0;
    private boolean touchscreen = true;
    private Spinner actionBarSpinner;
    private ArrayAdapter<CharSequence> spinnerAdapter;
    private View drawerHeader;
    private ImageView drawerHeaderToggle;
    private TextView drawerServerName;
    private TextView drawerUserName;
    private boolean showingTabs = true;
    private boolean drawerOpen = false;
    private SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
    private boolean drawerIdle = true;
    private boolean destroyed = false;

    public synchronized static ImageLoader getStaticImageLoader(Context context) {
        if (IMAGE_LOADER == null) {
            IMAGE_LOADER = new ImageLoader(context);
        }
        return IMAGE_LOADER;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            touchscreen = false;
        }

        applyTheme();
        applyFullscreen();
        super.onCreate(bundle);
        startService(new Intent(this, DownloadService.class));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (getIntent().hasExtra(Constants.FRAGMENT_POSITION)) {
            lastSelectedPosition = getIntent().getIntExtra(Constants.FRAGMENT_POSITION, 0);
        }

        if (preferencesListener == null) {
            Util.getPreferences(this).registerOnSharedPreferenceChangeListener(preferencesListener);
        }

        if (ContextCompat.checkSelfPermission(this, permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Util.toast(this, R.string.permission_external_storage_failed);
                    finish();
                }
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (spinnerAdapter == null) {
            createCustomActionBarView();
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (Util.shouldStartOnHeadphones(this)) {
            Intent serviceIntent = new Intent();
            serviceIntent.setClassName(this.getPackageName(), HeadphoneListenerService.class.getName());
            this.startService(serviceIntent);
        }
    }

    private void createCustomActionBarView() {
        actionBarSpinner = (Spinner) getLayoutInflater().inflate(R.layout.actionbar_spinner, null);
        if ((this instanceof SubsonicFragmentActivity || this instanceof SettingsActivity) && ThemeUtil.getThemeRes(this) != R.style.Theme_Audinaut_Light) {
            actionBarSpinner.setBackground(DrawableTint.getTintedDrawableFromColor(this));
        }
        spinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionBarSpinner.setOnItemSelectedListener(this);
        actionBarSpinner.setAdapter(spinnerAdapter);

        getSupportActionBar().setCustomView(actionBarSpinner);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Util.registerMediaButtonEventReceiver(this);

        // Make sure to update theme
        SharedPreferences prefs = Util.getPreferences(this);
        if (theme != null && !theme.equals(ThemeUtil.getTheme(this)) || fullScreen != prefs.getBoolean(Constants.PREFERENCES_KEY_FULL_SCREEN, false)) {
            restart();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            DrawableTint.wipeTintCache();
        }

        populateTabs();
        getImageLoader().onUIVisible();
        UpdateView.addActiveActivity();
    }

    @Override
    protected void onPause() {
        super.onPause();

        UpdateView.removeActiveActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        Util.getPreferences(this).unregisterOnSharedPreferenceChangeListener(preferencesListener);
    }

    @Override
    public void setContentView(int viewId) {
        super.setContentView(R.layout.abstract_activity);
        ViewGroup rootView = findViewById(R.id.content_frame);

        if (viewId != 0) {
            LayoutInflater layoutInflater = getLayoutInflater();
            layoutInflater.inflate(viewId, rootView);
        }

        drawerList = findViewById(R.id.left_drawer);
        drawerList.setNavigationItemSelectedListener(menuItem -> {
            if (showingTabs) {
                // Settings are on a different selectable track
                if (menuItem.getItemId() != R.id.drawer_settings && menuItem.getItemId() != R.id.drawer_offline) {
                    menuItem.setChecked(true);
                    lastSelectedPosition = menuItem.getItemId();
                }

                switch (menuItem.getItemId()) {
                    case R.id.drawer_library:
                        drawerItemSelected("Artist");
                        return true;
                    case R.id.drawer_playlists:
                        drawerItemSelected("Playlist");
                        return true;
                    case R.id.drawer_downloading:
                        drawerItemSelected("Download");
                        return true;
                    case R.id.drawer_offline:
                        toggleOffline();
                        return true;
                    case R.id.drawer_settings:
                        startActivity(new Intent(SubsonicActivity.this, SettingsActivity.class));
                        drawer.closeDrawers();
                        return true;
                }
            } else {
                int activeServer = menuItem.getItemId() - MENU_ITEM_SERVER_BASE;
                SubsonicActivity.this.setActiveServer(activeServer);
                populateTabs();
                return true;
            }

            return false;
        });

        drawerHeader = drawerList.inflateHeaderView(R.layout.drawer_header);
        drawerHeader.setOnClickListener(v -> {
            if (showingTabs) {
                populateServers();
            } else {
                populateTabs();
            }
        });

        drawerHeaderToggle = drawerHeader.findViewById(R.id.header_select_image);
        drawerServerName = drawerHeader.findViewById(R.id.header_server_name);
        drawerUserName = drawerHeader.findViewById(R.id.header_user_name);

        updateDrawerHeader();

        drawer = findViewById(R.id.drawer_layout);

        // Pass in toolbar if it exists
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.common_appname, R.string.common_appname) {
            @Override
            public void onDrawerClosed(View view) {
                drawerIdle = true;
                drawerOpen = false;

                if (!showingTabs) {
                    populateTabs();
                }
            }

            @Override
            public void onDrawerOpened(View view) {
                DownloadService downloadService = getDownloadService();
                boolean downloadingVisible = downloadService != null && !downloadService.getBackgroundDownloads().isEmpty();
                if (lastSelectedPosition == R.id.drawer_downloading) {
                    downloadingVisible = true;
                }
                setDrawerItemVisible(downloadingVisible);

                drawerIdle = true;
                drawerOpen = true;
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                drawerIdle = false;
            }
        };
        drawer.addDrawerListener(drawerToggle);
        drawerToggle.setDrawerIndicatorEnabled(true);

        drawer.setOnTouchListener((v, event) -> drawerIdle && currentFragment != null && currentFragment.getGestureDetector() != null && currentFragment.getGestureDetector().onTouchEvent(event));

        // Check whether this is a tablet or not
        secondaryContainer = findViewById(R.id.fragment_second_container);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        String[] ids = new String[backStack.size() + 1];
        ids[0] = currentFragment.getTag();
        int i = 1;
        for (SubsonicFragment frag : backStack) {
            ids[i] = frag.getTag();
            i++;
        }
        savedInstanceState.putStringArray(Constants.MAIN_BACK_STACK, ids);
        savedInstanceState.putInt(Constants.MAIN_BACK_STACK_SIZE, backStack.size() + 1);
        savedInstanceState.putInt(Constants.FRAGMENT_POSITION, lastSelectedPosition);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int size = savedInstanceState.getInt(Constants.MAIN_BACK_STACK_SIZE);
        String[] ids = savedInstanceState.getStringArray(Constants.MAIN_BACK_STACK);
        FragmentManager fm = getSupportFragmentManager();
        currentFragment = (SubsonicFragment) fm.findFragmentByTag(ids[0]);
        currentFragment.setPrimaryFragment(true);
        currentFragment.setSupportTag(ids[0]);
        supportInvalidateOptionsMenu();
        FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
        for (int i = 1; i < size; i++) {
            SubsonicFragment frag = (SubsonicFragment) fm.findFragmentByTag(ids[i]);
            frag.setSupportTag(ids[i]);
            if (secondaryContainer != null) {
                frag.setPrimaryFragment(false, true);
            }
            trans.hide(frag);
            backStack.add(frag);
        }
        trans.commit();

        // Current fragment is hidden in secondaryContainer
        if (secondaryContainer == null && !currentFragment.isVisible()) {
            trans = getSupportFragmentManager().beginTransaction();
            trans.remove(currentFragment);
            trans.commit();
            getSupportFragmentManager().executePendingTransactions();

            trans = getSupportFragmentManager().beginTransaction();
            trans.add(R.id.fragment_container, currentFragment, ids[0]);
            trans.commit();
        }
        // Current fragment needs to be moved over to secondaryContainer
        else if (secondaryContainer != null && secondaryContainer.findViewById(currentFragment.getRootId()) == null && backStack.size() > 0) {
            trans = getSupportFragmentManager().beginTransaction();
            trans.remove(currentFragment);
            trans.show(backStack.get(backStack.size() - 1));
            trans.commit();
            getSupportFragmentManager().executePendingTransactions();

            trans = getSupportFragmentManager().beginTransaction();
            trans.add(R.id.fragment_second_container, currentFragment, ids[0]);
            trans.commit();

            secondaryContainer.setVisibility(View.VISIBLE);
        }

        lastSelectedPosition = savedInstanceState.getInt(Constants.FRAGMENT_POSITION);
        if (lastSelectedPosition != 0) {
            MenuItem item = drawerList.getMenu().findItem(lastSelectedPosition);
            if (item != null) {
                item.setChecked(true);
            }
        }
        recreateSpinner();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        SubsonicFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            try {
                SubsonicFragment fragment = getCurrentFragment();
                fragment.onCreateOptionsMenu(menu, menuInflater);

                if (isTouchscreen()) {
                    menu.setGroupVisible(R.id.not_touchscreen, false);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error on creating options menu", e);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return getCurrentFragment().onOptionsItemSelected(item);
    }

    @Override
    public void setTitle(CharSequence title) {
        if (title != null && getSupportActionBar() != null && !title.equals(getSupportActionBar().getTitle())) {
            getSupportActionBar().setTitle(title);
            recreateSpinner();
        }
    }

    public void setSubtitle(CharSequence title) {
        getSupportActionBar().setSubtitle(title);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int top = spinnerAdapter.getCount() - 1;
        if (position < top) {
            for (int i = top; i > position && i >= 0; i--) {
                removeCurrent();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private void populateTabs() {
        drawerList.getMenu().clear();
        drawerList.inflateMenu(R.menu.drawer_navigation);

        MenuItem offlineMenuItem = drawerList.getMenu().findItem(R.id.drawer_offline);
        if (Util.isOffline(this)) {
            if (lastSelectedPosition == 0 || lastSelectedPosition == R.id.drawer_library) {
                String newFragment = "Artist";
                lastSelectedPosition = getDrawerItemId(newFragment);
                drawerItemSelected(newFragment);
            }

            offlineMenuItem.setTitle(R.string.main_online);
        } else {
            offlineMenuItem.setTitle(R.string.main_offline);
        }

        if (lastSelectedPosition != 0) {
            MenuItem item = drawerList.getMenu().findItem(lastSelectedPosition);
            if (item != null) {
                item.setChecked(true);
            }
        }
        drawerHeaderToggle.setImageResource(R.drawable.main_select_server);

        showingTabs = true;
    }

    private void populateServers() {
        drawerList.getMenu().clear();

        int serverCount = Util.getServerCount(this);
        int activeServer = Util.getActiveServer(this);
        for (int i = 1; i <= serverCount; i++) {
            MenuItem item = drawerList.getMenu().add(MENU_GROUP_SERVER, MENU_ITEM_SERVER_BASE + i, MENU_ITEM_SERVER_BASE + i, Util.getServerName(this, i));
            if (activeServer == i) {
                item.setChecked(true);
            }
        }
        drawerList.getMenu().setGroupCheckable(MENU_GROUP_SERVER, true, true);
        drawerHeaderToggle.setImageResource(R.drawable.main_select_tabs);

        showingTabs = false;
    }

    private void setDrawerItemVisible(boolean visible) {
        MenuItem item = drawerList.getMenu().findItem(R.id.drawer_downloading);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    void drawerItemSelected(String fragmentType) {
        if (currentFragment != null) {
            currentFragment.stopActionMode();
        }
        startFragmentActivity(fragmentType);
    }

    void startFragmentActivity(String fragmentType) {
        Intent intent = new Intent();
        intent.setClass(SubsonicActivity.this, SubsonicFragmentActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (!"".equals(fragmentType)) {
            intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, fragmentType);
        }
        if (lastSelectedPosition != 0) {
            intent.putExtra(Constants.FRAGMENT_POSITION, lastSelectedPosition);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            drawer.closeDrawers();
        } else if (backStack.size() > 0) {
            removeCurrent();
        } else {
            super.onBackPressed();
        }
    }

    public SubsonicFragment getCurrentFragment() {
        return this.currentFragment;
    }

    void replaceFragment(SubsonicFragment fragment, int tag) {
        replaceFragment(fragment, tag, false);
    }

    public void replaceFragment(SubsonicFragment fragment, int tag, boolean replaceCurrent) {
        SubsonicFragment oldFragment = currentFragment;
        if (currentFragment != null) {
            currentFragment.setPrimaryFragment(false, secondaryContainer != null);
        }
        backStack.add(currentFragment);

        currentFragment = fragment;
        currentFragment.setPrimaryFragment(true);
        supportInvalidateOptionsMenu();

        if (secondaryContainer == null || oldFragment.isAlwaysFullscreen() || currentFragment.isAlwaysStartFullscreen()) {
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
            trans.hide(oldFragment);
            trans.add(R.id.fragment_container, fragment, tag + "");
            trans.commit();
        } else {
            // Make sure secondary container is visible now
            secondaryContainer.setVisibility(View.VISIBLE);

            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();

            // Check to see if you need to put on top of old left or not
            if (backStack.size() > 1) {
                // Move old right to left if there is a backstack already
                SubsonicFragment newLeftFragment = backStack.get(backStack.size() - 1);
                trans.remove(newLeftFragment);

                // Only move right to left if replaceCurrent is false
                if (!replaceCurrent) {
                    SubsonicFragment oldLeftFragment = backStack.get(backStack.size() - 2);
                    oldLeftFragment.setSecondaryFragment(false);
                    // trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
                    trans.hide(oldLeftFragment);

                    // Make sure remove is finished before adding
                    trans.commit();
                    getSupportFragmentManager().executePendingTransactions();

                    trans = getSupportFragmentManager().beginTransaction();
                    // trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
                    trans.add(R.id.fragment_container, newLeftFragment, newLeftFragment.getSupportTag() + "");
                } else {
                    backStack.remove(backStack.size() - 1);
                }
            }

            // Add fragment to the right container
            trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
            trans.add(R.id.fragment_second_container, fragment, tag + "");

            // Commit it all
            trans.commit();

            oldFragment.setIsOnlyVisible(false);
            currentFragment.setIsOnlyVisible(false);
        }
        recreateSpinner();
    }

    public void removeCurrent() {
        // Don't try to remove current if there is no backstack to remove from
        if (backStack.isEmpty()) {
            return;
        }

        if (currentFragment != null) {
            currentFragment.setPrimaryFragment(false);
        }
        SubsonicFragment oldFragment = currentFragment;

        currentFragment = backStack.remove(backStack.size() - 1);
        currentFragment.setPrimaryFragment(true, false);
        supportInvalidateOptionsMenu();

        if (secondaryContainer == null || currentFragment.isAlwaysFullscreen() || oldFragment.isAlwaysStartFullscreen()) {
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);
            trans.remove(oldFragment);
            trans.show(currentFragment);
            trans.commit();
        } else {
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();

            // Remove old right fragment
            trans.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);
            trans.remove(oldFragment);

            // Only switch places if there is a backstack, otherwise primary container is correct
            if (backStack.size() > 0 && !backStack.get(backStack.size() - 1).isAlwaysFullscreen() && !currentFragment.isAlwaysStartFullscreen()) {
                trans.setCustomAnimations(0, 0, 0, 0);
                // Add current left fragment to right side
                trans.remove(currentFragment);

                // Make sure remove is finished before adding
                trans.commit();
                getSupportFragmentManager().executePendingTransactions();

                trans = getSupportFragmentManager().beginTransaction();
                // trans.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);
                trans.add(R.id.fragment_second_container, currentFragment, currentFragment.getSupportTag() + "");

                SubsonicFragment newLeftFragment = backStack.get(backStack.size() - 1);
                newLeftFragment.setSecondaryFragment(true);
                trans.show(newLeftFragment);
            } else {
                secondaryContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.exit_to_right));
                secondaryContainer.setVisibility(View.GONE);

                currentFragment.setIsOnlyVisible(true);
            }

            trans.commit();
        }
        recreateSpinner();
    }

    public void invalidate() {
        if (currentFragment != null) {
            while (backStack.size() > 0) {
                removeCurrent();
            }

            currentFragment.invalidate();
            populateTabs();
        }

        supportInvalidateOptionsMenu();
    }

    void recreateSpinner() {
        if (currentFragment == null || currentFragment.getTitle() == null) {
            return;
        }
        if (spinnerAdapter == null || getSupportActionBar().getCustomView() == null) {
            createCustomActionBarView();
        }

        if (backStack.size() > 0) {
            createCustomActionBarView();
            spinnerAdapter.clear();
            for (int i = 0; i < backStack.size(); i++) {
                CharSequence title = backStack.get(i).getTitle();
                if (title != null) {
                    spinnerAdapter.add(title);
                } else {
                    spinnerAdapter.add("null");
                }
            }
            if (currentFragment.getTitle() != null) {
                spinnerAdapter.add(currentFragment.getTitle());
            } else {
                spinnerAdapter.add("null");
            }
            spinnerAdapter.notifyDataSetChanged();
            actionBarSpinner.setSelection(spinnerAdapter.getCount() - 1);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayShowCustomEnabled(true);

            if (drawerToggle.isDrawerIndicatorEnabled()) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                drawerToggle.setDrawerIndicatorEnabled(false);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        } else {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(currentFragment.getTitle());
            getSupportActionBar().setDisplayShowCustomEnabled(false);
            drawerToggle.setDrawerIndicatorEnabled(true);
        }
    }

    private void restart() {
        Intent intent = new Intent(this, this.getClass());
        intent.putExtras(getIntent());
        intent.putExtra(Constants.FRAGMENT_POSITION, lastSelectedPosition);
        finish();
        Util.startActivityWithoutTransition(this, intent);
    }

    private void applyTheme() {
        theme = ThemeUtil.getTheme(this);

        if (theme != null && theme.contains("fullscreen")) {
            theme = theme.substring(0, theme.indexOf("_fullscreen"));
            ThemeUtil.setTheme(this, theme);
        }

        ThemeUtil.applyTheme(this, theme);
    }

    private void applyFullscreen() {
        fullScreen = Util.getPreferences(this).getBoolean(Constants.PREFERENCES_KEY_FULL_SCREEN, false);
        if (fullScreen) {
            int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            getWindow().getDecorView().setSystemUiVisibility(flags);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public boolean isDestroyedCompat() {
        return destroyed;
    }

    public synchronized ImageLoader getImageLoader() {
        if (IMAGE_LOADER == null) {
            IMAGE_LOADER = new ImageLoader(this);
        }
        return IMAGE_LOADER;
    }

    public DownloadService getDownloadService() {
        // If service is not available, request it to start and wait for it.
        for (int i = 0; i < 5; i++) {
            DownloadService downloadService = DownloadService.getInstance();
            if (downloadService != null) {
                break;
            }
            Log.w(TAG, "DownloadService not running. Attempting to start it.");
            startService(new Intent(this, DownloadService.class));
            Util.sleepQuietly(50L);
        }

        final DownloadService downloadService = DownloadService.getInstance();
        if (downloadService != null && afterServiceAvailable.size() > 0) {
            for (Runnable runnable : afterServiceAvailable) {
                handler.post(runnable);
            }
            afterServiceAvailable.clear();
        }
        return downloadService;
    }

    public void runWhenServiceAvailable(Runnable runnable) {
        if (getDownloadService() != null) {
            runnable.run();
        } else {
            afterServiceAvailable.add(runnable);
            checkIfServiceAvailable();
        }
    }

    private void checkIfServiceAvailable() {
        if (getDownloadService() == null) {
            handler.postDelayed(this::checkIfServiceAvailable, 50);
        } else if (afterServiceAvailable.size() > 0) {
            for (Runnable runnable : afterServiceAvailable) {
                handler.post(runnable);
            }
            afterServiceAvailable.clear();
        }
    }

    public boolean isTouchscreen() {
        return touchscreen;
    }

    public void openNowPlaying() {

    }

    public void closeNowPlaying() {

    }

    private void setActiveServer(int instance) {
        if (Util.getActiveServer(this) != instance) {
            final DownloadService service = getDownloadService();
            if (service != null) {
                new SilentBackgroundTask<Void>(this) {
                    @Override
                    protected Void doInBackground() {
                        service.clearIncomplete();
                        return null;
                    }
                }.execute();

            }
            Util.setActiveServer(this, instance);
            invalidate();
            updateDrawerHeader();
        }
    }

    private void updateDrawerHeader() {
        if (Util.isOffline(this)) {
            drawerServerName.setText(R.string.select_album_offline);
            drawerUserName.setText("");
            drawerHeader.setClickable(false);
            drawerHeaderToggle.setVisibility(View.GONE);
        } else {
            drawerServerName.setText(Util.getServerName(this));
            drawerUserName.setText(UserUtil.getCurrentUsername(this));
            drawerHeader.setClickable(true);
            drawerHeaderToggle.setVisibility(View.VISIBLE);
        }
    }

    private void toggleOffline() {
        boolean isOffline = Util.isOffline(this);
        Util.setOffline(this, !isOffline);
        invalidate();
        DownloadService service = getDownloadService();
        if (service != null) {
            service.setOnline(isOffline);
        }

        this.updateDrawerHeader();
        drawer.closeDrawers();
    }

    int getDrawerItemId(String fragmentType) {
        if (fragmentType == null) {
            return R.id.drawer_library;
        }

        switch (fragmentType) {
            case "Artist":
                return R.id.drawer_library;
            case "Playlist":
                return R.id.drawer_playlists;
            default:
                return R.id.drawer_library;
        }
    }
}
