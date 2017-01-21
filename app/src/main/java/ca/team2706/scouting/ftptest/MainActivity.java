package ca.team2706.scouting.ftptest;

import android.Manifest;
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
import android.widget.ImageView;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.InputStream;
import java.net.ConnectException;
import java.security.Permission;

public class MainActivity extends AppCompatActivity
        implements FTPRequester {

    FTPFile[] filelisting;
    FTPClient ftpclient = new FTPClient("ftp.team2706.ca", "scout", "2706IsWatching!", Environment.getExternalStorageDirectory() + "/frc2706/files");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED&&(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        try {
            ftpclient.connect();
            if (ftpclient.isConnected()) {
                ftpclient.dir(this);   // Kick off the network call to get the file list
                ftpclient.syncAllFiles(this, this);
            }else{
                Log.d("FTPClient", "Failed to connect, cannot DIR.");
            }
        }
        catch (ConnectException e) {
            // it must have failed to connect, so do nothing.
        }
    }


    /** This method accepts a list of files as a FTPFile[],
     * then it sets that list into the global variable FTPFile[] filelisting,
     * and finally it prints the list to the log.
     **/
    public void setFileList(FTPFile[] fileList) {
        this.filelisting = fileList;

        String toDisplay = "";
        for(int i = 0; i < filelisting.length; i++){
            toDisplay += "\n" + filelisting[i].getName();
        }

        Log.d("FTPdir", toDisplay);
    }
    public void getFileStream(InputStream filestream){
        Log.d("FTPCLIENT", "Function Started");
        try {
            int character;
            String string = "";
            while ((character = filestream.read()) != -1) {
                string += (char) character;
            }
            Log.i("FTPClient", string);
        } catch (Exception e) {
            Log.e("FTPClient", e.toString());
        }
        BitmapDrawable.createFromStream(filestream, "");

    }
    public void uploadFile(String filename){

    }
    public void downloadFile(String filename) {
        Log.d("FTPCLIENT", "Function Started");
        try {
            File imgFile = new File(filename);
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                ImageView myImage = (ImageView) findViewById(R.id.imageView);
                myImage.setImageBitmap(myBitmap);
            }
        } catch (Exception e) {
            Log.e("FTPClient", e.toString());
        }

    }
    public void doneSync(){

    }
}
