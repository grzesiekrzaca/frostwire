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

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 * @author grzesiekrzaca
 */
public class ButtonActionPreference2 extends Preference {

    private CharSequence title;
    private CharSequence summary;
    private CharSequence buttonText;

    private TextView textTitle;
    private TextView textSummary;
    private Button button;

    private OnClickListener listener;

    public ButtonActionPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ButtonActionPreference);

        buttonText = attributes.getString(R.styleable.ButtonActionPreference_button_text);

        setLayoutResource(R.layout.view_preference_button_action);
        attributes.recycle();
    }

    @Override
    public void setTitle(CharSequence title) {
        this.title = title;
        if (textTitle != null) {
            textTitle.setText(title);
        }
    }

    @Override
    public void setSummary(CharSequence summary) {
        this.summary = summary;
        if (textSummary != null) {
            textSummary.setText(summary);
        }
    }

    public void setOnActionListener(OnClickListener listener) {
        this.listener = listener;
        if (button != null) {
            button.setOnClickListener(listener);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        textTitle = (TextView) holder.findViewById(R.id.view_preference_button_action_title);
        textTitle.setText(title != null ? title : getTitle());

        textSummary = (TextView) holder.findViewById(R.id.view_preference_button_action_summary);
        textSummary.setText(summary != null ? summary : getSummary());

        button = (Button) holder.findViewById(R.id.view_preference_button_action_button);
        button.setText(buttonText);
        if (listener != null) {
            button.setOnClickListener(listener);
        }
    }

}
