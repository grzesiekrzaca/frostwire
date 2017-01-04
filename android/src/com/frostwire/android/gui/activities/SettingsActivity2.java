/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Grzesiek Rzaca (grzesiekrzaca)
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

package com.frostwire.android.gui.activities;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity2;
import com.frostwire.android.gui.views.preference.NumberPickerPreference;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.offers.Product;
import com.frostwire.android.offers.Products;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.Collection;

/**
 * @author gubatron
 * @author aldenml
 * @author grzesiekrzaca
 */
public final class SettingsActivity2 extends AbstractActivity2
        implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    private static final Logger LOG = Logger.getLogger(SettingsActivity.class);
    private static final boolean INTERNAL_BUILD = false;

    /**
     * When starting this activity, the invoking Intent can contain this extra
     * string to specify which fragment should be initially displayed.
     */
    public static final String EXTRA_SHOW_FRAGMENT =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT;

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specified to supply a Bundle of arguments to pass
     * to that fragment when it is instantiated during the initial creation
     * of the activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS;

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specify to supply the title to be shown for
     * that fragment.
     */
    public static final String EXTRA_SHOW_FRAGMENT_TITLE =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE;

    // keep this field as a starting point to support multipane settings in tablet
    // see PreferenceFragment source code
    private final boolean singlePane;

    private PreferenceFragment currentFragment;

    public SettingsActivity2() {
        super(R.layout.activity_settings);
        singlePane = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String fragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        Bundle fragmentArgs = intent.getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        int fragmentTitle = intent.getIntExtra(EXTRA_SHOW_FRAGMENT_TITLE, 0);

        if (fragmentName == null) {
            fragmentName = SettingsActivity2.Application.class.getName();
        }

        switchToFragment(fragmentName, fragmentArgs, fragmentTitle);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE &&
                data != null &&
                data.hasExtra(BuyActivity.EXTRA_KEY_PURCHASE_TIMESTAMP)) {
            // We (onActivityResult) are invoked before onResume()
            if (currentFragment instanceof Application) {
                ((Application) currentFragment).payment(data.getLongExtra(BuyActivity.EXTRA_KEY_PURCHASE_TIMESTAMP, 0));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        startPreferencePanel(pref.getFragment(), pref.getExtras(), pref.getTitleRes(), null, 0);
        return true;
    }

    private void startPreferencePanel(String fragmentClass, Bundle args, int titleRes,
                                      Fragment resultTo, int resultRequestCode) {
        if (singlePane) {
            startWithFragment(fragmentClass, args, titleRes, resultTo, resultRequestCode);
        } else {
            // check singlePane comment
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private void startWithFragment(String fragmentName, Bundle args, int titleRes,
                                   Fragment resultTo, int resultRequestCode) {
        Intent intent = buildStartFragmentIntent(fragmentName, args, titleRes);
        if (resultTo == null) {
            startActivity(intent);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    private Intent buildStartFragmentIntent(String fragmentName, Bundle args, int titleRes) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, titleRes);
        return intent;
    }

    private void switchToFragment(String fragmentName, Bundle args, int titleRes) {
        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.replace(R.id.activity_settings_content, f);
        transaction.commitAllowingStateLoss();

        currentFragment = (PreferenceFragment) f;
        if (titleRes != 0) {
            setTitle(titleRes);
        }
    }

    public static class Application extends PreferenceFragment {

        private long removeAdsPurchaseTime = 0;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_application);
        }

        @Override
        public void onResume() {
            super.onResume();

            setupStore(removeAdsPurchaseTime);
        }

        private void setupStore(long purchaseTimestamp) {
            Preference p = findPreference("frostwire.prefs.offers.buy_no_ads");
            if (p == null) {
                LOG.warn("No-ads preference not found");
                return;
            }
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                PreferenceScreen s = getPreferenceScreen();
                s.removePreference(p);
            } else {
                final PlayStore playStore = PlayStore.getInstance();
                playStore.refresh();
                final Collection<Product> purchasedProducts = Products.listEnabled(playStore, Products.DISABLE_ADS_FEATURE);
                if (purchaseTimestamp == 0 && purchasedProducts != null && purchasedProducts.size() > 0) {
                    initRemoveAdsSummaryWithPurchaseInfo(p, purchasedProducts);
                } else if (purchaseTimestamp > 0 &&
                        (System.currentTimeMillis() - purchaseTimestamp) < 30000) {
                    p.setSummary(getString(R.string.processing_payment) + "...");
                    p.setOnPreferenceClickListener(null);
                } else {
                    p.setSummary(R.string.remove_ads_description);
                    p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            PlayStore.getInstance().endAsync();
                            Intent intent = new Intent(getActivity(), BuyActivity.class);
                            getActivity().startActivityForResult(intent, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
                            return true;
                        }
                    });
                }
            }
        }

        private void initRemoveAdsSummaryWithPurchaseInfo(Preference p, Collection<Product> purchasedProducts) {
            final Product product = purchasedProducts.iterator().next();
            String daysLeft = "";
            // if it's a one time purchase, show user how many days left she has.
            if (!product.subscription() && product.purchased()) {
                int daysBought = Products.toDays(product.sku());
                if (daysBought > 0) {
                    final int MILLISECONDS_IN_A_DAY = 86400000;
                    long timePassed = System.currentTimeMillis() - product.purchaseTime();
                    int daysPassed = (int) timePassed / MILLISECONDS_IN_A_DAY;
                    if (daysPassed > 0 && daysPassed < daysBought) {
                        daysLeft = " (" + getString(R.string.days_left) + ": " + String.valueOf(daysBought - daysPassed) + ")";
                    }
                }
            }
            p.setSummary(getString(R.string.current_plan) + ": " + product.description() + daysLeft);
            p.setOnPreferenceClickListener(new RemoveAdsOnPreferenceClickListener(getActivity(), purchasedProducts));
        }

        public void payment(long time) {
            removeAdsPurchaseTime = time;
            LOG.info("User just purchased something. removeAdsPurchaseTime=" + removeAdsPurchaseTime);
        }

        private static class RemoveAdsOnPreferenceClickListener implements Preference.OnPreferenceClickListener {
            private int clicksLeftToConsumeProducts = 20;
            private final Collection<Product> purchasedProducts;
            private WeakReference<Activity> activityRef;

            RemoveAdsOnPreferenceClickListener(Activity activity, final Collection<Product> purchasedProducts) {
                activityRef = Ref.weak(activity);
                this.purchasedProducts = purchasedProducts;
            }

            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (purchasedProducts != null && !purchasedProducts.isEmpty()) {
                    LOG.info("Products purchased by user:");
                    for (Product p : purchasedProducts) {
                        LOG.info(" - " + p.description() + " (" + p.sku() + ")");
                    }

                    if (INTERNAL_BUILD) {
                        clicksLeftToConsumeProducts--;
                        LOG.info("If you click again " + clicksLeftToConsumeProducts + " times, all your ONE-TIME purchases will be forced-consumed.");
                        if (clicksLeftToConsumeProducts == 0) {
                            for (Product p : purchasedProducts) {
                                if (p.subscription()) {
                                    continue;
                                }
                                PlayStore.getInstance().consume(p);
                                LOG.info(" - " + p.description() + " (" + p.sku() + ") force-consumed!");
                                UIUtils.showToastMessage(preference.getContext(),
                                        "Product " + p.sku() + " forced-consumed.",
                                        Toast.LENGTH_SHORT);
                            }
                            if (Ref.alive(activityRef)) {
                                activityRef.get().finish();
                            }
                        }
                    }

                    return true; // true = click was handled.
                } else {
                    LOG.info("Couldn't find any purchases.");
                }
                return false;
            }
        }
    }

    public static class Search extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_search);
        }
    }

    public static class Torrent extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_torrent);
        }

        @Override
        public void onResume() {
            super.onResume();
            setupTorrentOptions();
        }

        private void setupTorrentOptions() {
            final BTEngine e = BTEngine.getInstance();
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED, e, 0L, true, null);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED, e, 0L, true, null);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, e, -1L, false, Unit.DOWNLOADS);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOADS, e, null, false, Unit.UPLOADS);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS, e, null, false, Unit.CONNECTIONS);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS, e, null, false, Unit.PEERS);
        }

        private void setupNumericalPreference(final String key, final BTEngine btEngine, final Long unlimitedValue, final boolean byteRate, final Unit unit) {
            final NumberPickerPreference pickerPreference = (NumberPickerPreference) findPreference(key);
            if (pickerPreference != null) {
                pickerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (btEngine != null) {
                            int newVal = (int) newValue;
                            executeBTEngineAction(key, btEngine, newVal);
                            displayNumericalSummaryForPreference(preference, newVal, unlimitedValue, byteRate, unit);
                            return checkBTEngineActionResult(key, btEngine, newVal);
                        }
                        return false;
                    }
                });
                displayNumericalSummaryForPreference(pickerPreference, ConfigurationManager.instance().getLong(key), unlimitedValue, byteRate, unit);
            }
        }

        private void executeBTEngineAction(final String key, final BTEngine btEngine, final int value) {
            switch (key) {
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED:
                    btEngine.downloadRateLimit(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED:
                    btEngine.uploadRateLimit(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS:
                    btEngine.maxActiveDownloads(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOADS:
                    btEngine.maxActiveSeeds(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS:
                    btEngine.maxConnections(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_PEERS:
                    btEngine.maxPeers(value);
                    break;
            }
        }

        private boolean checkBTEngineActionResult(final String key, final BTEngine btEngine, final int value) {
            switch (key) {
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED:
                    return btEngine.downloadRateLimit() == value;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED:
                    return btEngine.uploadRateLimit() == value;
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS:
                    return btEngine.maxActiveDownloads() == value;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOADS:
                    return btEngine.maxActiveSeeds() == value;
                case Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS:
                    return btEngine.maxConnections() == value;
                case Constants.PREF_KEY_TORRENT_MAX_PEERS:
                    return btEngine.maxPeers() == value;
            }
            return false;
        }

        private void displayNumericalSummaryForPreference(Preference preference, long value, Long unlimitedValue, boolean rate, Unit unit) {
            if (unlimitedValue != null && value == unlimitedValue) {
                preference.setSummary(R.string.unlimited);
            } else {
                if (rate) {
                    preference.setSummary(UIUtils.getBytesInHuman(value));
                } else {
                    preference.setSummary(getValueWithUnit(unit, value));
                }
            }
        }

        private String getValueWithUnit(Unit unit, long value) {
            if (unit != null) {
                return getActivity().getResources().getQuantityString(unit.getPluralResource(), (int) value, value);
            }
            return String.valueOf(value);
        }

        public enum Unit {
            DOWNLOADS(R.plurals.unit_downloads),
            UPLOADS(R.plurals.unit_uploads),
            CONNECTIONS(R.plurals.unit_connections),
            PEERS(R.plurals.unit_peers);

            private int pluralResource;

            Unit(int pluralResource) {
                this.pluralResource = pluralResource;
            }

            public int getPluralResource() {
                return pluralResource;
            }
        }

    }

    public static class Other extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_other);
        }
    }
}
