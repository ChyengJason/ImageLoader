package com.jscheng.bitmapapplication.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;
import com.jscheng.bitmapapplication.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by dell on 2016/9/20.
 */
public class ImageLoader {
    private final static String TAG = "ImageLoader";
    public final static String CACHE_FILE = "cacheBitmap";
    public static final int MESSAGE_POST_RESULT = 1;
    private final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private final static int CORE_POOL_SIZE = CPU_COUNT+1;
    private final static int MAXIMUM_POOL_SIZE = CPU_COUNT*2+1;
    private final static long KEEP_LIVE = 10L;

    private final static int TAG_KEY_URL = R.id.imageLoader_url;// ?
    private final static long DISK_CACHE_SIZE = 1024*1024*50;
    private final static int IO_BUFFER_SIZE = 8*1024;
    private final static int DISK_CACHE_INDEX = 0;
    private boolean mIsDiskLruCacheCreated = false;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable,"ImageLoad#"+mCount.getAndIncrement());
        }
    };

    private static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE,MAXIMUM_POOL_SIZE,KEEP_LIVE,
            TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>(),sThreadFactory);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult)msg.obj;
            ImageView imageView = result.imageView;
            String url = (String)imageView.getTag(TAG_KEY_URL);
            if(url.equals(result.uri)){
                imageView.setImageBitmap(result.bitmap);
            }else{
                Log.w(TAG, "handleMessage: bitmap url changed" );
            }
        }
    };

    private ImageResizer mImageResizer = new ImageResizer();
    private LruCache<String,Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;
    private Context mContext;

    private ImageLoader(Context context){
        this.mContext = context.getApplicationContext();
        int maxMemory = (int)(Runtime.getRuntime().maxMemory()/1024);//Java虚拟机将尝试使用的最大内存量，以字节单位--》kb
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String,Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight()/1024;// 获取大小并返回
                //Bitmap所占用的内存空间数等于Bitmap的每一行所占用的空间数乘以Bitmap的行数
            }
        };

        File diskCachDir = getDiskCacheDir(mContext,CACHE_FILE);
        if(!diskCachDir.exists()){
            diskCachDir.mkdirs();//创建文件夹
        }

        if(getUsableSpace(diskCachDir) > DISK_CACHE_SIZE){
            try {
                mDiskLruCache = DiskLruCache.open(diskCachDir,1,1,DISK_CACHE_SIZE);
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader build(Context context){
        return new ImageLoader(context);
    }

    private void addBitmapToMemoryCache(String key,Bitmap bitmap){
        if(getBitmapfromMemCache(key)==null){
            mMemoryCache.put(key,bitmap);
        }
    }

    private Bitmap getBitmapfromMemCache(String key){
        return mMemoryCache.get(key);
    }

    public void bindBitmap(final String url,final ImageView imageView){
        bindBitmap(url,imageView,0,0);
    }

    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URL,url);
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if(bitmap!=null){
            imageView.setImageBitmap(bitmap);
            return;
        }

        final Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url,reqWidth,reqHeight);
                if(bitmap!=null){
                    LoaderResult result = new LoaderResult(imageView,url,bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    public Bitmap loadBitmap(String url,int reqWidth,int reqHeight){
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if(bitmap!=null){
            Log.d(TAG, "loadBitmap: from MemCache->"+url);
            return bitmap;
        }

        try{
            bitmap = loadBitmapFromDiskCache(url,reqWidth,reqHeight);
            if(bitmap!=null){
                Log.d(TAG, "loadBitmap: from DiskCache->"+url);
                return bitmap;
            }
            bitmap = loadBitmapFromHttp(url,reqWidth,reqHeight);
            Log.d(TAG, "loadBitmap: from http-->"+url);
        }catch (IOException e){
            e.printStackTrace();
        }

        if(bitmap==null&&!mIsDiskLruCacheCreated){
            Log.w(TAG, "loadBitmap: diskLruCache is not created");
            bitmap = downloadBitmapFromUrl(url);
        }

        return bitmap;
    }

    private Bitmap loadBitmapFromMemCache(String url){
        final String key = hashKeyFormUrl(url);
        Bitmap bitmap = getBitmapfromMemCache(key);
        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url,int reqWidth,int reqHeight) throws IOException {
        if(Looper.myLooper()==Looper.getMainLooper()){
            throw new RuntimeException("cannot do network from ui thread");
        }
        if(mDiskLruCache==null)
            return null;
        String key = hashKeyFormUrl(url);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if(editor!=null){
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if(downloadUrlToStream(url,outputStream)){
                editor.commit();
            }else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBitmapFromDiskCache(url,reqWidth,reqHeight);
    }

    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) throws IOException {
        if(Looper.myLooper()==Looper.getMainLooper()){
            Log.w(TAG, "loadBitmapFromDiskCache: load bitmap from ui thread it is not recommended" );
        }
        if(mDiskLruCache == null){
            return null;
        }

        Bitmap bitmap = null;
        String key = hashKeyFormUrl(url);
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        if(snapshot!=null){
            FileInputStream fileInputStream = (FileInputStream)snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor descriptor = fileInputStream.getFD();
            bitmap = mImageResizer.decodeSampledBitmapFromDescriptor(descriptor,reqWidth,reqHeight);
            if(bitmap!=null){
                addBitmapToMemoryCache(key,bitmap);
            }
        }
        return bitmap;
    }

    public boolean downloadUrlToStream(String urlString,OutputStream outputSream){
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try{
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection)url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputSream,IO_BUFFER_SIZE);

            int b;
            while((b = in.read())!=-1){
                out.write(b);
            }
            return true;
        }catch (IOException e){
            e.printStackTrace();
            Log.e(TAG, "downloadUrlToStream: download picture fail" );
        }finally {
            if(urlConnection!=null){
                urlConnection.disconnect();
            }
            close(in);
            close(out);
        }
        return false;
    }

    private Bitmap downloadBitmapFromUrl(String urlString){
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        try{
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection)url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        }catch (IOException e){
            Log.e(TAG, "downloadBitmapFromUrl: error"+e );
        }finally {
            if(urlConnection!=null){
                urlConnection.disconnect();
            }
            close(in);
        }
        return bitmap;
    }

    private String hashKeyFormUrl(String url){
        String cacheKey;
        try{
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        }catch (NoSuchAlgorithmException e){
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static class LoaderResult {
        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }
    }

    public File getDiskCacheDir(Context context,String uniqueName){
        boolean extenalStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if(extenalStorageAvailable){
            cachePath = context.getExternalCacheDir().getPath();
        }else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath+File.separator+uniqueName);
    }

    private long getUsableSpace(File path){
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.GINGERBREAD){
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return ((long)stats.getBlockSize()*(long)stats.getAvailableBlocks());
    }

    public static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
