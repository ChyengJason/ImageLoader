package com.jscheng.bitmapapplication.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by cheng on 16-10-4.
 */
public class HttpUtils {

    private final static int IO_BUFFER_SIZE = 8*1024;
    public static boolean downloadUrlToStream(String urlString, final OutputStream outputStream){

        final Request request = new Request.Builder().url(urlString).build();
        OkHttpClient mOkHttpClient = new OkHttpClient();
        Call call = mOkHttpClient.newCall(request);
//        call.enqueue(new Callback() {
//            @Override
//            public void onFailure(Request request, IOException e) {
//                result = false;
//            }
//
//            @Override
//            public void onResponse(Response response) {
//                BufferedOutputStream out = null;
//                BufferedInputStream in = null;
//                try {
//                    in = new BufferedInputStream(response.body().byteStream(),IO_BUFFER_SIZE);
//                    out = new BufferedOutputStream(outputSream,IO_BUFFER_SIZE);
//                    try {
//                        in.reset();
//                    } catch (IOException e) {
//                        in = new BufferedInputStream(response.body().byteStream(),IO_BUFFER_SIZE);
//                    }
//                }
//                catch (Exception e){
//                    e.printStackTrace();
//                }
//                finally
//                {
//                    if (in != null) try {
//                        in.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    if (out != null) try {
//                        out.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
        Response response = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            response = call.execute();
            if(response.body()==null)return false;
            in = new BufferedInputStream(response.body().byteStream(),IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream,IO_BUFFER_SIZE);
            //in.reset();
            //byte[] buffer = new byte[1024];
            int len;
            while((len=in.read())!=-1)
                out.write(len);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(in!=null) {
                    in.close();
                }
                if(out!=null){
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static Bitmap downloadBitmapFromUrl(String urlString){
        Bitmap bitmap = null;
        final Request request = new Request.Builder().url(urlString).build();
        OkHttpClient mOkHttpClient = new OkHttpClient();
        Call call = mOkHttpClient.newCall(request);
        Response response = null;
        try {
            response = call.execute();
            bitmap= BitmapFactory.decodeStream(response.body().byteStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}
