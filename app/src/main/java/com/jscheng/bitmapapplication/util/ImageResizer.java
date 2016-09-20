package com.jscheng.bitmapapplication.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by dell on 2016/9/20.
 */
public class ImageResizer {
    public static final String TAG = "ImageResizer";

    public ImageResizer(){

    }

    public Bitmap decodeSampledBitmapFromResource(Resources res,int resId,int reqWidth,int reqHeight){
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res,resId,options);
        options.inSampleSize = caculateInSampleSize(options,reqWidth,reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res,resId,options);
    }

    private static int caculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if(height>reqHeight || width>reqWidth){
            final int halfHeight = height/2;
            final int halfWidth = width/2;
            while(halfHeight/inSampleSize>reqHeight || halfWidth/inSampleSize>reqWidth){
                inSampleSize*=2;
            }
        }
        return inSampleSize;
    }

    public Bitmap decodeSampledBitmapFromDescriptor(FileDescriptor fd,int reqWidth,int reqHeight){
        final BitmapFactory.Options option = new BitmapFactory.Options();
        option.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd,null,option);
        option.inSampleSize = caculateInSampleSize(option,reqWidth,reqHeight);
        option.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd,null,option);
    }
}
