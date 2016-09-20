package com.jscheng.bitmapapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.jscheng.bitmapapplication.util.ImageLoader;

public class MainActivity extends AppCompatActivity {
    ImageLoader mImageLoader;
    ImageView imageView;
    String picurl = "http://pic47.nipic.com/20140830/7487939_180041822000_2.jpg";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageLoader = ImageLoader.build(MainActivity.this);
        imageView = (ImageView)findViewById(R.id.imageview);
        imageView.setTag(picurl);
        mImageLoader.bindBitmap(picurl, imageView, 100, 100);
    }
}
