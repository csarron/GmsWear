package com.cscao.apps.gmsapidemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.cscao.apps.mlog.MLog;
import com.google.android.gms.wearable.DataMap;

public class PhoneActivity extends Activity {

    private final String MESSAGE1_PATH = "/p11";
    private final String MESSAGE2_PATH = "/p22";

    private TextView mTextView;
    private ImageView mImageView;
    private Button message1Button;
    private Button message2Button;
    private Button syncButton;
    private Button selectButton;

    private static final String MSG_CAPABILITY_NAME = "msg_capability";
    private Handler handler;

    private GmsApi gmsApi;
    private int count = 0;

    private static final int SELECT_PICTURE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.phone_activity);
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

        final int[] images = {R.drawable.img2, R.drawable.img1, R.drawable.img3, R.drawable.img4,
                R.drawable.img5, R.drawable.img6, R.drawable.img7, R.drawable.img8,
                R.drawable.img9, R.drawable.img10, R.drawable.img11, R.drawable.img12,
                R.drawable.img13, R.drawable.img14, R.drawable.img15, R.drawable.img16
        };

        syncButton = (Button) findViewById(R.id.syncButton);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                gmsApi.syncString("key", getString(R.string.sync_button) + count++);
                syncImage(images[count % images.length]);
//                new SendImageTask().execute(Images.urls[count % Images.urls.length]);
//                gmsApi.showToast("sent sync button",Toast.LENGTH_SHORT);

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

        selectButton = (Button) findViewById(R.id.selectButton);
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,
                        "Select Picture"), SELECT_PICTURE);
            }
        });

        gmsApi.setOnDataChangedListener(new GmsApi.OnDataChangedListener() {

            @Override
            public void onDataChanged(DataMap dataMap) {
                if (dataMap.getString("key") != null) {
//                    gmsApi.showToast(dataMap.getString("key"), Toast.LENGTH_SHORT);
                    syncButton.setText(dataMap.getString("key"));
                }
            }

            @Override
            public void onDataDeleted(DataMap dataMap) {

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

    }

//
//    class SendImageTask extends AsyncTask<String, Void, ByteString> {
//
//        @Override
//        protected ByteString doInBackground(String... params) {
//            return sendImage(params[0]);
//        }
//
//        @Override
//        protected void onPostExecute(ByteString v) {
//            super.onPostExecute(v);
//            MLog.d("send img");
//            if (v != null) {
//                MLog.d("bytes: " + v.size() / 1024 + "k");
//                gmsApi.syncAsset("img", Asset.createFromBytes(v.toByteArray()));
//                gmsApi.showToast("send img", Toast.LENGTH_SHORT);
//            } else {
//                MLog.d("byte string null");
//            }
//
//        }
//    }
//private ByteString sendImage(String url) {
//    OkHttpClient client = new OkHttpClient();
//    Request request = new Request.Builder()
//            .url(url)
//            .build();
//    try {
//        Response response = client.newCall(request).execute();
//        return ByteString.of(response.body().bytes());
//
//    } catch (IOException e) {
//        e.printStackTrace();
//    }
//    return null;
//}

    private void syncImage(final int count) {
        Glide.with(getApplicationContext())
                .load(count)
                .asBitmap()
                .into(new SimpleTarget<Bitmap>(mImageView.getWidth(), mImageView.getHeight()) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
                        mImageView.setImageBitmap(resource);
                        byte[] b = ImageUtils.getBytesFromBitmap(resource);
                        MLog.d("bytes: " + b.length);
                        gmsApi.syncAsset("img", b);
                        gmsApi.showToast("send img", Toast.LENGTH_SHORT);
                    }
                });

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                syncSelectedImage(selectedImageUri);
            }
        }
    }

    private void syncSelectedImage(final Uri uri) {
        Glide.with(getApplicationContext())
                .load(uri)
                .asBitmap()
                .into(new SimpleTarget<Bitmap>(mImageView.getWidth(), mImageView.getHeight()) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
                        mImageView.setImageBitmap(resource);
                        byte[] b = ImageUtils.getBytesFromBitmap(resource);
                        MLog.d("bytes: " + b.length);
                        gmsApi.syncAsset("img", b);
                        gmsApi.showToast("send img", Toast.LENGTH_SHORT);
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
        selectButton.setEnabled(true);
    }

    @Override
    protected void onPause() {
        MLog.d("onPause");

        gmsApi.disconnect();
        message1Button.setEnabled(false);
        message2Button.setEnabled(false);
        syncButton.setEnabled(false);
        selectButton.setEnabled(false);

        super.onPause();
    }

}
