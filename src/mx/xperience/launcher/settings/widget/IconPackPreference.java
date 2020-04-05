/*
 * Copyright (C) 2017 The Android Open Source Project
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

package mx.xperience.launcher.settings.widget;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;

public class IconPackPreference extends CheckBoxPreference {

    private View appendix;
    private int appendixVisibility = -1;

    public IconPackPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setWidgetLayoutResource(R.layout.preference_widget_radiobutton);
        setLayoutResource(R.layout.preference_icon_pack);
        setIconSpaceReserved(false);
    }

    public IconPackPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context,
                androidx.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public IconPackPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        View summaryContainer = view.findViewById(R.id.summary_container);
        if (summaryContainer != null) {
            summaryContainer.setVisibility(
                TextUtils.isEmpty(getSummary()) ? View.GONE : View.VISIBLE);
            appendix = view.findViewById(R.id.appendix);
            if (appendix != null && appendixVisibility != -1) {
                appendix.setVisibility(appendixVisibility);
            }
        }

        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title != null) {
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
    }

    public void setAppendixVisibility(int visibility) {
        if (appendix != null) {
            appendix.setVisibility(visibility);
        }
        appendixVisibility = visibility;
    }
}