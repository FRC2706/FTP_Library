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
    String hostname;
    String password;
    String username;

    //port for connection
    int port;

    //Local directory on device for files being downloaded.
    File localPath;

    //Boolean to see if client is still syncing files
    public boolean syncing;

    //Thread voodoo stuff created by Mike O!
    private Object connectedLock = new Object();

    //Is the client connected?
    private boolean connected = false;

    /**
     * Constructor without port option
     * @param hostname: Server IP Adress
     * @param username: Login Credential
     * @param password: Login Credential
     * @param localPath: Local path for saving to the device
     */
    public FTPClient(String hostname, String username, String password, String localPath){
        this.hostname = hostname;
        this.password = password;
        this.username = username;
        this.localPath = new File(localPath);
        this.port = 21;
    }

    /**
     * Constructor with port option
     * @param hostname: Server IP Adress
     * @param username: Login Credential
     * @param password: Login Credential
     * @param localPath: Local path for saving to the device
     * @param port: Custom port connection
     */
    public FTPClient(String hostname, String username, String password, String localPath, int port){
        this.hostname = hostname;
        this.password = password;
        this.username = username;
        this.localPath = new File(localPath);
        this.port = port;
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
                        ftpClient.connect(hostname, port);
                        Log.i("FTPClient", ftpClient.getReplyString());
                        ftpClient.enterLocalPassiveMode();
                        ftpClient.login(username, password);
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
     * Downloads a file from the server to the localPath
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
                    InputStream is = new FileInputStream(localPath.getAbsolutePath() + "/" + filename);
                    ftpClient.storeFile(RemotePath, is);
                    requester.uploadFileCallback(localPath.getAbsolutePath(), RemotePath);
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

                // TODO: add semaphores to all usages of this.
                syncing = true;
                FTPFile[] remoteFiles;
                File[] localFiles;
                ArrayList<String> localNames = new ArrayList<String>();
                ArrayList<String> remoteNames = new ArrayList<String>();
                ArrayList<String> upload = new ArrayList<String>();
                ArrayList<String> download = new ArrayList<String>();
                int changed = 0;
                try {
                    ftpClient.changeWorkingDirectory("files");
                    remoteFiles = ftpClient.listFiles();  // files
                    Log.i("FTP", ftpClient.getReplyString());
                }catch(Exception e){
                    Log.e("FTPDir", e.toString());
                    return;
                }
                File localDir = localPath;
                localFiles = localDir.listFiles();
                Log.d("FTPSync", localPath.toString());
                try {
                    for(int i = 0; i < localFiles.length; i++){
                        localNames.add(localFiles[i].getName());
                    }
                    for(int i = 0; i < remoteFiles.length; i++){
                        remoteNames.add(remoteFiles[i].getName());
                    }
                    for (int i = 0; i < remoteNames.size(); i++) {
                        if (!localNames.contains(remoteNames.get(i)))
                            download.add(remoteNames.get(i));
                    }
                    for (int i = 0; i < localNames.size(); i++) {
                        if (!remoteNames.contains(localNames.get(i)))
                            upload.add(localNames.get(i));
                    }
                    for (int i = 0; i < download.size(); i++) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + download.get(i);
                        OutputStream File = new FileOutputStream(newFile);
                        downloadFile(download.get(i), requester);
                        changed += 1;
                    }
                    for (int i = 0; i < upload.size(); i++) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + upload.get(i);
                        InputStream File = new FileInputStream(newFile);
                        uploadFile(upload.get(i), requester);
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
