package net.nullsum.audinaut.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.adapter.MainAdapter;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.UpdateView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainFragment extends SelectRecyclerFragment<Integer> {
    public static final String SONGS_LIST_PREFIX = "songs-";
    public static final String SONGS_NEWEST = SONGS_LIST_PREFIX + "newest";
    public static final String SONGS_TOP_PLAYED = SONGS_LIST_PREFIX + "topPlayed";
    public static final String SONGS_RECENT = SONGS_LIST_PREFIX + "recent";
    public static final String SONGS_FREQUENT = SONGS_LIST_PREFIX + "frequent";

    public MainFragment() {
        super();
        pullToRefresh = false;
        serialize = false;
        backgroundUpdate = false;
        alwaysFullscreen = true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.main, menu);
        onFinishSetupOptionsMenu(menu);
    }

    @Override
    public int getOptionsMenu() {
        return 0;
    }

    @Override
    public SectionAdapter getAdapter(List objs) {
        List<List<Integer>> sections = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        List<Integer> albums = new ArrayList<>();
        albums.add(R.string.main_albums_random);
        albums.add(R.string.main_albums_alphabetical);
        albums.add(R.string.main_albums_genres);
        albums.add(R.string.main_albums_year);
        albums.add(R.string.main_albums_recent);
        albums.add(R.string.main_albums_frequent);

        sections.add(albums);
        headers.add("albums");

        return new MainAdapter(context, headers, sections, this);
    }

    @Override
    public List<Integer> getObjects(MusicService musicService, boolean refresh, ProgressListener listener) {
        return Collections.singletonList(0);
    }

    @Override
    public int getTitleResource() {
        return R.string.common_appname;
    }

    private void showAlbumList(String type) {
        switch (type) {
            case "genres": {
                SubsonicFragment fragment = new SelectGenreFragment();
                replaceFragment(fragment);
                break;
            }
            case "years": {
                SubsonicFragment fragment = new SelectYearFragment();
                replaceFragment(fragment);
                break;
            }
            default: {
                // Clear out recently added count when viewing
                if ("newest".equals(type)) {
                    SharedPreferences.Editor editor = Util.getPreferences(context).edit();
                    editor.putInt(Constants.PREFERENCES_KEY_RECENT_COUNT + Util.getActiveServer(context), 0);
                    editor.apply();
                }

                SubsonicFragment fragment = new SelectDirectoryFragment();
                Bundle args = new Bundle();
                args.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 20);
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
                fragment.setArguments(args);

                replaceFragment(fragment);
                break;
            }
        }
    }

    @Override
    public void onItemClicked(UpdateView<Integer> updateView, Integer item) {
        switch (item) {
            case R.string.main_albums_random:
                showAlbumList("random");
                break;
            case R.string.main_albums_recent:
                showAlbumList("recent");
                break;
            case R.string.main_albums_frequent:
                showAlbumList("frequent");
                break;
            case R.string.main_albums_genres:
                showAlbumList("genres");
                break;
            case R.string.main_albums_year:
                showAlbumList("years");
                break;
            case R.string.main_albums_alphabetical:
                showAlbumList("alphabeticalByName");
                break;
            case R.string.main_songs_newest:
                showAlbumList(SONGS_NEWEST);
                break;
            case R.string.main_songs_top_played:
                showAlbumList(SONGS_TOP_PLAYED);
                break;
            case R.string.main_songs_recent:
                showAlbumList(SONGS_RECENT);
                break;
            case R.string.main_songs_frequent:
                showAlbumList(SONGS_FREQUENT);
                break;
        }
    }

    @Override
    public void onCreateContextMenu(Menu menu, MenuInflater menuInflater, UpdateView<Integer> updateView, Integer item) {
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem, UpdateView<Integer> updateView, Integer item) {
        return false;
    }
}
