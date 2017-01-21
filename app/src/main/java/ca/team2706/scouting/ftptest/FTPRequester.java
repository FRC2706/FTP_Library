package ca.team2706.scouting.ftptest;
import org.apache.commons.net.ftp.FTPFile;
import java.io.InputStream;
public interface FTPRequester {

    /**
     * Callback method for FTPClient thread
     * (download callback)
     * @param localFilename: Local path to file
     * @param remoteFilename: Remote path to file
     */
    void downloadFileCallback(String localFilename, String remoteFilename);

    /**
     * Callback method for FTPClient thread
     * (Upload callback)
     * @param localFilename: Local path to file
     * @param remoteFilename: Remote path to file
     */
    void uploadFileCallback(String localFilename, String remoteFilename);

    /**
     * Callback method for FTPClient thread
     * (sync)
     * @param changedFiles: number of files uploaded and downloaded
     */
    void syncCallback(int changedFiles);

    /**
     * Callback method for FTPClient thread
     * (Direcory lsting)
     * @param listing: FTPFile array of files in current working directory
     */
    void dirCallback(FTPFile[] listing);
}
