package ca.team2706.scouting.ftptest;

import org.apache.commons.net.ftp.FTPFile;

import java.io.InputStream;

/**
 * Created by nrgil on 2017-01-15.
 */

public interface FTPRequester {

    void setFileList(FTPFile[] fileList);
    void downloadFile(String filename);
    void uploadFile(String filename);
    void doneSync();
}
