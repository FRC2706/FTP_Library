package ca.team2706.scouting.ftptest;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;

public class FTPClient {
    org.apache.commons.net.ftp.FTPClient ftpClient = new org.apache.commons.net.ftp.FTPClient();

    // for FTP server credentials
    private String hostname;
    private String password;
    private String username;

    //port for connection
    private int port;

    //Local and remote directory on device for files being downloaded.
    private File localPath;
    private String remotePath;

    //Is the client connected?
    private Object connectedThreadLock = new Object();
    private boolean connected = false;
    private boolean syncing = false;


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
        this.localPath = new File(Environment.getExternalStorageDirectory() + localPath);
        this.remotePath = localPath;
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
    public void test(){
        try {
            FTPFile[] files = ftpClient.listFiles();
            for(FTPFile file : files){
                Log.d("LISTTest", file.getName());
            }
        }catch(Exception e){

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
        final File file = new File(Environment.getExternalStorageDirectory() + filename);
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
                    InputStream is = new FileInputStream(filename);
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
        if(syncing){
            Log.d("FTPClient", "A sync is already in progress!");
            return;
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run(){
                Log.i("Connection", "Starting SYNC");
                requester.updateSyncBar("^checking for differences...", 0, activity);
                int currentProgress = 0;
                int maxUpProgress = 0;
                int maxDownProgress = 0;
                ArrayList<String> localNames;
                ArrayList<String> remoteNames;
                ArrayList<String> filesToUpload = new ArrayList<String>();
                ArrayList<String> filesToDownload = new ArrayList<String>();
                int changed = 0;
                int Uploaded = 0;
                int Downloaded = 0;
                int Unchanged = 0;
                localNames = getLocalDir();
                try{ remoteNames = getRemoteDir("/", "", 0); }catch(Exception e){Log.d("FTPClient", "Failed to get remote file listing"); return;}
                Log.d("FTPSync", localPath.toString());
                try {
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
                        Log.d("FTPSync", fileToDownload);
                        String newFile = fileToDownload;
                        checkFilepath(Environment.getExternalStorageDirectory() + "/frc2706");
                        OutputStream File = new FileOutputStream(Environment.getExternalStorageDirectory() + "/frc2706" + newFile);
                        downloadSync(fileToDownload);
                        changed += 1;
                        Downloaded += 1;
                        currentProgress += 1;
                        //String display = newFile.split("frc2706")[1];
                        String display = "test";
                        requester.updateSyncBar("Downloading file " + currentProgress + "/" + maxDownProgress + ":\n" + display, (currentProgress*100) / maxDownProgress, activity);
                    }
                    currentProgress = 0;
                    for (String fileToUpload : filesToUpload) {
                        Log.d("FTPSync", fileToUpload);
                        String newFile = fileToUpload;
                        checkFilepath(fileToUpload);
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
                    changed = -1;
                }
                String up = String.valueOf(Uploaded);
                String down = String.valueOf(Downloaded);
                String unchanged = String.valueOf(Unchanged);
                if(changed<1)
                    if(changed==0)
                        requester.updateSyncBar("Your device had the latest files!", 100, activity);
                    else
                        requester.updateSyncBar("Error while syncing, see debug for more info.", 100, activity);
                else
                    requester.updateSyncBar("Done syncing! Unchanged Files: "+Unchanged+"\n"+down+" downloaded, "+up+" uploaded.", 100, activity);
                Log.d("FTPSync", "Sync done!");

            }
        });
    }
    private void uploadSync(String filename){
        final String RemotePath = filename.split("frc2706")[1];
        Log.d("UploadSync", RemotePath);
        try {
            InputStream is = new FileInputStream(filename);
            ftpClient.storeFile(RemotePath, is);
        }catch(Exception e) {
            Log.e("FTPUpload", e.toString());
            return;
        }
    }
    private void downloadSync(String filename){
        File file = new File(Environment.getExternalStorageDirectory() + "/frc2706" + filename);
        file.mkdirs();

        String RemotePath = filename;
        try {
            OutputStream os = new FileOutputStream(file.getAbsolutePath());
            ftpClient.retrieveFile(RemotePath, os);
        }catch(Exception e){
            Log.e("FTP", e.toString());
            return;
        }
    }


    /**
     * WARNING!!!
     * Everything below this is still being
     * created and it doesn't work.
     * don't use it.


    public Boolean checkNetwork(){
        final ConnectivityManager connMgr = (ConnectivityManager)
                this.getSystemService(Context.CONNECTIVITY_SERVICE);
        final android.net.NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (wifi.isConnectedOrConnecting ()) {
            return true;
        } else if (mobile.isConnectedOrConnecting ()) {
            return false;
        } else {
            return false;
        }
    }
    */

    /**
     * The public function for getting the filenames of files inside
     * @return
     */
    public ArrayList<String> getLocalDir(){
        String topLevelPath = localPath.getAbsolutePath();
        ArrayList<String> filenames = new ArrayList<>();
        ArrayList<String> localDirSlaveReturn = new ArrayList<>();
        File topLevel = new File(topLevelPath);
        File[] topDir = topLevel.listFiles();
        for(File file : topDir){
            if(file.isDirectory()){
                filenames.addAll(localDirSlave(file.getAbsolutePath()));
            }else{
                filenames.add(file.getAbsolutePath());
            }
        }
        return filenames;
    }
    private ArrayList<String> localDirSlave(String currentPath){
        ArrayList<String> filenames = new ArrayList<>();
        File newLevel = new File(currentPath);
        for(File file : newLevel.listFiles()){
            if(file.isDirectory()){
                filenames.addAll(localDirSlave(file.getAbsolutePath()));
            }else{
                filenames.add(file.getAbsolutePath());
            }
        }
        return filenames;
    }
    public ArrayList<String> getRemoteDir(String parentDir, String currentDir, int level) throws IOException {
        ArrayList<String> filenames = new ArrayList<>();
        String dirToList = parentDir;
        if (!currentDir.equals("")) {
            dirToList += "/" + currentDir;
        }
        ftpClient.changeWorkingDirectory(dirToList);
        FTPFile[] subFiles = ftpClient.listFiles();
        if (subFiles != null && subFiles.length > 0) {
            for (FTPFile aFile : subFiles) {
                String currentFileName = aFile.getName();
                if (currentFileName.equals(".")
                        || currentFileName.equals("..")) {
                    // skip parent directory and directory itself
                    continue;
                }
                for (int i = 0; i < level; i++) {
                    System.out.print("\t");
                }
                if (aFile.isDirectory()) {
                    filenames.addAll(getRemoteDir(dirToList, currentFileName, level + 1));
                } else {
                    filenames.add(ftpClient.printWorkingDirectory() + "/" + aFile.getName());
                    Log.d("FTPClient", ftpClient.printWorkingDirectory() +  "/" + aFile.getName());
                }
            }
        }
        return filenames;
    }
    private void checkFilepath(String filename){
        File file = new File(filename);
        String currentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String[] Levels = filename.split("/");
        int level = 0;
        int bottomlevel = Levels.length - 1;
        while(level<bottomlevel){
            File directory = new File(currentPath);
            if(!directory.isDirectory()){
                directory.delete();
                directory.mkdir();
            }
            currentPath += "/" + Levels[level];
            level++;
        }
    }
}
