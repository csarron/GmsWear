package com.cscao.apps.gmswear;

import static org.apache.commons.io.FileUtils.openInputStream;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.cscao.libs.GmsWear.GmsWear;
import com.cscao.libs.GmsWear.connectivity.FileTransfer;
import com.cscao.libs.GmsWear.consumer.AbstractDataConsumer;
import com.cscao.libs.GmsWear.consumer.DataConsumer;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.orhanobut.logger.Logger;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WearActivity extends Activity {

    private static final String MSG_CAPABILITY_NAME = "msg_capability";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private final String MESSAGE1_PATH = "/p11";
    private final String MESSAGE2_PATH = "/p22";
    private TextView mTextView;
    private ImageView mImageView;
    private Button message1Button;
    private Button message2Button;
    private Button syncButton;
    private GmsWear mGmsWear;
    private DataConsumer mDataConsumer;
    private int mHeight; // screen height
    private int mWidth; // screen width

    private int count = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wear_activity);
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        mHeight = displaymetrics.heightPixels;
        mWidth = displaymetrics.widthPixels;

        GmsWear.initialize(this, MSG_CAPABILITY_NAME);
        mGmsWear = GmsWear.getInstance();

        mTextView = (TextView) findViewById(R.id.textView);
        mTextView.setKeepScreenOn(true);

        mImageView = (ImageView) findViewById(R.id.imageView);

        message1Button = (Button) findViewById(R.id.message1Button);
        message2Button = (Button) findViewById(R.id.message2Button);

        // Set message1Button onClickListener to send message 1
        message1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.d("send m1");
                mGmsWear.sendMessage(MESSAGE1_PATH, getString(R.string.msg_info1).getBytes());
                showToast(getString(R.string.message_sent) + MESSAGE1_PATH);
            }
        });

        // Set message2Button onClickListener to send message 2
        message2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGmsWear.sendMessage(MESSAGE2_PATH, getString(R.string.msg_info2).getBytes(),
                        new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(
                                    @NonNull MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess()) {
                                    Logger.d("msg 2 sent success");
                                }
                            }
                        });
                showToast(getString(R.string.message_sent) + MESSAGE2_PATH);
            }
        });

        syncButton = (Button) findViewById(R.id.syncButton);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGmsWear.syncString("key", getString(R.string.sync_button) + count, false);
                String imgPath = "img" + (count = count % 5 + 1) + ".jpg";
                Logger.d("img path: " + imgPath);
                File file = copyFileToPrivateDataIfNeededAndReturn(imgPath);
                syncImage(file);
//                showToast("sent sync button");
            }
        });

        syncButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                count = 1;
                syncButton.setText(getString(R.string.sync_button));
                return false;
            }
        });

        mDataConsumer = new AbstractDataConsumer() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
                if (messageEvent.getPath().equals(MESSAGE1_PATH)) {
                    updateUI(messageEvent, getString(R.string.received_message1));
                } else if (messageEvent.getPath().equals(MESSAGE2_PATH)) {
                    updateUI(messageEvent, getString(R.string.received_message2));
                }
            }

            @Override
            public void onDataChanged(DataEvent event) {
                DataItem item = event.getDataItem();
                final DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            syncButton.setText(dataMap.getString("key"));
                        }
                    });
                } else if (event.getType() == DataEvent.TYPE_DELETED) {
                    Logger.d("DataItem Deleted" + event.getDataItem().toString());
                }

            }

            @Override
            public void onFileReceivedResult(int statusCode, String requestId, File savedFile,
                    String originalName) {
                Logger.d(
                        "File Received: status=%d, requestId=%s, savedLocation=%s, originalName=%s",
                        statusCode, requestId, savedFile.getAbsolutePath(), originalName);

                Glide.with(getApplicationContext()).load(savedFile).into(mImageView);
            }

            @Override
            public void onInputStreamForChannelOpened(int statusCode, String requestId,
                    Channel channel, final InputStream inputStream) {
                if (statusCode != WearableStatusCodes.SUCCESS) {
                    Logger.e("onInputStreamForChannelOpened(): " + "Failed to get input stream");
                    return;
                }
                Logger.d("Channel opened for path: " + channel.getPath());
                new AsyncTask<Void, Void, File>() {
                    @Override
                    protected File doInBackground(Void... params) {
                        try {
                            File imageFile = new File(getCacheDir(), "temp");
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
                        Logger.d("setting image");
                        if (imageFile != null) {
                            Glide.with(getApplicationContext()).load(imageFile)
                                    .into(mImageView);
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
        };

        checkPermissions();
    }

    private void syncImage(final File uri) {
        // show image locally
        Glide.with(this).load(uri).into(mImageView);

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
                        sendFile(uri, outputStream);
                    }
                }).build();
        fileTransferHighLevel.requestOutputStream();

    }

    private void sendFile(final File uri, final OutputStream os) {
        new Thread(new Runnable() {
            @Override
            public void run() {
//                final byte[] buffer = new byte[1024];
                try {
                    InputStream is = openInputStream(uri);
                    IOUtils.copy(is, os);
                    is.close();
                    os.close();
//                    long fileSize = is.available();
//                    Logger.d("img size: " + fileSize);
//                    BufferedInputStream bis = new BufferedInputStream(is);
//                    BufferedOutputStream bos = new BufferedOutputStream(os);
//                    int read;
//                    while ((read = bis.read(buffer)) != -1) {
//                        bos.write(buffer, 0, read);
//                        Logger.d("writing buffer size: " + read);
//                    }
                } catch (IOException e) {
                    Logger.e("startTransfer(): IO Error while reading/writing", e);
                }
            }
        }).start();
    }

    private void updateUI(final MessageEvent messageEvent, final String uiText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String display = uiText + "\n" + new String(messageEvent.getData());
                Logger.d("wear display is:" + display);
                mTextView.setText(display);
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private File copyFileToPrivateDataIfNeededAndReturn(String fileName) {
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
            InputStream inputStream = getAssets().open(fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            IOUtils.copy(inputStream, fileOutputStream);
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void checkPermissions() {
        boolean recordAudioPermissionGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;

        if (recordAudioPermissionGranted) {
            enableButtons();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_CODE);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableButtons();
            } else {
                // Permission has been denied before. At this point we should show a dialog to
                // user and explain why this permission is needed and direct him to go to the
                // Permissions settings for the app in the System settings. For this sample, we
                // simply exit to get to the important part.
                disableButtons();
                Toast.makeText(this, R.string.exiting_for_permission, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void enableButtons() {
        message1Button.setEnabled(true);
        message2Button.setEnabled(true);
        syncButton.setEnabled(true);
    }

    private void disableButtons() {
        message1Button.setEnabled(false);
        message2Button.setEnabled(false);
        syncButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGmsWear.addWearConsumer(mDataConsumer);
    }

    @Override
    protected void onPause() {
        mGmsWear.removeWearConsumer(mDataConsumer);
        super.onPause();
    }
}
