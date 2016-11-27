/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.*;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Finger;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.Peer;
import com.frostwire.android.gui.adapters.menu.FileGridAdapter;
import com.frostwire.android.gui.adapters.menu.FileListAdapter;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.BrowsePeerSearchBarView;
import com.frostwire.android.gui.views.BrowsePeerSearchBarView.OnActionListener;
import com.frostwire.android.gui.views.FileTypeRadioButtonSelectorFactory;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class BrowsePeerFragment extends AbstractFragment implements LoaderCallbacks<Object>, MainFragment {
    private static final Logger LOG = Logger.getLogger(BrowsePeerFragment.class);
    private static final int LOADER_FILES_ID = 0;
    private final BroadcastReceiver broadcastReceiver;
    private BrowsePeerSearchBarView filesBar;
    private SwipeRefreshLayout swipeRefreshList;
    private SwipeRefreshLayout swipeRefreshGrid;
    private ListView list;
    private byte currentFileType;
    private FileListAdapter adapter;
    private GridView grid;
    private FileGridAdapter gridAdapter;
    private Peer peer;
    private View header;
    private long lastAdapterRefresh;
    private String previousFilter;
    private Set<FileListAdapter.FileDescriptorItem> previouslyChecked;

    // given the byte:fileType as the index, this array will match the corresponding UXAction code.
    // no if's necessary, random access -> O(1)
    private final int[] browseUXActions = {
            UXAction.LIBRARY_BROWSE_FILE_TYPE_AUDIO,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_PICTURES,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_VIDEOS,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_DOCUMENTS,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_APPLICATIONS,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_RINGTONES,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_TORRENTS
    };

    private final SparseArray<Byte> toTheRightOf = new SparseArray<>(6);
    private final SparseArray<Byte> toTheLeftOf = new SparseArray<>(6);
    private final Map<Byte, RadioButton> radioButtonFileTypeMap;
    private View swipeLayoutList;
    private View swipeLayoutGrid;

    private boolean isOnAFileTypeWithAlternativeDisplay(){
        return isFileTypeWithAlternativeDisplay(currentFileType);
    }

    private boolean isFileTypeWithAlternativeDisplay(byte fileType) {
        return (Constants.FILE_TYPE_PICTURES == fileType || Constants.FILE_TYPE_VIDEOS == fileType);
    }
    
    public BrowsePeerFragment() {
        super(R.layout.fragment_browse_peer);
        broadcastReceiver = new LocalBroadcastReceiver();
        this.peer = new Peer();

        toTheRightOf.put(Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_RINGTONES);   //0x00 - Audio -> Ringtones
        toTheRightOf.put(Constants.FILE_TYPE_PICTURES, Constants.FILE_TYPE_DOCUMENTS); //0x01 - Pictures -> Documents
        toTheRightOf.put(Constants.FILE_TYPE_VIDEOS, Constants.FILE_TYPE_PICTURES);    //0x02 - Videos -> Pictures
        toTheRightOf.put(Constants.FILE_TYPE_DOCUMENTS, Constants.FILE_TYPE_TORRENTS); //0x03 - Documents -> Torrents
        toTheRightOf.put(Constants.FILE_TYPE_RINGTONES, Constants.FILE_TYPE_VIDEOS);   //0x05 - Ringtones -> Videos
        toTheRightOf.put(Constants.FILE_TYPE_TORRENTS, Constants.FILE_TYPE_AUDIO);     //0x06 - Torrents -> Audio

        toTheLeftOf.put(Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_TORRENTS);     //0x00 - Audio <- Torrents
        toTheLeftOf.put(Constants.FILE_TYPE_PICTURES, Constants.FILE_TYPE_VIDEOS);    //0x01 - Pictures <- Video
        toTheLeftOf.put(Constants.FILE_TYPE_VIDEOS, Constants.FILE_TYPE_RINGTONES);   //0x02 - Videos <- Ringtones
        toTheLeftOf.put(Constants.FILE_TYPE_DOCUMENTS, Constants.FILE_TYPE_PICTURES); //0x03 - Documents <- Pictures
        toTheLeftOf.put(Constants.FILE_TYPE_RINGTONES, Constants.FILE_TYPE_AUDIO);    //0x05 - Ringtones <- Audio
        toTheLeftOf.put(Constants.FILE_TYPE_TORRENTS, Constants.FILE_TYPE_DOCUMENTS); //0x06 - Torrents <- Documents

        radioButtonFileTypeMap = new HashMap<>();  // see initRadioButton(...)
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public Loader<Object> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_FILES_ID && args != null) {
            return createLoaderFiles(args.getByte("fileType"));
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Object> loader, Object data) {
        if (data == null) {
            LOG.warn("Something wrong, data is null");
            return;
        }

        if (loader.getId() == LOADER_FILES_ID) {
            updateFiles((Object[]) data);
        }

        updateHeader();
        if (swipeRefreshList != null) {
            swipeRefreshList.setRefreshing(false);
        }
    }

    @Override
    public void onLoaderReset(Loader<Object> loader) {
    }

    @Override
    public void onResume() {
        super.onResume();
        initBroadcastReceiver();

        restorePreviouslyChecked();
        restorePreviousFilter();
        browseFilesButtonClick(currentFileType);

        updateHeader();
    }

    private void restorePreviouslyChecked() {
        if (previouslyChecked != null && !previouslyChecked.isEmpty()) {
            adapter.setChecked(previouslyChecked);
            gridAdapter.setChecked(previouslyChecked);
        }
    }

    private void restorePreviousFilter() {
        if (previousFilter != null && filesBar != null) {
            filesBar.setText(previousFilter);
        }
    }

    private void savePreviouslyCheckedFileDescriptors() {

        //todo simplify
        if (gridAdapter != null) {
            final Set<FileListAdapter.FileDescriptorItem> checked = gridAdapter.getChecked();
            if (checked != null && !checked.isEmpty()) {
                previouslyChecked = new HashSet<>(checked);
            } else {
                previouslyChecked = null;
            }
        }

        if (adapter != null) {
            final Set<FileListAdapter.FileDescriptorItem> checked = adapter.getChecked();
            if (checked != null && !checked.isEmpty()) {
                previouslyChecked = new HashSet<>(checked);
            } else {
                previouslyChecked = null;
            }
        }
    }

    private void savePreviousFilter() {
        if (!StringUtils.isNullOrEmpty(filesBar.getText())) {
            previousFilter = filesBar.getText();
        }
    }

    private void clearPreviousFilter() {
        previousFilter = null;
    }

    private void initBroadcastReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_REFRESH_FINGER);
        filter.addAction(Constants.ACTION_MEDIA_PLAYER_PLAY);
        filter.addAction(Constants.ACTION_MEDIA_PLAYER_PAUSED);
        filter.addAction(Constants.ACTION_MEDIA_PLAYER_STOPPED);
        filter.addAction(Constants.ACTION_FILE_ADDED_OR_REMOVED);
        filter.addAction(MusicPlaybackService.META_CHANGED);
        filter.addAction(MusicPlaybackService.PLAYSTATE_CHANGED);
        filter.addAction(MusicPlaybackService.SIMPLE_PLAYSTATE_STOPPED);
        getActivity().registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        savePreviouslyCheckedFileDescriptors();
        savePreviousFilter();
        MusicUtils.stopSimplePlayer();
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    @Override
    public View getHeader(Activity activity) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        header = inflater.inflate(R.layout.view_browse_peer_header, null);

        updateHeader();

        return header;
    }

    @Override
    public void onShow() {

    }

    @Override
    protected void initComponents(View v) {
        filesBar = findView(v, R.id.fragment_browse_peer_files_bar);
        filesBar.setOnActionListener(new OnActionListener() {
            public void onCheckAll(View v, boolean isChecked) {
                if (adapter != null) {
                    if (isChecked) {
                        adapter.checkAll();
                    } else {
                        adapter.clearChecked();
                    }
                }
                if (gridAdapter != null) {
                    if (isChecked) {
                        gridAdapter.checkAll();
                    } else {
                        gridAdapter.clearChecked();
                    }
                }
            }

            public void onFilter(View v, String str) {
                if (adapter != null) {
                    adapter.getFilter().filter(str);
                }
                if (gridAdapter != null) {
                    gridAdapter.getFilter().filter(str);
                }
            }

            @Override
            public void onClear() {
                clearPreviousFilter();
            }
        });

        swipeRefreshList = findView(v, R.id.fragment_browse_peer_swipe_refresh);
        swipeRefreshGrid = findView(v, R.id.fragment_browse_peer_swipe_refresh_grid);

        swipeLayoutList = findView(v, R.id.fragment_browse_peer_swipe);
        swipeLayoutGrid = findView(v, R.id.fragment_browse_peer_swipe_grid);


        SwipeRefreshLayout.OnRefreshListener refreshListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                long now = SystemClock.elapsedRealtime();
                if ((now - lastAdapterRefresh) > 5000) {
                    refreshSelection();
                } else {
                    swipeRefreshList.setRefreshing(false);
                    swipeRefreshGrid.setRefreshing(false);
                }
            }
        };


        swipeRefreshList.setOnRefreshListener(refreshListener);
        swipeRefreshGrid.setOnRefreshListener(refreshListener);

        list = findView(v, R.id.fragment_browse_peer_list);
        grid = findView(v, R.id.fragment_browse_peer_grid);

        SwipeLayout swipe = findView(v, R.id.fragment_browse_peer_swipe);
        SwipeLayout swipeGrid = findView(v, R.id.fragment_browse_peer_swipe_grid);

        SwipeLayout.OnSwipeListener onSwipeListener = new SwipeLayout.OnSwipeListener() {
            @Override
            public void onSwipeLeft() {
                switchToThe(true);
            }

            @Override
            public void onSwipeRight() {
                switchToThe(false);
            }
        };

        swipe.setOnSwipeListener(onSwipeListener);
        swipeGrid.setOnSwipeListener(onSwipeListener);

        initRadioButton(v, R.id.fragment_browse_peer_radio_audio, Constants.FILE_TYPE_AUDIO);
        initRadioButton(v, R.id.fragment_browse_peer_radio_ringtones, Constants.FILE_TYPE_RINGTONES);
        initRadioButton(v, R.id.fragment_browse_peer_radio_videos, Constants.FILE_TYPE_VIDEOS);
        initRadioButton(v, R.id.fragment_browse_peer_radio_pictures, Constants.FILE_TYPE_PICTURES);
        initRadioButton(v, R.id.fragment_browse_peer_radio_documents, Constants.FILE_TYPE_DOCUMENTS);
        initRadioButton(v, R.id.fragment_browse_peer_radio_torrents, Constants.FILE_TYPE_TORRENTS);
    }

    private void updateCheckAllStatusInSearchBar() {
        if ( isOnAFileTypeWithAlternativeDisplay() && gridAdapter!=null && filesBar !=null ) {
            //if we would allow to display ringtones in grid
            filesBar.setCheckAllVisible(gridAdapter.getFileType() != Constants.FILE_TYPE_RINGTONES);
        } else if (adapter != null && filesBar != null) {
            filesBar.setCheckAllVisible(adapter.getFileType() != Constants.FILE_TYPE_RINGTONES);
        }
    }

    private RadioButton initRadioButton(View v, int viewId, final byte fileType) {
        RadioButton button = findView(v, viewId);
        Resources r = button.getResources();
        FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory =
                new FileTypeRadioButtonSelectorFactory(fileType,
                        r,
                        FileTypeRadioButtonSelectorFactory.RadioButtonContainerType.BROWSE);
        fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        button.setClickable(true);
        RadioButtonListener rbListener = new RadioButtonListener(button, fileType, fileTypeRadioButtonSelectorFactory);
        button.setOnClickListener(rbListener);
        button.setOnCheckedChangeListener(rbListener);
        button.setChecked(fileType == Constants.FILE_TYPE_AUDIO);
        radioButtonFileTypeMap.put(fileType, button);
        return button;
    }

    private void browseFilesButtonClick(byte fileType) {
        savePreviouslyCheckedFileDescriptors();
        savePreviousFilter();
        if (isOnAFileTypeWithAlternativeDisplay() && gridAdapter !=null) {
            saveFirstVisiblePosition(gridAdapter.getFileType());
            gridAdapter.clear();
        } else if (adapter != null) {
            saveFirstVisiblePosition(adapter.getFileType());
            adapter.clear();
        }
        filesBar.clearCheckAll();
        reloadFiles(fileType);
        logBrowseAction(fileType);
    }

    private void logBrowseAction(byte fileType) {
        try {
            UXStats.instance().log(browseUXActions[fileType]);
        } catch (Throwable ignored) {
        }
    }

    private void reloadFiles(byte fileType) {
        getLoaderManager().destroyLoader(LOADER_FILES_ID);
        Bundle bundle = new Bundle();
        bundle.putByte("fileType", fileType);
        getLoaderManager().restartLoader(LOADER_FILES_ID, bundle, this);
    }

    private Loader<Object> createLoaderFiles(final byte fileType) {
        AsyncTaskLoader<Object> loader = new AsyncTaskLoader<Object>(getActivity()) {
            @Override
            public Object loadInBackground() {
                try {
                    return new Object[]{fileType, peer.browse(fileType)};
                } catch (Throwable e) {
                    LOG.error("Error performing finger", e);
                }
                return null;
            }
        };
        loader.forceLoad();
        return loader;
    }

    private void updateHeader() {
        if (peer == null) {
            LOG.warn("Something wrong. peer is null");
            return;
        }

        Librarian.instance().invalidateCountCache();
        Finger finger = peer.finger();

        if (header != null) {
            byte fileType = currentFileType;
            int numTotal = 0;
            switch (fileType) {
                case Constants.FILE_TYPE_TORRENTS:
                    numTotal = finger.numTotalTorrentFiles;
                    break;
                case Constants.FILE_TYPE_AUDIO:
                    numTotal = finger.numTotalAudioFiles;
                    break;
                case Constants.FILE_TYPE_DOCUMENTS:
                    numTotal = finger.numTotalDocumentFiles;
                    break;
                case Constants.FILE_TYPE_PICTURES:
                    numTotal = finger.numTotalPictureFiles;
                    break;
                case Constants.FILE_TYPE_RINGTONES:
                    numTotal = finger.numTotalRingtoneFiles;
                    break;
                case Constants.FILE_TYPE_VIDEOS:
                    numTotal = finger.numTotalVideoFiles;
                    break;
            }

            String fileTypeStr = getString(R.string.my_filetype, UIUtils.getFileTypeAsString(getResources(), fileType));

            TextView title = (TextView) header.findViewById(R.id.view_browse_peer_header_text_title);
            TextView total = (TextView) header.findViewById(R.id.view_browse_peer_header_text_total);

            title.setText(fileTypeStr);
            total.setText("(" + String.valueOf(numTotal) + ")");
        }

        //setup if first
        if (gridAdapter == null && isOnAFileTypeWithAlternativeDisplay()) {
            browseFilesButtonClick(Constants.FILE_TYPE_AUDIO);
        } else if (adapter == null) {
            browseFilesButtonClick(Constants.FILE_TYPE_AUDIO);
        }

        MusicUtils.stopSimplePlayer();
        updateCheckAllStatusInSearchBar();
        restoreListViewScrollPosition();
    }

    private void restoreListViewScrollPosition() {
        if ( isOnAFileTypeWithAlternativeDisplay() && gridAdapter != null){
            int savedListViewVisiblePosition = getSavedListViewVisiblePosition(gridAdapter.getFileType());
            savedListViewVisiblePosition = (savedListViewVisiblePosition > 0) ? savedListViewVisiblePosition + 1 : 0;
            grid.setSelection(savedListViewVisiblePosition);
        } else if (adapter != null) {
            int savedListViewVisiblePosition = getSavedListViewVisiblePosition(adapter.getFileType());
            savedListViewVisiblePosition = (savedListViewVisiblePosition > 0) ? savedListViewVisiblePosition + 1 : 0;
            list.setSelection(savedListViewVisiblePosition);
        }
    }

    private void updateFiles(Object[] data) {
        if (data == null || data.length < 2 || data[1] == null) {
            LOG.warn("Something wrong, data is null");
            return;
        }

        try {
            byte fileType = (Byte) data[0];

            currentFileType = fileType;
            @SuppressWarnings("unchecked")
            List<FileDescriptor> items = (List<FileDescriptor>) data[1];
            if ( isOnAFileTypeWithAlternativeDisplay() ) {
                gridAdapter = new FileGridAdapter(getActivity(), items, fileType) {
                    @Override
                    protected void onItemChecked(CompoundButton v, boolean isChecked) {
                        if (!isChecked) {
                            filesBar.clearCheckAll();
                        }
                        super.onItemChecked(v, isChecked);
                    }

                    @Override
                    protected void onLocalPlay() {
                        if (gridAdapter != null) {
                            saveFirstVisiblePosition(currentFileType);
                        }
                    }
                };
            } else {
                adapter = new FileListAdapter(getActivity(), items, fileType) {

                    @Override
                    protected void onItemChecked(CompoundButton v, boolean isChecked) {
                        if (!isChecked) {
                            filesBar.clearCheckAll();
                        }
                        super.onItemChecked(v, isChecked);
                    }

                    @Override
                    protected void onLocalPlay() {
                        if (adapter != null) {
                            saveFirstVisiblePosition(currentFileType);
                        }
                    }
                };
            }

            restorePreviouslyChecked();
            restorePreviousFilter();

            if(isOnAFileTypeWithAlternativeDisplay()){
                grid.setAdapter(gridAdapter);
                swipeLayoutGrid.setVisibility(View.VISIBLE);
                swipeLayoutList.setVisibility(View.GONE);
            } else {
                swipeLayoutGrid.setVisibility(View.GONE);
                list.setAdapter(adapter);
                swipeLayoutList.setVisibility(View.VISIBLE);
            }
        } catch (Throwable e) {
            LOG.error("Error updating files in list", e);
        }
    }

    private void saveFirstVisiblePosition(byte fileType) {
        int firstVisiblePosition;
        if(isOnAFileTypeWithAlternativeDisplay()) {
            firstVisiblePosition = grid.getFirstVisiblePosition();
        } else {
            firstVisiblePosition = list.getFirstVisiblePosition();
        }
        ConfigurationManager.instance().setInt(Constants.BROWSE_PEER_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION + fileType, firstVisiblePosition);
    }

    private int getSavedListViewVisiblePosition(byte fileType) {
        //will return 0 if not found.
        return ConfigurationManager.instance().getInt(Constants.BROWSE_PEER_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION + fileType);
    }

    private RadioButton getRadioButton(byte fileType) {
        return radioButtonFileTypeMap.get(fileType);
    }

    private void switchToThe(boolean right) {
        final byte nextFileType = (right) ? toTheRightOf.get(currentFileType) : toTheLeftOf.get(currentFileType);
        changeSelectedRadioButton(currentFileType, nextFileType);
    }

    private void changeSelectedRadioButton(byte currentFileType, byte nextFileType) {
        // browseFilesButtonClick(currentFileType) isn't enough, it won't update the radio button background.
        RadioButton currentButton = getRadioButton(currentFileType);
        RadioButton nextButton = getRadioButton(nextFileType);
        if (nextButton != null) {
            currentButton.setChecked(false);
            nextButton.setChecked(true);
            nextButton.callOnClick();
        }
    }

    private final class RadioButtonListener implements OnClickListener, CompoundButton.OnCheckedChangeListener {
        private final RadioButton button;
        private final byte fileType;
        private final FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory;

        RadioButtonListener(RadioButton button,
                            byte fileType,
                            FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory) {
            this.button = button;
            this.fileType = fileType;
            this.fileTypeRadioButtonSelectorFactory = fileTypeRadioButtonSelectorFactory;
        }

        @Override
        public void onClick(View v) {
            if (button.isChecked()) {
                browseFilesButtonClick(fileType);
            }
            fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        }
    }

    private final class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Constants.ACTION_MEDIA_PLAYER_PLAY) ||
                    action.equals(Constants.ACTION_MEDIA_PLAYER_STOPPED) ||
                    action.equals(Constants.ACTION_MEDIA_PLAYER_PAUSED) ||
                    action.equals(MusicPlaybackService.PLAYSTATE_CHANGED) ||
                    action.equals(MusicPlaybackService.META_CHANGED) ||
                    action.equals(MusicPlaybackService.SIMPLE_PLAYSTATE_STOPPED)
                    ) {
                //grid adapter
                if (gridAdapter != null && isOnAFileTypeWithAlternativeDisplay()) {
                    gridAdapter.notifyDataSetChanged();
                } else if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }

            if (action.equals(Constants.ACTION_FILE_ADDED_OR_REMOVED)) {
                if (intent.hasExtra(Constants.EXTRA_REFRESH_FILE_TYPE)) {
                    reloadFiles(intent.getByteExtra(Constants.EXTRA_REFRESH_FILE_TYPE, Constants.FILE_TYPE_AUDIO));
                } else {
                    // reload everything
                    reloadFiles(Constants.FILE_TYPE_APPLICATIONS);
                    reloadFiles(Constants.FILE_TYPE_RINGTONES);
                    reloadFiles(Constants.FILE_TYPE_TORRENTS);
                    reloadFiles(Constants.FILE_TYPE_PICTURES);
                    reloadFiles(Constants.FILE_TYPE_VIDEOS);
                    reloadFiles(Constants.FILE_TYPE_AUDIO);
                }
            }
        }
    }

    private void refreshSelection() {
        if (isOnAFileTypeWithAlternativeDisplay() && gridAdapter!=null) {
            lastAdapterRefresh = SystemClock.elapsedRealtime();
            browseFilesButtonClick(currentFileType);
        } else if (adapter != null) {
            lastAdapterRefresh = SystemClock.elapsedRealtime();
            browseFilesButtonClick(currentFileType);
        }
    }
}
