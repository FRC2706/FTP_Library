package ca.team2706.scouting.ftptest;
import org.apache.commons.net.ftp.FTPFile;
import java.io.InputStream;
public interface FTPRequester {
    void downloadFileFeedback(String localFilename, String remoteFilename);
    void uploadFileFeedback(String localFilename, String remoteFilename);
    void syncFeedback(int changedFiles);
    void dirFeedback(FTPFile[] listing);
}
