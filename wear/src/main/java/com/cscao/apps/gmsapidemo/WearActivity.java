package com.cscao.apps.gmsapidemo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.cscao.apps.mlog.MLog;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;

public class WearActivity extends Activity {

    private final String MESSAGE1_PATH = "/p11";
    private final String MESSAGE2_PATH = "/p22";

    private TextView mTextView;
    private ImageView mImageView;
    private Button message1Button;
    private Button message2Button;
    private Button syncButton;

    private static final String MSG_CAPABILITY_NAME = "msg_capability";
    private Handler handler;

    private GmsApi gmsApi;
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wear_activity);
        MLog.init(this);
        handler = new Handler();


        gmsApi = new GmsApi(this, handler, MSG_CAPABILITY_NAME);

        mTextView = (TextView) findViewById(R.id.textView);
        mTextView.setKeepScreenOn(true);

        mImageView = (ImageView) findViewById(R.id.imageView);

        message1Button = (Button) findViewById(R.id.message1Button);
        message2Button = (Button) findViewById(R.id.message2Button);

        // Set message1Button onClickListener to send message 1
        message1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MLog.d("send m1", null);
                gmsApi.sendMsg(MESSAGE1_PATH, getString(R.string.msg_info1).getBytes());
//                gmsApi.showToast(getString(R.string.message_sent) + MESSAGE1_PATH, Toast.LENGTH_SHORT);
            }
        });

        // Set message2Button onClickListener to send message 2
        message2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gmsApi.sendMsg(MESSAGE2_PATH, getString(R.string.msg_info2).getBytes());
//                gmsApi.showToast(getString(R.string.message_sent) + MESSAGE2_PATH, Toast.LENGTH_SHORT);
            }
        });

        gmsApi.setOnMessageReceivedListener(new GmsApi.OnMessageReceivedListener() {
            @Override
            public void onMessageReceived(final GmsApi.MessageData messageEvent) {

                if (messageEvent.getPath().equals(MESSAGE1_PATH)) {

                    updateUI(messageEvent, getString(R.string.received_message1));

                } else if (messageEvent.getPath().equals(MESSAGE2_PATH)) {

                    updateUI(messageEvent, getString(R.string.received_message2));
                }
            }
        });

        syncButton = (Button) findViewById(R.id.syncButton);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                gmsApi.syncString("key", getString(R.string.sync_button) + count++);

//                gmsApi.showToast("sent sync button", Toast.LENGTH_SHORT);
            }
        });

        syncButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                count = 0;
                syncButton.setText(getString(R.string.sync_button));
                return false;
            }
        });

        gmsApi.setOnDataChangedListener(new GmsApi.OnDataChangedListener() {
            @Override
            public void onDataChanged(DataMap dataMap) {
                if (dataMap.getString("key") != null) {
//                    gmsApi.showToast(dataMap.getString("key"), Toast.LENGTH_SHORT);
                    syncButton.setText(dataMap.getString("key"));
                }

                if (dataMap.getAsset("img") != null) {
                    Asset img = dataMap.getAsset("img");
                    gmsApi.setOnAssetReceivedListener(new GmsApi.OnAssetReceivedListener() {
                        @Override
                        public void onAssetReceived(byte[] bytes) {
                            if (bytes != null) {
                                MLog.d("bytes: "+bytes.length);
                        int width = mImageView.getWidth();
                        int height = mImageView.getHeight();
                        Bitmap bitmap = ImageUtils.decodeBytes(bytes, width, height);
//                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                if (bitmap != null) {
                                    MLog.d("bitmap size is: " + bitmap.getByteCount() / 1024 + "K");
                                    mImageView.setImageBitmap(bitmap);
                                } else {
                                    MLog.d("bitmap null ");
                                }
                            }
                        }
                    },img);

                }
//                        Asset img = dataMap.getAsset("img");
//                if (img != null) {
//                    MLog.d("asset !");
//                    Wearable.DataApi.getFdForAsset(
//                            gmsApi.getApiClient(), img).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
//                        @Override
//                        public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
//
//                            MLog.d("width:" + mImageView.getWidth() + "height:" + mImageView.getHeight());
//                            Bitmap bitmap = null;
//                            try {
//                                bitmap = ImageUtils.decodeStream(getFdForAssetResult.getInputStream(), mImageView.getWidth(), mImageView.getHeight());
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//
//                            if (bitmap != null) {
//                                MLog.d("bitmap size is: " + bitmap.getByteCount() / 1024);
//                                mImageView.setImageBitmap(bitmap);
//                            }
//                        }
//                    });
//                }
            }

            @Override
            public void onDataDeleted(DataMap dataMap) {

            }
        });


    }

    private void updateUI(final GmsApi.MessageData messageEvent, final String uiText) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String display = uiText + "\n" + new String(messageEvent.getData());
                MLog.d("display is:" + display);
                mTextView.setText(display);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        MLog.d("onResume");

        gmsApi.connect();
        message1Button.setEnabled(true);
        message2Button.setEnabled(true);
        syncButton.setEnabled(true);
    }

    @Override
    protected void onPause() {
        MLog.d("onPause");

        gmsApi.disconnect();
        message1Button.setEnabled(false);
        message2Button.setEnabled(false);
        syncButton.setEnabled(false);

        super.onPause();
    }
}
