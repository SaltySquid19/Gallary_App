package com.ass1.a2_v2;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.WindowMetrics;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;

public class FullImage extends AppCompatActivity {

    private ImageView imageView;
    private int mPosition;
    private ArrayList<String> mImages;
    private ScaleGestureDetector mScaleGestureDetector;
    private float mScaleFactor = 1.f;
    private float mSwipeSize;
    private float getSwipeSize;

    private static final String POSITION = "position";

    float x1;




    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.full_image);
        imageView = findViewById(R.id.imageView);

        Intent intent = getIntent();
        mPosition = intent.getIntExtra("POSITION", 0);

        //get position on rotate
        if(savedInstanceState != null){
            mPosition = savedInstanceState.getInt(POSITION);
        }

        mImages = intent.getStringArrayListExtra("IMAGE_LIST");

        init();

    }


    public void init(){
        assert mImages != null;
        Log.i("mPosition init():", String.valueOf(mPosition));
        String path = mImages.get(mPosition);
        BitmapFactory.Options options = new BitmapFactory.Options();

        //getting size of image to change Options
        File mImageThing = new File(path);
        long mSize = mImageThing.length() / 1024;
        //Log.i("mSize", Long.toString(mSize)  );

        if(mSize > 12000){
            options.inSampleSize = 32;
        }else if(mSize > 5000){
            options.inSampleSize = 16;
        }else if(mSize > 3500){
            options.inSampleSize = 8;
        }else if(mSize > 2000){
            options.inSampleSize = 4;
        }else{
            options.inSampleSize = 2;
        }

        Bitmap bmp = BitmapFactory.decodeFile(path, options);

        //get and change orientation
        int rotate = getPhotoOrientation(path);
        Matrix matrix = new Matrix();
        matrix.postRotate(rotate);
        bmp = Bitmap.createBitmap(bmp , 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

        imageView.setImageBitmap(bmp);

        //get width of screen to calculate swipe size on swipe
        //though get width is deprecated
        mSwipeSize = (float) (getDisplay().getWidth()/ 1.70);
        Log.i("mSwipeSize:", String.valueOf(mSwipeSize));
        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        //save position on rotate
        savedInstanceState.putInt(POSITION, mPosition);
        super.onSaveInstanceState(savedInstanceState);
    }



    //only for pinch to zoom
//    public boolean onTouchEvent(MotionEvent motionEvent) {
//        mScaleGestureDetector.onTouchEvent(motionEvent);
//        return true;
//    }



    @Override
    public boolean onTouchEvent(MotionEvent event){
        mScaleGestureDetector.onTouchEvent(event);

        //swipe to switch images
            switch (event.getAction()) {
                //when first tough screen
                case MotionEvent.ACTION_DOWN:
                    x1 = event.getX();
                    break;
                //when finger list off screen
                case MotionEvent.ACTION_UP:
                    float finalX = event.getX();

                    //get distance from 2 points of x on screen
                    getSwipeSize =  Math.abs(finalX - x1);
                    Log.i("getSwipeSize:", String.valueOf(getSwipeSize));

                    //set the swipe to be a certain ration of the screen width
                    //so wont trigger swipe image when on pinch to zoom
                    if (x1 > finalX && getSwipeSize >= mSwipeSize) {
                        if (mPosition >= mImages.size() - 1) {
                            mPosition = mImages.size() - 1;

                        } else mPosition++;
                        Log.i("mPosition++:", String.valueOf(mPosition));
                        init();

                    } else if (x1 < finalX && getSwipeSize >= mSwipeSize) {
                        if (mPosition <= 0) {
                            mPosition = 0;
                        } else {
                            mPosition--;
                            Log.i("mPosition--:", String.valueOf(mPosition));
                            init();
                        }
                    }
                    break;
            }

        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 10.0f));
            imageView.setScaleX(mScaleFactor);
            imageView.setScaleY(mScaleFactor);

            imageView.invalidate();
            return true;
        }
    }

    //this is to check the orientation of the image
    //referenced from stackOverflow
    public int getPhotoOrientation(String imagePath){
        int rotate = 0;
        try {

            File imageFile = new File(imagePath);

            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return rotate;
    }



}
