/*************************************************************************
FILENAME VoiceRepeater.java

COPYRIGHT (c) 2013

DESCRIPTION
This file define VoiceRepeater, the main activity

--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2013/05/18   ZISEEZHOU       0x00000000       base code
*************************************************************************/
package com.ziseezhou.voicerepeater;

import java.util.Arrays;

import junit.framework.Test;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.app.Activity;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class VoiceRepeater extends ListActivity {
	private static final String TAG = "VoiceRepeater";
	private static final String PREF_KEY_FOLDERSTRING = "pref_folder_key";
	private ImageButton mFilter;
	private ImageButton mCollapser;
	private ImageButton mMediaPrevious;
	private ImageButton mMediaPlayPause;
	private ImageButton mMediaNext;
	private ImageButton mMediaLoop;
	private ImageButton mMediaRewind;
	private ImageButton mMediaText;
	private ImageButton mMediaForward;
	private ImageButton mMediaRepeater;
	private ListView mTrackList;
	private Cursor mTrackCursor;
	private TrackListAdapter mAdapter;
	private boolean mAdapterSent = false;
	private static int mLastListPosCourse = -1;
    private static int mLastListPosFine = -1;
	private boolean mUseLastListPos = false;
	private BroadcastReceiver mSDCardMountEventReceiver = null;
	private boolean mBindLaunched = false;
	protected IVoiceRepeaterService mService = null;
	private String mSortOrder;
	private String[] mCursorCols;
	private String mFilterFolderString;
	private TextView mTotalTime;
	private TextView mCurrentTime;
	private ProgressBar mProgress;
	private long mDuration;
	private boolean mRefreshShouldPause;
	private long mLastSeekEventTime;
	private long mPosOverride = -1;
	private boolean mFromTouch = false;
	
	private static final int MSG_UPDATE_UI = 0;
    private static final int MSG_UPDATE_TIMER = 1;
    private static final int REFRESH = 2;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.main);
        
        mCursorCols = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION
        };
        
        mFilterFolderString = Utils.getStringPref(this, PREF_KEY_FOLDERSTRING, "");
        
        mFilter = (ImageButton) findViewById(R.id.filter);
        mCollapser = (ImageButton) findViewById(R.id.collapse);
        mTotalTime = (TextView) findViewById(R.id.duration);
        mCurrentTime = (TextView) findViewById(R.id.position);
        mProgress = (ProgressBar) findViewById(R.id.progressBar);
        
        mMediaPrevious = (ImageButton) findViewById(R.id.media_previous);
        mMediaPrevious.setOnClickListener(mMediaPreviousListener);
        mMediaPlayPause = (ImageButton) findViewById(R.id.media_playPause);
        mMediaPlayPause.setOnClickListener(mMediaPlayPauseListener);
        mMediaNext = (ImageButton) findViewById(R.id.media_next);
        mMediaNext.setOnClickListener(mMediaNextListener);
        mMediaLoop = (ImageButton) findViewById(R.id.media_loop);
        mMediaLoop.setOnClickListener(mMediaLoopListener);
        mMediaRewind = (ImageButton) findViewById(R.id.media_rewind); 
        mMediaRewind.setOnClickListener(mMediaRewindListener);
        mMediaText = (ImageButton) findViewById(R.id.media_text);
        mMediaText.setOnClickListener(mMediaTextListener);
        mMediaForward = (ImageButton) findViewById(R.id.media_forward);
        mMediaForward.setOnClickListener(mMediaForwardListener);
        mMediaRepeater = (ImageButton) findViewById(R.id.media_repeater);
        mMediaRepeater.setOnClickListener(mMediaRepeaterListener);

        
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);
        
        mFilter.setOnClickListener(mFilterListener);
        mCollapser.setOnClickListener(mCollapserListener);
        findViewById(R.id.filterString).setOnClickListener(mFilterStringListener);
        
        mTrackList = getListView();
        mTrackList.setOnCreateContextMenuListener(this);
        mTrackList.setCacheColorHint(0);
        mTrackList.setVerticalFadingEdgeEnabled(true);
        mTrackList.setFastScrollEnabled(false);
        
        mAdapter = (TrackListAdapter) getLastNonConfigurationInstance();
        
        if (mAdapter != null) {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
        }
        
        enableTools(false);
        
        registerExternalStorageListener();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.voice_repeater, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_exit: {
                // exit
                Intent i = new Intent(VoiceRepeaterService.SERVICECMD);
                i.putExtra("command", "stop");
                sendBroadcast(i);
                finish();
                return true;
            }
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    public void onServiceConnected(ComponentName name, IBinder service)
    {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);

        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new TrackListAdapter(
                    getApplication(), // need to use application context to avoid leaks
                    this,
                    R.layout.track_list_item,
                    null, // cursor
                    new String[] {},
                    new int[] {},
                    false,
                    true);
            setListAdapter(mAdapter);
            //setTitle(R.string.working_songs);
            getTrackCursor(mAdapter.getQueryHandler(), null, true);
        } else {
            mTrackCursor = mAdapter.getCursor();
            // If mTrackCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mTrackCursor != null) {
                init(mTrackCursor, false);
            } else {
                //setTitle(R.string.working_songs);
                getTrackCursor(mAdapter.getQueryHandler(), null, true);
            }
        }
    }
    
    public void onServiceDisconnected(ComponentName name) {
        // we can't really function without the service, so don't
        finish();
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        TrackListAdapter a = mAdapter;
        mAdapterSent = true;
        return a;
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	updateUI();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        Log.v(TAG, ">>> onStart");
        
        mRefreshShouldPause = false;

        IntentFilter f = new IntentFilter();
        f.addAction(VoiceRepeaterService.PLAYSTATE_CHANGED);
        f.addAction(VoiceRepeaterService.META_CHANGED);
        f.addAction(VoiceRepeaterService.REFRESH);
        f.addAction(VoiceRepeaterService.EXIT);
        registerReceiver(mStatusListener, new IntentFilter(f));
        updateTrackInfo();
        //long next = refreshNow();
        //queueNextRefresh(next);
        
        if (bindService(new Intent(this, VoiceRepeaterService.class),
                mConnection, Context.BIND_AUTO_CREATE)) {
            mBindLaunched = true;
            Log.v(TAG, ">>> onStart bind launched successful");
        }
    }
    
    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(VoiceRepeaterService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                //setPauseButtonImage();
                //queueNextRefresh(1);
            } else if (action.equals(VoiceRepeaterService.PLAYSTATE_CHANGED)) {
                //setPauseButtonImage();
            }
            
            postUpdateUI();
        }
    };
    
    @Override
    public void onStop() {
        mRefreshShouldPause = false;
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        mService = null;
        Log.v(TAG, ">>> onStop");
        
        if (mBindLaunched) {
            Log.v(TAG, ">>> onStop unbind");
            unbindService(mConnection);
        }
        
        super.onStop();
    }
    
    @Override
    protected void onDestroy() {
    	ListView lv = getListView();
        if (lv != null) {
            if (mUseLastListPos) {
                mLastListPosCourse = lv.getFirstVisiblePosition();
                View cv = lv.getChildAt(0);
                if (cv != null) {
                    mLastListPosFine = cv.getTop();
                }
            }
        }
        
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }
        
        if (null != mService) {
            try {
                //mService.unregisterCallback(mCallback);
            }catch (Exception e) { e.printStackTrace(); }
        }

        try {
        	unregisterReceiverSafe(mTrackListListener);
        } catch (IllegalArgumentException ex) {
            // we end up here in case we never registered the listeners
        }
        
        // If we have an adapter and didn't send it off to another activity yet, we should
        // close its cursor, which we do by assigning a null cursor to it. Doing this
        // instead of closing the cursor directly keeps the framework from accessing
        // the closed cursor later.
        if (!mAdapterSent && mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        setListAdapter(null);
        mAdapter = null;
        unregisterReceiverSafe(mScanListener);
    	super.onDestroy();
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        if (mTrackCursor.getCount() == 0) {
            return;
        }
       
        //Utils.playAll(this, mTrackCursor, position);
        long [] list = Utils.getSongListForCursor(mTrackCursor);
        if (mService != null) {
            if (list.length == 0l) {
                Log.e(TAG, ">>> attempt to play empty song list");
                // Don't try to play empty playlists. Nothing good will come of it.
                String message = getString(R.string.listIsNull);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                long curid = mService.getAudioId();
                int curpos = mService.getQueuePosition();
                if (position != -1 && curpos == position && curid == list[position]) {
                    // The selected file is the file that's currently playing;
                    // figure out if we need to restart with a new playlist,
                    // or just launch the playback activity.
                    long [] playlist = mService.getQueue();
                    if (Arrays.equals(list, playlist)) {
                        // we don't need to set a new list, but we should resume playback if needed
                        mService.play();
                        return; // the 'finally' block will still run
                    }
                }
                if (position < 0) {
                    position = 0;
                }
                mService.open(list, position);
                mService.play();
            } catch (RemoteException ex) {
            } finally {
                //
            }
        }
    }
    
    
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action) ||
                    Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                //MusicUtils.setSpinnerState(TrackBrowserActivity.this);
            }
            mReScanHandler.sendEmptyMessage(0);
        }
    };
    
    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getTrackCursor(mAdapter.getQueryHandler(), null, true);
            }
            // if the query results in a null cursor, onQueryComplete() will
            // call init(), which will post a delayed message to this handler
            // in order to try again.
        }
    };
    
    private void registerExternalStorageListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        postUpdateUI();
                        // stop event will be send by service if need
                        // so, if we not receive state change immediately, don't worry,
                        // we will be notified later.
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        postUpdateUI();
                    }
                }
            };

            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }
    
    private void unregisterReceiverSafe(BroadcastReceiver receiver) {
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }
    
    private void postUpdateUI() {
        Message msg = mHandler.obtainMessage(MSG_UPDATE_UI);
        mHandler.removeMessages(MSG_UPDATE_UI);
        if (!isFinishing()) {
            mHandler.sendMessage(msg);
        }
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_UI: 
                updateUI();
                break;
            case MSG_UPDATE_TIMER:
                //updateUITimerView();
                break;
            case REFRESH:
                Log.v(TAG, ">>> refresh");
                long next = refreshNow();
                queueNextRefresh(next);
                break;
            }
        }
    };
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IVoiceRepeaterService.Stub.asInterface(service);
            Log.v(TAG, ">>> service connected.");
            
            VoiceRepeater.this.onServiceConnected(name, service);
            
            //mService.registerCallback(mCallback);
            postUpdateUI();
            
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.v(TAG, ">>> service disconnected~~");
            postUpdateUI();
        }
    };
    
    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            mPosOverride = mDuration * progress / 1000;
            refreshNow();
            
            /*
            if (!fromuser || (mService == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                }

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }*/
        }
        @Override
        public void onStartTrackingTouch(SeekBar arg0) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        @Override
        public void onStopTrackingTouch(SeekBar arg0) {
            //mPosOverride = -1;
            //mFromTouch = false;
            
            try {
                if (mService == null) throw new Exception("service is null");
                
                mPosOverride = mDuration * mProgress.getProgress() / 1000;
                mService.seek(mPosOverride);
            } catch (RemoteException ex) {
            } catch (Exception e) {
            } finally {
                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
                
                mPosOverride = -1;
                mFromTouch = false;
            }
        }
    };
    
    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
            //if (!mEditMode) {
            //    MusicUtils.updateNowPlaying(TrackBrowserActivity.this);
            //}
        }
    };
    
    private OnClickListener mFilterListener = new OnClickListener() {
    	public void onClick(android.view.View v) {
    	    InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    	    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    	    
    	    findViewById(R.id.filterString).setVisibility(View.VISIBLE);
            findViewById(R.id.filterContainer).setVisibility(View.GONE);
            
            EditText edFolder = (EditText)findViewById(R.id.folderKey);
            edFolder.setImeOptions(EditorInfo.IME_ACTION_NONE);
            mFilterFolderString = edFolder.getText().toString();
            Utils.setStringPref(VoiceRepeater.this, PREF_KEY_FOLDERSTRING, mFilterFolderString);
            
            postUpdateUI();
            getTrackCursor(mAdapter.getQueryHandler(), null, true);
    	};
    };
    
    private OnClickListener mFilterStringListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            EditText edFolder = (EditText)findViewById(R.id.folderKey);
            findViewById(R.id.filterString).setVisibility(View.GONE);
            findViewById(R.id.filterContainer).setVisibility(View.VISIBLE);
            
            if (mFilterFolderString!=null && mFilterFolderString.length()>0) {
                edFolder.setText(mFilterFolderString);
            }
            edFolder.setSelectAllOnFocus(true);
            edFolder.requestFocus();
            
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
        };
    };
    
    private OnClickListener mCollapserListener = new OnClickListener() {
    	public void onClick(android.view.View v) {
    		boolean isToolsVisible = View.VISIBLE == findViewById(R.id.tools).getVisibility();
    		if (isToolsVisible) {
    			findViewById(R.id.tools).setVisibility(View.GONE);
    		} else {
    			findViewById(R.id.tools).setVisibility(View.VISIBLE);
    		}
    		
    		postUpdateUI();
    	};
    };
    
    private OnClickListener mMediaPreviousListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            if (mService == null) return;
            try {
                mService.prev();
            } catch (RemoteException ex) {
            }
        }
    };
    
    private OnClickListener mMediaPlayPauseListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            try {
                if(mService != null) {
                    if (mService.isPlaying()) {
                        mService.pause();
                    } else {
                        mService.play();
                    }
                    refreshNow();
                    //setPauseButtonImage();
                }
            } catch (RemoteException ex) {
            }
        }
    };
    
    private OnClickListener mMediaNextListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            if (mService == null) return;
            try {
                mService.next();
            } catch (RemoteException ex) {
            }
        }
    };
    
    private OnClickListener mMediaLoopListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            if (mService == null) {
                return;
            }
            try {
                int mode = mService.getRepeatMode();
                if (mode == VoiceRepeaterService.REPEAT_NONE) {
                    mService.setRepeatMode(VoiceRepeaterService.REPEAT_ALL);
                    //showToast(R.string.repeat_all_notif);
                } else if (mode == VoiceRepeaterService.REPEAT_ALL) {
                    mService.setRepeatMode(VoiceRepeaterService.REPEAT_CURRENT);
                    //showToast(R.string.repeat_current_notif);
                } else {
                    mService.setRepeatMode(VoiceRepeaterService.REPEAT_NONE);
                    //showToast(R.string.repeat_off_notif);
                }
                setRepeatButtonImage();
            } catch (RemoteException ex) {
            }
        }
    };
    
    private OnClickListener mMediaRewindListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            if (mService == null) return;
            try {
                mService.rewind();
            } catch (RemoteException ex) {
            }
        }
    };
    
    private OnClickListener mMediaTextListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            ;
        }
    };
    
    private OnClickListener mMediaForwardListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            if (mService == null) return;
            try {
                mService.forward();
            } catch (RemoteException ex) {
            }
        }
    };
    
    private OnClickListener mMediaRepeaterListener = new OnClickListener() {
        public void onClick(android.view.View v) {
            ;
        }
    };
    
    private void updateUI() {
		String strFilterString;
		if ((mFilterFolderString==null || mFilterFolderString.length()<=0) ) {
		    strFilterString = getString(R.string.showFilterTip);
		} else {
		    strFilterString = getString(R.string.folderPre);
		    if (mFilterFolderString!=null && mFilterFolderString.length()>0) {
		        strFilterString += mFilterFolderString;
		    }
		}
		((TextView)findViewById(R.id.filterString)).setText(strFilterString);
		
		boolean isPlaying = false;
		boolean hasAudioInService = false;
		if (mService != null) {
            try {
                isPlaying = mService.isPlaying();
                hasAudioInService = (mService.getAudioId() != -1);
            } catch (Exception e) { e.printStackTrace(); }
        }
        
        if (isPlaying){
            queueNextRefresh(1);
        } else {
            mHandler.removeMessages(REFRESH);
            mPosOverride = -1;
        }
        
        enableTools(hasAudioInService);
    }
    
    private void setRepeatButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getRepeatMode()) {
                case VoiceRepeaterService.REPEAT_ALL:
                    mMediaLoop.setImageResource(R.drawable.media_loop_all);
                    break;
                case VoiceRepeaterService.REPEAT_CURRENT:
                    mMediaLoop.setImageResource(R.drawable.media_loop_once);
                    break;
                default:
                    mMediaLoop.setImageResource(R.drawable.media_loop_off);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void enableTools(boolean enable){
        Log.v(TAG, ">>> enableTools="+enable);
        if (enable) {
            mCollapser.setEnabled(true);
            mProgress.setEnabled(true);
            
            boolean isToolsVisible = View.VISIBLE == findViewById(R.id.tools).getVisibility();
            if (isToolsVisible) {
                mCollapser.setImageResource(R.drawable.tools_hide);
            } else {
                mCollapser.setImageResource(R.drawable.tools_show);
            }
            
        } else {
            mCollapser.setImageResource(R.drawable.tools_show_gray);
            mCollapser.setEnabled(false);
            mProgress.setEnabled(false);
            findViewById(R.id.tools).setVisibility(View.GONE);
        }
    }
    
    private void queueNextRefresh(long delay) {
        if (!mRefreshShouldPause) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if(mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            long remaining = 1000 - (pos % 1000);
            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(Utils.makeTimeString(this, pos / 1000));
                
                if (mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    remaining = 500;
                }

                mProgress.setProgress((int) (1000 * pos / mDuration));
            } else {
                mCurrentTime.setText("00:00");
                mProgress.setProgress(0);
            }
            // return the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            return remaining;
        } catch (RemoteException ex) {
        }
        return 500;
    }
    
    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                finish();
                return;
            }
            
//            long songid = mService.getAudioId(); 
//            ((View) mArtistName.getParent()).setVisibility(View.VISIBLE);
//            ((View) mAlbumName.getParent()).setVisibility(View.VISIBLE);
//            String artistName = mService.getArtistName();
//            if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
//                artistName = getString(R.string.unknown_artist_name);
//            }
//            mArtistName.setText(artistName);
//            String albumName = mService.getAlbumName();
//            long albumid = mService.getAlbumId();
//            if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
//                albumName = getString(R.string.unknown_album_name);
//                albumid = -1;
//            }
//            mAlbumName.setText(albumName);
//            mTrackName.setText(mService.getTrackName());
//            mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
//            mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(albumid, songid)).sendToTarget();
//            mAlbum.setVisibility(View.VISIBLE);
            
            mDuration = mService.duration();
            mTotalTime.setText(Utils.makeTimeString(this, mDuration / 1000));
        } catch (RemoteException ex) {
            finish();
        }
    }
    
    public void init(Cursor newCursor, boolean isLimited) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor
        
        if (mTrackCursor == null) {
            //MusicUtils.displayDatabaseError(this);
            closeContextMenu();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

        //MusicUtils.hideDatabaseError(this);
        //mUseLastListPos = MusicUtils.updateButtonBar(this, R.id.songtab);
        //setTitle();

        // Restore previous position
        if (mLastListPosCourse >= 0 && mUseLastListPos) {
            ListView lv = getListView();
            // this hack is needed because otherwise the position doesn't change
            // for the 2nd (non-limited) cursor
            lv.setAdapter(lv.getAdapter());
            lv.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
            if (!isLimited) {
                mLastListPosCourse = -1;
            }
        }

        // When showing the queue, position the selection on the currently playing track
        // Otherwise, position the selection on the first matching artist, if any
        IntentFilter f = new IntentFilter();
        f.addAction(VoiceRepeaterService.META_CHANGED);
        f.addAction(VoiceRepeaterService.QUEUE_CHANGED);
        
        String key = getIntent().getStringExtra("artist");
        if (key != null) {
            int keyidx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
            mTrackCursor.moveToFirst();
            while (! mTrackCursor.isAfterLast()) {
                String artist = mTrackCursor.getString(keyidx);
                if (artist.equals(key)) {
                    setSelection(mTrackCursor.getPosition());
                    break;
                }
                mTrackCursor.moveToNext();
            }
        }
        registerReceiver(mTrackListListener, new IntentFilter(f));
        mTrackListListener.onReceive(this, new Intent(VoiceRepeaterService.META_CHANGED));
    }
    
    private Cursor getTrackCursor(TrackListAdapter.TrackQueryHandler queryhandler, String filter,
            boolean async) {

        if (queryhandler == null) {
            throw new IllegalArgumentException();
        }

        Cursor ret = null;
        mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.DATA + " != ''");

        {
            if (mFilterFolderString!=null && mFilterFolderString.length()>0) {
                where.append(" AND " + MediaStore.Audio.Media.DATA + " like '%" + mFilterFolderString+"%/%'");
            }
            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            if (!TextUtils.isEmpty(filter)) {
                uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
            }
            Log.v(TAG, ">>> where="+where);
            ret = queryhandler.doQuery(uri,
                    mCursorCols, where.toString() , null, mSortOrder, async);
        }
        
        // This special case is for the "nowplaying" cursor, which cannot be handled
        // asynchronously using AsyncQueryHandler, so we do some extra initialization here.
        if (ret != null && async) {
            init(ret, false);
            //setTitle();
        }
        return ret;
    }
    
    static class TrackListAdapter extends SimpleCursorAdapter {
        boolean mIsNowPlaying;
        boolean mDisableNowPlayingIndicator;

        int mTitleIdx;
        int mArtistIdx;
        int mDurationIdx;
        int mAudioIdIdx;
        int mAudioDataIdx;

        private final StringBuilder mBuilder = new StringBuilder();
        private final String mUnknownArtist;
        private final String mUnknownAlbum;
        
        private VoiceRepeater mActivity = null;
        private TrackQueryHandler mQueryHandler;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;
        
        static class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            ImageView play_indicator;
            CharArrayBuffer buffer1;
            char [] buffer2;
        }

        class TrackQueryHandler extends AsyncQueryHandler {

            class QueryArgs {
                public Uri uri;
                public String [] projection;
                public String selection;
                public String [] selectionArgs;
                public String orderBy;
            }

            TrackQueryHandler(ContentResolver res) {
                super(res);
            }
            
            public Cursor doQuery(Uri uri, String[] projection,
                    String selection, String[] selectionArgs,
                    String orderBy, boolean async) {
                if (async) {
                    // Get 100 results first, which is enough to allow the user to start scrolling,
                    // while still being very fast.
                    Uri limituri = uri.buildUpon().appendQueryParameter("limit", "100").build();
                    QueryArgs args = new QueryArgs();
                    args.uri = uri;
                    args.projection = projection;
                    args.selection = selection;
                    args.selectionArgs = selectionArgs;
                    args.orderBy = orderBy;

                    startQuery(0, args, limituri, projection, selection, selectionArgs, orderBy);
                    return null;
                }
                return Utils.query(mActivity,
                        uri, projection, selection, selectionArgs, orderBy);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
                mActivity.init(cursor, cookie != null);
                if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
                    QueryArgs args = (QueryArgs) cookie;
                    startQuery(1, null, args.uri, args.projection, args.selection,
                            args.selectionArgs, args.orderBy);
                }
            }
        }
        
        TrackListAdapter(Context context, VoiceRepeater currentactivity,
                int layout, Cursor cursor, String[] from, int[] to,
                boolean isnowplaying, boolean disablenowplayingindicator) {
            super(context, layout, cursor, from, to);
            mActivity = currentactivity;
            getColumnIndices(cursor);
            mIsNowPlaying = isnowplaying;
            mDisableNowPlayingIndicator = disablenowplayingindicator;
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
            mUnknownAlbum = context.getString(R.string.unknown_album_name);
            
            mQueryHandler = new TrackQueryHandler(context.getContentResolver());
        }
        
        public void setActivity(VoiceRepeater newactivity) {
            mActivity = newactivity;
        }
        
        public TrackQueryHandler getQueryHandler() {
            return mQueryHandler;
        }
        
        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                mDurationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                mAudioDataIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                try {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID);
                } catch (IllegalArgumentException ex) {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            //ImageView iv = (ImageView) v.findViewById(R.id.icon);
            //iv.setVisibility(View.GONE);
            
            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            
            ViewHolder vh = (ViewHolder) view.getTag();
            
            //cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
            //vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);
            
            vh.line1.setText(Uri.parse(cursor.getString(mAudioDataIdx)).getLastPathSegment().toString());
            
            int secs = cursor.getInt(mDurationIdx) / 1000;
            if (secs == 0) {
                vh.duration.setText("");
            } else {
                vh.duration.setText(Utils.makeTimeString(context, secs));
            }
            
            final StringBuilder builder = mBuilder;
            builder.delete(0, builder.length());

            String name = cursor.getString(mArtistIdx);
            if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                builder.append(mUnknownArtist);
            } else {
                builder.append(name);
            }
            int len = builder.length();
            if (vh.buffer2.length < len) {
                vh.buffer2 = new char[len];
            }
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);

            ImageView iv = vh.play_indicator;
            long id = -1;
            if (mActivity.mService != null) {
                // TODO: IPC call on each bind??
                try {
                    if (mIsNowPlaying) {
                        id = mActivity.mService.getQueuePosition();
                    } else {
                        id = mActivity.mService.getAudioId();
                    }
                } catch (RemoteException ex) {
                }
            }
            
            // Determining whether and where to show the "now playing indicator
            // is tricky, because we don't actually keep track of where the songs
            // in the current playlist came from after they've started playing.
            //
            // If the "current playlists" is shown, then we can simply match by position,
            // otherwise, we need to match by id. Match-by-id gets a little weird if
            // a song appears in a playlist more than once, and you're in edit-playlist
            // mode. In that case, both items will have the "now playing" indicator.
            // For this reason, we don't show the play indicator at all when in edit
            // playlist mode (except when you're viewing the "current playlist",
            // which is not really a playlist)
            if ( (mIsNowPlaying && cursor.getPosition() == id) ||
                 (!mIsNowPlaying && !mDisableNowPlayingIndicator && cursor.getLong(mAudioIdIdx) == id)) {
                iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
        }
        
        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mActivity.mTrackCursor) {
                mActivity.mTrackCursor = cursor;
                super.changeCursor(cursor);
                getColumnIndices(cursor);
            }
        }
        
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String s = constraint.toString();
            if (mConstraintIsValid && (
                    (s == null && mConstraint == null) ||
                    (s != null && s.equals(mConstraint)))) {
                return getCursor();
            }
            Cursor c = mActivity.getTrackCursor(mQueryHandler, s, false);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }
    }

}
