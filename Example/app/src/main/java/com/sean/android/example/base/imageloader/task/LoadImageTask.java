package com.sean.android.example.base.imageloader.task;

import android.graphics.Bitmap;
import android.os.Handler;
import android.widget.ImageView;

import com.sean.android.example.base.imageloader.ImageInfo;
import com.sean.android.example.base.imageloader.ImageLoadingListener;
import com.sean.android.example.base.imageloader.ImageSize;
import com.sean.android.example.base.imageloader.cache.ImageCache;
import com.sean.android.example.base.imageloader.decoder.ImageDecoder;
import com.sean.android.example.base.imageloader.decoder.ImageFileDecoder;
import com.sean.android.example.base.imageloader.decoder.ImageType;
import com.sean.android.example.base.imageloader.executor.ImageLoadExecutor;
import com.sean.android.example.base.imageloader.view.ViewWrapper;
import com.sean.android.example.base.protocol.Client;
import com.sean.android.example.base.protocol.ConnectException;
import com.sean.android.example.base.protocol.RequestData;
import com.sean.android.example.base.protocol.ResponseData;
import com.sean.android.example.base.protocol.UrlConnectionClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Seonil on 2017-03-13.
 */

public class LoadImageTask implements Runnable {


    private ImageLoadExecutor imageLoadExecutor;

    private ImageInfo imageInfo;

    private ImageCache imageCache;

    private Client imageDownloader;

    private ImageDecoder imageDecoder;

    private ViewWrapper<ImageView> imageViewWrapper;

    private final String uri;

    private final String memoryCacheKey;

    private final ImageLoadingListener imageLoadingListener;

    private final Handler handler;

    private final ImageSize imageSize;

    public LoadImageTask(ImageLoadExecutor imageLoadExecutor, ImageInfo imageInfo, Handler handler) {
        this.imageLoadExecutor = imageLoadExecutor;
        this.imageCache = imageLoadExecutor.getImageCache();
        this.imageInfo = imageInfo;
        this.uri = imageInfo.getUri();
        this.handler = handler;
        this.memoryCacheKey = imageInfo.getMemoryCacheKey();
        this.imageSize = imageInfo.getImageSize();
        this.imageViewWrapper = imageInfo.getImageViewWrapper();
        this.imageLoadingListener = imageInfo.getLoadingListener();

        this.imageDecoder = new ImageFileDecoder();
        this.imageDownloader = new UrlConnectionClient();
    }

    @Override
    public void run() {
        if (isPaused()) return;

        ReentrantLock loadFromUriLock = imageInfo.getLoadFromUriLock();

        loadFromUriLock.lock();
        Bitmap bitmap;

        try {
            bitmap = imageCache.getBitmapFromMemCache(memoryCacheKey);

            if (bitmap == null || bitmap.isRecycled()) {
                bitmap = tryLoadBitmap();
                if (bitmap == null)
                    return;

                checkTaskNotActual();
                checkTaskInterrupted();


                if (bitmap != null) { // Memory Caching
                    imageCache.put(memoryCacheKey, bitmap);
                }
            }
        } catch (Exception e) {
            fireCancelEvent();
            return;
        } finally {
            loadFromUriLock.unlock();
        }

        DisplayImageTask displayBitmapTask = new DisplayImageTask(bitmap, imageInfo, imageLoadExecutor);
        runTask(displayBitmapTask, handler);
    }


    private boolean isPaused() {
        AtomicBoolean pause = imageLoadExecutor.getPause();

        if (pause.get()) {
            synchronized (imageLoadExecutor.getPauseLock()) {
                if (pause.get()) {
                    try {
                        imageLoadExecutor.getPauseLock().wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return true;
                    }
                }
            }
        }
        return isTaskNotActual();
    }

    private Bitmap tryLoadBitmap() throws TaskException {
        Bitmap bitmap = null;
        try {
            File imageFile = imageCache.getFileFromDiskCache(uri);
            if (imageFile != null && imageFile.exists() && imageFile.length() > 0) {

                //Image Load from DiskCache;

                checkTaskNotActual();


                bitmap = decodeImage(ImageType.FILE.getUrlWithScheme(imageFile.getAbsolutePath()));
            }
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                //Image Load from Network;

                tryCacheImageOnDisk();
                imageFile = imageCache.getFileFromDiskCache(uri);
                checkTaskNotActual();
                bitmap = decodeImage(ImageType.FILE.getUrlWithScheme(imageFile.getAbsolutePath()));

                if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    fireFailEvent(null);
                }
            }
        } catch (IllegalStateException e) {
            fireFailEvent(e);
        } catch (TaskException e) {
            throw e;
        } catch (IOException e) {
            fireFailEvent(e);
        } catch (OutOfMemoryError e) {
            fireFailEvent(e);
        } catch (Throwable e) {
            fireFailEvent(e);
        }
        return bitmap;
    }


    private void checkTaskNotActual() throws TaskException {
        checkViewGarbageCollected();
        checkViewReused();
    }

    private void checkTaskInterrupted() throws TaskException {
        if (isTaskInterrupted()) {
            throw new TaskException();
        }
    }

    private void checkViewReused() throws TaskException {
        if (isViewReused()) {
            throw new TaskException();
        }
    }

    private void checkViewGarbageCollected() throws TaskException {
        if (isViewGarbageCollected()) {
            throw new TaskException();
        }
    }

    private boolean isTaskInterrupted() {
        if (Thread.interrupted()) {
            return true;
        }
        return false;
    }

    private boolean isTaskNotActual() {
        return isViewGarbageCollected() || isViewReused();
    }

    public String getUri() {
        return uri;
    }

    private boolean isViewGarbageCollected() {
        if (imageViewWrapper.isGarbageCollected()) {
            return true;
        }
        return false;
    }

    private boolean isViewReused() {
        String currentCacheKey = imageLoadExecutor.getLoadingImage(imageViewWrapper);
        // Check whether memory cache key (image URI) for current ImageAware is actual.
        // If ImageAware is reused for another task then current task should be cancelled.
        boolean imageViewWrapperReused = !memoryCacheKey.equals(currentCacheKey);
        if (imageViewWrapperReused) {
            return true;
        }
        return false;
    }

    private void fireFailEvent(final Throwable failCause) {
        if (isTaskInterrupted() || isTaskNotActual()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                imageLoadingListener.onLoadingFailed(uri, imageViewWrapper.getWrappedView(), failCause);
            }
        };
        runTask(r, handler);
    }

    private void fireCancelEvent() {
        if (isTaskInterrupted()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                imageLoadingListener.onLoadingCancelled(uri, imageViewWrapper.getWrappedView());
            }
        };
        runTask(r, handler);
    }


    private void runTask(Runnable r, Handler handler) {
        handler.post(r);
    }

    private boolean tryCacheImageOnDisk() throws TaskException {
        boolean loaded = false;
        InputStream inputStream = null;


        try {
            inputStream = downloadImage();
            loaded = imageCache.save(uri, inputStream);
        } catch (IOException e) {
            loaded = false;
            fireFailEvent(e.getCause());
        } catch (ConnectException e) {
            fireFailEvent(e.getCause());
            loaded = false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    loaded = false;
                    fireFailEvent(e.getCause());
                }
            }
        }
        return loaded;
    }

    private InputStream downloadImage() throws ConnectException, IOException {
        ResponseData responseData = imageDownloader.call(new RequestData(uri, null));
        InputStream inputStream = responseData.getResponseBody().in();
        return inputStream;

    }

    private Bitmap decodeImage(String imageUri) throws IOException {
        return imageDecoder.decode(imageUri, imageSize);
    }
}