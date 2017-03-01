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
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.adapters.menu.FileListAdapter;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.FileTypeRadioButtonSelectorFactory;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public class BrowsePeerFragment extends AbstractFragment implements LoaderCallbacks<Object>, MainFragment {
    private static final Logger LOG = Logger.getLogger(BrowsePeerFragment.class);
    private static final int LOADER_FILES_ID = 0;
    private static final int PERCENTAGE_CHANCE_OF_CHECK_MODE_UI_HINT = 50;

    private final BroadcastReceiver broadcastReceiver;
    private LinearLayout selectAllContainer;
    private TextView selectAllLabel;
    private CheckBox selectAllCheckBox;
    private RadioGroup fileTypeButtons;
    private SwipeRefreshLayout swipeRefresh;
    private ListView list;
    private FileListAdapter adapter;
    private Peer peer;
    private Finger oldFinger;
    private View header;
    private long lastAdapterRefresh;
    private String filterString;
    private HashMap<Byte, Set<FileListAdapter.FileDescriptorItem>> checkedItemsMap;
    private SwipeLayout swipe;

    private BrowserPeerFragmentState currentFragmentState;
    private BrowserPeerFragmentState beforeCheckedFragmentState;

    private BrowserPeerFragmentState normalState = new NormalState();
    private BrowserPeerFragmentState checkState = new CheckState();
    private BrowserPeerFragmentState filteringState = new FilteringState();
    private BrowserPeerFragmentState filteredState = new FilteredState();

    //-----header
    private ImageView checkButton;
    private ImageView searchButton;
    private EditText searchBar;
    private View textContainer;
    private ImageView menuButton;
    private ImageView firstActionButton;
    private ImageView secondActionButton;

    private ImageView overlay;

    private CompoundButton.OnCheckedChangeListener checkListener = new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (adapter != null) {
                if (isChecked) {
                    adapter.checkAll();
                    selectAllLabel.setText(R.string.deselect_all);
                } else {
                    adapter.clearChecked();
                    selectAllLabel.setText(R.string.select_all);
                }
                if (adapter.getCheckedCount() > 0) {
                    menuButton.setVisibility(View.VISIBLE);
                } else {
                    menuButton.setVisibility(View.GONE);
                }
                updateHeader();
            }
        }
    };

    private SwipeLayout.OnSwipeListener swipeListener = new SwipeLayout.OnSwipeListener() {
        @Override
        public void onSwipeLeft() {
            switchToThe(true);
        }

        @Override
        public void onSwipeRight() {
            switchToThe(false);
        }
    };

    private TextWatcher textChangeFilterWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            performFilter(charSequence.toString());
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    };

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

    public BrowsePeerFragment() {
        super(R.layout.fragment_browse_peer);
        broadcastReceiver = new LocalBroadcastReceiver();
        currentFragmentState = normalState;
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
        checkedItemsMap = new HashMap<>();
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
        if (StringUtils.isNullOrEmpty(filterString)) {
            updateHeader();
        }

        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void onLoaderReset(Loader<Object> loader) {
    }

    @Override
    public void onResume() {
        super.onResume();
        initBroadcastReceiver();
        if (adapter != null) {
            restorePreviouslyChecked();
            restoreFilterString();
            browseFilesButtonClick(adapter.getFileType());
        }

        if (currentFragmentState != null) {
            currentFragmentState.enterState();
        }

        updateHeader();
        restoreListViewScrollPosition();
    }

    private void restorePreviouslyChecked() {
        Set<FileListAdapter.FileDescriptorItem> previouslyChecked = checkedItemsMap.get(adapter.getFileType());
        if (previouslyChecked != null) {
            if (!previouslyChecked.isEmpty()) {
                adapter.setChecked(previouslyChecked);
            }
            changeSelectAllCheckBoxSilently(previouslyChecked.size() == adapter.getCount());
        } else {
            changeSelectAllCheckBoxSilently(false);
        }
    }

    private void changeSelectAllCheckBoxSilently(boolean newState) {
        selectAllCheckBox.setOnCheckedChangeListener(null);
        selectAllCheckBox.setChecked(newState);
        selectAllCheckBox.setOnCheckedChangeListener(checkListener);
        if (newState) {
            selectAllLabel.setText(R.string.deselect_all);
        } else {
            selectAllLabel.setText(R.string.select_all);
        }
    }

    private void restoreFilterString() {
        if (filterString != null && searchBar != null) {
            searchBar.setText(filterString);
            performFilter(filterString);
        }
    }

    private void savePreviouslyCheckedFileDescriptors() {
        if (adapter != null) {
            final Set<FileListAdapter.FileDescriptorItem> checked = adapter.getChecked();
            if (checked != null && !checked.isEmpty()) {
                Set<FileListAdapter.FileDescriptorItem> checkedCopy = new HashSet<>();
                for (FileListAdapter.FileDescriptorItem fileDescriptorItem : checked) {
                    checkedCopy.add(fileDescriptorItem);
                }
                checkedItemsMap.put(adapter.getFileType(), checkedCopy);
            }
        }
    }

    private void saveFilterString() {
        if (searchBar != null) {
            String filter = searchBar.getText().toString();
            if (!StringUtils.isNullOrEmpty(filter)) {
                filterString = filter;
            }
        }
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
        saveFilterString();
        MusicUtils.stopSimplePlayer();
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    @Override
    public View getHeader(Activity activity) {

        // old searchBar muting
        if (searchBar != null) {
            //need to mute this in case of drawer changes requesting recreation of header
            searchBar.setOnFocusChangeListener(null);
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        header = inflater.inflate(R.layout.view_browse_peer_header, null);
        checkButton = findView(header, R.id.view_browse_peer_header_check_mode_button);
        searchButton = findView(header, R.id.view_browse_peer_header_search_button);
        searchBar = findView(header, R.id.view_browse_peer_header_search_bar);
        textContainer = findView(header, R.id.view_browse_peer_header_text_container);
        menuButton = findView(header, R.id.view_browse_peer_header_menu_button);
        firstActionButton = findView(header, R.id.view_browse_peer_header_action_button_1);
        secondActionButton = findView(header, R.id.view_browse_peer_header_action_button_2);

        checkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentFragmentState == checkState) {
                    getActivity().onBackPressed();
                } else {
                    if (adapter.getFileType() != Constants.FILE_TYPE_RINGTONES) {
                        checkState.enterState();
                        randomlyShowUIHint();
                    }
                }
            }
        });

        searchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentFragmentState == filteringState) {
                    getActivity().onBackPressed();
                } else {
                    filteringState.enterState();
//                    enterFilteringState();
                }
            }
        });

        menuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                int[] pos = new int[2];
                menuButton.getLocationOnScreen(pos);
                pos[0] += menuButton.getWidth();
                pos[1] -= menuButton.getHeight();
                if (adapter.getMenuForSelectedItems().getCount() > 2) {
                    MenuAction mask = adapter.getMenuForSelectedItems().getItem(adapter.getMenuForSelectedItems().getCount() - 1);
                    adapter.showMenuForSelectedItems(mask, pos[0], pos[1]);
                } else {
                    adapter.showMenuForSelectedItems(pos[0], pos[1]);
                }
            }
        });

        updateHeader();
        if (currentFragmentState == filteringState) {
            enableSearchBar(true);
        }

        return header;
    }

    @Override
    public void onShow() {
    }

    @Override
    protected void initComponents(View v) {

        selectAllContainer = findView(v, R.id.fragment_browse_peer_selection_mode_container);
        selectAllLabel = findView(v, R.id.fragment_browse_peer_selection_mode_label);
        selectAllCheckBox = findView(v, R.id.fragment_browse_peer_selection_mode_checkbox);

        selectAllCheckBox.setOnCheckedChangeListener(checkListener);
        fileTypeButtons = findView(v, R.id.fragment_browse_peer_radiogroup_browse_type);


        swipeRefresh = findView(v, R.id.fragment_browse_peer_swipe_refresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                long now = SystemClock.elapsedRealtime();
                if ((now - lastAdapterRefresh) > 5000) {
                    refreshSelection();
                } else {
                    swipeRefresh.setRefreshing(false);
                }
            }
        });
        list = findView(v, R.id.fragment_browse_peer_list);
        swipe = findView(v, R.id.fragment_browse_peer_swipe);
        swipe.setOnSwipeListener(swipeListener);

        initRadioButton(v, R.id.fragment_browse_peer_radio_audio, Constants.FILE_TYPE_AUDIO);
        initRadioButton(v, R.id.fragment_browse_peer_radio_ringtones, Constants.FILE_TYPE_RINGTONES);
        initRadioButton(v, R.id.fragment_browse_peer_radio_videos, Constants.FILE_TYPE_VIDEOS);
        initRadioButton(v, R.id.fragment_browse_peer_radio_pictures, Constants.FILE_TYPE_PICTURES);
        initRadioButton(v, R.id.fragment_browse_peer_radio_documents, Constants.FILE_TYPE_DOCUMENTS);
        initRadioButton(v, R.id.fragment_browse_peer_radio_torrents, Constants.FILE_TYPE_TORRENTS);

        //fake button for spacing
        RadioButton button = findView(v, R.id.fragment_browse_peer_selection_mode_fake_spacer);
        Resources r = button.getResources();
        FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory =
                new FileTypeRadioButtonSelectorFactory(Constants.FILE_TYPE_AUDIO,
                        r,
                        FileTypeRadioButtonSelectorFactory.RadioButtonContainerType.BROWSE);
        fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        button.setClickable(false);

        overlay = findView(v, R.id.fragment_browse_peer_content_overlay);
        overlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().onBackPressed();
            }
        });
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

    private void randomlyShowUIHint() {
        int roll = new Random().nextInt(100);
        if (roll < PERCENTAGE_CHANCE_OF_CHECK_MODE_UI_HINT) {
            UIUtils.showShortMessage(getView(), R.string.checkmode_hint_message);
        }
    }

    private void browseFilesButtonClick(byte fileType) {
        if (adapter != null) {
            savePreviouslyCheckedFileDescriptors();
            saveFilterString();
            saveListViewVisiblePosition(adapter.getFileType());
            adapter.clear();
        }
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
        updateHeader(true);
    }

    private void updateHeader(boolean updateFinger) {
        if (peer == null) {
            LOG.warn("Something wrong. peer is null");
            return;
        }
        if(updateFinger || oldFinger == null) {
            Runnable fingerTask = new Runnable() {
                @Override
                public void run() {
                    Librarian.instance().invalidateCountCache();
                    Finger finger = peer.finger();
                    oldFinger = finger;
                    if (header != null) {
                        final byte fileType = adapter != null ? adapter.getFileType() : Constants.FILE_TYPE_AUDIO;
                        final int numTotalFinal = getTotalFromFinger(finger, fileType);
                        Runnable postExecute = new Runnable() {
                            @Override
                            public void run() {
                                updateHeaderText(fileType, numTotalFinal);
                            }
                        };
                        Handler handler = new Handler(getActivity().getMainLooper());
                        handler.post(postExecute);
                    }
                }
            };
            Engine.instance().getThreadPool().submit(fingerTask);
        } else {
            byte fileType = adapter != null ? adapter.getFileType() : Constants.FILE_TYPE_AUDIO;
            int numTotalFinal = getTotalFromFinger(oldFinger, fileType);
            updateHeaderText(fileType, numTotalFinal);
        }
        
    }
    
    private int getTotalFromFinger(Finger finger, byte fileType) {
        switch (fileType) {
            case Constants.FILE_TYPE_TORRENTS:
                return finger.numTotalTorrentFiles;
            case Constants.FILE_TYPE_AUDIO:
                return finger.numTotalAudioFiles;
            case Constants.FILE_TYPE_DOCUMENTS:
                return finger.numTotalDocumentFiles;
            case Constants.FILE_TYPE_PICTURES:
                return finger.numTotalPictureFiles;
            case Constants.FILE_TYPE_RINGTONES:
                return finger.numTotalRingtoneFiles;
            case Constants.FILE_TYPE_VIDEOS:
                return finger.numTotalVideoFiles;
        }
        return 0;
    }
    
    private void updateHeaderText(byte fileType, int numTotalFinal) {
        String fileTypeStr = getString(R.string.my_filetype, UIUtils.getFileTypeAsString(getResources(), fileType));
        int filterNumTotal = adapter.getCount();
        TextView title = (TextView) header.findViewById(R.id.view_browse_peer_header_text_title);
        TextView total = (TextView) header.findViewById(R.id.view_browse_peer_header_text_total);
        if (currentFragmentState == checkState) {
            title.setText(adapter.getCheckedCount() + " " + getResources().getString(R.string.selected));
            total.setText("");
        } else {
            if (StringUtils.isNullOrEmpty(filterString)) {
                title.setText(fileTypeStr);
                total.setText("(" + String.valueOf(numTotalFinal) + ")");
            } else {
                title.setText(filterString);
                total.setText("(" + filterNumTotal + "/" + String.valueOf(numTotalFinal) + ")");
            }
        }
        if (adapter == null) {
            browseFilesButtonClick(Constants.FILE_TYPE_AUDIO);
        }
        MusicUtils.stopSimplePlayer();
        updateViewsToState();
    }

    private void updateViewsToState() {
        if (header != null) {
            if(currentFragmentState!=null) {
                currentFragmentState.updateViews();
            }
        }
    }

    private Drawable getMutableDrawable(int id) {
        Drawable base = getResources().getDrawable(id).mutate();
        if (base.getConstantState() != null) {
            return base.getConstantState().newDrawable(getResources());
        }
        return base;
    }

    private void getSelectedItemsOptions() {
        MenuAdapter menu = adapter.getMenuForSelectedItems();
        if (menu != null) {
            final MenuAction lastMenuItem = menu.getItem(menu.getCount() - 1);
            Drawable icon = getMutableDrawable(lastMenuItem.getImageId());
            icon.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP));
            firstActionButton.setImageDrawable(icon);
            firstActionButton.setVisibility(View.VISIBLE);
            firstActionButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    lastMenuItem.onClick();
                }
            });
            secondActionButton.setVisibility(View.GONE);
            if (menu.getCount() <= 2) {// just show the actions don't show the menu
                menuButton.setVisibility(View.GONE);
                if (menu.getCount() == 2) {
                    final MenuAction secondToLastMenuItem = menu.getItem(0);
                    icon = getMutableDrawable(secondToLastMenuItem.getImageId());
                    icon.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP));
                    secondActionButton.setImageDrawable(icon);
                    secondActionButton.setVisibility(View.VISIBLE);
                    secondActionButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            secondToLastMenuItem.onClick();
                        }
                    });
                } else {
                    secondActionButton.setVisibility(View.GONE);
                }
            }
        } else {
            menuButton.setVisibility(View.GONE);
            firstActionButton.setVisibility(View.GONE);
            secondActionButton.setVisibility(View.GONE);
        }
    }

    private void restoreListViewScrollPosition() {
        if (adapter != null) {
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
            @SuppressWarnings("unchecked")
            List<FileDescriptor> items = (List<FileDescriptor>) data[1];
            //todo dont recreate adapter - resuse old
            adapter = new FileListAdapter(getActivity(), items, fileType) {
                @Override
                protected void onLocalPlay() {
                    if (adapter != null) {
                        saveListViewVisiblePosition(adapter.getFileType());
                    }
                }

                @Override
                protected boolean onItemLongClicked(View v) {
                    if (adapter.getFileType() == Constants.FILE_TYPE_RINGTONES) {
                        UIUtils.showShortMessage(getActivity(), R.string.checkmode_invalid_type_message);
                    } else if (currentFragmentState != checkState) {
                        checkState.enterState();
//                        onItemClicked(v); // select the one used to enter state
                    } else {
                        onItemClicked(v);
                    }
                    return true;
                }
            };
            adapter.setCheckState(currentFragmentState == checkState);
            adapter.setOnItemCheckedListener(new AbstractListAdapter.OnItemCheckedListener<FileListAdapter.FileDescriptorItem>() {
                @Override
                public void onItemChecked(CompoundButton v, FileListAdapter.FileDescriptorItem item, boolean checked) {
                    changeSelectAllCheckBoxSilently(adapter.getCheckedCount() == adapter.getCount());
                    if (adapter.getCheckedCount() > 0) {
                        menuButton.setVisibility(View.VISIBLE);
                    } else {
                        menuButton.setVisibility(View.GONE);
                    }
                    updateHeader();
                }
            });
            restorePreviouslyChecked();
            restoreFilterString();
            if (filterString != null) {
                performFilter(filterString);
            } else {
                updateAdapter();
            }
        } catch (Throwable e) {
            LOG.error("Error updating files in list", e);
        }
    }

    private void updateAdapter() {
        list.setAdapter(adapter);
        restoreListViewScrollPosition();
    }

    private void performFilter(String filterString) {
        this.filterString = filterString;
        if (adapter != null && filterString != null) {
            adapter.getFilter().filter(filterString, new Filter.FilterListener() {
                @Override
                public void onFilterComplete(int i) {
                    updateAdapter();
                    updateHeader();
                }
            });
        }
    }

    private void saveListViewVisiblePosition(byte fileType) {
        int firstVisiblePosition = list.getFirstVisiblePosition();
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
        if (adapter == null) {
            return;
        }
        final byte currentFileType = adapter.getFileType();
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

    public boolean inSpecialState() {
        return currentFragmentState != normalState;
    }

    public void endSpecialState() {
        if (currentFragmentState != null) {
            currentFragmentState.leaveState();
        }
    }

    private void enableSwipe(boolean enabled) {
        swipe.setOnSwipeListener(enabled ? swipeListener : null);
    }

    private void enableSearchBar(boolean enabled) {
        if (enabled) {
            searchBar.setText(filterString);
            searchBar.requestFocus();

            searchBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (!hasFocus) {
                        getActivity().onBackPressed();
                    }
                }
            });
            searchBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        String filter = textView.getText().toString();
                        if (!StringUtils.isNullOrEmpty(filter)) {
                            filteredState.enterState();
                            performFilter(filter);
                            searchBar.setOnFocusChangeListener(null);
                        }
                        searchBar.clearFocus();
                        return true;
                    }
                    return false;
                }
            });
            searchBar.addTextChangedListener(textChangeFilterWatcher);

            final InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchBar.removeTextChangedListener(textChangeFilterWatcher);
            searchBar.setOnFocusChangeListener(null);
            searchBar.setText(null);
            searchBar.requestFocus();
            UIUtils.hideKeyboardFromActivity(getActivity());
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
                if (adapter != null) {
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
        if (adapter != null) {
            lastAdapterRefresh = SystemClock.elapsedRealtime();
            browseFilesButtonClick(adapter.getFileType());
        }
    }

    private abstract class BrowserPeerFragmentState {
        abstract void enterState();
        abstract void leaveState();
        protected void showRequiredViews() {}
        protected void hideOtherViews() {}
        public void updateViews() {
            hideOtherViews();
            showRequiredViews();
        }

    }

    private class NormalState extends BrowserPeerFragmentState {

        @Override
        public void enterState() {

        }

        @Override
        public void leaveState() {

        }

        @Override
        protected void showRequiredViews() {
            searchButton.setVisibility(View.VISIBLE);
            fileTypeButtons.setVisibility(View.VISIBLE);
            if (adapter != null) {
                checkButton.setVisibility(adapter.getFileType() != Constants.FILE_TYPE_RINGTONES ? View.VISIBLE : View.GONE);
            }
            textContainer.setVisibility(View.VISIBLE);
        }

        @Override
        protected void hideOtherViews() {
            searchBar.setVisibility(View.GONE);
            firstActionButton.setVisibility(View.GONE);
            secondActionButton.setVisibility(View.GONE);
            menuButton.setVisibility(View.GONE);
            selectAllContainer.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
        }
    }

    private class CheckState extends BrowserPeerFragmentState {

        @Override
        public void enterState() {
            if(currentFragmentState != this) {
                beforeCheckedFragmentState = currentFragmentState;
            }
            currentFragmentState = this;
            ((MainActivity) getActivity()).swapDrawerForBack(true);
            enableSwipe(false);
            adapter.setCheckState(true);
            updateHeader();
        }

        @Override
        public void leaveState() {
            currentFragmentState = beforeCheckedFragmentState;
            if(beforeCheckedFragmentState == filteredState) {
                ((MainActivity) getActivity()).swapDrawerForBack(true);
            }
            enableSwipe(true);
            adapter.setCheckState(false);
            adapter.clearChecked();
            changeSelectAllCheckBoxSilently(false);
            updateHeader();
        }

        @Override
        protected void showRequiredViews() {
            if (adapter != null) {
                menuButton.setVisibility(adapter.getCheckedCount() > 0 ? View.VISIBLE : View.GONE);
            }
            selectAllContainer.setVisibility(View.VISIBLE);
            getSelectedItemsOptions();
        }

        @Override
        protected void hideOtherViews() {
            searchButton.setVisibility(View.GONE);
            searchBar.setVisibility(View.GONE);
            fileTypeButtons.setVisibility(View.GONE);
            checkButton.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
        }
    }

    private class FilteringState extends BrowserPeerFragmentState {

        @Override
        public void enterState() {
            currentFragmentState = this;
            ((MainActivity) getActivity()).swapDrawerForBack(true);
            updateHeader();
            enableSearchBar(true);
        }

        @Override
        public void leaveState() {
            currentFragmentState = normalState;
            enableSearchBar(false);
            filterString = null;
            reloadFiles(adapter.getFileType());
            updateHeader();
        }

        @Override
        protected void showRequiredViews() {
            searchBar.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
        }

        @Override
        protected void hideOtherViews() {
            searchButton.setVisibility(View.GONE);
            firstActionButton.setVisibility(View.GONE);
            secondActionButton.setVisibility(View.GONE);
            menuButton.setVisibility(View.GONE);
            checkButton.setVisibility(View.GONE);
            textContainer.setVisibility(View.GONE);
            selectAllContainer.setVisibility(View.GONE);
        }

        @Override
        public void updateViews() {
            super.updateViews();
            searchBar.requestFocus();
            final InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private class FilteredState extends BrowserPeerFragmentState {

        @Override
        public void enterState() {
            currentFragmentState = this;
            enableSearchBar(false);
            ((MainActivity) getActivity()).swapDrawerForBack(true);
            updateHeader();
        }

        @Override
        public void leaveState() {
            currentFragmentState = normalState;
            enableSearchBar(false);
            filterString = null;
            reloadFiles(adapter.getFileType());
            updateHeader();
        }

        @Override
        protected void showRequiredViews() {
            if (adapter != null) {
                checkButton.setVisibility(adapter.getFileType() != Constants.FILE_TYPE_RINGTONES ? View.VISIBLE : View.GONE);
            }
            searchButton.setVisibility(View.VISIBLE);
            fileTypeButtons.setVisibility(View.VISIBLE);
            textContainer.setVisibility(View.VISIBLE);
        }

        @Override
        protected void hideOtherViews() {
            searchBar.setVisibility(View.GONE);
            firstActionButton.setVisibility(View.GONE);
            secondActionButton.setVisibility(View.GONE);
            menuButton.setVisibility(View.GONE);
            selectAllContainer.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
        }
    }

}
