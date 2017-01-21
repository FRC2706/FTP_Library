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
    public String Thing;
    //FTPClient ftpClient = new FTPClient();
    org.apache.commons.net.ftp.FTPClient ftpClient = new org.apache.commons.net.ftp.FTPClient();
    String Hostname;
    String Password;
    String Username;
    File LocalPath;
    public boolean syncing;
    int Port;
    private Object connectedLock = new Object();  // some multi-threading voodoo, we can explain in person.
    private boolean connected = false;
    private FTPFile[] DirReturn;

    public FTPClient(String Hostname, String Username, String Password, String LocalPath){
        this.Hostname = Hostname;
        this.Password = Password;
        this.Username = Username;
        this.LocalPath = new File(LocalPath);
        this.Port = 21;
    }


    public FTPClient(String Hostname, String Username, String Password, String LocalPath, int Port){
        this.Hostname = Hostname;
        this.Password = Password;
        this.Username = Username;
        this.LocalPath = new File(LocalPath);
        this.Port = Port;
    }

    public boolean isConnected() {
        synchronized (connectedLock) {
            return connected;
        }
    }

    /**
     *
     * @throws ConnectException If the connection to the server fails.
     */
    public void connect() throws ConnectException {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (connectedLock) {  // some mutli-threading voodoo
                                                // keep a lock on this while we are opening the
                                                // connection so other threads
                                                // know we are in the process of connecting
                                                // and they should wait for this to finish.
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
            Thread.sleep(5);  // multi-threaded voodoo. Give the AsyncTask 5 ms to get started and get the lock.
        } catch (InterruptedException e) {
            // do nothing.
        }

        synchronized (connectedLock) {  // Some multi-threaded voodoo.
                                        // Only one thread can have the lock at a time, so this will
                                        // is will block until the Async task is finished.
            if (!connected) {
                throw new ConnectException("FTPClient failed to connect to FTP server");
            }
        }
    }

    /**
     * @param requester the UI Activity that called me, once I have my data, I'll hand it back to
     *                  this Activity's setFileList(..) method.
     *
     * @throws ConnectException If you try to call this when we are not properly connected to an FTP server.
     */
    public void dir(final FTPRequester requester) throws ConnectException{

        synchronized (connectedLock) {  // Some multi-threaded voodoo.
                                        // Only one thread can have the lock at a time, so if a
                                        // connection is in progress, we will get blocked
                                        // until it is finished.
            if (!connected) {
                throw new ConnectException("FTPClient can not perform a dir() if it is not connected!");
            }
        }

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i("Connection", "Starting DIR");
                try {
                    FTPFile[] files = ftpClient.listFiles();  // files
                    Log.i("FTP", ftpClient.getReplyString());

                    requester.setFileList(files);   // hand the file list back to whichever Activity
                                                    // called me
                }catch(Exception e){
                    Log.e("FTPDir", e.toString());

                    // Option 1: call requester.setFileList(null);
                    // so that it knows the call failed and can print something to the user.

                    // Option 2: don't do anything and don't report anything to the user.
                }
            }
        });
    }
    public void downloadFile(final String filename, final FTPRequester requester, final Activity activery) {
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
                }catch(Exception e){
                    Log.e("FTP", e.toString());
                    return;
                }
            }
        });
    }
    public void uploadFile(final String filename, final FTPRequester requester, final Activity activery){
        final String RemotePath = filename;
        AsyncTask.execute(new Runnable(){
            @Override
            public void run(){
                try {
                    InputStream is = new FileInputStream(LocalPath.getAbsolutePath() + "/" + filename);
                    ftpClient.storeFile(filename, is);
                }catch(Exception e){
                    Log.e("FTPUpload", e.toString());
                    return;
                }
            }
        });
    }
    public void syncAllFiles(final FTPRequester requester, final Activity activery){
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
                    Log.d("FTPSync", "\nLocal Detection");
                    for(int i = 0; i < LocalFiles.length; i++){
                        Log.d("FTPSync", LocalFiles[i].getName());
                        LocalNames.add(LocalFiles[i].getName());
                    }
                    Log.d("FTPSync", "\nRemote Detection");
                    for(int i = 0; i < RemoteFiles.length; i++){
                        Log.d("FTPSync", RemoteFiles[i].getName());
                        RemoteNames.add(RemoteFiles[i].getName());
                    }
                    Log.d("FTPSync", "\nLocal Compare");
                    for (int i = 0; i < RemoteNames.size(); i++) {
                        Log.d("FTPSync", RemoteNames.get(i));
                        if (!LocalNames.contains(RemoteNames.get(i)))
                            Download.add(RemoteNames.get(i));
                    }
                    Log.d("FTPSync", "\nRemote Compare");
                    for (int i = 0; i < LocalNames.size(); i++) {
                        Log.d("FTPSync", LocalNames.get(i));
                        if (!RemoteNames.contains(LocalNames.get(i)))
                            Upload.add(LocalNames.get(i));
                    }
                    Log.d("FTPSync", "\nLocal Update");
                    for (int i = 0; i < Download.size(); i++) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + Download.get(i);
                        Log.d("FTPSync", newFile);
                        OutputStream File = new FileOutputStream(newFile);
                        downloadFile(Download.get(i), requester, activery);
                    }
                    Log.d("FTPSync", "\nRemote Update");
                    for (int i = 0; i < Upload.size(); i++) {
                        String newFile = Environment.getExternalStorageDirectory() + "/frc2706/files/" + Upload.get(i);
                        Log.d("FTPSync", newFile);
                        InputStream File = new FileInputStream(newFile);
                        uploadFile(Upload.get(i), requester, activery);
                    }
                }catch(Exception e){
                    Log.e("FTPSync", e.toString());
                }
                syncing = false;
                Log.d("FTPSync", "Sync done!");
            }
        });
    }
}
