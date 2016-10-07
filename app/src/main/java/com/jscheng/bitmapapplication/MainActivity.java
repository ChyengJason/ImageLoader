package com.jscheng.bitmapapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.jscheng.bitmapapplication.util.ImageLoader;

public class MainActivity extends AppCompatActivity {
    ImageView imageView;
    TextView textView;
    String picurl = "http://pic47.nipic.com/20140830/7487939_180041822000_2.jpg";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView)findViewById(R.id.imageview);
        textView = (TextView)findViewById(R.id.text);

        ImageLoader.build(this)
                .load(picurl)
                //.setSize(200,200)
                .into(imageView);
    }
}
