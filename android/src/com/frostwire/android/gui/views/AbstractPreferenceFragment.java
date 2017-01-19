/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.os.Build;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroupAdapter;
import android.support.v7.preference.TwoStatePreference;
import android.view.View;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractPreferenceFragment extends PreferenceFragment {

    private final int preferencesResId;

    public AbstractPreferenceFragment(int preferencesResId) {
        this.preferencesResId = preferencesResId;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(preferencesResId);
        initComponents();
    }

    @Override
    public void onResume() {
        super.onResume();
        fixPaddingProblemsOnOldAPIs();
    }

    /**
     * This function fixes problems with the support library on older APIs (JellyBeans 16-18)
     * The problem is that when the Preference Category is inflated the
     * attributes of padding get deleted due to calling a function (setBackground) that clears the padding
     * This leads to padding being always 0 on JellyBeans. This function manually forces
     * correct padding for the view
     */
    protected void fixPaddingProblemsOnOldAPIs() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            getListView().post(new Runnable() {
                @Override
                public void run() {
                    int count = getListView().getChildCount();
                    for (int i = 0; i < count; i++) {
                        View v = getListView().getChildAt(i);
                        int pos = getListView().getChildAdapterPosition(v);
                        Preference p = ((PreferenceGroupAdapter) getListView().getAdapter()).getItem(pos);
                        if (p instanceof PreferenceCategory) {
                            float scale = getResources().getDisplayMetrics().density;
                            v.setPadding(dp2px(16, scale),
                                    dp2px(16, scale),
                                    dp2px(16, scale),
                                    dp2px(16, scale));
                        }
                    }
                }
            });
        }
    }

    protected final int dp2px(int value, float scale) {
        return (int) (value * scale + 0.5f);
    }


    protected void initComponents() {
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Preference> T findPreference(String key) {
        return (T) super.findPreference(key);
    }

    protected final void setEnabled(Preference preference, boolean enabled, boolean notifyChange) {
        if (notifyChange) {
            preference.setEnabled(enabled);
        } else {
            Preference.OnPreferenceChangeListener l = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener(null);
            preference.setEnabled(enabled);
            preference.setOnPreferenceChangeListener(l);
        }
    }

    protected final void setChecked(TwoStatePreference preference, boolean checked, boolean notifyChange) {
        if (notifyChange) {
            preference.setChecked(checked);
        } else {
            Preference.OnPreferenceChangeListener l = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener(null);
            preference.setChecked(checked);
            preference.setOnPreferenceChangeListener(l);
        }
    }
}
