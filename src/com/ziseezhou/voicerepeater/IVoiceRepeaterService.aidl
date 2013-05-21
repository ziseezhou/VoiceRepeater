/*************************************************************************
FILENAME IVoiceRepeaterService.aidl

COPYRIGHT (c) 2013

DESCRIPTION
This file define IVoiceRepeaterService aidl.

--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2013/05/18   ZISEEZHOU       0x00000000       base code
*************************************************************************/

package com.ziseezhou.voicerepeater;


interface IVoiceRepeaterService {

    void openFile(String path);
    void open(in long [] list, int position);
    int getQueuePosition();
    boolean isPlaying();
    void stop();
    void pause();
    void play();
    void prev();
    void next();
    void rewind();
    void forward();
    long duration();
    long position();
    long seek(long pos);
    String getTrackName();
    long [] getQueue();
    String getPath();
    long getAudioId();
    void setRepeatMode(int shufflemode);
    int getRepeatMode();
    int getMediaMountedCount();
    int getAudioSessionId();
}

