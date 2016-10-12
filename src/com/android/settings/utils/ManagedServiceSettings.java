/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.utils;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;
import android.view.View;
import android.widget.TextView;

import com.android.internal.logging.MetricsProto;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.notification.EmptyTextSettings;

import java.util.Collections;
import java.util.List;

public abstract class ManagedServiceSettings extends EmptyTextSettings {
    private final Config mConfig;

    protected Context mContext;
    private PackageManager mPM;
    protected ServiceListing mServiceListing;

    abstract protected Config getConfig();

    public ManagedServiceSettings() {
        mConfig = getConfig();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = getActivity();
        mPM = mContext.getPackageManager();
        mServiceListing = new ServiceListing(mContext, mConfig);
        mServiceListing.addCallback(new ServiceListing.Callback() {
            @Override
            public void onServicesReloaded(List<ServiceInfo> services) {
                updateList(services);
            }
        });
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(mContext));
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText(mConfig.emptyText);
    }

    @Override
    public void onResume() {
        super.onResume();
        mServiceListing.reload();
        mServiceListing.setListening(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mServiceListing.setListening(false);
    }

    private void updateList(List<ServiceInfo> services) {
        final PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        Collections.sort(services, new PackageItemInfo.DisplayNameComparator(mPM));
        for (ServiceInfo service : services) {
            final ComponentName cn = new ComponentName(service.packageName, service.name);
            final String title = service.loadLabel(mPM).toString();
            final SwitchPreference pref = new SwitchPreference(getPrefContext());
            pref.setPersistent(false);
            pref.setIcon(service.loadIcon(mPM));
            pref.setTitle(title);
            pref.setChecked(mServiceListing.isEnabled(cn));
            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean enable = (boolean) newValue;
                    return setEnabled(cn, title, enable);
                }
            });
            screen.addPreference(pref);
        }
    }

    protected boolean setEnabled(ComponentName service, String title, boolean enable) {
        if (!enable) {
            // the simple version: disabling
            mServiceListing.setEnabled(service, false);
            return true;
        } else {
            if (mServiceListing.isEnabled(service)) {
                return true; // already enabled
            }
            // show a scary dialog
            new ScaryWarningDialogFragment()
                    .setServiceInfo(service, title, this)
                    .show(getFragmentManager(), "dialog");
            return false;
        }
    }

    public static class ScaryWarningDialogFragment extends InstrumentedDialogFragment {
        static final String KEY_COMPONENT = "c";
        static final String KEY_LABEL = "l";

        @Override
        public int getMetricsCategory() {
            return MetricsProto.MetricsEvent.DIALOG_SERVICE_ACCESS_WARNING;
        }

        public ScaryWarningDialogFragment setServiceInfo(ComponentName cn, String label,
                Fragment target) {
            Bundle args = new Bundle();
            args.putString(KEY_COMPONENT, cn.flattenToString());
            args.putString(KEY_LABEL, label);
            setArguments(args);
            setTargetFragment(target, 0);
            return this;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            final String label = args.getString(KEY_LABEL);
            final ComponentName cn = ComponentName.unflattenFromString(args
                    .getString(KEY_COMPONENT));
            ManagedServiceSettings parent = (ManagedServiceSettings) getTargetFragment();

            final String title = getResources().getString(parent.mConfig.warningDialogTitle, label);
            final String summary = getResources().getString(parent.mConfig.warningDialogSummary,
                    label);
            return new AlertDialog.Builder(getContext())
                    .setMessage(summary)
                    .setTitle(title)
                    .setCancelable(true)
                    .setPositiveButton(R.string.allow,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    parent.mServiceListing.setEnabled(cn, true);
                                }
                            })
                    .setNegativeButton(R.string.deny,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // pass
                                }
                            })
                    .create();
        }
    }

    public static class Config {
        public String tag;
        public String setting;
        public String secondarySetting;
        public String intentAction;
        public String permission;
        public String noun;
        public int warningDialogTitle;
        public int warningDialogSummary;
        public int emptyText;
    }
}
