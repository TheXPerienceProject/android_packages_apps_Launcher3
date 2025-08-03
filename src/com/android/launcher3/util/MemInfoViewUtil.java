/*
 * Copyright (C) 2025 The XPerience Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util;

import android.view.View;

import com.android.launcher3.R;
import com.android.quickstep.views.MemInfoView;

public final class MemInfoViewUtil {

    private MemInfoViewUtil() {}

    public static MemInfoView getMemInfoViewFromView(View rootView) {
        return rootView.findViewById(R.id.meminfo);
    }
}
