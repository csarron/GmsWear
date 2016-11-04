package com.cscao.apps.shared;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.cscao.libs.gmswear.connectivity.FileTransfer;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.orhanobut.logger.Logger;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by qqcao on 11/4/16.
 *
 * File utilities
 */

public class Utils {
    public static File copyFileToPrivateDataIfNeededAndReturn(Context context, String fileName) {
        File downloadFolder = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadFolder, fileName);
        if (!downloadFolder.exists()) {
            boolean hasDirCreated = downloadFolder.mkdirs();
            if (!hasDirCreated) {
                Logger.e("cannot create download folder");
                return null;
            }
        }

        if (file.exists()) {
            Logger.d("File already exists in the target location");
            return file;
        } else {
            try {
                if (!file.createNewFile()) {
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            InputStream inputStream = context.getAssets().open(fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            IOUtils.copy(inputStream, fileOutputStream);
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void sendSelectedImage(final Uri uri, ImageView imageView) {
        final Context context = imageView.getContext();

        // show image locally
        Glide.with(context).load(uri).into(imageView);

        // send to wearable
        FileTransfer fileTransferHighLevel = new FileTransfer.Builder()
                .setOnChannelOutputStreamListener(new FileTransfer.OnChannelOutputStreamListener() {
                    @Override
                    public void onOutputStreamForChannelReady(int statusCode, Channel channel,
                            final OutputStream outputStream) {
                        if (statusCode != WearableStatusCodes.SUCCESS) {
                            Logger.e("Failed to open a channel, status code: " + statusCode);
                            return;
                        }
                        sendFile(context, uri, outputStream);
                    }
                }).build();
        fileTransferHighLevel.requestOutputStream();

    }

    private static void sendFile(final Context context, final Uri uri, final OutputStream os) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream is = context.getContentResolver().openInputStream(uri);
                    assert is != null;
                    IOUtils.copy(is, os);
                    is.close();
                    os.close();
//                    }
                } catch (IOException e) {
                    Logger.e("startTransfer(): IO Error while reading/writing", e);
                }
            }
        }).start();
    }

    public static void setImageFromInputStream(final InputStream inputStream,
            final ImageView imageView) {
        final Context context = imageView.getContext();
        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... params) {
                try {
                    File imageFile = new File(context.getCacheDir(),
                            "temp" + SystemClock.elapsedRealtime());
                    FileOutputStream outputStream = new FileOutputStream(imageFile);
                    IOUtils.copy(inputStream, outputStream);
                    return imageFile;
                } catch (IOException e) {
                    e.printStackTrace();
                    closeStreams();
                }
                return null;
            }

            @Override
            protected void onPostExecute(File imageFile) {
                if (imageFile != null) {
                    Logger.d("setting image");
                    Glide.with(context).load(imageFile).into(imageView);
                } else {
                    Logger.e("image file null");
                }
            }

            void closeStreams() {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    // no-op
                }
            }
        }.execute();
    }

    @NonNull
    public static String getElapsedTimeMsg() {
        return "" + SystemClock.elapsedRealtime();
    }
}
