package com.ass1.a2_v2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.lang.Object;



public class MainActivity extends AppCompatActivity {

    //gridview
    private GridView mGridview;
    private static final int NCOLS = 3;

    private ArrayList<String> mImages;

    //bitmap
    private LruCache<String, Bitmap> mCache;

    //single thread executor
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Cursor mCursor;

    //pinch
    private ScaleGestureDetector mScaleGestureDetector;

    Parcelable mSvaingState;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            init();
        } else{
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,},1);
        }


        //Lru cache - referenced from https://developer.android.com/reference/android/util/LruCache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        mCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    private void init(){
        mGridview = (GridView) findViewById(R.id.gridView);
        mGridview.setNumColumns(NCOLS);
        mImages = new ArrayList<>();

//        String id;
//        InputStream is;
//
//        id=mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
//        try {
//            is=getContentResolver().openInputStream(Uri.withAppendedPath(MediaStore.Images.Media.
//                    EXTERNAL_CONTENT_URI,id));
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        while(mCursor.moveToNext()){
//            mImages.add(id);
//        }


        mCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null,
                null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");

        while(mCursor.moveToNext()){
            int index = mCursor.getColumnIndex(MediaStore.Images.Media.DATA);
            mImages.add(mCursor.getString(index));

        }

        mCursor.close();


        ImageAdapter imageAdapter = new ImageAdapter();
        mGridview.setAdapter(imageAdapter);
        //Log.i("set adapter:", "adapter set" );

        //open full image
        mGridview.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                openImageActivity(mImages, i);
            }
        });

        //pinch to change scale
        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener(){

            private float mCols = NCOLS;

            @Override
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                mCols = mCols/ scaleGestureDetector.getScaleFactor();
                if(mCols<1)
                    mCols=1;
                if(mCols>6)
                    mCols=6;
                mGridview.setNumColumns((int)mCols);
                for(int i = 0; i< mGridview.getChildCount(); i++) {
                    if(mGridview.getChildAt(i)!=null) {
                        mGridview.getChildAt(i).setMinimumHeight(mGridview.getWidth()/(int)(mCols));
                    }
                }
                mGridview.invalidate();
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

            }
        });

        mGridview.setOnTouchListener((View view, MotionEvent motionEvent) -> {
            mScaleGestureDetector.onTouchEvent(motionEvent);
            return false;
        });

    }

    @Override
    public void onPause(){
        mSvaingState = mGridview.onSaveInstanceState();
        super.onPause();
    }

    @Override
    public void onResume(){
        super.onResume();
        mSvaingState = mGridview.onSaveInstanceState();
        init();
    }


    public class ImageAdapter extends BaseAdapter{


        class ViewHolder{
            int position;
            ImageView image;
        }

        @Override
        public int getCount(){return mImages.size();}

        @Override
        public Object getItem(int i) {return null;}

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup viewGroup) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.gallery, viewGroup,false);
                vh=new ViewHolder();
                vh.image = convertView.findViewById(R.id.mainGallery);
                convertView.setTag(vh);
            }else
                vh=(ViewHolder)convertView.getTag();

            //set to be square
            int width = mGridview.getWidth() / mGridview.getNumColumns();
            vh.image.setLayoutParams(new LinearLayout.LayoutParams(width,width));
            vh.position = i;
            vh.image.setImageBitmap(null);


            Bitmap thumbnail = mCache.get(String.valueOf(vh.position));
            if(thumbnail!= null) {
                vh.image.setImageBitmap(thumbnail);
            }


            //using executor since async is deprecated in api30
            mExecutor.submit(()->{
                if(vh.position!=i){
                    return;
                }
                String path = mImages.get(i);
                BitmapFactory.Options options = new BitmapFactory.Options();

                //getting size of image to change Options
                File mImageThing = new File(path);
                long mSize = mImageThing.length() / 1024;
                //Log.i("mSize", Long.toString(mSize)  );

                if(mSize > 12000){
                    options.inSampleSize = 64;
                }else if(mSize > 5000){
                    options.inSampleSize = 32;
                }else if(mSize > 3500){
                    options.inSampleSize = 16;
                }else if(mSize > 2000){
                    options.inSampleSize = 8;
                }else if(mSize > 100){
                    options.inSampleSize = 2;
                }else {
                    options.inSampleSize = 2;
                }

                Bitmap bmp;


                try {
                    if(vh.position != i){
                        return;
                    }
                    Bitmap bitmap = BitmapFactory.decodeFile(path,options);


                    //rotate bitmap
                    //some referenced from a stackOverflow post with code below
                    Matrix matrix = new Matrix();
                    int orientation = getPhotoOrientation(path);
                    matrix.postRotate(orientation);

                    bitmap = Bitmap.createBitmap(bitmap, 0,0, bitmap.getWidth(), bitmap.getHeight(),matrix,true);

                    bmp = ThumbnailUtils.extractThumbnail(bitmap,400,400);
                    String key = String.valueOf(i);

                    if(mCache.get(key) == null){
                        mCache.put(key,bmp);
                    }

                } catch (Exception e){
                    return;
                }

                if(vh.position == i){
                    vh.image.post(()->vh.image.setImageBitmap(bmp));
                }
            });



            //original without rotation and different Options
//            mExecutor.submit(()->{
//                if(vh.position!=i){
//                    return;
//                }
//                //String path = mImages.get(i);
//
//                final Bitmap bmp;
//                Bitmap thumbnail = BitmapFactory.decodeFile(mImages.get(vh.position));
//                //BitmapFactory.Options options = new BitmapFactory.Options();
//                //options.inSampleSize = 8;
//                try {
//                    if(vh.position != i){
//                        return;
//                    }
//                    //Bitmap thumbnail = BitmapFactory.decodeFile(path,options);
//                    bmp = ThumbnailUtils.extractThumbnail(thumbnail,400,400);
//                } catch (Exception e){
//                    return;
//                }
//                if(vh.position == i){
//                    vh.image.post(()->vh.image.setImageBitmap(bmp));
//                }
//            });

            return convertView;
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

    public void openImageActivity(ArrayList<String> mImages, int i){
        ArrayList<String> images = new ArrayList<>(mImages);
        Intent imageIntent = new Intent(this,FullImage.class);
        imageIntent.putExtra("POSITION", i);
        imageIntent.putExtra("IMAGE_LIST", images);

        this.startActivity(imageIntent);

    }






}