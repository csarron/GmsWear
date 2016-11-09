package com.cscao.apps.gmswear;

import static com.cscao.apps.shared.Constants.CAPABILITY;
import static com.cscao.apps.shared.Constants.PATH_MSG_ONE;
import static com.cscao.apps.shared.Constants.PATH_MSG_TWO;
import static com.cscao.apps.shared.Constants.PERMISSIONS_REQUEST_CODE;
import static com.cscao.apps.shared.Constants.SELECT_PICTURE;
import static com.cscao.apps.shared.Constants.SYNC_KEY;
import static com.cscao.apps.shared.Constants.SYNC_PATH;
import static com.cscao.apps.shared.Utils.getElapsedTimeMsg;
import static com.cscao.apps.shared.Utils.sendSelectedImage;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.cscao.apps.shared.Utils;
import com.cscao.libs.gmswear.GmsWear;
import com.cscao.libs.gmswear.connectivity.FileTransfer;
import com.cscao.libs.gmswear.consumer.AbstractDataConsumer;
import com.cscao.libs.gmswear.consumer.DataConsumer;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.orhanobut.logger.Logger;

import java.io.File;

public class PhoneActivity extends Activity {

    // already specified in res/values/wear.xml
//    public static final String MSG_CAPABILITY = "msg_capability";

    private TextView mMsgTextView;
    private ImageView mImageView;
    private Button mMsgOneButton;
    private Button mMsgTwoButton;
    private Button mSyncButton;
    private Button mSelectButton;
    private GmsWear mGmsWear;
    private DataConsumer mDataConsumer;

    private int mImageNo = 1;
//    private int mIntSync = 1; // 1, 4, 7...
//    private int mIntMsgOne = 2; // 2, 5, 8...
//    private int mIntMsgTwo = 3; // 3, 6, 9...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity);

        mGmsWear = GmsWear.getInstance();

        mMsgTextView = (TextView) findViewById(R.id.tv_msg);
        mImageView = (ImageView) findViewById(R.id.imageView);

        mMsgOneButton = (Button) findViewById(R.id.btn_send_msg1);
        mMsgTwoButton = (Button) findViewById(R.id.btn_send_msg2);
        mSyncButton = (Button) findViewById(R.id.btn_sync);
        mSelectButton = (Button) findViewById(R.id.btn_select_image);

        mDataConsumer = new AbstractDataConsumer() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
                String msg = new String(messageEvent.getData());
                if (messageEvent.getPath().equals(PATH_MSG_ONE)) {
                    setTextAsync(mMsgTextView, msg + PATH_MSG_ONE);
                } else if (messageEvent.getPath().equals(PATH_MSG_TWO)) {
                    setTextAsync(mMsgTextView, msg + PATH_MSG_TWO);
                }
            }

            @Override
            public void onDataChanged(DataEvent event) {
                DataItem item = event.getDataItem();
                final DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    String syncString = dataMap.getString(SYNC_KEY);
                    setTextAsync(mMsgTextView, syncString);
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

        };

        checkPermissions();
    }

    private void setTextAsync(final TextView view, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.setText(text);
            }
        });
    }

    public void selectImage(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,
                "Select Picture"), SELECT_PICTURE);
    }

    public void syncString(View view) {
        mGmsWear.syncString(SYNC_PATH, SYNC_KEY,
                getString(R.string.synced_msg) + getElapsedTimeMsg(), false);
    }

    public void sendMsgTwo(View view) {
        mGmsWear.sendMessage(PATH_MSG_TWO, getElapsedTimeMsg().getBytes(),
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(
                            @NonNull MessageApi.SendMessageResult sendMessageResult) {
                        if (sendMessageResult.getStatus().isSuccess()) {
                            Logger.d("msg 2 sent success");
                        }
                    }
                });
    }

    public void sendMsgOne(View view) {
        Logger.d("send m1");
        mGmsWear.sendMessage(PATH_MSG_ONE, getElapsedTimeMsg().getBytes());
    }

    // send image through file api
    public void sendImage(View view) {
        String imgPath = "img" + (mImageNo = mImageNo % 5 + 1) + ".jpg";
        Logger.d("img path: " + imgPath);
        File fileName = Utils.copyFileToPrivateDataIfNeededAndReturn(this, imgPath);

        // show image locally
        Glide.with(this).load(fileName).into(mImageView);

        // send to wearable
        FileTransfer fileTransferHighLevel = new FileTransfer.Builder()
                .setFile(fileName).build();
        fileTransferHighLevel.startTransfer();
    }

    private void checkPermissions() {
        boolean writeExternalStoragePermissionGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;

        if (writeExternalStoragePermissionGranted) {
            enableButtons();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_CODE);
        }

    }

    private void enableButtons() {
        mMsgOneButton.setEnabled(true);
        mMsgTwoButton.setEnabled(true);
        mSyncButton.setEnabled(true);
        mSelectButton.setEnabled(true);
    }

    private void disableButtons() {
        mMsgOneButton.setEnabled(false);
        mMsgTwoButton.setEnabled(false);
        mSyncButton.setEnabled(false);
        mSelectButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.d("onResume");
        mGmsWear.addCapabilities(CAPABILITY);
        mGmsWear.addWearConsumer(mDataConsumer);
    }

    @Override
    protected void onPause() {
        Logger.d("onPause");
        mGmsWear.removeCapabilities(CAPABILITY);
        mGmsWear.removeWearConsumer(mDataConsumer);
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                // send image through InputStream  api
                sendSelectedImage(selectedImageUri, mImageView);
            }
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
}
