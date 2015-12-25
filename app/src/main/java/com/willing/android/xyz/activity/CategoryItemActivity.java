package com.willing.android.xyz.activity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.willing.android.xyz.App;
import com.willing.android.xyz.R;
import com.willing.android.xyz.adapter.SongAdapter;
import com.willing.android.xyz.asynctask.CursorLoadCallback;
import com.willing.android.xyz.asynctask.DbLoader;
import com.willing.android.xyz.entity.Music;
import com.willing.android.xyz.event.ChangeMusicEvent;
import com.willing.android.xyz.event.StartPauseEvent;
import com.willing.android.xyz.service.MusicPlayService;
import com.willing.android.xyz.utils.CategoryUtils;
import com.willing.android.xyz.utils.MusicDbHelper;
import com.willing.android.xyz.utils.MusicUtils;
import com.willing.android.xyz.view.SmallPlayer;

import de.greenrobot.event.EventBus;

/**
 * Created by Willing on 2015/12/11 0011.
 */
public class CategoryItemActivity extends BaseActivity
{
    public static final String CATEGORY_NAME = "category_name";
    private static final int LOAD_ID = 1;

    private String mCategoryName;
    private ListView mPlaylistItemListView;
    private SongAdapter mAdapter;
    private SmallPlayer mSmallPlayer;

    private TextView mPlayAll;

    private ActionMode mActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_song);

        Intent intent = getIntent();
        mCategoryName = (String) intent.getExtras().get(CategoryItemActivity.CATEGORY_NAME);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(mCategoryName);

        initView();
        setupListener();
    }

    private void initView()
    {
        mPlaylistItemListView = (ListView) findViewById(R.id.lv_song);
        mPlayAll = (TextView) findViewById(R.id.tv_play_allsong);
        //        mHeader = findViewById(R.id.ll_header);

        mSmallPlayer = (SmallPlayer) findViewById(R.id.smallPlayer);

        mAdapter = new SongAdapter(this, true, mCategoryName);
        mPlaylistItemListView.setAdapter(mAdapter);

        getSupportLoaderManager().initLoader(LOAD_ID, null, new CursorLoadCallback(this, mAdapter, DbLoader.TYPE_CATEGORY_ITEM, mCategoryName));
    }

    private void setupListener()
    {
        mPlaylistItemListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {

                Cursor cursor = (Cursor) mAdapter.getItem(position);

                Music music = MusicDbHelper.convertCursorToMusic(cursor);

                MusicPlayService service = App.getInstance().getPlayService();
                if (service != null)
                {
                    service.addToPlayList(music, true);
                }

            }
        });

        mPlayAll.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                PlayAllThread thread = new PlayAllThread(mCategoryName);

                thread.start();
            }
        });

        mPlaylistItemListView.setMultiChoiceModeListener(new CategoryItemMultiChoiceModeListener());
        mPlaylistItemListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

    }

    @Override
    public void onBackPressed()
    {
        super.onBackPressed();

        overridePendingTransition(0, 0);
    }


    @Override
    protected void onStart()
    {
        super.onStart();

        EventBus.getDefault().registerSticky(this);
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        if (mActionMode != null)
        {
            finishActionMode();

            mActionMode.finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            finish();
            overridePendingTransition(0, 0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onEventMainThread(ChangeMusicEvent event)
    {
        mSmallPlayer.updateState(App.getInstance().getPlayService().getPlayingMusic(), true);
    }

    public void onEventMainThread(StartPauseEvent event)
    {
        mSmallPlayer.setPlaying(App.getInstance().getPlayService().isPlaying());
    }

    private void finishActionMode()
    {
        mAdapter.setActionModeStarted(false);

        mAdapter.notifyDataSetChanged();

        mPlayAll.setVisibility(View.VISIBLE);

        mActionMode = null;
    }


    private static class PlayAllThread extends Thread
    {
        private String mCategoryName;

        public PlayAllThread(String categoryName)
        {
            mCategoryName = categoryName;
        }

        @Override
        public void run()
        {
            MusicPlayService service = App.getInstance().getPlayService();
            if (service != null)
            {
                Context context = App.getInstance();
                if (context != null)
                {
                    Cursor cursor = MusicDbHelper.queryCategoryItem(context, mCategoryName);
                    service.addToPlayList(cursor, true);
                }
            }
        }
    }

    private class CategoryItemMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {

            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            finishActionMode();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            mActionMode = mode;

            mode.getMenuInflater().inflate(R.menu.catelog, menu);

            mPlayAll.setVisibility(View.GONE);

            mAdapter.setActionModeStarted(true);

            mAdapter.notifyDataSetChanged();

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            switch (item.getItemId())
            {
                case R.id.delete:

                    MusicUtils.showDeleteMusicsDialog(CategoryItemActivity.this,
                            mPlaylistItemListView.getCheckedItemPositions(), mAdapter, true, mCategoryName);

                    return true;
                case R.id.add_to_catelog:

                    CategoryUtils.showAddToCategorysDialog(CategoryItemActivity.this,
                            mPlaylistItemListView.getCheckedItemPositions(), mAdapter,
                            true, mCategoryName);

                    return true;
            }
            return false;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position,
                                              long id, boolean checked)
        {
            View view = mPlaylistItemListView.getChildAt(position - mPlaylistItemListView.getFirstVisiblePosition());
            CheckBox checkbox = (CheckBox) view.findViewById(R.id.cb_checked);
            checkbox.setChecked(mPlaylistItemListView.isItemChecked(position));
        }
    }
}