package ca.team2706.scouting.ftptest;

import android.app.Activity;
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
    private String hostname;
    private String password;
    private String username;

    //port for connection
    private int port;

    //Local directory on device for files being downloaded.
    private File localPath;

    //Is the client connected?
    private Object connectedThreadLock = new Object();
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
        synchronized (connectedThreadLock) {
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
                synchronized (connectedThreadLock) {
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
        synchronized (connectedThreadLock) {
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
        synchronized (connectedThreadLock) {
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
                } catch (Exception e) {
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
        synchronized (connectedThreadLock){
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
        synchronized (connectedThreadLock){
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

    public void syncAllFiles(final FTPRequester requester, final Activity activity)throws ConnectException{
      synchronized (connectedThreadLock){
            if(!connected){
                throw new ConnectException("You cannot sync files if you're not connected!");
            }
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run(){
                Log.i("Connection", "Starting SYNC");
                int currentProgress = 0;
                int maxUpProgress = 0;
                int maxDownProgress = 0;
                FTPFile[] remoteFiles;
                File[] localFiles;
                ArrayList<String> localNames = new ArrayList<String>();
                ArrayList<String> remoteNames = new ArrayList<String>();
                ArrayList<String> filesToUpload = new ArrayList<String>();
                ArrayList<String> filesToDownload = new ArrayList<String>();
                int changed = 0;
                int Uploaded = 0;
                int Downloaded = 0;
                int Unchanged = 0;
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
                    requester.updateSyncBar("^checking for differences...", 0, activity);
                    for(File localFile : localFiles){
                        localNames.add(localFile.getName());
                    }
                    for(FTPFile remoteFile : remoteFiles){
                        remoteNames.add(remoteFile.getName());
                    }
                    for (String remoteName : remoteNames) {
                        if (!localNames.contains(remoteName))
                            filesToDownload.add(remoteName);
                    }
                    for (String localName : localNames) {
                        if (!remoteNames.contains(localName))
                            filesToUpload.add(localName);
                        else
                            Unchanged += 1;
                    }
                    maxDownProgress = filesToDownload.size();
                    maxUpProgress = filesToUpload.size();
                    for (String fileToDownload : filesToDownload) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + fileToDownload;
                        OutputStream File = new FileOutputStream(newFile);
                        downloadSync(fileToDownload);
                        changed += 1;
                        Downloaded += 1;
                        currentProgress += 1;
                        String display = newFile.split("frc2706")[1];
                        requester.updateSyncBar("Downloading file " + currentProgress + "/" + maxDownProgress + ":\n" + display, (currentProgress*100) / maxDownProgress, activity);
                    }
                    currentProgress = 0;
                    for (String fileToUpload : filesToUpload) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + fileToUpload;
                        InputStream File = new FileInputStream(newFile);
                        uploadSync(fileToUpload);
                        changed += 1;
                        Uploaded += 1;
                        currentProgress += 1;
                        String display = newFile.split("frc2706")[1];
                        requester.updateSyncBar("Uploading file " + currentProgress + "/" + maxUpProgress + ":\n" + display, (currentProgress*100) / maxUpProgress, activity);
                    }
                    requester.syncCallback(changed);

                }catch(Exception e){
                    Log.e("FTPSync", e.toString());
                    requester.updateSyncBar("Error while syncing, see debug for more info.", 100, activity);
                }
                String up = String.valueOf(Uploaded);
                String down = String.valueOf(Downloaded);
                String unchanged = String.valueOf(Unchanged);
                if(changed==0)
                    requester.updateSyncBar("Your device had the latest files!", 100, activity);
                else
                    requester.updateSyncBar("Done syncing! Unchanged Files: "+Unchanged+"\n"+down+" downloaded, "+up+" uploaded.", 100, activity);
                Log.d("FTPSync", "Sync done!");

            }
        });
    }
    private void uploadSync(String filename){
        final String RemotePath = filename;
        try {
            InputStream is = new FileInputStream(localPath.getAbsolutePath() + "/" + filename);
            ftpClient.storeFile(RemotePath, is);
        }catch(Exception e) {
            Log.e("FTPUpload", e.toString());
            return;
        }
    }
    private void downloadSync(String filename){
        final File file = new File(Environment.getExternalStorageDirectory() + "/frc2706/files/" + filename);
        final File folder = new File(file.getParent());
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        if (success) {
            // Do something on success
        } else {
            Log.e("FTPDownload.mkdirs", "Unable to create directorys they might already exist!");
        }
        String RemotePath = filename;
        try {
            OutputStream os = new FileOutputStream(file.getAbsolutePath());
            ftpClient.retrieveFile(RemotePath, os);
        }catch(Exception e){
            Log.e("FTP", e.toString());
            return;
        }
    }
}
