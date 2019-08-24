package net.nullsum.audinaut.util;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.fragments.SubsonicFragment;
import net.nullsum.audinaut.service.DownloadFile;
import net.nullsum.audinaut.service.DownloadService;
import net.nullsum.audinaut.view.SongView;
import net.nullsum.audinaut.view.UpdateView;

import java.util.ArrayDeque;
import java.util.Deque;

public class DownloadFileItemHelperCallback extends ItemTouchHelper.SimpleCallback {

    private final SubsonicFragment fragment;
    private final boolean mainList;
    private final Deque pendingOperations = new ArrayDeque();
    private BackgroundTask pendingTask = null;

    public DownloadFileItemHelperCallback(SubsonicFragment fragment, boolean mainList) {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.fragment = fragment;
        this.mainList = mainList;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder fromHolder, RecyclerView.ViewHolder toHolder) {
        int from = fromHolder.getAdapterPosition();
        int to = toHolder.getAdapterPosition();
        getSectionAdapter().moveItem(from, to);

        synchronized (pendingOperations) {
            pendingOperations.add(new Pair<>(from, to));
            updateDownloadService();
        }
        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        SongView songView = (SongView) ((UpdateView.UpdateViewHolder) viewHolder).getUpdateView();
        DownloadFile downloadFile = songView.getDownloadFile();

        getSectionAdapter().removeItem(downloadFile);
        synchronized (pendingOperations) {
            pendingOperations.add(downloadFile);
            updateDownloadService();
        }
    }

    private DownloadService getDownloadService() {
        return fragment.getDownloadService();
    }

    private SectionAdapter getSectionAdapter() {
        return fragment.getCurrentAdapter();
    }

    private void updateDownloadService() {
        if (pendingTask == null) {
            final DownloadService downloadService = getDownloadService();
            if (downloadService == null) {
                return;
            }

            pendingTask = new SilentBackgroundTask<Void>(downloadService) {
                @Override
                protected Void doInBackground() {
                    boolean running = true;
                    while (running) {
                        Object nextOperation = null;
                        synchronized (pendingOperations) {
                            if (!pendingOperations.isEmpty()) {
                                nextOperation = pendingOperations.remove();
                            }
                        }

                        if (nextOperation != null) {
                            if (nextOperation instanceof Pair) {
                                Pair<Integer, Integer> swap = (Pair) nextOperation;
                                downloadService.swap(mainList, swap.getFirst(), swap.getSecond());
                            } else if (nextOperation instanceof DownloadFile) {
                                DownloadFile downloadFile = (DownloadFile) nextOperation;
                                if (mainList) {
                                    downloadService.remove(downloadFile);
                                } else {
                                    downloadService.removeBackground(downloadFile);
                                }
                            }
                        } else {
                            running = false;
                        }
                    }

                    synchronized (pendingOperations) {
                        pendingTask = null;

                        // Start a task if this is non-empty.  Means someone added while we were running operations
                        if (!pendingOperations.isEmpty()) {
                            updateDownloadService();
                        }
                    }
                    return null;
                }
            };
            pendingTask.execute();
        }
    }
}
