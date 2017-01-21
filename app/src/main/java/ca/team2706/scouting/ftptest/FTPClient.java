package ca.team2706.scouting.ftptest;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.ArrayList;

public class FTPClient {
    org.apache.commons.net.ftp.FTPClient ftpClient = new org.apache.commons.net.ftp.FTPClient();

    // for FTP server credentials
    String Hostname;
    String Password;
    String Username;
    int Port;

    //Local directory on device for files being downloaded.
    File LocalPath;

    //Boolean to see if client is still syncing files
    public boolean syncing;

    //Thread voodoo stuff created by Mike O!
    private Object connectedLock = new Object();

    //Is the client connected?
    private boolean connected = false;

    /**
     * Constructor without port option
     * @param Hostname: Server IP Adress
     * @param Username: Login Credential
     * @param Password: Login Credential
     * @param LocalPath: Local path for saving to the device
     */
    public FTPClient(String Hostname, String Username, String Password, String LocalPath){
        this.Hostname = Hostname;
        this.Password = Password;
        this.Username = Username;
        this.LocalPath = new File(LocalPath);
        this.Port = 21;
    }

    /**
     * Constructor with port option
     * @param Hostname: Server IP Adress
     * @param Username: Login Credential
     * @param Password: Login Credential
     * @param LocalPath: Local path for saving to the device
     * @param Port: Custom port connection
     */
    public FTPClient(String Hostname, String Username, String Password, String LocalPath, int Port){
        this.Hostname = Hostname;
        this.Password = Password;
        this.Username = Username;
        this.LocalPath = new File(LocalPath);
        this.Port = Port;
    }

    /**
     * Tells you whether the FTPClient is connected or not.
     *
     * @return whether the FTPClient is connected or not.
     **/
    public boolean isConnected() {
        synchronized (connectedLock) {
            return connected;
        }
    }

    /**
     * Tries to connect to a server with the parameters supplied with constructor
     *
     * @throws ConnectException
     */
    public void connect() throws ConnectException {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (connectedLock) {
                    try {
                        ftpClient.connect(Hostname, Port);
                        Log.i("FTPClient", ftpClient.getReplyString());
                        ftpClient.enterLocalPassiveMode();
                        ftpClient.login(Username, Password);
                        Log.i("FTPClient", ftpClient.getReplyString());
                        connected = true;
                    } catch (Exception e) {
                        Log.e("FTPClient", e.toString());
                        connected = false;
                    }
                }
            }
        });
        try {
            Thread.sleep(5);  //multi-threaded voodoo. Give the AsyncTask 5 ms to get started and get the lock.
        } catch (InterruptedException e) {
            // do nothing.
        }
        synchronized (connectedLock) {
            if (!connected) {
                throw new ConnectException("FTPClient failed to connect to FTP server");
            }
        }
    }

    /**
     * Gets a directory listing of the current working directory
     * @param requester: Callback for the thread
     * @throws ConnectException
     */
    public void dir(final FTPRequester requester) throws ConnectException{
        synchronized (connectedLock) {
            if (!connected) {
                throw new ConnectException("You cannot get a directory listing if you are not connected!");
            }
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i("Connection", "Starting DIR");
                try {
                    FTPFile[] files = ftpClient.listFiles();  // files
                    Log.i("FTP", ftpClient.getReplyString());
                    requester.dirCallback(files);
                }catch(Exception e){
                    Log.e("FTPDir", e.toString());
                    //DIR failed, log the error to console.
                }
            }
        });
    }

    /**
     * Downloads a file from the server to the LocalPath
     * @param filename: Name of the file on the server
     * @param requester: callback for the thread
     * @throws ConnectException
     */
    public void downloadFile(final String filename, final FTPRequester requester)throws ConnectException {
        synchronized (connectedLock){
            if(!connected){
                throw new ConnectException("You cannot download a file if you're not connected!");
            }
        }
        final File file = new File(Environment.getExternalStorageDirectory() + "/frc2706/files/" + filename);
        final File folder = new File(file.getParent());
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdir();
        }
        if (success) {
            // Do something on success
        } else {
            Log.e("FTPDownload.mkdirs", "Unable to create directorys they might already exist!");
        }
        final String RemotePath = filename;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream os = new FileOutputStream(file.getAbsolutePath());
                    ftpClient.retrieveFile(RemotePath, os);
                    requester.downloadFileCallback(file.getAbsolutePath(), RemotePath);
                }catch(Exception e){
                    Log.e("FTP", e.toString());
                    return;
                }
            }
        });
    }

    /**
     * Uploads a file from the device to the server
     * @param filename: Path of file on device
     * @param requester: Callback for thread
     * @throws ConnectException
     */
    public void uploadFile(final String filename, final FTPRequester requester)throws ConnectException {
        synchronized (connectedLock){
            if(!connected){
                throw new ConnectException("You cannot upload a file if you're not connected!");
            }
        }
        final String RemotePath = filename;
        AsyncTask.execute(new Runnable(){
            @Override
            public void run(){
                try {
                    InputStream is = new FileInputStream(LocalPath.getAbsolutePath() + "/" + filename);
                    ftpClient.storeFile(RemotePath, is);
                    requester.uploadFileCallback(LocalPath.getAbsolutePath(), RemotePath);
                }catch(Exception e){
                    Log.e("FTPUpload", e.toString());
                    return;
                }
            }
        });
    }

    /**
     * Downloads all missing files on device from the server, and
     * uploads all missing files on server from device.
     * @param requester: Callback for thread
     * @throws ConnectException
     */
    public void syncAllFiles(final FTPRequester requester)throws ConnectException{
        synchronized (connectedLock){
            if(!connected){
                throw new ConnectException("You cannot sync files if you're not connected!");
            }
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run(){
                Log.i("Connection", "Starting SYNC");
                syncing = true;
                FTPFile[] RemoteFiles;
                File[] LocalFiles;
                ArrayList<String> LocalNames = new ArrayList<String>();
                ArrayList<String> RemoteNames = new ArrayList<String>();
                ArrayList<String> Upload = new ArrayList<String>();
                ArrayList<String> Download = new ArrayList<String>();
                int changed = 0;
                try {
                    ftpClient.changeWorkingDirectory("files");
                    RemoteFiles = ftpClient.listFiles();  // files
                    Log.i("FTP", ftpClient.getReplyString());
                }catch(Exception e){
                    Log.e("FTPDir", e.toString());
                    return;
                }
                File LocalDir = LocalPath;
                LocalFiles = LocalDir.listFiles();
                Log.d("FTPSync", LocalPath.toString());
                try {
                    for(int i = 0; i < LocalFiles.length; i++){
                        LocalNames.add(LocalFiles[i].getName());
                    }
                    for(int i = 0; i < RemoteFiles.length; i++){
                        RemoteNames.add(RemoteFiles[i].getName());
                    }
                    for (int i = 0; i < RemoteNames.size(); i++) {
                        if (!LocalNames.contains(RemoteNames.get(i)))
                            Download.add(RemoteNames.get(i));
                    }
                    for (int i = 0; i < LocalNames.size(); i++) {
                        if (!RemoteNames.contains(LocalNames.get(i)))
                            Upload.add(LocalNames.get(i));
                    }
                    for (int i = 0; i < Download.size(); i++) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + Download.get(i);
                        OutputStream File = new FileOutputStream(newFile);
                        downloadFile(Download.get(i), requester);
                        changed += 1;
                    }
                    for (int i = 0; i < Upload.size(); i++) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + Upload.get(i);
                        InputStream File = new FileInputStream(newFile);
                        uploadFile(Upload.get(i), requester);
                        changed += 1;
                    }
                    requester.syncCallback(changed);
                }catch(Exception e){
                    Log.e("FTPSync", e.toString());
                }
                syncing = false;
                Log.d("FTPSync", "Sync done!");
            }
        });
    }
}