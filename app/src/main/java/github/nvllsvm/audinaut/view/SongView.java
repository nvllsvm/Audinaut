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
package github.nvllsvm.audinaut.view;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import github.nvllsvm.audinaut.R;
import github.nvllsvm.audinaut.domain.MusicDirectory;
import github.nvllsvm.audinaut.service.DownloadService;
import github.nvllsvm.audinaut.service.DownloadFile;
import github.nvllsvm.audinaut.util.DrawableTint;
import github.nvllsvm.audinaut.util.SongDBHandler;
import github.nvllsvm.audinaut.util.ThemeUtil;
import github.nvllsvm.audinaut.util.Util;

import java.io.File;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView2<MusicDirectory.Entry, Boolean> {
	private static final String TAG = SongView.class.getSimpleName();

	private TextView trackTextView;
	private TextView titleTextView;
	private TextView playingTextView;
	private TextView artistTextView;
	private TextView durationTextView;
	private TextView statusTextView;
	private ImageView statusImageView;
	private ImageView playedButton;
	private View bottomRowView;

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
	private boolean isPlayed = false;
	private boolean isPlayedShown = false;
	private boolean showAlbum = false;

	public SongView(Context context) {
		super(context);
		LayoutInflater.from(context).inflate(R.layout.song_list_item, this, true);

		trackTextView = (TextView) findViewById(R.id.song_track);
		titleTextView = (TextView) findViewById(R.id.song_title);
		artistTextView = (TextView) findViewById(R.id.song_artist);
		durationTextView = (TextView) findViewById(R.id.song_duration);
		statusTextView = (TextView) findViewById(R.id.song_status);
		statusImageView = (ImageView) findViewById(R.id.song_status_icon);
		playedButton = (ImageButton) findViewById(R.id.song_played);
		moreButton = (ImageView) findViewById(R.id.item_more);
		bottomRowView = findViewById(R.id.song_bottom);
	}

	public void setObjectImpl(MusicDirectory.Entry song, Boolean checkable) {
		this.checkable = checkable;

		StringBuilder artist = new StringBuilder(40);

        if(showAlbum) {
            artist.append(song.getAlbum());
        } else {
            artist.append(song.getArtist());
        }

        durationTextView.setText(Util.formatDuration(song.getDuration()));
        bottomRowView.setVisibility(View.VISIBLE);

		String title = song.getTitle();
		Integer track = song.getTrack();
		TextView newPlayingTextView;
		if(track != null && Util.getDisplayTrack(context)) {
			trackTextView.setText(String.format("%02d", track));
			trackTextView.setVisibility(View.VISIBLE);
			newPlayingTextView = trackTextView;
		} else {
			trackTextView.setVisibility(View.GONE);
			newPlayingTextView = titleTextView;
		}

		if(newPlayingTextView != playingTextView || playingTextView == null) {
			if(playing) {
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

	public void setDownloadFile(DownloadFile downloadFile) {
		this.downloadFile = downloadFile;
		dontChangeDownloadFile = true;
	}

	public DownloadFile getDownloadFile() {
		return downloadFile;
	}

	@Override
	protected void updateBackground() {
		if (downloadService == null) {
			downloadService = DownloadService.getInstance();
			if(downloadService == null) {
				return;
			}
		}

		long newRevision = downloadService.getDownloadListUpdateRevision();
		if((revision != newRevision && dontChangeDownloadFile == false) || downloadFile == null) {
			downloadFile = downloadService.forSong(item);
			revision = newRevision;
		}

		isWorkDone = downloadFile.isWorkDone();
		isSaved = downloadFile.isSaved();
		partialFile = downloadFile.getPartialFile();
		partialFileExists = partialFile.exists();

		// Check if needs to load metadata: check against all fields that we know are null in offline mode
		if(item.getBitRate() == null && item.getDuration() == null && item.getDiscNumber() == null && isWorkDone) {
			item.loadMetadata(downloadFile.getCompleteFile());
			loaded = true;
		}
	}

	@Override
	protected void update() {
		if(loaded) {
			setObjectImpl(item, item2);
		}
		if (downloadService == null || downloadFile == null) {
			return;
		}

		if (isWorkDone) {
			int moreImage = isSaved ? R.drawable.download_pinned : R.drawable.download_cached;
			if(moreImage != this.moreImage) {
				moreButton.setImageResource(moreImage);
				this.moreImage = moreImage;
			}
		} else if(this.moreImage != R.drawable.download_none_light) {
			moreButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.download_none));
			this.moreImage = R.drawable.download_none_light;
		}

		if (downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && partialFileExists) {
			double percentage = (partialFile.length() * 100.0) / downloadFile.getEstimatedSize();
			percentage = Math.min(percentage, 100);
			statusTextView.setText((int)percentage + " %");
			if(!rightImage) {
				statusImageView.setVisibility(View.VISIBLE);
				rightImage = true;
			}
		} else if(rightImage) {
			statusTextView.setText(null);
			statusImageView.setVisibility(View.GONE);
			rightImage = false;
		}

		boolean playing = Util.equals(downloadService.getCurrentPlaying(), downloadFile);
		if (playing) {
			if(!this.playing) {
				this.playing = playing;
				playingTextView.setCompoundDrawablesWithIntrinsicBounds(DrawableTint.getDrawableRes(context, R.attr.playing), 0, 0, 0);
			}
		} else {
			if(this.playing) {
				this.playing = playing;
				playingTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
			}
		}

		if(isPlayed) {
			if(!isPlayedShown) {
				if(playedButton.getDrawable() == null) {
					playedButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_toggle_played));
				}

				playedButton.setVisibility(View.VISIBLE);
				isPlayedShown = true;
			}
		} else {
			if(isPlayedShown) {
				playedButton.setVisibility(View.GONE);
				isPlayedShown = false;
			}
		}
	}

	public MusicDirectory.Entry getEntry() {
		return item;
	}

	public void setShowAlbum(boolean showAlbum) {
		this.showAlbum = showAlbum;
	}
}
