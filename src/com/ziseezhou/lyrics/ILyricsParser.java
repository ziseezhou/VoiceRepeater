package com.ziseezhou.lyrics;

public interface ILyricsParser
{
    public void cancel();
    public void parse() throws Exception;
    public void setFD(LyricsFileDriver fd); // set file driver
    public void setDB(ILyricsDB  db);  // set database driver
}
