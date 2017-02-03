package ca.team2706.scouting.ftptest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.InputStream;
import java.net.ConnectException;
import java.security.Permission;

public class MainActivity extends AppCompatActivity implements FTPRequester {

    //FTPClient object
    FTPClient ftpclient = new FTPClient("ftp.team2706.ca", "scout", "2706IsWatching!", "/frc2706");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Android code
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Request for permissions
        requestPermissions();
        try {
            //Try to connect
            ftpclient.connect();
            if (ftpclient.isConnected()) {
                //really do nothing because the rest is started when the button is clcked.
            } else {
                //FTPClient didn't connect.
                Log.d("FTPClient", "Failed to connect, cannot DIR.");
            }
        } catch (ConnectException e) {
            // it must have failed to connect, or something threw an error so do nothing.
        }
    }

    /**
     * place code here for when the client is done uploading a file.
     * This is run on the background thread
     * @param localFilename: Local path to file
     * @param remoteFilename: Remote path to file
     */
    public void uploadFileCallback(String localFilename, String remoteFilename){

    }

    /**
     * Place code here for when the client is done downloading a file.
     * This is run on the background thread
     * @param localFilename: Local path to file
     * @param remoteFilename: Remote path to file
     */
    public void downloadFileCallback(String localFilename, String remoteFilename) {

    }

    /**
     * Place code here for when the client is done syncing files.
     * This is run on the background thread
     * @param changedFiles: number of files uploaded and downloaded
     */
    public void syncCallback(int changedFiles){

    }

    /**
     * Place code here for when the client is done getting a file listing.
     * This is run on the background thread
     * @param filelisting
     */
    public void dirCallback(FTPFile[] filelisting){

    }

    /**
     * Place code here to display progress of sync.
     * @param Caption: Current caption to place above sync bar
     * @param Progress: Progress out of 100 to be displayed.
     */
    public void updateSyncBar(final String Caption, final int Progress, Activity activity){
        activity.runOnUiThread(new Runnable(){
            @Override
            public void run(){
                TextView tv = (TextView)findViewById(R.id.syncCaption);
                ProgressBar pb = (ProgressBar)findViewById(R.id.syncProgressBar);
                if(Caption.startsWith("^")){
                    pb.setIndeterminate(true);
                    tv.setText("Scanning for differences...");
                }else{
                    pb.setIndeterminate(false);
                    pb.setProgress(Progress);
                    tv.setText(Caption);
                }
            }
        });
    }

    /**
     * Checks for read / write permissions, and requests them if neccesary.
     * This function also creates neccesary folders for the FTP Library.
     */
    void requestPermissions(){
        Boolean ReadStoragePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED;
        Boolean WriteStoragePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED;
        if (ReadStoragePerm&&WriteStoragePerm) {
            //We already have both permissions, so we're good.
        }else{
            //We need to get one of the two permissions, so we'll ask for both.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            }
        }
        File frc2706 = new File(Environment.getExternalStorageDirectory() + "/frc2706");
        File files = new File(Environment.getExternalStorageDirectory() + "/frc2706/files");
        if(!frc2706.exists()){
            frc2706.mkdir();
        }
        if(!files.exists()){
            files.mkdir();
        }
    }

    void startSync(View v){
        try{
            ftpclient.syncAllFiles(this, this);
        }catch(Exception e){
            Log.e("FTPClient", e.toString());
        }
    }
}
