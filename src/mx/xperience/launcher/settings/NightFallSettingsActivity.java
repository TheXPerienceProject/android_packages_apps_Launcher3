/*
 * Copyright (C) 2019 Paranoid Android
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
package mx.xperience.launcher.settings;

import static mx.xperience.launcher.providers.IconPackProvider.PREF_ICON_PACK;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.settings.SettingsActivity;
import com.android.launcher3.settings.SettingsActivity.LauncherSettingsFragment;

import mx.xperience.launcher.NightFallLauncherCallbacks;
import mx.xperience.launcher.XPerienceUtils;

public class NightFallSettingsActivity extends SettingsActivity {

    public static final String MINUS_ONE_KEY = "pref_enable_minus_one";
    public static final String ICON_PACK = "pref_icon_pack";

    /**
     * This fragment shows the launcher NightFall preferences.
     */
    public static class NightFallLauncherSettingsFragment extends LauncherSettingsFragment implements
        Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

        private String mHighLightKey;
        private boolean mPreferenceHighlighted = false;

        private Preference mIconPack;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();
            mHighLightKey = args == null ? null : args.getString(EXTRA_FRAGMENT_ARG_KEY);
            if (rootKey == null && !TextUtils.isEmpty(mHighLightKey)) {
                rootKey = getParentKeyForPref(mHighLightKey);
            }

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.launcher_preferences, rootKey);
            Utilities.getPrefs(getContext()).registerOnSharedPreferenceChangeListener(this);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = screen.getPreference(i);
                // Initiate our preferences then the remaining stock ones
                if (!initNightFallPreference(preference)) {
                    screen.removePreference(preference);
                }
                if (!initPreference(preference)) {
                    screen.removePreference(preference);
                }
            }
            mIconPack = screen.findPreference(ICON_PACK);
            mIconPack.setOnPreferenceClickListener(this);
            updateIconPackSummary();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Utilities.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        }

        protected boolean initNightFallPreference(Preference preference) {
            switch (preference.getKey()) {
                case MINUS_ONE_KEY:
                    return XPerienceUtils.hasPackageInstalled(getActivity(),
                            NightFallLauncherCallbacks.SEARCH_PACKAGE);
                case ICON_PACK:
                    return true;
            }
            return true;
        }

        private String getApplicationName(String packageName) {
            PackageManager pm = getContext().getPackageManager();
            ApplicationInfo ai = null;
            String applicationName = null;
            try {
                ai = pm.getApplicationInfo(packageName, 0);
                applicationName = (String) pm.getApplicationLabel(ai);
            } catch (NameNotFoundException e) {
                applicationName = "System default";
            }
            return applicationName;
        }

        private void updateIconPackSummary() {
            SharedPreferences prefs = Utilities.getPrefs(getContext());
            String packageName = prefs.getString(PREF_ICON_PACK, "");
            mIconPack.setSummary(String.format(
                    getContext().getResources().getString(
                    R.string.icon_pack_summary), getApplicationName(packageName)));

        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case ICON_PACK: {
                    Intent iconPack = new Intent(getActivity(), IconPackSettingsActivity.class);
                    startActivity(iconPack);
                }
                break;
            }
            return false;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PREF_ICON_PACK.equals(key)) {
                updateIconPackSummary();
            }
        }
    }
}
