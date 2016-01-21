package com.cscao.apps.gmsapidemo;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.cscao.apps.gmsapi.R;
import com.cscao.apps.mlog.MLog;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/**
 * Created by qqcao on 1/16/16.
 *
 */
@SuppressWarnings("unused")
public class GmsApi implements DataApi.DataListener, MessageApi.MessageListener, CapabilityApi.CapabilityListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mApiClient;
    private Set<Node> mNodes;
    private String mCapability;

    private OnMessageReceivedListener onMessageReceivedListener;
    private OnDataChangedListener onDataChangedListener;

    private static final String DATA_PATH_PREFIX = "/data/";
    private static final String ASSET_PATH_PREFIX = "/asset/";

    private Context mContext;
    private Handler mHandler;

    public GmsApi(Context context, Handler handler, String capability) {

        if (context == null) {
            throw new NullPointerException("context must be nonnull");
        }

        mContext = context;
        mHandler = handler;
        mCapability = capability;

        MLog.init(context);

        // Create GoogleApiClient
        mApiClient = new GoogleApiClient
                .Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    public void connect() {
        MLog.d("connect");
        int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);

        if (connectionResult != ConnectionResult.SUCCESS) {
            String errorString = null;
            // Google Play Services is not working properly
            switch (connectionResult) {
                case ConnectionResult.API_UNAVAILABLE:
                    errorString = mContext.getString(R.string.common_google_play_services_api_unavailable_text);

                    break;
                case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                    errorString = mContext.getString(R.string.common_google_play_services_wear_update_text);
                    break;
                default:
                    MLog.d("other connection result code");
            }

            // show error toast
            MLog.d(errorString);
            showToast(errorString, Toast.LENGTH_SHORT);

        } else {
            if (!mApiClient.isConnecting() && !mApiClient.isConnected()) {
                mApiClient.connect();
            } else {
                MLog.d("already connected or trying to connect");
            }

        }
    }

    public void disconnect() {
        MLog.d("disconnect");

        Wearable.DataApi.removeListener(mApiClient, this);
        Wearable.MessageApi.removeListener(mApiClient, this);
        Wearable.CapabilityApi.removeCapabilityListener(mApiClient, this, mCapability);

        if (mApiClient.isConnected() || mApiClient.isConnecting()) {
            mApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        MLog.d("onConnected");

        Wearable.CapabilityApi.addCapabilityListener(mApiClient, this, mCapability);
        Wearable.MessageApi.addListener(mApiClient, this);
        Wearable.DataApi.addListener(mApiClient, this);

        Wearable.CapabilityApi.getCapability(
                mApiClient, mCapability,
                CapabilityApi.FILTER_REACHABLE).setResultCallback(
                new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                    @Override
                    public void onResult(@NonNull CapabilityApi.GetCapabilityResult result) {
                        if (result.getStatus().isSuccess()) {
                            mNodes = result.getCapability().getNodes();
                            MLog.d(mNodes.toString());
                        } else {
                            MLog.d("Failed to get capabilities, "
                                    + "status: "
                                    + result.getStatus().getStatusMessage());
                        }
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {
        MLog.d("onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        MLog.d("onConnectionFailed");
        if (connectionResult.getErrorCode() == ConnectionResult.API_UNAVAILABLE)
            showToast(mContext.getString(R.string.common_google_play_services_api_unavailable_text), Toast.LENGTH_SHORT);
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        mNodes = capabilityInfo.getNodes();
    }

    public void showToast(final String notifyText, final int length) {
        if (mHandler == null) {
            MLog.w("mHandler null!");
        } else if (notifyText != null) {
            final Context context = mContext;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, notifyText, length).show();
                }
            });
        }

    }

    private void sendMsgToNode(String node, final String path, byte[] msg) {
        Wearable.MessageApi.sendMessage(mApiClient, node, path, msg).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                if (sendMessageResult.getStatus().isSuccess()) {
                    MLog.d("Message sent through:" + path);
                } else {
                    showToast(sendMessageResult.getStatus().getStatusMessage(), Toast.LENGTH_SHORT);
                    MLog.d("Message sent error from:" + path);
                }
            }
        });
    }

    private String pickBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        // Find a nearby node or pick one arbitrarily
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }

    public void sendMsg(String path, byte[] msg) {
        sendMsg(path, msg, false);
    }

    public void sendMsg(String path, byte[] msg, boolean isSentToAllNodes) {
        if (isSentToAllNodes) {
            for (Node node : mNodes) {
                sendMsgToNode(node.getId(), path, msg);
            }
        } else {
            sendMsgToNode(pickBestNodeId(mNodes), path, msg);
        }
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        onMessageReceivedListener.onMessageReceived(new MessageData() {
            @Override
            public int getRequestId() {
                return messageEvent.getRequestId();
            }

            @Override
            public String getPath() {
                return messageEvent.getPath();
            }

            @Override
            public byte[] getData() {
                return messageEvent.getData();
            }

            @Override
            public String getSourceNodeId() {
                return messageEvent.getSourceNodeId();
            }
        });
    }

    /**
     * MessageData interface is directly inherited from MessageEvent, so the usage is the same
     */
    public interface MessageData extends MessageEvent {
    }

    public interface OnMessageReceivedListener {
        void onMessageReceived(MessageData messageEvent);
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        onMessageReceivedListener = listener;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (final DataEvent event : dataEvents) {

            DataItem item = event.getDataItem();
            DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

            if (event.getType() == DataEvent.TYPE_CHANGED) {
                MLog.d("DataItem Changed" + event.getDataItem().toString());
                onDataChangedListener.onDataChanged(dataMap);

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                MLog.d("DataItem Deleted" + event.getDataItem().toString());
                onDataChangedListener.onDataDeleted(dataMap);
            }
        }
    }

    public interface OnDataChangedListener {
        void onDataChanged(DataMap dataMap);

        void onDataDeleted(DataMap dataMap);
    }

    public void setOnDataChangedListener(OnDataChangedListener listener) {
        onDataChangedListener = listener;
    }

//    public byte[] getAsset(Asset asset) {
//        final byte[][] bytes = new byte[1][1];
//        Wearable.DataApi.getFdForAsset(
//                mApiClient, asset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
//            @Override
//            public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
//                try {
//                  bytes[0] =  IOUtils.toByteArray(getFdForAssetResult.getInputStream()) ;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        return bytes[0];
//    }

    public interface OnAssetReceivedListener {
        void onAssetReceived(byte[] bytes);
    }

    public  void setOnAssetReceivedListener(final OnAssetReceivedListener listener, Asset asset) {
        Wearable.DataApi.getFdForAsset(
                mApiClient, asset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
            @Override
            public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
                try {
                    final byte[] bytes= IOUtils.toByteArray(getFdForAssetResult.getInputStream()) ;
                    listener.onAssetReceived(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void syncAsset(String key, byte[] bytes) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(ASSET_PATH_PREFIX + key);
        Asset asset = Asset.createFromBytes(bytes);
        putDataMapRequest.getDataMap().putAsset(key, asset);
        syncData(putDataMapRequest);
    }

    public void syncBoolean(String key, boolean item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putBoolean(key, item);
        syncData(putDataMapRequest);
    }

    public void syncByte(String key, byte item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putByte(key, item);
        syncData(putDataMapRequest);
    }

    public void syncInt(String key, int item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putInt(key, item);
        syncData(putDataMapRequest);
    }

    public void syncLong(String key, long item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putLong(key, item);
        syncData(putDataMapRequest);
    }

    public void syncFloat(String key, float item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putFloat(key, item);
        syncData(putDataMapRequest);
    }

    public void syncDouble(String key, long item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putDouble(key, item);
        syncData(putDataMapRequest);
    }

    public void syncByteArray(String key, byte[] item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putByteArray(key, item);
        syncData(putDataMapRequest);
    }

    public void syncString(String key, String item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putString(key, item);
        syncData(putDataMapRequest);
    }

    public void syncLongArray(String key, long[] item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putLongArray(key, item);
        syncData(putDataMapRequest);
    }

    public void syncFloatArray(String key, float[] item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putFloatArray(key, item);
        syncData(putDataMapRequest);
    }

    public void syncStringArray(String key, String[] item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putStringArray(key, item);
        syncData(putDataMapRequest);
    }

    public void syncIntegerArrayList(String key, ArrayList<Integer> item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putIntegerArrayList(key, item);
        syncData(putDataMapRequest);
    }

    public void syncStringArrayList(String key, ArrayList<String> item) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH_PREFIX + key);
        putDataMapRequest.getDataMap().putStringArrayList(key, item);
        syncData(putDataMapRequest);
    }

    //General method to sync data in the Data Layer
    private void syncData(PutDataMapRequest putDataMapRequest) {
        if (MLog.isDebuggable()) {
            DataMap dataMap=putDataMapRequest.getDataMap();
            dataMap.putLong("timestamp", System.currentTimeMillis());
            MLog.d("timestamp");
        }

        PutDataRequest request = putDataMapRequest.asPutDataRequest();

        MLog.d("Generating DataItem: " + request);
        if (!mApiClient.isConnected()) {
            MLog.w("ApiClient not connected");
            return;
        }

        Wearable.DataApi.putDataItem(mApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            MLog.d("putDataItem success");

                        } else {
                            String errStr = dataItemResult.getStatus().getStatusMessage();
                            MLog.e("ERROR: failed to putDataItem, status code: "
                                    + dataItemResult.getStatus().getStatusCode()
                                    + ",status message:"
                                    + errStr);

                            showToast(errStr, Toast.LENGTH_SHORT);
                        }
                    }
                });
    }

//    public  Asset createAssetFromBitmap(Bitmap bitmap) {
//        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
//        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
//        return Asset.createFromBytes(byteStream.toByteArray());
//    }
//
//    public  Bitmap loadBitmapFromAsset(Asset asset) {
//        if (asset == null) {
//            throw new IllegalArgumentException("Asset must be non-null");
//        }
//
//        final Bitmap[] bitmap = new Bitmap[1];
//
//        // convert asset into a file descriptor and block until it's ready
//       Wearable.DataApi.getFdForAsset(
//                mApiClient, asset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
//           @Override
//           public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
//              bitmap[0] =  BitmapFactory.decodeStream(getFdForAssetResult.getInputStream());
//
//           }
//       });
//
//        if (bitmap[0] == null) {
//            MLog.w( "Requested an unknown Asset.");
//            return null;
//        }
//
//        // decode the stream into a bitmap
//        return bitmap[0];
//    }

}
