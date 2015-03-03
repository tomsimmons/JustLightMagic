package com.example.android.photobyintent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class PhotoIntentActivity extends Activity {

    private static final int ACTION_TAKE_PHOTO_B = 1;
    private static final int ACTION_ANALYZE_PHOTO = 2;

    private static final String BITMAP_STORAGE_KEY = "viewbitmap";
    private static final String IMAGEVIEW_VISIBILITY_STORAGE_KEY = "imageviewvisibility";
    private ImageView mImageView;
    private Bitmap mImageBitmap;
    TextView mainTextView;


    private String mCurrentPhotoPath;

    private static final String JPEG_FILE_PREFIX = "IMG_";
    private static final String JPEG_FILE_SUFFIX = ".jpg";

    private AlbumStorageDirFactory mAlbumStorageDirFactory = null;

    /* Photo album for this application */
    private String getAlbumName() {
        return getString(R.string.album_name);
    }


    private File getAlbumDir() {
        File storageDir = null;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

            storageDir = mAlbumStorageDirFactory.getAlbumStorageDir(getAlbumName());

            if (storageDir != null) {
                if (! storageDir.mkdirs()) {
                    if (! storageDir.exists()){
                        Log.d("CameraSample", "failed to create directory");
                        return null;
                    }
                }
            }

        } else {
            Log.v(getString(R.string.app_name), "External storage is not mounted READ/WRITE.");
        }

        return storageDir;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = JPEG_FILE_PREFIX + timeStamp + "_";
        File albumF = getAlbumDir();
        File imageF = File.createTempFile(imageFileName, JPEG_FILE_SUFFIX, albumF);
        return imageF;
    }

    private File setUpPhotoFile() throws IOException {

        File f = createImageFile();
        mCurrentPhotoPath = f.getAbsolutePath();

        return f;
    }

    private void setPic() {
		/* There isn't enough memory to open up more than a couple camera photos */
		/* So pre-scale the target bitmap into which the file is decoded */


		/* Get the size of the ImageView */
        int targetW = mImageView.getWidth();
        int targetH = mImageView.getHeight();

		/* Get the size of the image */
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

		/* Figure out which way needs to be reduced less */
        int scaleFactor = 1;
        if ((targetW > 0) || (targetH > 0)) {
            scaleFactor = Math.min(photoW/targetW, photoH/targetH);
        }

		/* Set bitmap options to scale the image decode target */
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

		/* Decode the JPEG file into a Bitmap */
        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);

		/* Associate the Bitmap to the ImageView */
        //mImageView.setRotationX(90);
        mImageView.setImageBitmap(bitmap);
        mImageView.setVisibility(View.VISIBLE);
    }

    private void analyzePicture(){
        // Prime ImageView for Bitmap generation
        mImageView.buildDrawingCache();
        // Grab the raw pixels in a bitmap
        Bitmap bitmap = mImageView.getDrawingCache();


        if (bitmap == null) // If the user has not taken a pic yet
        {
            mainTextView.setText("Take a photo first");
        }
        else // We're in business
        {
            // If we are analyzing the photo, save it to internal storage
            galleryAddPic();
            //mCurrentPhotoPath = null; // We may need this, but it could prevent the crash from double analysis presses

            Canvas canvas = new Canvas(bitmap);


            //mImageView.draw(canvas);
            // Create new bitmap, crop full pic to the dimensions we want
            Bitmap result; // = Bitmap.createBitmap(bitmap,0, mImageView.getHeight()*2/3, mImageView.getWidth(), 1);
            try{
                // Because image is rotated, crop from 2/3 along x axis 1px wide down the middle
                result = Bitmap.createBitmap(bitmap,mImageView.getWidth()/2, 0, 1, mImageView.getHeight());
            } catch (IllegalArgumentException e)
            {
                Log.d("Analyze","Error cropping");
                result = Bitmap.createBitmap(bitmap,0, mImageView.getHeight()/2, mImageView.getHeight(), 1);
            }
            //bitmap.recycle();


            // Convert the bitmap into an array of raw pixelly goodness
            // TODO: Change array name to be more apropos
            int[] byteArray = new int[result.getHeight()];
            for (int i=0; i<result.getHeight();i++)
            {
                byteArray[i] = result.getPixel(0,i);
            }

            // Log messages
            //Log.d("Analyze","result width: " + result.getWidth());
            //Log.d("Analyze","result height: " + result.getHeight());

            // Find the dominant colors
            /*
            int grn, blu, red; grn = blu = red = 0;
            for (int i=0; i<byteArray.length; i++)
            {

                if (Color.blue(byteArray[i]) > Color.green(byteArray[i]))
                {
                    if (Color.blue(byteArray[i]) > Color.red(byteArray[i])) blu++;
                    else red++;
                }
                else
                {
                    if (Color.green(byteArray[i]) > Color.red(byteArray[i])) grn++;
                    else red++;
                }

                if (Color.blue(byteArray[i])>120) blu++;
                if (Color.green(byteArray[i])>120) grn++;
                if (Color.red(byteArray[i])>120) red++;

            }
            //Log.d("Analyze","Red: " + red + " Green: " + grn + " Blue: " + blu);
            */

            double r, g, b;
            double delta, Luminance, Hue, Lambda;
            ArrayList<Double> firstValues = new ArrayList<Double>();
            ArrayList<Double> duplicateValues = new ArrayList<Double>();
            int lastLambda = -1;

            String wavelengths = "";

            //check luminance and if above a certain threshold put em in an array
            for(int i=0;i<byteArray.length;i++){
                double cMax = 0.0;
                double cMin = 1.0;

                r = (double)Color.red(byteArray[i]);
                g = (double)Color.green(byteArray[i]);
                b = (double)Color.blue(byteArray[i]);

                r=r/255;
                g=g/255;
                b=b/255;

                if(r>cMax){
                    cMax=r;
                }
                if(g>cMax){
                    cMax=g;
                }
                if(b>=cMax){
                    cMax=b;
                }

                if(r<cMin){
                    cMin=r;
                }
                if(g<cMin){
                    cMin=g;
                }
                if(b<=cMin){
                    cMin=b;
                }

                Luminance=(cMax+cMin)/2;

                if(Luminance>.20&&Luminance<=.80){ //more than 50%, less than 80% (since too bright ones get hecked up)
                    delta = cMax - cMin;

                    if(r==cMax){
                        Hue = (g-b)/delta;
                    }
                    else if(g==cMax){
                        Hue = 2.0 + (b-r)/delta;
                    }
                    else{
                        Hue = 4.0 + (r-g)/delta;
                    }

                    Hue=Hue*40;
                    if(Hue<0){Hue = (Hue + 360)*2/3;} //adjust hues and whatever
                    if(Hue>230){Hue=0;}

                    Lambda = 648.97-1.7407*(Hue)+0.038362*Math.pow(Hue,2)-0.002093*Math.pow(Hue,3)+
                            0.000039528*Math.pow(Hue,4)-0.00000033297*Math.pow(Hue,5)+0.0000000013054*Math.pow(Hue,6)-
                            0.0000000000019497*Math.pow(Hue,7);

                    if(Lambda>=380&&Lambda<=780){
                        /*Boolean isDuplicate = Boolean.FALSE;

                        for(int j=0;j<firstValues.size();j++) { //check current hue against already recorded hues {
                            double currentLambda = (double) firstValues.get(j);

                            Log.d("Analyze", "currentLambda: " + currentLambda);
                            if (Lambda > currentLambda - 10 && Lambda < currentLambda + 10) { //if similar enough to another hue
                                isDuplicate = Boolean.TRUE;
                            }
                        }

                        if(!isDuplicate && duplicateValues.size()>0) {
                            firstValues.add(Lambda);

                            double Sum = 0.0;
                            for (int k = 0; k < duplicateValues.size(); k++) {
                                Sum = Sum + (double) duplicateValues.get(k);
                            }
                            double average = Sum / duplicateValues.size();
                            duplicateValues.clear();

                            duplicateValues.add(Lambda);

                            wavelengths += String.format("%.00f",average) + "nm \n";
                        }

                        else if(isDuplicate){
                            duplicateValues.add(Lambda);
                        }
                        else{
                            wavelengths+=String.format("%.00f",Lambda) + "nm \n";
                        }
                        */
                        if (lastLambda < 0)
                        {
                            duplicateValues.add(Lambda);
                            lastLambda = 0;
                        }
                        else
                        {
                            if (Lambda > duplicateValues.get(lastLambda) - 10 && Lambda < duplicateValues.get(lastLambda) + 10)
                            {
                                duplicateValues.add(Lambda);
                                lastLambda++;
                            }
                            else
                            {
                                double average = 0;
                                for (int j=0; j<lastLambda+1;j++)
                                {
                                    average += duplicateValues.get(j);
                                }
                                average = average / (lastLambda+1);

                                wavelengths += String.format("%.00f",average) + "nm \n";

                                duplicateValues.clear();
                                lastLambda = -1;
                            }
                        }
                    }

                }
            }

            Log.d("Analyze","the wavelengths are: \n" + wavelengths );
            mainTextView.setText(wavelengths);

        }

        //mainTextView.setText("Band 1: 410 nm\nBand 2: 486 nm\nBand 3: 656 nm");
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    private void dispatchTakePictureIntent(int actionCode) {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        switch(actionCode) {
            case ACTION_TAKE_PHOTO_B:
                File f = null;

                try {
                    f = setUpPhotoFile();
                    mCurrentPhotoPath = f.getAbsolutePath();
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                } catch (IOException e) {
                    e.printStackTrace();
                    f = null;
                    mCurrentPhotoPath = null;
                }
                break;

            default:
                break;
        } // switch

        startActivityForResult(takePictureIntent, actionCode);
    }

    private void handleBigCameraPhoto() {
        mainTextView.setText("");
        if (mCurrentPhotoPath != null) {
            setPic();
            //mCurrentPhotoPath = null;
        }
    }

    Button.OnClickListener mTakePicOnClickListener =
            new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dispatchTakePictureIntent(ACTION_TAKE_PHOTO_B);
                }
            };

    /*
    Button.OnClickListener mAnalyzePicOnClickListener =
        new Button.OnClickListener() {
        @Override
            public void onClick(View v) {
                dispatchAnalyzePictureIntent(ACTION_ANALYZE_PHOTO);
            }
        };
    */

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mImageView = (ImageView) findViewById(R.id.imageView1);
        mImageBitmap = null;

        Button picBtn = (Button) findViewById(R.id.btnIntend);

        setBtnListenerOrDisable(
                picBtn,
                mTakePicOnClickListener,
                MediaStore.ACTION_IMAGE_CAPTURE
        );

        Button analysisBtn = (Button) findViewById(R.id.btnIntendAnalyze);

        analysisBtn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                analyzePicture();

            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mAlbumStorageDirFactory = new FroyoAlbumDirFactory();
        } else {
            mAlbumStorageDirFactory = new BaseAlbumDirFactory();
        }
        mainTextView = (TextView) findViewById(R.id.main_textview);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTION_TAKE_PHOTO_B: {
                if (resultCode == RESULT_OK) {
                    handleBigCameraPhoto();
                }
                break;
            } // ACTION_TAKE_PHOTO_B

            case ACTION_ANALYZE_PHOTO: {
                if (resultCode == RESULT_OK) {
                    analyzePicture();
                }
                break;
            } // ACTION_TAKE_PHOTO_ANALYZE
        } // switch
    }

    // Some lifecycle callbacks so that the image can survive orientation change
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(BITMAP_STORAGE_KEY, mImageBitmap);
        outState.putBoolean(IMAGEVIEW_VISIBILITY_STORAGE_KEY, (mImageBitmap != null) );
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mImageBitmap = savedInstanceState.getParcelable(BITMAP_STORAGE_KEY);
        mImageView.setImageBitmap(mImageBitmap);
        mImageView.setVisibility(
                savedInstanceState.getBoolean(IMAGEVIEW_VISIBILITY_STORAGE_KEY) ?
                        ImageView.VISIBLE : ImageView.INVISIBLE
        );
    }

    /**
     * Indicates whether the specified action can be used as an intent. This
     * method queries the package manager for installed packages that can
     * respond to an intent with the specified action. If no suitable package is
     * found, this method returns false.
     * http://android-developers.blogspot.com/2009/01/can-i-use-this-intent.html
     *
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and
     *         responded to, false otherwise.
     */
    public static boolean isIntentAvailable(Context context, String action) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    private void setBtnListenerOrDisable(
            Button btn,
            Button.OnClickListener onClickListener,
            String intentName
    ) {
        if (isIntentAvailable(this, intentName)) {
            btn.setOnClickListener(onClickListener);
        } else {
            btn.setText(
                    getText(R.string.cannot).toString() + " " + btn.getText());
            btn.setClickable(false);
        }
    }

}