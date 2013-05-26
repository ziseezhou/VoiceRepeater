
package com.ziseezhou.voicerepeater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.util.Log;

/**
 * 
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver {

    private static final String TAG = "VoiceRepeater-mediabutton";
    private static final int MSG_LONGPRESS_TIMEOUT = 1;
    private static final int MSG_TOUCHED_REWIND    = 2;
    private static final int MSG_TOUCHED_FORWARD   = 3;
    private static final int MSG_TOUCHED_NEXT_BY_HEADSETHOOK   = 4;
    private static final int MSG_TOUCHED_CMDTOGGLEPAUSE   = 5;
    private static final int LONG_PRESS_DELAY = 1000;
    private static final int SEEK_INTERVAL  = 260;
    private static final int MSG_CMD_DELAY  = 500;

    private static long mLastVirtualBtnTime = 0;
    private static long mLastClickTime = 0;
    private static long mLastSeekTime = 0;
    private static boolean mDown = false;
    private static boolean mLaunched = false;
    private static int mClickIsHeadsetHookCounter = 0;

    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (!mLaunched) {
                        Context context = (Context)msg.obj;
                        Intent i = new Intent(context, VoiceRepeaterService.class);
                        i.setAction(VoiceRepeaterService.SERVICECMD);
                        i.putExtra(VoiceRepeaterService.CMDNAME, VoiceRepeaterService.CMDSTOP);
                        context.startService(i);
                        mLaunched = true;
                    }
                    break;
                    
                case MSG_TOUCHED_REWIND:
                case MSG_TOUCHED_FORWARD: {
                    String cmd = VoiceRepeaterService.CMDFORWARD;
                    if (msg.what == MSG_TOUCHED_REWIND) {
                        cmd = VoiceRepeaterService.CMDREWIND;
                    }
                    
                    Context context = (Context)msg.obj;
                    Intent i = new Intent(context, VoiceRepeaterService.class);
                    i.setAction(VoiceRepeaterService.SERVICECMD);
                    i.putExtra(VoiceRepeaterService.CMDNAME, cmd);
                    context.startService(i);
                    break;
                }

                case MSG_TOUCHED_NEXT_BY_HEADSETHOOK: {
                    mClickIsHeadsetHookCounter = 0; // reset counter

                    Context context = (Context)msg.obj;
                    Intent i = new Intent(context, VoiceRepeaterService.class);
                    i.setAction(VoiceRepeaterService.SERVICECMD);
                    i.putExtra(VoiceRepeaterService.CMDNAME, VoiceRepeaterService.CMDNEXT);
                    context.startService(i);
                    break;
                }

                case MSG_TOUCHED_CMDTOGGLEPAUSE: {
                    mClickIsHeadsetHookCounter = 0; // reset counter
                    
                    Context context = (Context)msg.obj;
                    Intent i = new Intent(context, VoiceRepeaterService.class);
                    i.setAction(VoiceRepeaterService.SERVICECMD);
                    i.putExtra(VoiceRepeaterService.CMDNAME, VoiceRepeaterService.CMDTOGGLEPAUSE);
                    context.startService(i);
                    break;
                }
            }
        }
    };
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();
        Log.v(TAG, ">>> intentAction="+intentAction);
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
            Intent i = new Intent(context, VoiceRepeaterService.class);
            i.setAction(VoiceRepeaterService.SERVICECMD);
            i.putExtra(VoiceRepeaterService.CMDNAME, VoiceRepeaterService.CMDPAUSE);
            context.startService(i);
        } else if ("android.media.VOLUME_CHANGED_ACTION".equals(intentAction)) {
            Intent i = new Intent(context, VoiceRepeaterService.class);
            i.setAction(VoiceRepeaterService.SERVICECMD);
            i.putExtra(VoiceRepeaterService.CMDNAME, VoiceRepeaterService.CMDTOGGLEVOLUME);
            i.putExtra("v_type", intent.getExtras().getInt("android.media.EXTRA_VOLUME_STREAM_TYPE"));
            i.putExtra("v_value", intent.getExtras().getInt("android.media.EXTRA_VOLUME_STREAM_VALUE"));
            i.putExtra("v_value_old", intent.getExtras().getInt("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"));
            context.startService(i);
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            KeyEvent event = (KeyEvent)
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            
            if (event == null) {
                return;
            }

            int keycode = event.getKeyCode();
            int action = event.getAction();
            long eventtime = event.getEventTime();

            Log.v(TAG, ">>> keycode="+keycode+", action="+action+", count="+event.getRepeatCount()+", downed="+mDown+", btnid="+intent.getIntExtra("buttonId", 0));
            //Log.v(TAG, ">>> #### headsetCounter="+mClickIsHeadsetHookCounter+", mLaunched="+mLaunched);

            // single quick press: pause/resume. 
            // double press: next track
            // long press: start auto-shuffle mode.
            
            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = VoiceRepeaterService.CMDSTOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = VoiceRepeaterService.CMDTOGGLEPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = VoiceRepeaterService.CMDNEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = VoiceRepeaterService.CMDPREVIOUS;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    command = VoiceRepeaterService.CMDPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    command = VoiceRepeaterService.CMDPLAY;
                    break;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    command = VoiceRepeaterService.CMDREWIND;
                    break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    command = VoiceRepeaterService.CMDFORWARD;
                
                // unfortunately, bellows do not work
                /*
                case KeyEvent.KEYCODE_VOLUME_UP:
                    command = VoiceRepeaterService.CMDREWIND;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    command = VoiceRepeaterService.CMDTOGGLEREPEAT;
                    break;
                */
            }

            if (command != null) {
                // This buttonId will be got when intent launched by NotificationBar of MediaPlaybackService;
                int btnId = intent.getIntExtra("buttonId", 0);

                // enhance to make sure NotificationBar cmd effective and reset button down flag.
                // maybe should never happen, but happened somtimes? not sure, just enhance.
                // {{{
                if (btnId != 0 && mDown) {
                    Log.e(TAG, ">>> shit, why this down flag is ture!");
                    mDown = false;
                }
                // }}}
                
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mDown) {
                        if ((VoiceRepeaterService.CMDTOGGLEPAUSE.equals(command) ||
                                VoiceRepeaterService.CMDPLAY.equals(command))
                                && mLastClickTime != 0
                                && eventtime - mLastClickTime > LONG_PRESS_DELAY) 
                        {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context));
                                    
                        } else if (VoiceRepeaterService.CMDREWIND.equals(command) && eventtime-mLastSeekTime>=SEEK_INTERVAL){
                            mHandler.removeMessages(MSG_TOUCHED_REWIND);
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_TOUCHED_REWIND, context));
                            mLastSeekTime = eventtime;
                            
                        } else if (VoiceRepeaterService.CMDFORWARD.equals(command) && eventtime-mLastSeekTime>=SEEK_INTERVAL){
                            mHandler.removeMessages(MSG_TOUCHED_FORWARD);
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_TOUCHED_FORWARD, context));
                            mLastSeekTime = eventtime;
                        }
                    } else if (event.getRepeatCount() == 0) {
                        if (btnId != 0) { // Consider the command sent by MediaPlayerService NotificationBar
                            eventtime = System.currentTimeMillis();
                            long intervalTime = eventtime - mLastVirtualBtnTime;

                            // Be careful! Generally, the intervalTime could not be negative, but it really happened.
                            // Consider below case:
                            // 1. we launch an event, so the intervalTime record a time;
                            // 2. system time back to a ealy one. (maybe the system time is not right before, 
                            //    and then be adjusted to right auotmatically);
                            // 3. click the notification bar media button, well, no any response.
                            // {{
                            if (intervalTime < 0) intervalTime = -intervalTime;
                            // }}
                            
                            if (intervalTime >= 800 || (VoiceRepeaterService.CMDTOGGLEPAUSE.equals(command) && intervalTime>=500) ) 
                            { // avoid quickly click
                                Intent i = new Intent(context, VoiceRepeaterService.class);
                                i.setAction(VoiceRepeaterService.SERVICECMD);
                                i.putExtra(VoiceRepeaterService.CMDNAME, command);
                                i.putExtra("buttonId", btnId);
                                context.startService(i);
                                mLastVirtualBtnTime = System.currentTimeMillis();
                                Log.v(TAG, ">>> call MeidaPlaybackService cmd="+command);
                            }

                            // buttonId is from MediaPlayerService NotificationBar intent, on this case
                            // we will never receive a button up event
                            // so, mDown should not be set to true
                            
                        } else {
                            // only consider the first event in a sequence, not the repeat events,
                            // so that we don't trigger in cases where the first event went to
                            // a different app (e.g. when the user ends a phone call by
                            // long pressing the headset button)

                            // The service may or may not be running, but we need to send it
                            // a command.
                            Intent i = new Intent(context, VoiceRepeaterService.class);
                            i.setAction(VoiceRepeaterService.SERVICECMD);
                            if (VoiceRepeaterService.CMDTOGGLEPAUSE.equals(command) &&
                                    eventtime - mLastClickTime < 300) {

                                ++mClickIsHeadsetHookCounter;
                                if (mClickIsHeadsetHookCounter>=3) {
                                    mHandler.removeMessages(MSG_TOUCHED_NEXT_BY_HEADSETHOOK); // cancel the forward_by_headsethook
                                    mClickIsHeadsetHookCounter = 0; // reset counter

                                    i.putExtra(VoiceRepeaterService.CMDNAME, VoiceRepeaterService.CMDPREVIOUS);
                                    i.putExtra("forceprev", true);
                                    context.startService(i);
                                    mLastClickTime = 0;
                                } else {
                                    // launch delay CMDNEXT
                                    mHandler.removeMessages(MSG_TOUCHED_CMDTOGGLEPAUSE); // cancal single click event
                                    mLaunched = true; // at least, it's a double click event
                                    
                                    mHandler.removeMessages(MSG_TOUCHED_NEXT_BY_HEADSETHOOK);
                                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TOUCHED_NEXT_BY_HEADSETHOOK, context), MSG_CMD_DELAY);
                                    mLastClickTime = eventtime;
                                }
                            } else {
                                if (VoiceRepeaterService.CMDTOGGLEPAUSE.equals(command)) {
                                    mClickIsHeadsetHookCounter = 1;
                                    mLaunched = false;

                                    // launch delay CMDTOGGLEPAUSE // move to up event
                                    // mHandler.removeMessages(MSG_TOUCHED_CMDTOGGLEPAUSE);
                                    // mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TOUCHED_CMDTOGGLEPAUSE, context), MSG_CMD_DELAY);
                                } else {
                                    mClickIsHeadsetHookCounter = 0;
                                    i.putExtra(VoiceRepeaterService.CMDNAME, command);
                                    i.putExtra("repcnt", 0);
                                    context.startService(i);
                                }

                                mLastClickTime = eventtime;
                                mLastSeekTime  = eventtime;
                            }

                            mDown = true;
                        }
                    }
                } else {
                    if (VoiceRepeaterService.CMDTOGGLEPAUSE.equals(command)) {
                        if (!mLaunched && mDown) {
                            // launch delay CMDTOGGLEPAUSE
                            mHandler.removeMessages(MSG_TOUCHED_CMDTOGGLEPAUSE);
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TOUCHED_CMDTOGGLEPAUSE, context), MSG_CMD_DELAY);
                        }
                    }
                    
                    mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }
                
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
            }
        }
    }
}
