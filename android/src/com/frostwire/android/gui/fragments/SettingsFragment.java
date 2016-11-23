package com.frostwire.android.gui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.MainActivity;

/**
 * Created by Grzesiek on 2016-11-22.
 */

public class SettingsFragment extends PreferenceFragment {

    public static final String REBUILD_SETTINGS_SLAVE_ACTION = "com.frostwire.android.gui.fragments.REBUILD";
    public static final String ID_EXTRA_NAME = "resId";

    private void addPreferencesFromResourceToCategoryBottom (int id, String title) {
        PreferenceScreen screen = getPreferenceScreen ();
        addPreferencesFromResource(R.xml.category_stub);
        PreferenceCategory newParent = (PreferenceCategory) screen.findPreference("category_stub");
        newParent.setKey(title);
        newParent.setTitle(title);
        int last = screen.getPreferenceCount ();
        addPreferencesFromResource (id);
        while (screen.getPreferenceCount () > last) {
            Preference p = screen.getPreference (last);
            screen.removePreference (p);
            newParent.addPreference (p);
        }
    }

    private void addPreferencesFromResourceToScreenBottom (int id, String title) {
        PreferenceScreen screen = getPreferenceScreen ();
        addPreferencesFromResource(R.xml.screen_stub);
        PreferenceScreen newScreen = (PreferenceScreen) screen.findPreference("screen_stub");
        newScreen.setKey(title);
        newScreen.setTitle(title);
        int last = screen.getPreferenceCount ();
        addPreferencesFromResource (id);
        while (screen.getPreferenceCount () > last) {
            Preference p = screen.getPreference (last);
            screen.removePreference (p);
            newScreen.addPreference (p);
        }
    }

    private void addPreferencesAsDirectory(int id, String title) {
        PreferenceScreen screen = getPreferenceScreen();
        Preference newPreference = new Preference(getActivity());
        newPreference.setTitle(title);
        Intent intent = new Intent(REBUILD_SETTINGS_SLAVE_ACTION, null, getActivity(), MainActivity.class);
        intent.putExtra(ID_EXTRA_NAME,id);
        newPreference.setIntent(intent);
        screen.addPreference(newPreference);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().getTheme().applyStyle(R.style.Preferences, true);
        View inflated = super.onCreateView(inflater, container, savedInstanceState);
        getActivity().setTheme(R.style.Theme_FrostWire);
        return inflated;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if( getActivity().getResources().getBoolean(R.bool.isTablet) ) {
            //todo clear
            Log.w("SF","building as dir");
            buildAsDirectory();
        } else {
            Log.w("SF","building as screen");
            buildAsScreen();
        }
    }

    private void buildAsScreen() {
        addPreferencesFromResource(R.xml.base_setting_container);
        addPreferencesFromResource(R.xml.general_settings);
        addPreferencesFromResourceToScreenBottom(R.xml.torrent_settings,"torrent");
        addPreferencesFromResourceToCategoryBottom(R.xml.general_settings,"general");
    }

    private void buildAsDirectory() {
        addPreferencesFromResource(R.xml.base_setting_container);
        addPreferencesAsDirectory(R.xml.general_settings, "general");
        addPreferencesAsDirectory(R.xml.torrent_settings, "torrent");
    }


    public void rebuild(Integer screenId){
        getPreferenceScreen().removeAll();
        if(screenId!=null) {
            addPreferencesFromResource(screenId);
        } else {
            getView().setVisibility(View.GONE);//todo think
        }
    }
}
