/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.JsonObject;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog;
import com.ichi2.anki.dialogs.CardBrowserOrderDialog;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.IntegerDialog;
import com.ichi2.anki.dialogs.SimpleMessageDialog;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.widget.WidgetStatus;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import timber.log.Timber;

public class CardBrowser extends NavigationDrawerActivity implements
        DeckDropDownAdapter.SubtitleListener {

    private List<Map<String, String>> mCards;
    private HashMap<String, String> mDeckNames;
    private ArrayList<JSONObject> mDropDownDecks;
    private ListView mCardsListView;
    private SearchView mSearchView;
    private MultiColumnListAdapter mCardsAdapter;
    private String mSearchTerms;
    private String mRestrictOnDeck;

    private MenuItem mSearchItem;
    private MenuItem mSaveSearchItem;
    private MenuItem mMySearchesItem;
    private MenuItem mPreviewItem;

    private Snackbar mUndoSnackbar;

    public static Card sCardBrowserCard;

    // card that was clicked (not marked)
    private long mCurrentCardId;

    private int mOrder;
    private boolean mOrderAsc;
    private int mColumn1Index;
    private int mColumn2Index;

    private long mNewDid;   // for change_deck

    private static final int BACKGROUND_NORMAL = 0;
    private static final int BACKGROUND_MARKED = 1;
    private static final int BACKGROUND_SUSPENDED = 2;
    private static final int BACKGROUND_MARKED_SUSPENDED = 3;

    private static final int EDIT_CARD = 0;
    private static final int ADD_NOTE = 1;
    private static final int DEFAULT_FONT_SIZE_RATIO = 100;
    // Should match order of R.array.card_browser_order_labels
    public static final int CARD_ORDER_NONE = 0;
    private static final String[] fSortTypes = new String[] {
        "",
        "noteFld",
        "noteCrt",
        "noteMod",
        "cardMod",
        "cardDue",
        "cardIvl",
        "cardEase",
        "cardReps",
        "cardLapses"};
    private static final String[] COLUMN1_KEYS = {"question", "sfld"};
    // list of available keys in mCards corresponding to the column names in R.array.browser_column2_headings.
    // Note: the last 6 are currently hidden
    private static final String[] COLUMN2_KEYS = {"answer",
        "card",
        "deck",
        "note",
        "question",
        "tags",
        "lapses",
        "reviews",
        "interval",
        "changed",
        "created",
        "due",
        "ease",
        "edited",
    };
    private long mLastRenderStart = 0;
    private DeckDropDownAdapter mDropDownAdapter;
    private Spinner mActionBarSpinner;
    private TextView mActionBarTitle;
    private boolean mReloadRequired = false;
    private boolean mInMultiSelectMode = false;
    private Set<Integer> mCheckedCardPositions = new LinkedHashSet<>();
    private int mLastSelectedPosition;
    private Menu mActionBarMenu;

    private static final int SNACKBAR_DURATION = 8000;


    // Values related to persistent state data
    private static final long ALL_DECKS_ID = 0L;
    private static String PERSISTENT_STATE_FILE = "DeckPickerState";
    private static String LAST_DECK_ID_KEY = "lastDeckId";


    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private MaterialDialog.ListCallbackSingleChoice mOrderDialogListener =
            new MaterialDialog.ListCallbackSingleChoice() {
        @Override
        public boolean onSelection(MaterialDialog materialDialog, View view, int which,
                CharSequence charSequence) {
            if (which != mOrder) {
                mOrder = which;
                mOrderAsc = false;
                try {
                    if (mOrder == 0) {
                        getCol().getConf().put("sortType", fSortTypes[1]);
                        AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                                .putBoolean("cardBrowserNoSorting", true)
                                .commit();
                    } else {
                        getCol().getConf().put("sortType", fSortTypes[mOrder]);
                        AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                                .putBoolean("cardBrowserNoSorting", false)
                                .commit();
                    }
                    // default to descending for non-text fields
                    if (fSortTypes[mOrder].equals("noteFld")) {
                        mOrderAsc = true;
                    }
                    getCol().getConf().put("sortBackwards", mOrderAsc);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                searchCards();
            } else if (which != CARD_ORDER_NONE) {
                mOrderAsc = !mOrderAsc;
                try {
                    getCol().getConf().put("sortBackwards", mOrderAsc);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                Collections.reverse(mCards);
                updateList();
            }
            return true;
        }
    };


    private DeckTask.TaskListener mRepositionCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Timber.d("CardBrowser::RepositionCardHandler() onPreExecute");
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("CardBrowser::RepositionCardHandler() onPostExecute");
            mReloadRequired = true;
            int cardCount = result.getObjArray().length;
            UIUtils.showThemedToast(CardBrowser.this,
                    getResources().getQuantityString(R.plurals.reposition_card_dialog_acknowledge, cardCount, cardCount), true);
        }
    };

    private DeckTask.TaskListener mResetProgressCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Timber.d("CardBrowser::ResetProgressCardHandler() onPreExecute");
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("CardBrowser::ResetProgressCardHandler() onPostExecute");
            mReloadRequired = true;
            int cardCount = result.getObjArray().length;
            UIUtils.showThemedToast(CardBrowser.this,
                    getResources().getQuantityString(R.plurals.reset_cards_dialog_acknowledge, cardCount, cardCount), true);
        }
    };

    private DeckTask.TaskListener mRescheduleCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Timber.d("CardBrowser::RescheduleCardHandler() onPreExecute");
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("CardBrowser::RescheduleCardHandler() onPostExecute");
            mReloadRequired = true;
            int cardCount = result.getObjArray().length;
            UIUtils.showThemedToast(CardBrowser.this,
                    getResources().getQuantityString(R.plurals.reschedule_cards_dialog_acknowledge, cardCount, cardCount), true);
        }
    };

    private CardBrowserMySearchesDialog.MySearchesDialogListener mMySearchesDialogListener =
            new CardBrowserMySearchesDialog.MySearchesDialogListener() {
        @Override
        public void onSelection(String searchName) {
            Timber.d("OnSelection using search named: %s", searchName);
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            Timber.d("SavedFilters are %s", savedFiltersObj.toString());
            if (savedFiltersObj != null) {
                mSearchTerms = savedFiltersObj.optString(searchName);
                Timber.d("OnSelection using search terms: %s", mSearchTerms);
                mSearchView.setQuery(mSearchTerms, false);
                mSearchItem.expandActionView();
                searchCards();
            }
        }

        @Override
        public void onRemoveSearch(String searchName) {
            Timber.d("OnRemoveSelection using search named: %s", searchName);
            try {
                JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
                if (savedFiltersObj != null && savedFiltersObj.has(searchName)) {
                    savedFiltersObj.remove(searchName);
                    getCol().getConf().put("savedFilters", savedFiltersObj);
                    getCol().flush();
                    if (savedFiltersObj.length() == 0) {
                        mMySearchesItem.setVisible(false);
                    }
                }

            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onSaveSearch(String searchName, String searchTerms) {
            if (TextUtils.isEmpty(searchName)) {
                UIUtils.showThemedToast(CardBrowser.this,
                        getString(R.string.card_browser_list_my_searches_new_search_error_empty_name), true);
                return;
            }
            try {
                JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
                boolean should_save = false;
                if (savedFiltersObj == null) {
                    savedFiltersObj = new JSONObject();
                    savedFiltersObj.put(searchName, searchTerms);
                    should_save = true;
                } else if (!savedFiltersObj.has(searchName)) {
                    savedFiltersObj.put(searchName, searchTerms);
                    should_save = true;
                } else {
                    UIUtils.showThemedToast(CardBrowser.this,
                            getString(R.string.card_browser_list_my_searches_new_search_error_dup), true);
                }
                if (should_save) {
                    getCol().getConf().put("savedFilters", savedFiltersObj);
                    getCol().flush();
                    mSearchView.setQuery("", false);
                    mMySearchesItem.setVisible(true);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    };


    private void onSearch() {
        mSearchTerms = mSearchView.getQuery().toString();
        if (mSearchTerms.length() == 0) {
            mSearchView.setQueryHint(getResources().getString(R.string.downloaddeck_search));
        }
        searchCards();
    }

    private long[] getSelectedCardIds() {
        long[] ids = new long[mCheckedCardPositions.size()];
        int count = 0;
        for (int cardPosition : mCheckedCardPositions) {
            ids[count++] = Long.valueOf(mCards.get(cardPosition).get("id"));
        }
        return ids;
    }

    private void changeDeck(int selectedDeck) {
        long[] ids = getSelectedCardIds();
        try {
            mNewDid = mDropDownDecks.get(selectedDeck).getLong("id");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (ids.length == 0) {
            endMultiSelectMode();
            mCardsAdapter.notifyDataSetChanged();
            return;
        }

        if (CardUtils.isIn(ids, getReviewerCardId())) {
            mReloadRequired = true;
        }

        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI, mChangeDeckHandler,
                new DeckTask.TaskData(new Object[]{ids, Collection.DismissType.CHANGE_DECK_MULTI, mNewDid}));
    }

    private Long getLastDeckId() {
        SharedPreferences state = getSharedPreferences(PERSISTENT_STATE_FILE,0);
        if (!state.contains(LAST_DECK_ID_KEY)) {
            return null;
        }
        return state.getLong(LAST_DECK_ID_KEY, -1);
    }

    public static void clearLastDeckId() {
        Context context = AnkiDroidApp.getInstance();
        context.getSharedPreferences(PERSISTENT_STATE_FILE,0).edit().remove(LAST_DECK_ID_KEY).apply();
    }

    private void saveLastDeckId(Long id) {
        if (id == null) {
            clearLastDeckId();
            return;
        }
        getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit().putLong(LAST_DECK_ID_KEY, id).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("onCreate()");
        setContentView(R.layout.card_browser);
        initNavigationDrawer(findViewById(android.R.id.content));
        startLoadingCollection();
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        Timber.d("onCollectionLoaded()");
        mDeckNames = new HashMap<>();
        for (long did : getCol().getDecks().allIds()) {
            mDeckNames.put(String.valueOf(did), getCol().getDecks().name(did));
        }
        registerExternalStorageListener();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        // Load reference to action bar title
        mActionBarTitle = (TextView) findViewById(R.id.toolbar_title);

        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = getCol().getDecks().allSorted();
        mDropDownAdapter = new DeckDropDownAdapter(this, mDropDownDecks);
        ActionBar mActionBar = getSupportActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayShowTitleEnabled(false);
        }
        mActionBarSpinner = (Spinner) findViewById(R.id.toolbar_spinner);
        mActionBarSpinner.setAdapter(mDropDownAdapter);
        mActionBarSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectDropDownItem(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
        mActionBarSpinner.setVisibility(View.VISIBLE);

        try {
            mOrder = CARD_ORDER_NONE;
            String colOrder = getCol().getConf().getString("sortType");
            for (int c = 0; c < fSortTypes.length; ++c) {
                if (fSortTypes[c].equals(colOrder)) {
                    mOrder = c;
                    break;
                }
            }
            if (mOrder == 1 && preferences.getBoolean("cardBrowserNoSorting", false)) {
                mOrder = 0;
            }
            mOrderAsc = Upgrade.upgradeJSONIfNecessary(getCol(), getCol().getConf(), "sortBackwards", false);
            // default to descending for non-text fields
            if (fSortTypes[mOrder].equals("noteFld")) {
                mOrderAsc = !mOrderAsc;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        mCards = new ArrayList<>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);
        // Create a spinner for column1
        Spinner cardsColumn1Spinner = (Spinner) findViewById(R.id.browser_column1_spinner);
        ArrayAdapter<CharSequence> column1Adapter = ArrayAdapter.createFromResource(this,
                R.array.browser_column1_headings, android.R.layout.simple_spinner_item);
        column1Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cardsColumn1Spinner.setAdapter(column1Adapter);
        mColumn1Index = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserColumn1", 0);
        cardsColumn1Spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // If a new column was selected then change the key used to map from mCards to the column TextView
                if (pos != mColumn1Index) {
                    mColumn1Index = pos;
                    AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                            .putInt("cardBrowserColumn1", mColumn1Index).commit();
                    String[] fromMap = mCardsAdapter.getFromMapping();
                    fromMap[0] = COLUMN1_KEYS[mColumn1Index];
                    mCardsAdapter.setFromMapping(fromMap);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do Nothing
            }
        });
        // Load default value for column2 selection
        mColumn2Index = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserColumn2", 0);
        // Setup the column 2 heading as a spinner so that users can easily change the column type
        Spinner cardsColumn2Spinner = (Spinner) findViewById(R.id.browser_column2_spinner);
        ArrayAdapter<CharSequence> column2Adapter = ArrayAdapter.createFromResource(this,
                R.array.browser_column2_headings, android.R.layout.simple_spinner_item);
        column2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cardsColumn2Spinner.setAdapter(column2Adapter);
        // Create a new list adapter with updated column map any time the user changes the column
        cardsColumn2Spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // If a new column was selected then change the key used to map from mCards to the column TextView
                if (pos != mColumn2Index) {
                    mColumn2Index = pos;
                    AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                            .putInt("cardBrowserColumn2", mColumn2Index).commit();
                    String[] fromMap = mCardsAdapter.getFromMapping();
                    fromMap[1] = COLUMN2_KEYS[mColumn2Index];
                    mCardsAdapter.setFromMapping(fromMap);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do Nothing
            }
        });
        // get the font and font size from the preferences
        int sflRelativeFontSize = preferences.getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO);
        String sflCustomFont = preferences.getString("browserEditorFont", "");
        // make a new list adapter mapping the data in mCards to column1 and column2 of R.layout.card_item_browser
        mCardsAdapter = new MultiColumnListAdapter(
                this,
                R.layout.card_item_browser,
                new String[] {COLUMN1_KEYS[mColumn1Index], COLUMN2_KEYS[mColumn2Index]},
                new int[] {R.id.card_sfld, R.id.card_column2},
                "flags",
                sflRelativeFontSize,
                sflCustomFont);
        // link the adapter to the main mCardsListView
        mCardsListView.setAdapter(mCardsAdapter);
        // make the items (e.g. question & answer) render dynamically when scrolling
        mCardsListView.setOnScrollListener(new RenderOnScroll());
        // set the spinner index
        cardsColumn1Spinner.setSelection(mColumn1Index);
        cardsColumn2Spinner.setSelection(mColumn2Index);


        mCardsListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mInMultiSelectMode) {
                    // click on whole cell triggers select
                    CheckBox cb = (CheckBox) view.findViewById(R.id.card_checkbox);
                    cb.toggle();
                    onCheck(position, view);
                } else {
                    // load up the card selected on the list
                    mCurrentCardId = Long.parseLong(getCards().get(position).get("id"));
                    sCardBrowserCard = getCol().getCard(mCurrentCardId);
                    // start note editor using the card we just loaded
                    Intent editCard = new Intent(CardBrowser.this, NoteEditor.class);
                    editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_EDIT);
                    editCard.putExtra(NoteEditor.EXTRA_CARD_ID, sCardBrowserCard.getId());
                    startActivityForResultWithAnimation(editCard, EDIT_CARD, ActivityTransitionAnimation.LEFT);
                }
            }
        });
        mCardsListView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int position, long id) {
                mLastSelectedPosition = position;
                loadMultiSelectMode();

                // click on whole cell triggers select
                CheckBox cb = (CheckBox) view.findViewById(R.id.card_checkbox);
                cb.toggle();
                onCheck(position, view);
                recenterListView(view);
                mCardsAdapter.notifyDataSetChanged();
                return true;
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // If a valid value for last deck exists then use it, otherwise use libanki selected deck
        if (getLastDeckId() != null && getLastDeckId() == ALL_DECKS_ID) {
            selectDropDownItem(0);
        } else  if (getLastDeckId() != null && getCol().getDecks().get(getLastDeckId(), false) != null) {
            selectDeckById(getLastDeckId());
        } else {
            selectDeckById(getCol().getDecks().selected());
        }
    }

    @Override
    protected void onStop() {
        Timber.d("onStop()");
        // cancel rendering the question and answer, which has shared access to mCards
        DeckTask.cancelTask(DeckTask.TASK_TYPE_SEARCH_CARDS);
        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(this);
        }
    }


    @Override
    protected void onDestroy() {
        Timber.d("onDestroy()");
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed();
        } else if (mInMultiSelectMode) {
            endMultiSelectMode();
        } else {
            Timber.i("Back key pressed");
            Intent data = new Intent();
            if (mReloadRequired) {
                // Add reload flag to result intent so that schedule reset when returning to note editor
                data.putExtra("reloadRequired", true);
            }
            closeCardBrowser(RESULT_OK, data);
        }
    }

    @Override
    protected void onResume() {
        Timber.d("onResume()");
        super.onResume();
        selectNavigationItem(R.id.nav_browser);
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        Timber.d("onCreateOptionsMenu()");
        mActionBarMenu = menu;
        if (!mInMultiSelectMode) {
            // restore drawer click listener and icon
            restoreDrawerIcon();
            getMenuInflater().inflate(R.menu.card_browser, menu);
            mSaveSearchItem = menu.findItem(R.id.action_save_search);
            mSaveSearchItem.setVisible(false); //the searchview's query always starts empty.
            mMySearchesItem = menu.findItem(R.id.action_list_my_searches);
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            mMySearchesItem.setVisible(savedFiltersObj != null && savedFiltersObj.length() > 0);
            mSearchItem = menu.findItem(R.id.action_search);
            mSearchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    // SearchView doesn't support empty queries so we always reset the search when collapsing
                    mSearchTerms = "";
                    Log.d("测试","搜索");
                    mSearchView.setQuery(mSearchTerms, false);
                    searchCards();
                    // invalidate options menu so that disappeared icons would appear again
                    supportInvalidateOptionsMenu();
                    return true;
                }
            });
            mSearchView = (SearchView) mSearchItem.getActionView();
            mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String newText) {
                    mSaveSearchItem.setVisible(!TextUtils.isEmpty(newText));
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String query) {
                    onSearch();
                    mSearchView.clearFocus();
                    return true;
                }
            });
            mSearchView.setOnSearchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Provide SearchView with the previous search terms
                    mSearchView.setQuery(mSearchTerms, false);
                }
            });
        } else {
            // multi-select mode
            getMenuInflater().inflate(R.menu.card_browser_multiselect, menu);
            showBackIcon();
            updateMultiselectMenu();
        }

        if (mActionBarMenu != null && mActionBarMenu.findItem(R.id.action_undo) != null) {
            MenuItem undo =  mActionBarMenu.findItem(R.id.action_undo);
            undo.setVisible(getCol().undoAvailable());
            undo.setTitle(getResources().getString(R.string.studyoptions_congrats_undo, getCol().undoName(getResources())));
        }

        // Maybe we were called from ACTION_PROCESS_TEXT.
        // In that case we already fill in the search.
        Intent intent = getIntent();
        Compat compat = CompatHelper.getCompat();
        if (intent.getAction() == compat.ACTION_PROCESS_TEXT) {
            CharSequence search = intent.getCharSequenceExtra(compat.EXTRA_PROCESS_TEXT);
            if (search != null && search.length() != 0) {
                Timber.d("CardBrowser :: Called with search intent: %s", search.toString());
                mSearchView.setQuery(search, true);
                intent.setAction(Intent.ACTION_DEFAULT);
            }
        }

        mPreviewItem = menu.findItem(R.id.action_preview);
        updatePreviewMenuItem();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onNavigationPressed() {
        if (mInMultiSelectMode) {
            endMultiSelectMode();
        } else {
            super.onNavigationPressed();
        }
    }

    private void updatePreviewMenuItem() {
        if (mPreviewItem == null) {
            return;
        }
        mPreviewItem.setVisible(getCards().size() > 0);
    }

    private void updateMultiselectMenu() {
        if (mActionBarMenu == null || mActionBarMenu.findItem(R.id.action_suspend_card) == null) {
            return;
        }

        if (!mCheckedCardPositions.isEmpty()) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CHECK_CARD_SELECTION,
                    mCheckSelectedCardsHandler,
                    new DeckTask.TaskData(new Object[]{mCheckedCardPositions, getCards()}));
        }

        if (mCheckedCardPositions.size() < getCards().size()) {
            mActionBarMenu.findItem(R.id.action_select_all).setTitle(R.string.card_browser_select_all);
        } else {
            mActionBarMenu.findItem(R.id.action_select_all).setTitle(R.string.card_browser_select_none);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }

        // dismiss undo-snackbar if shown to avoid race condition
        // (when another operation will be performed on the model, it will undo the latest operation)
        if (mUndoSnackbar != null && mUndoSnackbar.isShown())
            mUndoSnackbar.dismiss();

        switch (item.getItemId()) {
            case android.R.id.home:
                endMultiSelectMode();
                return true;
            case R.id.action_add_card_from_card_browser: {
                Intent intent = new Intent(CardBrowser.this, NoteEditor.class);
                intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_ADD);
                startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.LEFT);
                return true;
            }

            case R.id.action_save_search: {
                String searchTerms = mSearchView.getQuery().toString();
                showDialogFragment(CardBrowserMySearchesDialog.newInstance(null, mMySearchesDialogListener,
                        searchTerms, CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE));
                return true;
            }

            case R.id.action_list_my_searches: {
                JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
                HashMap<String, String> savedFilters = new HashMap<>();
                if (savedFiltersObj != null) {
                    Iterator<String> it = savedFiltersObj.keys();
                    while (it.hasNext()) {
                        String searchName = it.next();
                        savedFilters.put(searchName, savedFiltersObj.optString(searchName));
                    }
                }
                showDialogFragment(CardBrowserMySearchesDialog.newInstance(savedFilters, mMySearchesDialogListener,
                        "", CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_LIST));
                return true;
            }

            case R.id.action_sort_by_size:
                showDialogFragment(CardBrowserOrderDialog
                        .newInstance(mOrder, mOrderAsc, mOrderDialogListener));
                return true;

            case R.id.action_show_marked:
                mSearchTerms = "tag:marked";
                mSearchView.setQuery("", false);
                mSearchView.setQueryHint(getResources().getString(R.string.card_browser_show_marked));
                searchCards();
                return true;

            case R.id.action_show_suspended:
                mSearchTerms = "is:suspended";
                mSearchView.setQuery("", false);
                mSearchView.setQueryHint(getResources().getString(R.string.card_browser_show_suspended));
                searchCards();
                return true;

            case R.id.action_search_by_tag:
                showTagsDialog();
                return true;

            case R.id.action_delete_card:
                if (mInMultiSelectMode) {
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI,
                            mDeleteNoteHandler,
                            new DeckTask.TaskData(new Object[]{getSelectedCardIds(), Collection.DismissType.DELETE_NOTE_MULTI}));

                    mCheckedCardPositions.clear();
                    endMultiSelectMode();
                    mCardsAdapter.notifyDataSetChanged();
                }
                return true;

            case R.id.action_mark_card:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI,
                        mMarkCardHandler,
                        new DeckTask.TaskData(new Object[]{getSelectedCardIds(), Collection.DismissType.MARK_NOTE_MULTI}));

                return true;


            case R.id.action_suspend_card:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI,
                        mSuspendCardHandler,
                        new DeckTask.TaskData(new Object[]{getSelectedCardIds(), Collection.DismissType.SUSPEND_CARD_MULTI}));

                return true;

            case R.id.action_change_deck: {
                AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
                builderSingle.setTitle(getString(R.string.move_all_to_deck));

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, R.layout.dropdown_deck_item);
                for (JSONObject deck : mDropDownDecks) {
                    try {
                        arrayAdapter.add(deck.getString("name"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                builderSingle.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeDeck(which);
                    }
                });
                builderSingle.show();

                return true;
            }

            case R.id.action_undo:
                if (getCol().undoAvailable()) {
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoHandler);
                }
                return true;
            case R.id.action_select_all:
                onCheckAll();
                return true;

            case R.id.action_preview: {
                Intent previewer = new Intent(CardBrowser.this, Previewer.class);
                if (mInMultiSelectMode && mCheckedCardPositions.size() > 1) {
                    // Multiple cards have been explicitly selected, so preview only those cards
                    previewer.putExtra("index", 0);
                    previewer.putExtra("cardList", getSelectedCardIds());
                } else {
                    // Preview all cards, starting from the one that is currently selected
                    int startIndex = mCheckedCardPositions.isEmpty() ? 0: mCheckedCardPositions.iterator().next();
                    previewer.putExtra("index", startIndex);
                    previewer.putExtra("cardList", getAllCardIds());
                }
                startActivityWithoutAnimation(previewer);

                return true;
            }

            case R.id.action_reset_cards_progress: {
                Timber.i("NoteEditor:: Reset progress button pressed");
                // Show confirmation dialog before resetting card progress
                ConfirmationDialog dialog = new ConfirmationDialog();
                String title = getString(R.string.reset_card_dialog_title);
                String message = getString(R.string.reset_card_dialog_message);
                dialog.setArgs(title, message);
                Runnable confirm = () -> {
                    Timber.i("CardBrowser:: ResetProgress button pressed");
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI, mResetProgressCardHandler,
                            new DeckTask.TaskData(new Object[]{getSelectedCardIds(), Collection.DismissType.RESET_CARDS}));
                };
                dialog.setConfirm(confirm);
                showDialogFragment(dialog);
                return true;
            }
            case R.id.action_reschedule_cards: {
                Timber.i("CardBrowser:: Reschedule button pressed");
                IntegerDialog rescheduleDialog = new IntegerDialog();
                rescheduleDialog.setArgs(
                        getString(R.string.reschedule_card_dialog_title),
                        getString(R.string.reschedule_card_dialog_message),
                        4);
                rescheduleDialog.setCallbackRunnable(rescheduleDialog.new IntRunnable() {
                    public void run() {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI, mRescheduleCardHandler,
                                new DeckTask.TaskData(new Object[]{getSelectedCardIds(), Collection.DismissType.RESCHEDULE_CARDS, this.getInt()}));
                    }
                });
                showDialogFragment(rescheduleDialog);
                return true;
            }
            case R.id.action_reposition_cards: {
                Timber.i("CardBrowser:: Reposition button pressed");

                // Only new cards may be repositioned
                long[] cardIds = getSelectedCardIds();
                for (int i = 0; i < cardIds.length; i++) {
                    if (getCol().getCard(cardIds[i]).getQueue() != Card.TYPE_NEW) {
                        SimpleMessageDialog dialog = SimpleMessageDialog.newInstance(
                                getString(R.string.vague_error),
                                getString(R.string.reposition_card_not_new_error),
                                false);
                        showDialogFragment(dialog);
                        return false;
                    }
                }

                IntegerDialog repositionDialog = new IntegerDialog();
                repositionDialog.setArgs(
                        getString(R.string.reposition_card_dialog_title),
                        getString(R.string.reposition_card_dialog_message),
                        5);
                repositionDialog.setCallbackRunnable(repositionDialog.new IntRunnable() {
                    public void run() {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI, mRepositionCardHandler,
                                new DeckTask.TaskData(new Object[] {cardIds, Collection.DismissType.REPOSITION_CARDS, this.getInt()}));
                    }
                });
                showDialogFragment(repositionDialog);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // FIXME:
        Timber.d("onActivityResult(requestCode=%d, resultCode=%d)", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
        }

        if (requestCode == EDIT_CARD && resultCode != RESULT_CANCELED) {
            Timber.i("CardBrowser:: CardBrowser: Saving card...");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler,
                    new DeckTask.TaskData(sCardBrowserCard, false));
        } else if (requestCode == ADD_NOTE && resultCode == RESULT_OK) {
            if (mSearchView != null) {
                mSearchTerms = mSearchView.getQuery().toString();
                searchCards();
            } else {
                Timber.w("Note was added from browser and on return mSearchView == null");
            }

        }

        if (requestCode == EDIT_CARD &&  data!=null && data.hasExtra("reloadRequired")) {
            // if reloadRequired flag was sent from note editor then reload card list
            searchCards();
            // in use by reviewer?
            if (getReviewerCardId() == mCurrentCardId) {
                mReloadRequired = true;
            }
        }

        invalidateOptionsMenu();    // maybe the availability of undo changed
    }


    // We spawn DeckTasks that may create memory pressure, this transmits it so polling isCancelled sees the pressure
    @Override
    public void onTrimMemory(int pressureLevel) {
        DeckTask.cancelTask();
    }

    private long getReviewerCardId() {
        if (getIntent().hasExtra("currentCard")) {
            return getIntent().getExtras().getLong("currentCard");
        } else {
            return -1;
        }
    }

    private void showTagsDialog() {
        TagsDialog dialog = TagsDialog.newInstance(
                TagsDialog.TYPE_FILTER_BY_TAG, new ArrayList<String>(), new ArrayList<>(getCol().getTags().all()));
        dialog.setTagsDialogListener(new TagsDialogListener() {
            @Override
            public void onPositive(List<String> selectedTags, int option) {
                mSearchView.setQuery("", false);
                String tags = selectedTags.toString();
                mSearchView.setQueryHint(getResources().getString(R.string.card_browser_tags_shown,
                        tags.substring(1, tags.length() - 1)));
                StringBuilder sb = new StringBuilder();
                switch (option) {
                    case 1:
                        sb.append("is:new ");
                        break;
                    case 2:
                        sb.append("is:due ");
                        break;
                    default:
                        // Logging here might be appropriate : )
                        break;
                }
                int i = 0;
                for (String tag : selectedTags) {
                    if (i != 0) {
                        sb.append("or ");
                    } else {
                        sb.append("("); // Only if we really have selected tags
                    }
                    sb.append("tag:").append(tag).append(" ");
                    i++;
                }
                if (i > 0) {
                    sb.append(")"); // Only if we added anything to the tag list
                }
                mSearchTerms = sb.toString();
                searchCards();
            }
        });
        showDialogFragment(dialog);
    }


    public void selectDropDownItem(int position) {
        mActionBarSpinner.setSelection(position);
        if (position == 0) {
            mRestrictOnDeck = "";
            saveLastDeckId(ALL_DECKS_ID);
        } else {
            JSONObject deck = mDropDownDecks.get(position - 1);
            try {
                mRestrictOnDeck = "deck:\"" + deck.getString("name") + "\" ";
                saveLastDeckId(deck.getLong("id"));
            } catch (JSONException e) {
                throw new RuntimeException();
            }
        }
        searchCards();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save current search terms
        savedInstanceState.putString("mSearchTerms", mSearchTerms);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSearchTerms = savedInstanceState.getString("mSearchTerms");
        searchCards();
    }

    private void searchCards() {
        // cancel the previous search & render tasks if still running
        DeckTask.cancelTask(DeckTask.TASK_TYPE_SEARCH_CARDS);
        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
        String searchText;
        if (mSearchTerms == null) {
            mSearchTerms = "";
        }
        if (!"".equals(mSearchTerms) && (mSearchView != null)) {
            mSearchView.setQuery(mSearchTerms, false);
            mSearchItem.expandActionView();
        }
        if (mSearchTerms.contains("deck:")) {
            searchText = mSearchTerms;
        } else {
            searchText = mRestrictOnDeck + mSearchTerms;
        }
        if (colIsOpen() && mCardsAdapter!= null) {
            // clear the existing card list
            getCards().clear();
            mCardsAdapter.notifyDataSetChanged();
            //  estimate maximum number of cards that could be visible (assuming worst-case minimum row height of 20dp)
            int numCardsToRender = (int) Math.ceil(mCardsListView.getHeight()/
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics())) + 5;
            // Perform database query to get all card ids
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SEARCH_CARDS, mSearchCardsHandler, new DeckTask.TaskData(
                    new Object[] { mDeckNames, searchText, ((mOrder != CARD_ORDER_NONE)),  numCardsToRender}));
        }
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        mDropDownAdapter.notifyDataSetChanged();
        updatePreviewMenuItem();

    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    public String getSubtitleText() {
        int count = getCards().size();
        return getResources().getQuantityString(R.plurals.card_browser_subtitle, count, count);
    }


    private Map<Long, Integer> getPositionMap(List<Map<String, String>> list) {
        Map<Long, Integer> positions = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            positions.put(Long.valueOf(list.get(i).get("id")), i);
        }
        return positions;
    }

    // Iterates the drop down decks, and selects the one matching the given id
    private boolean selectDeckById(@NonNull Long deckId) {
        for (int dropDownDeckIdx = 0; dropDownDeckIdx < mDropDownDecks.size(); dropDownDeckIdx++) {
            try {
                if (mDropDownDecks.get(dropDownDeckIdx).getLong("id") == deckId) {
                    selectDropDownItem(dropDownDeckIdx + 1);
                    return true;
                }
            } catch (JSONException e) {
                throw new RuntimeException();
            }
        }
        return false;
    }

    // convenience method for updateCardsInList(...)
    private void updateCardInList(Card card, String updatedCardTags){
        List<Card> cards = new ArrayList<>();
        cards.add(card);
        if (updatedCardTags != null) {
            Map<Long, String> updatedCardTagsMult = new HashMap<>();
            updatedCardTagsMult.put(card.getNid(), updatedCardTags);
            updateCardsInList(cards, updatedCardTagsMult);
        } else {
            updateCardsInList(cards, null);
        }
    }

    /**
     * @param cards Cards that were changed
     * @param updatedCardTags Mapping note id -> updated tags
     */
    private void updateCardsInList(List<Card> cards, Map<Long, String> updatedCardTags) {
        Map<Long, Integer> idToPos = getPositionMap(getCards());
        for (Card c : cards) {
            Note note = c.note();
            // get position in the mCards search results HashMap
            int pos = idToPos.containsKey(c.getId()) ? idToPos.get(c.getId()) : -1;
            if (pos < 0 || pos >= getCards().size()) {
                continue;
            }
            // update tags
            if (updatedCardTags != null) {
                getCards().get(pos).put("tags", updatedCardTags.get(c.getNid()));
            }
            // update sfld
            String sfld = note.getSFld();
            getCards().get(pos).put("sfld", sfld);
            // update Q & A etc
            updateSearchItemQA(getBaseContext(), getCards().get(pos), c);
            // update deck
            String deckName;
            try {
                deckName = getCol().getDecks().get(c.getDid()).getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            getCards().get(pos).put("deck", deckName);
            // update flags (marked / suspended / etc) which determine color
            String flags = Integer.toString((c.getQueue() == -1 ? 1 : 0) + (note.hasTag("marked") ? 2 : 0));
            getCards().get(pos).put("flags", flags);
        }

        updateList();

    }

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            updateCardInList(values[0].getCard(), values[0].getString());
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("Card Browser - mUpdateCardHandler.onPostExecute()");
            if (result.getBoolean()) {
                hideProgressBar();
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };

    private DeckTask.TaskListener mChangeDeckHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("Card Browser - mChangeDeckHandler.onPostExecute()");
            if (result.getBoolean()) {
                hideProgressBar();

                searchCards();
                endMultiSelectMode();
                mCardsAdapter.notifyDataSetChanged();
                invalidateOptionsMenu();    // maybe the availability of undo changed

                // snackbar to offer undo
                String deckName = getCol().getDecks().name(mNewDid);
                mUndoSnackbar = UIUtils.showSnackbar(CardBrowser.this, String.format(getString(R.string.changed_deck_message), deckName), SNACKBAR_DURATION, R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoHandler);
                    }
                }, mCardsListView, null);
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };


    public static void updateSearchItemQA(Context context, Map<String, String> item, Card c) {
        // render question and answer
        Map<String, String> qa = c._getQA(true, true);
        // Render full question / answer if the bafmt (i.e. "browser appearance") setting forced blank result
        if (qa.get("q").equals("") || qa.get("a").equals("")) {
            HashMap<String, String> qaFull = c._getQA(true, false);
            if (qa.get("q").equals("")) {
                qa.put("q", qaFull.get("q"));
            }
            if (qa.get("a").equals("")) {
                qa.put("a", qaFull.get("a"));
            }
        }
        // update the original hash map to include rendered question & answer
        String q = qa.get("q");
        String a = qa.get("a");
        // remove the question from the start of the answer if it exists
        if (a.startsWith(q)) {
            a = a.replaceFirst(Pattern.quote(q), "");
        }
        // put all of the fields in except for those that have already been pulled out straight from the
        // database
        item.put("answer", formatQA(a));
        item.put("card", c.template().optString("name"));
        // item.put("changed",strftime("%Y-%m-%d", localtime(c.getMod())));
        // item.put("created",strftime("%Y-%m-%d", localtime(c.note().getId()/1000)));
        // item.put("due",getDueString(c));
        // item.put("ease","");
        // item.put("edited",strftime("%Y-%m-%d", localtime(c.note().getMod())));
        // interval
        int type = c.getType();
        if (type == 0) {
            item.put("interval", context.getString(R.string.card_browser_interval_new_card));
        } else if (type == 1) {
            item.put("interval", context.getString(R.string.card_browser_interval_learning_card));
        } else {
            item.put("interval", Utils.timeSpan(context, c.getIvl()*86400));
        }
        item.put("lapses", Integer.toString(c.getLapses()));
        item.put("note", c.model().optString("name"));
        item.put("question", formatQA(q));
        item.put("reviews", Integer.toString(c.getReps()));
    }


    private static String formatQA(String txt) {
        /* Strips all formatting from the string txt for use in displaying question/answer in browser */
        String s = txt;
        s = s.replaceAll("<!--.*?-->", "");
        s = s.replace("<br>", " ");
        s = s.replace("<br />", " ");
        s = s.replace("<div>", " ");
        s = s.replace("\n", " ");
        s = s.replaceAll("\\[sound:[^]]+\\]", "");
        s = s.replaceAll("\\[\\[type:[^]]+\\]\\]", "");
        s = Utils.stripHTMLMedia(s);
        s = s.trim();
        return s;
    }

    /**
     * Removes cards from view. Doesn't delete them in model (database).
     */
    private void removeNotesView(Card[] cards) {
        List<Integer> posList = new ArrayList<>();
        long reviewerCardId = getReviewerCardId();
        Map<Long, Integer> idToPos = getPositionMap(getCards());
        for (Card card : cards) {
            int pos = idToPos.containsKey(card.getId()) ? idToPos.get(card.getId()) : -1;
            if (card.getId() == reviewerCardId) {
                mReloadRequired = true;
            }
            if (pos >= 0 && pos < getCards().size()) {
                posList.add(pos);
            }
        }

        // sort in descending order so we can delete all
        Collections.sort(posList, Collections.reverseOrder());

        for (int delPos : posList) {
            getCards().remove(delPos);
        }

        updateList();

    }


    private DeckTask.TaskListener mSuspendCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean()) {
                Card[] cards = (Card[]) result.getObjArray();
                updateCardsInList(Arrays.asList(cards), null);
                updateMultiselectMenu();
                hideProgressBar();
                invalidateOptionsMenu();    // maybe the availability of undo changed
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };

    private DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean()) {
                Card[] cards = (Card[]) result.getObjArray();
                updateCardsInList(CardUtils.getAllCards(CardUtils.getNotes(Arrays.asList(cards))), null);
                updateMultiselectMenu();
                hideProgressBar();
                invalidateOptionsMenu();    // maybe the availability of undo changed
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };

    private DeckTask.TaskListener mDeleteNoteHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            Card[] cards = (Card[]) values[0].getObjArray();
            removeNotesView(cards);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean()) {
                hideProgressBar();
                mActionBarTitle.setText(Integer.toString(mCheckedCardPositions.size()));
                invalidateOptionsMenu();    // maybe the availability of undo changed
                // snackbar to offer undo
                mUndoSnackbar = UIUtils.showSnackbar(CardBrowser.this, getString(R.string.deleted_message), SNACKBAR_DURATION, R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoHandler);
                    }
                }, mCardsListView, null);
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };

    private DeckTask.TaskListener mUndoHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("Card Browser - mUndoHandler.onPostExecute()");
            if (result.getBoolean()) {
                hideProgressBar();
                // reload whole view
                searchCards();
                endMultiSelectMode();
                mCardsAdapter.notifyDataSetChanged();
                updatePreviewMenuItem();
                invalidateOptionsMenu();    // maybe the availability of undo changed
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };

    private DeckTask.TaskListener mSearchCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onProgressUpdate(TaskData... values) {
            if (values[0] != null) {
                mCards = values[0].getCards();
                if(mSearchTerms!=""){
                    for(int i = 0; i < mCards.size(); i++){
                        if(!mCards.get(i).get("question").contains(mSearchTerms)){
                            mCards.remove(i);
                            i--;
                        }
                    }
                }
                System.out.println(mSearchTerms);
                updateList();
            }
        }


        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onPostExecute(TaskData result) {
            if (result != null && mCards != null) {
                Timber.i("CardBrowser:: Completed doInBackgroundSearchCards Successfully");
                updateList();
                if ((mSearchView != null) && !mSearchView.isIconified()) {
                    UIUtils.showSimpleSnackbar(CardBrowser.this, getSubtitleText(), true);
                }
            }
            updatePreviewMenuItem();
            hideProgressBar();
            AlertDialog.Builder builder1 = new AlertDialog.Builder(CardBrowser.this);
            AlertDialog.Builder builder2 = new AlertDialog.Builder(CardBrowser.this);

            if(mCards.size() == 0&&mSearchTerms!="")
            {
                builder1.setTitle("提示").setMessage("没有搜到想要的？试试全网下载？").setPositiveButton("下载", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//确定按钮的点击事件,像服务器发送申请下载消息
                        int[] success = sendRequest("{\"fastq\":\""+ mSearchTerms.toLowerCase()+"\"}" );
                        if(success[0] == 0){
                            builder2.setTitle("提示").setMessage("下载成功").setPositiveButton("刷新", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
//确定按钮的点击事件,像服务器发送申请下载消息
                                    onSearch();
                                }
                            }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
//取消按钮的点击事件
                                }
                            }).show();
                        }
                        else if (success[0] == -1){
                            builder2.setTitle("提示").setMessage("下载失败,请检查单词拼写").setNegativeButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
//取消按钮的点击事件
                                }
                            }).show();
                        }
                        else if (success[0] == -2){
                            builder2.setTitle("提示").setMessage("下载失败,请检查网络").setNegativeButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
//取消按钮的点击事件
                                }
                            }).show();
                        }
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
//取消按钮的点击事件
                    }
                }).show();
            }
        }
    };

    private DeckTask.TaskListener mRenderQAHandler = new DeckTask.TaskListener() {
        @Override
        public void onProgressUpdate(TaskData... values) {
            // Note: This is called every time a card is rendered.
            // It blocks the long-click callback while the task is running, so usage of the task should be minimized
            mCardsAdapter.notifyDataSetChanged();
        }


        @Override
        public void onPreExecute() {
            Timber.d("Starting Q&A background rendering");
        }


        @Override
        public void onPostExecute(TaskData result) {
            if (result != null) {
                hideProgressBar();
                mCardsAdapter.notifyDataSetChanged();
                Timber.d("Completed doInBackgroundRenderBrowserQA Successfuly");
            } else {
                // Might want to do something more proactive here like show a message box?
                Timber.e("doInBackgroundRenderBrowserQA was not successful... continuing anyway");
            }
        }


        @Override
        public void onCancelled() {
            hideProgressBar();
        }
    };

    private DeckTask.TaskListener mCheckSelectedCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            hideProgressBar();

            Object[] resultArr = result.getObjArray();
            boolean hasUnsuspended = (boolean) resultArr[0];
            boolean hasUnmarked = (boolean) resultArr[1];

            if (hasUnsuspended) {
                mActionBarMenu.findItem(R.id.action_suspend_card).setTitle(getString(R.string.card_browser_suspend_card));
                mActionBarMenu.findItem(R.id.action_suspend_card).setIcon(R.drawable.ic_action_suspend);
            } else {
                mActionBarMenu.findItem(R.id.action_suspend_card).setTitle(getString(R.string.card_browser_unsuspend_card));
                mActionBarMenu.findItem(R.id.action_suspend_card).setIcon(R.drawable.ic_action_unsuspend);
            }

            if (hasUnmarked) {
                mActionBarMenu.findItem(R.id.action_mark_card).setTitle(getString(R.string.card_browser_mark_card));
                mActionBarMenu.findItem(R.id.action_mark_card).setIcon(R.drawable.ic_star_outline_white_24dp);
            } else {
                mActionBarMenu.findItem(R.id.action_mark_card).setTitle(getString(R.string.card_browser_unmark_card));
                mActionBarMenu.findItem(R.id.action_mark_card).setIcon(R.drawable.ic_star_white_24dp);
            }
        }
    };


    private void closeCardBrowser(int result) {
        closeCardBrowser(result, null);
    }

    private void closeCardBrowser(int result, Intent data) {
        // Set result and finish
        setResult(result, data);
        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
    }

    /**
     * Render the second column whenever the user stops scrolling
     */
    private final class RenderOnScroll implements AbsListView.OnScrollListener {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            // Show the progress bar if scrolling to given position requires rendering of the question / answer
            int lastVisibleItem = firstVisibleItem + visibleItemCount;
            int size = getCards().size();
            if ((size > 0) && (firstVisibleItem < size) && ((lastVisibleItem - 1) < size)) {
                String firstAns = getCards().get(firstVisibleItem).get("answer");
                // Note: max value of lastVisibleItem is totalItemCount, so need to subtract 1
                String lastAns = getCards().get(lastVisibleItem - 1).get("answer");
                if ("".equals(firstAns) || "".equals(lastAns)) {
                    showProgressBar();
                    // Also start rendering the items on the screen every 300ms while scrolling
                    long currentTime = SystemClock.elapsedRealtime ();
                    if ((currentTime - mLastRenderStart > 300 || lastVisibleItem >= totalItemCount)) {
                        mLastRenderStart = currentTime;
                        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA, mRenderQAHandler,
                                new DeckTask.TaskData(new Object[]{getCards(), firstVisibleItem, visibleItemCount}));
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView listView, int scrollState) {
            // TODO: Try change to RecyclerView as currently gets stuck a lot when using scrollbar on right of ListView
            // Start rendering the question & answer every time the user stops scrolling
            if (scrollState == SCROLL_STATE_IDLE) {
                int startIdx = listView.getFirstVisiblePosition();
                int numVisible = listView.getLastVisiblePosition() - startIdx;
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA, mRenderQAHandler,
                        new DeckTask.TaskData(new Object[]{getCards(), startIdx - 5, 2 * numVisible + 5}));
            }
        }
    }

    private final class MultiColumnListAdapter extends BaseAdapter {
        private final int mResource;
        private String[] mFromKeys;
        private final int[] mToIds;
        private final String mColorFlagKey;
        private float mOriginalTextSize = -1.0f;
        private final int mFontSizeScalePcent;
        private Typeface mCustomTypeface = null;
        private LayoutInflater mInflater;

        public MultiColumnListAdapter(Context context, int resource, String[] from, int[] to, String colorFlagKey,
                                      int fontSizeScalePcent, String customFont) {
            mResource = resource;
            mFromKeys = from;
            mToIds = to;
            mColorFlagKey = colorFlagKey;
            mFontSizeScalePcent = fontSizeScalePcent;
            if (!"".equals(customFont)) {
                mCustomTypeface = AnkiFont.getTypeface(context, customFont);
            }
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }


        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the main container view if it doesn't already exist, and call bindView
            View v;
            if (convertView == null) {
                v = mInflater.inflate(mResource, parent, false);
                final int count = mToIds.length;
                final View[] columns = new View[count];
                for (int i = 0; i < count; i++) {
                    columns[i] = v.findViewById(mToIds[i]);
                }
                v.setTag(columns);
            } else {
                v = convertView;
            }
            bindView(position, v);
            return v;
        }


        private void bindView(final int position, final View v) {
            // Draw the content in the columns
            View[] columns = (View[]) v.getTag();
            final Map<String, String> dataSet = getCards().get(position);
            final int colorIdx = getColor(dataSet.get(mColorFlagKey));
            int[] colors = Themes.getColorFromAttr(CardBrowser.this, new int[]{android.R.attr.colorBackground,
                    R.attr.markedColor, R.attr.suspendedColor, R.attr.markedColor});
            for (int i = 0; i < mToIds.length; i++) {
                TextView col = (TextView) columns[i];
                // set font for column
                setFont(col);
                // set text for column
                col.setText(dataSet.get(mFromKeys[i]));
            }
            // set card's background color
            final int backgroundColor = colors[colorIdx];
            v.setBackgroundColor(backgroundColor);
            // setup checkbox to change color in multi-select mode
            final CheckBox checkBox = (CheckBox) v.findViewById(R.id.card_checkbox);
            // if in multi-select mode, be sure to show the checkboxes
            if(mInMultiSelectMode) {
                checkBox.setVisibility(View.VISIBLE);
                if (mCheckedCardPositions.contains(position)) {
                    checkBox.setChecked(true);
                } else {
                    checkBox.setChecked(false);
                }
                // this prevents checkboxes from showing an animation from selected -> unselected when
                // checkbox was selected, then selection mode was ended and now restarted
                checkBox.jumpDrawablesToCurrentState();
            } else {
                checkBox.setChecked(false);
                checkBox.setVisibility(View.GONE);
            }
            // change bg color on check changed
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onCheck(position, v);
                }
            });
        }

        private void setFont(TextView v) {
            // Set the font and font size for a TextView v
            float currentSize = v.getTextSize();
            if (mOriginalTextSize < 0) {
                mOriginalTextSize = v.getTextSize();
            }
            // do nothing when pref is 100% and apply scaling only once
            if (mFontSizeScalePcent != 100 && Math.abs(mOriginalTextSize - currentSize) < 0.1) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, mOriginalTextSize * (mFontSizeScalePcent / 100.0f));
            }

            if (mCustomTypeface != null) {
                v.setTypeface(mCustomTypeface);
            }
        }

        /**
         * Get the index that specifies the background color of items in the card list based on the String tag
         * @param flag a string flag
         * @return index into TypedArray specifying the background color
         */
        private int getColor(String flag) {
            if (flag == null) {
                return BACKGROUND_NORMAL;
            }
            switch (flag) {
                case "1":
                    return BACKGROUND_SUSPENDED;
                case "2":
                    return  BACKGROUND_MARKED;
                case "3":
                    return  BACKGROUND_MARKED_SUSPENDED;
                default:
                    return BACKGROUND_NORMAL;
            }
        }


        public void setFromMapping(String[] from) {
            mFromKeys = from;
            notifyDataSetChanged();
        }


        public String[] getFromMapping() {
            return mFromKeys;
        }


        @Override
        public int getCount() {
            return getCards().size();
        }


        @Override
        public Object getItem(int position) {
            return getCards().get(position);
        }


        @Override
        public long getItemId(int position) {
            return position;
        }

    }

    private void onCheck(int position, View cell) {
        CheckBox checkBox = (CheckBox) cell.findViewById(R.id.card_checkbox);

        if (checkBox.isChecked()) {
            mCheckedCardPositions.add(position);
        } else {
            mCheckedCardPositions.remove(position);
        }

        updateMultiselectMenu();

        if (mCheckedCardPositions.isEmpty()) {
            // when 0 are selected: end selection mode
            endMultiSelectMode();
        } else {
            mActionBarTitle.setText(Integer.toString(mCheckedCardPositions.size()));
        }
    }

    private void onCheckAll() {
        boolean all = mCheckedCardPositions.size() < getCards().size();
        if (all) {
            for (int i = 0; i < mCards.size(); i++) {
                mCheckedCardPositions.add(i);
            }
        } else {
            mCheckedCardPositions.clear();
        }
        updateMultiselectMenu();
        mActionBarTitle.setText(Integer.toString(mCheckedCardPositions.size()));
        mCardsAdapter.notifyDataSetChanged();
    }

    private List<Map<String, String>> getCards() {
        if (mCards == null) {
            mCards = new ArrayList<>();
        }
        return mCards;
    }

    private long[] getAllCardIds() {
        long[] l = new long[mCards.size()];
        for (int i = 0; i < mCards.size(); i++) {
            l[i] = Long.parseLong(mCards.get(i).get("id"));
        }
        return l;
    }


    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        finishWithoutAnimation();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    /**
     * The views expand / contract when switching between multi-select mode so we manually
     * adjust so that the vertical position of the given view is maintained
     */
    private void recenterListView(@NonNull View view) {
        final int position = mCardsListView.getPositionForView(view);
        // Get the current vertical position of the top of the selected view
        final int top = view.getTop();
        final Handler handler = new Handler();
        // Post to event queue with some delay to give time for the UI to update the layout
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Scroll to the same vertical position before the layout was changed
                mCardsListView.setSelectionFromTop(position, top);
            }
        }, 10);
    }

    /**
     * Turn on Multi-Select Mode so that the user can select multiple cards at once.
     */
    private void loadMultiSelectMode() {
        if (mInMultiSelectMode) {
            return;
        }
        // set in multi-select mode
        mInMultiSelectMode = true;
        // show title and hide spinner
        mActionBarTitle.setVisibility(View.VISIBLE);
        mActionBarTitle.setText(String.valueOf(mCheckedCardPositions.size()));
        mActionBarSpinner.setVisibility(View.GONE);
        // reload the actionbar using the multi-select mode actionbar
        supportInvalidateOptionsMenu();
    }

    /**
     * Turn off Multi-Select Mode and return to normal state
     */
    private void endMultiSelectMode() {
        mCheckedCardPositions.clear();
        mInMultiSelectMode = false;
        // If view which was originally selected when entering multi-select is visible then maintain its position
        View view = mCardsListView.getChildAt(mLastSelectedPosition - mCardsListView.getFirstVisiblePosition());
        if (view != null) {
            recenterListView(view);
        }
        // update adapter to remove check boxes
        mCardsAdapter.notifyDataSetChanged();
        // update action bar
        supportInvalidateOptionsMenu();
        mActionBarSpinner.setVisibility(View.VISIBLE);
        mActionBarTitle.setVisibility(View.GONE);
    }
//    实现思路：
//    sendrequest调用testadd
//    testadd先读取model，得到需要爬取的来源
//    根据来源调用getfromyoudao和getfrombaicizhan进行爬取，将结果放在一个json里
//    添加卡片时判断音频和视频进行下载到本地，并重命名

    public int[] sendRequest(String parameter)
    {
        final int[] success = new int[1];
        Thread thread = new Thread(){
            public void run() {
                try {
                    JSONObject fastq = new JSONObject(parameter);
                    success[0]=-1;
                    int errormsg= testAdd(fastq.getString("fastq"));
                    success[0]=errormsg;
                }catch (Exception e)
                { Log.d("main", e.getMessage());
                    e.printStackTrace();
                }
            };
        };
        thread.start();
        try
        {
            thread.join();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        return success;
    }

    public int testAdd(String str) throws JSONException {
        //线程创建相关
        DeckTask.TaskListener mSaveFactHandler = new DeckTask.TaskListener() {

            @Override
            public void onPreExecute() {

            }

            @Override
            public void onPostExecute(DeckTask.TaskData result) {

            }
        };

        //得到所有的model（记录类型）和col中的当前牌组
        ArrayList<JSONObject> models = getCol().getModels().all();
        JSONObject model = null;
        String basic = "Diego爸爸定制模板";

        //遍历所有model，找到Basic模板，设置为当前model
        for (JSONObject m : models) {
            try {

                if(m.getString("name").equals(basic)) {
                    getCol().getModels().setCurrent(m);
                    model = m;
                    Log.d("main", "testAdd: "+m.toString());
                    break;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        if (model==null)
        {
            model = new JSONObject("{\"sortf\":0,\"did\":1,\"latexPre\":\"\\\\documentclass[12pt]{article}\\n\\\\special{papersize=3in,5in}\\n\\\\usepackage[utf8]{inputenc}\\n\\\\usepackage{amssymb,amsmath}\\n\\\\pagestyle{empty}\\n\\\\setlength{\\\\parindent}{0in}\\n\\\\begin{document}\\n\",\"latexPost\":\"\\\\end{document}\",\"mod\":1581948529,\"usn\":0,\"vers\":[],\"type\":0,\"css\":\".card {\\n font-family: arial;\\n font-size: 20px;\\n text-align: center;\\n color: black;\\n background-color: white;\\n}\\n\",\"name\":\"Diego爸爸定制模板\",\"flds\":[{\"name\":\"单词\",\"ord\":0,\"sticky\":false,\"rtl\":false,\"font\":\"Arial\",\"size\":20,\"media\":[]},{\"name\":\"释义\",\"ord\":1,\"sticky\":false,\"rtl\":false,\"font\":\"Arial\",\"size\":20,\"media\":[]},{\"name\":\"例句\",\"ord\":2,\"sticky\":false,\"rtl\":false,\"font\":\"Arial\",\"size\":20,\"media\":[]},{\"name\":\"图片\",\"ord\":3,\"sticky\":false,\"rtl\":false,\"font\":\"Arial\",\"size\":20,\"media\":[]},{\"name\":\"音频\",\"ord\":4,\"sticky\":false,\"rtl\":false,\"font\":\"Arial\",\"size\":20,\"media\":[]}],\"tmpls\":[{\"name\":\"Diego爸爸定制显示样式\",\"ord\":0,\"qfmt\":\"{{单词}}<br><span class='voice'>{{音频}}<\\/span>\",\"afmt\":\"{{FrontSide}}<hr id=answer>{{例句}}<br>{{图片}}<br>{{释义}}\",\"did\":null,\"bqfmt\":\"\",\"bafmt\":\"\"}],\"tags\":[],\"id\":\"1581948520316\",\"source\":{\"单词\":\"Baicizhan:word\",\"释义\":\"Baicizhan:mean_cn\",\"例句\":\"Baicizhan:st\",\"图片\":\"Baicizhan:img\",\"音频\":\"Baicizhan:sound\"},\"req\":[[0,\"any\",[0,4]]]}\n");
            getCol().getModels().add(model);
        }
        JSONObject source=new JSONObject();
        try {
            Set hs = new HashSet();
            String[] key1= model.getJSONObject("source").getString("单词").split(":");
            hs.add(key1[0]);
            key1=model.getJSONObject("source").getString("释义").split(":");
            hs.add(key1[0]);
            key1=model.getJSONObject("source").getString("例句").split(":");
            hs.add(key1[0]);
            key1=model.getJSONObject("source").getString("图片").split(":");
            hs.add(key1[0]);
            key1=model.getJSONObject("source").getString("音频").split(":");
            hs.add(key1[0]);
            Iterator it = hs.iterator();
            while (it.hasNext()) {
                String s= it.next().toString();
                if (s.equals("Baicizhan")){
                    source.put("Baicizhan",get_from_Baicizhan(str));
                    if(source.getJSONObject("Baicizhan").getInt("errormsg")<0)
                        return source.getJSONObject("Baicizhan").getInt("errormsg");
                }else if (s.equals("Youdao")){
                    source.put("Youdao",get_from_Youdao(str));
                    if(source.getJSONObject("Youdao").getInt("errormsg")<0)
                        return source.getJSONObject("Youdao").getInt("errormsg");
                }
            }
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }


        JSONObject cdeck = getCol().getDecks().current();
        long cdeckid = 1;
        try{
            cdeckid = cdeck.getLong("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        //设置牌组为默认牌组
        try {
            model.put("did",cdeckid);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        //这段没用，可以删除
        /*JSONObject cdeck = getCol().getDecks().current();
        try {
            cdeck.put("mid", model.getLong("id"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }*/

        //解析接受的字符串

        //利用上述模板(model)新建一个Note对象并设置其字段
        Note note = new Note(getCol(),model);
        Iterator iter = model.getJSONObject("source").keys();
        int i=0;
        while (iter.hasNext())
        {
            String[] key= model.getJSONObject("source").getString(iter.next().toString()).split(":");

            if (key[1].equals("img"))
            {
                URL url = null;
                try {
                    url = new URL(source.getJSONObject(key[0]).getString(key[1]));
                    HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5*1000);
                    InputStream inStream = conn.getInputStream();
                    ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len = -1;
                    while( (len=inStream.read(buffer)) != -1){
                        outSteam.write(buffer, 0, len);
                    }
                    outSteam.close();
                    inStream.close();
                    byte[] data = outSteam.toByteArray();
                    File file=new File( Environment.getExternalStorageDirectory()+"/AnkiDroid/collection.media/"+ source.getJSONObject(key[0]).getString("word")+".jpg");
                    FileOutputStream outStream = new FileOutputStream(file);
                    outStream.write(data);
                    outStream.close();
                    getCol().getMedia().addFile(file);
                } catch (Exception e) {
                    i++;
                    continue;
                }
                note.values()[i]="<img src="+source.getJSONObject(key[0]).getString("word")+".jpg>";

            }else if (key[1].equals("sound"))
            {
                URL url = null;
                try {
                    url = new URL(source.getJSONObject(key[0]).getString(key[1]));
                    HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5*1000);
                    InputStream inStream = conn.getInputStream();
                    ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len = -1;
                    while( (len=inStream.read(buffer)) != -1){
                        outSteam.write(buffer, 0, len);
                    }
                    outSteam.close();
                    inStream.close();
                    byte[] data = outSteam.toByteArray();
                    File file=new File( Environment.getExternalStorageDirectory()+"/AnkiDroid/collection.media/"+ source.getJSONObject(key[0]).getString("word")+".mp3");
                    FileOutputStream outStream = new FileOutputStream(file);
                    outStream.write(data);
                    outStream.close();
                    getCol().getMedia().addFile(file);
                } catch (Exception e) {
                    i++;
                    continue;
                }
                note.values()[i]="[sound:"+source.getJSONObject(key[0]).getString("word")+".mp3]";

            } else if (key[1].equals("st")){
                try {
                    note.values()[i]=source.getJSONObject(key[0]).getString(key[1]);
                }catch ( Exception e)
                {
                    i++;
                    continue;
                }
        }
            else{
                note.values()[i]=source.getJSONObject(key[0]).getString(key[1]);
            }
            i++;
        }
        //将更改先保存至Collection，在创建线程写入数据库。
        getCol().getModels().setChanged();

        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, mSaveFactHandler, new DeckTask.TaskData(note));
        return 0;
    }
    public JSONObject get_from_Youdao(String str)
    {
        String url = String.format("http://fanyi.youdao.com/openapi.do?keyfrom=youdaoci&key=694691143&type=data&doctype=json&version=1.1&q=%s", str);
        // 定义一个字符串用来存储网页内容
        String result = "";
        JSONObject result_json=null;
        // 定义一个缓冲字符输入流
        BufferedReader in = null;
        try {
            // 将string转成url对象
            URL realUrl = new URL(url);
            // 初始化一个链接到那个url的连接
            URLConnection connection = realUrl.openConnection();
            // 开始实际的连接
            connection.connect();
            // 初始化 BufferedReader输入流来读取URL的响应
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            // 用来临时存储抓取到的每一行的数据
            String line;
            while ((line = in.readLine()) != null) {
                // 遍历抓取到的每一行并将其存储到result里面
                result += line + "\n";
            }

            result_json=new JSONObject(result);
            result_json=result_json.getJSONObject("basic");
            result_json.put("word",str);
            result_json.put("sound","http://dict.youdao.com/dictvoice?type=0&audio=" +str);// 美音 type=0 英音type=1

            result_json.put("errormsg",0);

        }
        catch (JSONException e){
            result="{\"errormsg\":-1}";
            try {
                result_json=new JSONObject(result);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
    }
        catch (Exception e) {
            System.out.println("发送GET请求出现异常！" + e);
            result="{\"errormsg\":-2}";
            try {
                result_json=new JSONObject(result);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        } // 使用finally来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return result_json;
    }
    public JSONObject get_from_Baicizhan(String str)
    {
        String url = String.format("http://mall.baicizhan.com/ws/search?w=%s", str);
        // 定义一个字符串用来存储网页内容
        String result = "";
        JSONObject result_json=null;
        // 定义一个缓冲字符输入流
        BufferedReader in = null;
        try {
            // 将string转成url对象
            URL realUrl = new URL(url);
            // 初始化一个链接到那个url的连接
            URLConnection connection = realUrl.openConnection();
            // 开始实际的连接
            connection.connect();
            // 初始化 BufferedReader输入流来读取URL的响应
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            // 用来临时存储抓取到的每一行的数据
            String line;
            while ((line = in.readLine()) != null) {
                // 遍历抓取到的每一行并将其存储到result里面
                result += line + "\n";
            }
            result_json=new JSONObject(result);
            result_json.put("sound","http://baicizhan.qiniucdn.com/word_audios/" +str+".mp3");
            result_json.put("errormsg",0);

        }        catch (JSONException e){
            result="{\"errormsg\":-1}";
            try {
                result_json=new JSONObject(result);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
        catch (Exception e) {
            System.out.println("发送GET请求出现异常！" + e);
            result="{\"errormsg\":-2}";
            try {
                result_json=new JSONObject(result);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }// 使用finally来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return result_json;

    }




}
