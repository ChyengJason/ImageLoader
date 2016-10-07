package com.jscheng.bitmapapplication;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

/**
 * Created by cheng on 16-10-6.
 */
public class BitmapApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LeakCanary.install(this);
    }
}
