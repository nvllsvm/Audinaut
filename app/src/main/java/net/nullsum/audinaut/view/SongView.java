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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.util.DrawableTint;
import net.nullsum.audinaut.util.Util;

import java.io.File;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView2<MusicDirectory.Entry, Boolean> {

    private final TextView trackTextView;
    private final TextView titleTextView;
    private final TextView artistTextView;
    private final TextView durationTextView;
    private final TextView statusTextView;
    private final ImageView statusImageView;
    private final ImageView playedButton;
    private final View bottomRowView;
    private TextView playingTextView;
    private DownloadService downloadService;
    private long revision = -1;
    private DownloadFile downloadFile;
    private boolean dontChangeDownloadFile = false;

    private boolean playing = false;
    private boolean rightImage = false;
    private int moreImage = 0;
    private boolean isWorkDone = false;
    private boolean isSaved = false;
    private File partialFile;
    private boolean partialFileExists = false;
    private boolean loaded = false;
    private boolean isPlayedShown = false;
    private boolean showAlbum = false;

    public SongView(Context context) {
        super(context, true);
        LayoutInflater.from(context).inflate(R.layout.song_list_item, this, true);

        trackTextView = findViewById(R.id.song_track);
        titleTextView = findViewById(R.id.song_title);
        artistTextView = findViewById(R.id.song_artist);
        durationTextView = findViewById(R.id.song_duration);
        statusTextView = findViewById(R.id.song_status);
        statusImageView = findViewById(R.id.song_status_icon);
        playedButton = (ImageButton) findViewById(R.id.song_played);
        moreButton = findViewById(R.id.item_more);
        bottomRowView = findViewById(R.id.song_bottom);
    }

    protected void setObjectImpl(MusicDirectory.Entry song, Boolean checkable) {
        this.checkable = checkable;

        StringBuilder artist = new StringBuilder(40);

        if (showAlbum) {
            artist.append(song.getAlbum());
        } else {
            artist.append(song.getArtist());
        }

        durationTextView.setText(Util.formatDuration(song.getDuration()));
        bottomRowView.setVisibility(View.VISIBLE);

        String title = song.getTitle();
        Integer track = song.getTrack();
        TextView newPlayingTextView;
        if (track != null && Util.getDisplayTrack(context)) {
            trackTextView.setText(String.format("%02d", track));
            trackTextView.setVisibility(View.VISIBLE);
            newPlayingTextView = trackTextView;
        } else {
            trackTextView.setVisibility(View.GONE);
            newPlayingTextView = titleTextView;
        }

        if (newPlayingTextView != playingTextView || playingTextView == null) {
            if (playing) {
                playingTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                playing = false;
            }

            playingTextView = newPlayingTextView;
        }

        titleTextView.setText(title);
        artistTextView.setText(artist);

        this.setBackgroundColor(0x00000000);

        revision = -1;
        loaded = false;
        dontChangeDownloadFile = false;
    }

    public DownloadFile getDownloadFile() {
        return downloadFile;
    }

    public void setDownloadFile(DownloadFile downloadFile) {
        this.downloadFile = downloadFile;
        dontChangeDownloadFile = true;
    }

    @Override
    protected void updateBackground() {
        if (downloadService == null) {
            downloadService = DownloadService.getInstance();
            if (downloadService == null) {
                return;
            }
        }

        long newRevision = downloadService.getDownloadListUpdateRevision();
        if ((revision != newRevision && !dontChangeDownloadFile) || downloadFile == null) {
            downloadFile = downloadService.forSong(item);
            revision = newRevision;
        }

        isWorkDone = downloadFile.isWorkDone();
        isSaved = downloadFile.isSaved();
        partialFile = downloadFile.getPartialFile();
        partialFileExists = partialFile.exists();

        // Check if needs to load metadata: check against all fields that we know are null in offline mode
        if (item.getBitRate() == null && item.getDuration() == null && item.getDiscNumber() == null && isWorkDone) {
            item.loadMetadata(downloadFile.getCompleteFile());
            loaded = true;
        }
    }

    @Override
    protected void update() {
        if (loaded) {
            setObjectImpl(item, item2);
        }
        if (downloadService == null || downloadFile == null) {
            return;
        }

        if (isWorkDone) {
            int moreImage = isSaved ? R.drawable.download_pinned : R.drawable.download_cached;
            if (moreImage != this.moreImage) {
                moreButton.setImageResource(moreImage);
                this.moreImage = moreImage;
            }
        } else if (this.moreImage != R.drawable.download_none_light) {
            moreButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.download_none));
            this.moreImage = R.drawable.download_none_light;
        }

        if (downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && partialFileExists) {
            double percentage = (partialFile.length() * 100.0) / downloadFile.getEstimatedSize();
            percentage = Math.min(percentage, 100);
            statusTextView.setText((int) percentage + " %");
            if (!rightImage) {
                statusImageView.setVisibility(View.VISIBLE);
                rightImage = true;
            }
        } else if (rightImage) {
            statusTextView.setText(null);
            statusImageView.setVisibility(View.GONE);
            rightImage = false;
        }

        boolean playing = Util.equals(downloadService.getCurrentPlaying(), downloadFile);
        if (playing) {
            if (!this.playing) {
                this.playing = true;
                playingTextView.setCompoundDrawablesWithIntrinsicBounds(DrawableTint.getDrawableRes(context, R.attr.playing), 0, 0, 0);
            }
        } else {
            if (this.playing) {
                this.playing = false;
                playingTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        }

        if (isPlayedShown) {
            playedButton.setVisibility(View.GONE);
            isPlayedShown = false;
        }
    }

    public MusicDirectory.Entry getEntry() {
        return item;
    }

    public void setShowAlbum(boolean showAlbum) {
        this.showAlbum = showAlbum;
    }
}
