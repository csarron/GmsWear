/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * imitations under the License.
 */

package com.cscao.libs.gmswear;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.cscao.libs.gmswear.connectivity.FileTransfer;
import com.cscao.libs.gmswear.consumer.AbstractDataConsumer;
import com.cscao.libs.gmswear.consumer.DataConsumer;
import com.cscao.libs.gmswear.filter.NearbyFilter;
import com.cscao.libs.gmswear.filter.NodeSelectionFilter;
import com.cscao.libs.gmswear.util.AppVisibilityDetector;
import com.cscao.libs.gmswear.util.Constants;
import com.cscao.libs.gmswear.util.WearUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

/**
 * A singleton to manage the wear interaction between a device and its connected devices.
 * This class maintains certain states of the system and hides direct interaction with the
 * {@link GoogleApiClient}. Clients should initialize this singleton as early as possible (e.g. in
 * the onCreate() of the Application instance). Then, accessing this singleton instance is possible
 * throughout the client application by calling {@link #getInstance()}.
 * <p>
 * <p>In most cases, components within the client application need to have access to certain events
 * and receive messages, data items, etc. Each component can register an implementation of the
 * {@link DataConsumer} interface with the {@link GmsWear}. Then, the relevant callbacks
 * within that interface will be called when an event is received or a life-cycle change
 * happens. To make this easier, {@link AbstractDataConsumer}
 * provides a no-op implementation of the {@link DataConsumer} which clients can extend
 * and only override the callbacks that they are interested in.
 */
public class GmsWear {

    private static final String TAG = "GmsWear";
    private static GmsWear sInstance;
    private final Context mContext;
    private final String[] mCapabilitiesToBeAdded;
    private final Set<DataConsumer> mDataConsumers = new CopyOnWriteArraySet<>();
    private final Set<String> mWatchedCapabilities = new CopyOnWriteArraySet<>();
    private final Set<Node> mConnectedNodes = new CopyOnWriteArraySet<>();
    private final Map<String, Set<Node>> mCapabilityToNodesMapping = Collections
            .synchronizedMap(new HashMap<String, Set<Node>>());
    private final String mGmsWearVersion;
    private GoogleApiClient mGoogleApiClient;
    private boolean mAppForeground;

    /**
     * The private constructor which is called internally by the
     * {@link #initialize(Context, String...)} method.
     */
    private GmsWear(Context context, String... capabilitiesToBeAdded) {
        mContext = context;
        mCapabilitiesToBeAdded = capabilitiesToBeAdded != null ? Arrays.copyOf(
                capabilitiesToBeAdded, capabilitiesToBeAdded.length) : null;
        mGmsWearVersion = context.getString(R.string.gms_wear_version);
        WearUtil.logD(TAG, "*** GmsWear Library version: " + mGmsWearVersion + " ***");
    }

    /**
     * A static method to get a hold of this singleton after it is initialized. If a client calls
     * this method prior to the initialization of this singleton, an {@link IllegalStateException}
     * exception will be thrown.
     */
    public static GmsWear getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException(
                    "No instance of GmsWear was found, did you forget to call initialize()?");
        }
        return sInstance;
    }

    /**
     * Initializes this singleton. Clients should call this prior to calling any other public
     * method on this singleton. It is required to call this method from within the
     * {@link Application#onCreate()} method of the Application instance of the client application.
     * <p>
     * <p>A {@link Context} is required but this singleton only holds a reference to the
     * (Application) context. Clients can also decide to declare zero or more capabilities to be
     * declared at runtime. Note that at any later time, clients can add or remove any capabilities
     * that are declared at runtime. Here, we set up the Google Api Client
     * instance and call the connect on it.
     *
     * @param context      A context. An application context will be extracted from this to avoid
     *                     having a reference to any other type of context.
     * @param capabilities (optional) zero or more capabilities, to be declared dynamically by the
     *                     caller client.
     */
    public static synchronized GmsWear initialize(Context context, String... capabilities) {
        if (sInstance == null) {
            sInstance = new GmsWear(context.getApplicationContext(), capabilities);
            sInstance.initialize();
        }
        return sInstance;
    }

    private void initialize() {
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GmsConnectionCallbacksListener())
                .addOnConnectionFailedListener(new GmsConnectionFailedListener())
                .build();
        mGoogleApiClient.connect();
        AppVisibilityDetector visibilityDetector = AppVisibilityDetector
                .forApp((Application) mContext);
        visibilityDetector.addListener(new WearAppVisibilityDetectorListener());
    }

    /**
     * Sends an asynchronous message to the node with the given {@code nodeId} through {@code
     * path}.
     * If the {@code callback} is null, then a default callback will be used that provides a
     * feedback to the caller using the {@link DataConsumer#onSendMessageResult}, in which case,
     * the status of the result will be made available. Callers may decide to provide their own
     * {@code callback} to be used instead. This variant receives the message in a {@link DataMap}.
     */
    public void sendMessage(String nodeId, String path, @Nullable DataMap dataMap,
            @Nullable ResultCallback<? super MessageApi.SendMessageResult> callback) {
        sendMessage(nodeId, path, dataMap != null ? dataMap.toByteArray() : null, callback);
    }

    /**
     * Sends an asynchronous message to the nearest node through {@code path}. If the
     * {@code callback} is null, then a default callback will be used that provides a feedback to
     * the caller using the {@link DataConsumer#onSendMessageResult}, in which case, the
     * status of the result will be made available. Callers may decide to provide their own
     * {@code callback} to be used instead. This variant receives the message in a {@link DataMap}.
     */
    public void sendMessage(String path, @Nullable DataMap dataMap,
            @Nullable ResultCallback<? super MessageApi.SendMessageResult> callback) {
        sendMessage(path, dataMap != null ? dataMap.toByteArray() : null, callback);
    }

    /**
     * Sends an asynchronous message to the node with the given {@code nodeId} through {@code
     * path}. If the {@code callback} is null, then a default callback will be used that provides a
     * feedback to the caller using the {@link DataConsumer#onSendMessageResult}, in which case,
     * the status of the result will be made available. Callers may decide to provide their own
     * {@code callback} to be used instead. This variant receives the message in an array of bytes.
     */
    public void sendMessage(String nodeId, String path, @Nullable byte[] bytes,
            @Nullable final ResultCallback<? super MessageApi.SendMessageResult> callback) {
        assertApiConnectivity();
        Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, path, bytes).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message, statusCode: " + sendMessageResult
                                    .getStatus().getStatusCode());
                        }
                        if (callback == null) {
                            for (DataConsumer consumer : mDataConsumers) {
                                consumer.onSendMessageResult(sendMessageResult.getStatus()
                                        .getStatusCode());
                            }
                        } else {
                            callback.onResult(sendMessageResult);
                        }
                    }
                });
    }

    /**
     * Sends an asynchronous message to the node with the given {@code nodeId} through {@code
     * path}.
     * A default callback will be used that provides a feedback to the caller using the
     * {@link DataConsumer#onSendMessageResult} where the status of the result will be
     * made available. To provide your own callback, use
     * {@link #sendMessage(String, String, byte[], ResultCallback)}.
     *
     * @see GmsWear#sendMessage(String, String, byte[], ResultCallback)
     */
    public void sendMessage(String nodeId, String path, @Nullable byte[] bytes) {
        sendMessage(nodeId, path, bytes, null);
    }

    /**
     * Sends an asynchronous message to the nearest node through {@code path}. If the
     * {@code callback} is null, then a default callback will be used that provides a feedback to
     * the caller using the {@link DataConsumer#onSendMessageResult}, in which case, the
     * status of the result will be made available. Callers may decide to provide their own
     * {@code callback} to be used instead. This variant receives the message in an array of bytes.
     */
    public void sendMessage(String path, @Nullable byte[] bytes,
            @Nullable final ResultCallback<? super MessageApi.SendMessageResult> callback) {
        assertApiConnectivity();
        Set<Node> nearbyNodes = new NearbyFilter().filterNodes(getConnectedNodes());
        for (Node node : nearbyNodes) {
            sendMessage(node.getId(), path, bytes, callback);
        }
    }

    /**
     * Sends an asynchronous message to the nearby nodes through {@code path}.
     * A default callback will be used that provides a feedback to the caller using the
     * {@link DataConsumer#onSendMessageResult} where the status of the result will be
     * made available. To provide your own callback, use
     * {@link #sendMessage(String, String, byte[], ResultCallback)}.
     *
     * @see GmsWear#sendMessage(String, String, byte[], ResultCallback)
     */
    public void sendMessage(String path, @Nullable byte[] bytes) {
        Set<Node> nearbyNodes = new NearbyFilter().filterNodes(getConnectedNodes());
        for (Node node : nearbyNodes) {
            sendMessage(node.getId(), path, bytes, null);
        }
    }

    /**
     * Adds a data item asynchronously. Caller can specify a {@link ResultCallback} or pass a
     * {@code null}; if {@code null} is passed, a default {@link ResultCallback} will be used which
     * will call {@link DataConsumer#onSendDataResult(int)} and passes the status code of
     * the result.
     *
     * @see #putDataItem(PutDataRequest)
     */
    public void putDataItem(PutDataRequest request,
            @Nullable final ResultCallback<? super DataApi.DataItemResult> callback) {
        assertApiConnectivity();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(
                new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send data, status code = " + dataItemResult
                                    .getStatus()
                                    .getStatusCode());
                        }
                        if (callback == null) {
                            for (DataConsumer consumer : mDataConsumers) {
                                consumer.onSendDataResult(dataItemResult.getStatus()
                                        .getStatusCode());
                            }
                        } else {
                            callback.onResult(dataItemResult);
                        }
                    }
                });
    }

    /**
     * Adds a data item asynchronously. A default {@link ResultCallback} will be used to capture
     * the result of this call.
     *
     * @see #putDataItem(PutDataRequest, ResultCallback)
     */
    public void putDataItem(PutDataRequest request) {
        putDataItem(request, null);
    }


    /**
     * Adds a data item  <b>synchronously</b>. This should be called only on non-UI threads.
     * A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked.
     */
    public int putDataItemSynchronous(PutDataRequest request, long timeoutInMillis) {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        DataApi.DataItemResult result = Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .await(timeoutInMillis, TimeUnit.MILLISECONDS);
        return result.getStatus().getStatusCode();
    }

    /**
     * Adds a {@code bitmap} image to a data item asynchronously. Caller can
     * specify a {@link ResultCallback} or pass a {@code null}; if a {@code null} is passed, a
     * default {@link ResultCallback} will be used (see
     * {@link #putDataItem(PutDataRequest, ResultCallback)} for details).
     *
     * @param bitmap       The bitmap to be added.
     * @param path         The path for the data item.
     * @param key          The key to be used for this item in the data map.
     * @param isUrgent     If {@code true}, request will be set as urgent.
     * @param addTimestamp If {@code true}, adds a timestamp to the data map to always create a new
     *                     data item even if an identical data item with the same bitmap has
     *                     already
     *                     been added
     * @param callback     The callback to be notified of the result (can be {@code null}).
     */
    public void putImageData(Bitmap bitmap, String path, String key, boolean isUrgent,
            boolean addTimestamp,
            @Nullable ResultCallback<? super DataApi.DataItemResult> callback) {
        WearUtil.assertNotNull(bitmap, "bitmap");
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        Asset imageAsset = WearUtil.toAsset(bitmap);
        PutDataMapRequest dataMap = PutDataMapRequest.create(path);
        dataMap.getDataMap().putAsset(key, imageAsset);
        if (addTimestamp) {
            dataMap.getDataMap().putLong(Constants.KEY_TIMESTAMP, new Date().getTime());
        }
        PutDataRequest request = dataMap.asPutDataRequest();
        if (isUrgent) {
            request.setUrgent();
        }
        putDataItem(request, callback);
    }

    /**
     * Retrieves data items asynchronously. Caller can specify a {@link ResultCallback} or pass a
     * {@code null}; if a {@code null} is passed, a default {@link ResultCallback} will be used
     * that calls {@link DataConsumer#onGetDataItems(int, DataItemBuffer)}.
     *
     * @see DataConsumer#onGetDataItems(int, DataItemBuffer)
     */
    public void getDataItems(@Nullable final ResultCallback<? super DataItemBuffer> callback) {
        assertApiConnectivity();
        Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(
                new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(DataItemBuffer dataItems) {
                        try {
                            int statusCode = dataItems.getStatus().getStatusCode();
                            if (!dataItems.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to get items, status code: " + statusCode);
                            }
                            if (callback == null) {
                                for (DataConsumer consumer : mDataConsumers) {
                                    consumer.onGetDataItems(statusCode, dataItems);
                                }
                            } else {
                                callback.onResult(dataItems);
                            }
                        } finally {
                            dataItems.release();
                        }
                    }
                });
    }

    /**
     * Retrieves data items asynchronously from the Android Wear network, matching the provided URI
     * and filter type. Caller can specify a {@link ResultCallback} or pass a
     * {@code null}; if a {@code null} is passed, a default {@link ResultCallback} will be used
     * that calls {@link DataConsumer#onGetDataItems(int, DataItemBuffer)}.
     *
     * @see DataApi#getDataItems(GoogleApiClient, Uri, int)
     */
    public void getDataItems(Uri uri, int filterType,
            @Nullable final ResultCallback<? super DataItemBuffer> callback) {
        assertApiConnectivity();
        Wearable.DataApi.getDataItems(mGoogleApiClient, uri, filterType).setResultCallback(
                new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(DataItemBuffer dataItems) {
                        int statusCode = dataItems.getStatus().getStatusCode();
                        if (!dataItems.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to get items, status code: " + statusCode);
                        }
                        if (callback == null) {
                            for (DataConsumer consumer : mDataConsumers) {
                                consumer.onGetDataItems(statusCode, dataItems);
                            }
                        } else {
                            callback.onResult(dataItems);
                        }
                        dataItems.release();
                    }
                });
    }

    /**
     * Retrieves data items synchronously from the Android Wear network. A {@code timeoutInMillis}
     * is required to specify the maximum length of time, in milliseconds, that the thread should
     * be blocked. Caller needs to call {@code release()} on the returned {@link DataItemBuffer}
     * when done.
     */
    public DataItemBuffer getDataItemsSynchronous(long timeoutInMillis) {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        return Wearable.DataApi.getDataItems(mGoogleApiClient).await(
                timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieves data items <b>synchronously</b> from the Android Wear network, matching the
     * provided URI and filter type. This should only be called on a non-UI thread.
     * A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked. Note that caller
     * needs to call {@code release()} on the returned {@link DataItemBuffer} when done.
     */
    public DataItemBuffer getDataItemsSynchronous(Uri uri, int filterType, long timeoutInMillis) {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        return Wearable.DataApi.getDataItems(mGoogleApiClient, uri, filterType).await(
                timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieves a single data item with the given {@code dataItemUri} asynchronously. Caller can
     * specify a {@link ResultCallback} or pass a {@code null}; if a {@code null} is passed, a
     * default {@link ResultCallback} will be used that will call
     * {@link DataConsumer#onGetDataItem(int, DataApi.DataItemResult)}.
     *
     * @see DataConsumer#onGetDataItem(int, DataApi.DataItemResult)
     */
    public void getDataItem(Uri dataItemUri,
            @Nullable final ResultCallback<? super DataApi.DataItemResult> callback) {
        assertApiConnectivity();
        Wearable.DataApi.getDataItem(mGoogleApiClient, dataItemUri).setResultCallback(
                new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        int statusCode = dataItemResult.getStatus().getStatusCode();
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to get the data item, status code: " + statusCode);
                        }
                        if (callback == null) {
                            for (DataConsumer consumer : mDataConsumers) {
                                consumer.onGetDataItem(statusCode, dataItemResult);
                            }
                        } else {
                            callback.onResult(dataItemResult);
                        }
                    }
                });
    }

    /**
     * Retrieves data items with the given {@code dataItemUri} <b>synchronously</b>.
     * This should be called on non-UI threads. A {@code timeoutInMillis} is required to specify
     * the maximum length of time, in milliseconds, that the thread should be blocked.
     */
    public DataApi.DataItemResult getDataItemSynchronous(Uri dataItemUri, long timeoutInMillis) {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        return Wearable.DataApi.getDataItem(mGoogleApiClient, dataItemUri).await(
                timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Deletes a data items asynchronously. Caller can specify a {@link ResultCallback} or
     * pass a {@code null}; if a {@code null} is passed, a default {@link ResultCallback} will be
     * used that would call {@link DataConsumer#onDeleteDataItemsResult(int)}.
     */
    public void deleteDataItems(final Uri dataItemUri,
            @Nullable final ResultCallback<? super DataApi.DeleteDataItemsResult> callback) {
        assertApiConnectivity();
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataItemUri).setResultCallback(
                new ResultCallback<DataApi.DeleteDataItemsResult>() {
                    @Override
                    public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
                        int statusCode = deleteDataItemsResult.getStatus().getStatusCode();
                        if (!deleteDataItemsResult.getStatus().isSuccess()) {
                            Log.e(TAG, String.format(
                                    "Failed to delete data items (status code=%d): %s",
                                    statusCode, dataItemUri));
                        }
                        if (callback == null) {
                            for (DataConsumer consumer : mDataConsumers) {
                                consumer.onDeleteDataItemsResult(statusCode);
                            }
                        } else {
                            callback.onResult(deleteDataItemsResult);
                        }
                    }
                });
    }

    /**
     * Deletes a data items <b>synchronously</b>. This should
     * only be called on a non-UI thread. A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked.
     */
    public DataApi.DeleteDataItemsResult deleteDataItemsSynchronous(final Uri dataItemUri,
            long timeoutInMillis) {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        return Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataItemUri)
                .await(timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    public void syncAsset(String path, String key, byte[] bytes, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        Asset asset = Asset.createFromBytes(bytes);
        putDataMapRequest.getDataMap().putAsset(key, asset);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncBoolean(String path, String key, boolean item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putBoolean(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncByte(String path, String key, byte item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putByte(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncInt(String path, String key, int item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putInt(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncLong(String path, String key, long item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putLong(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncFloat(String path, String key, float item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putFloat(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncDouble(String path, String key, long item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putDouble(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncByteArray(String path, String key, byte[] item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putByteArray(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncString(String path, String key, String item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putString(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncLongArray(String path, String key, long[] item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putLongArray(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncFloatArray(String path, String key, float[] item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putFloatArray(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncStringArray(String path, String key, String[] item, boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putStringArray(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncIntegerArrayList(String path, String key, ArrayList<Integer> item,
            boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putIntegerArrayList(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    public void syncStringArrayList(String path, String key, ArrayList<String> item,
            boolean isUrgent) {
        WearUtil.assertNotEmpty(path, "path");
        WearUtil.assertNotEmpty(key, "key");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        putDataMapRequest.getDataMap().putStringArrayList(key, item);
        syncData(putDataMapRequest, isUrgent);
    }

    //General method to sync data in the Data Layer
    private void syncData(PutDataMapRequest putDataMapRequest, boolean isUrgent) {
        assertApiConnectivity();
        if (isUrgent) {
            putDataMapRequest = putDataMapRequest.setUrgent();
        }
        PutDataRequest request = putDataMapRequest.asPutDataRequest();

        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            WearUtil.logD(TAG, "putDataItem success");
                        } else {
                            String errStr = dataItemResult.getStatus().getStatusMessage();
                            Log.e(TAG, "ERROR: failed to putDataItem, status code: "
                                    + dataItemResult.getStatus().getStatusCode()
                                    + ",status message:"
                                    + errStr);

                        }
                    }
                });
    }

    /**
     * Adds one or more capabilities to the client at runtime. Make sure you balance this with a
     * similar call to {@link #removeCapabilities(String...)}
     *
     * @see #removeCapabilities(String...)
     */
    public void addCapabilities(String... capabilities) {
        if (capabilities == null || capabilities.length == 0) {
            return;
        }
        assertApiConnectivity();
        for (final String capability : capabilities) {
            Wearable.CapabilityApi.addLocalCapability(mGoogleApiClient, capability)
                    .setResultCallback(
                            new ResultCallback<CapabilityApi.AddLocalCapabilityResult>() {
                                @Override
                                public void onResult(
                                        @NonNull CapabilityApi.AddLocalCapabilityResult
                                                addLocalCapabilityResult) {
                                    if (!addLocalCapabilityResult.getStatus().isSuccess()) {
                                        Log.e(TAG, "Failed to add the capability " + capability);
                                    } else {
                                        mWatchedCapabilities.add(capability);
                                    }
                                    for (DataConsumer consumer : mDataConsumers) {
                                        consumer.onAddCapabilityResult(
                                                addLocalCapabilityResult.getStatus()
                                                        .getStatusCode());
                                    }
                                }
                            });
        }
    }

    /**
     * Removes one or more capabilities from the client at runtime.
     *
     * @see #addCapabilities(String...)
     */
    public void removeCapabilities(String... capabilities) {
        if (capabilities == null || capabilities.length == 0) {
            return;
        }
        assertApiConnectivity();
        for (final String capability : capabilities) {
            Wearable.CapabilityApi.removeLocalCapability(mGoogleApiClient, capability)
                    .setResultCallback(
                            new ResultCallback<CapabilityApi.RemoveLocalCapabilityResult>() {
                                @Override
                                public void onResult(
                                        CapabilityApi.RemoveLocalCapabilityResult
                                                removeLocalCapabilityResult) {
                                    if (!removeLocalCapabilityResult.getStatus().isSuccess()) {
                                        Log.e(TAG, "Failed to remove the capability " + capability);
                                    } else {
                                        mWatchedCapabilities.remove(capability);
                                    }
                                    for (DataConsumer consumer : mDataConsumers) {
                                        consumer.onRemoveCapabilityResult(
                                                removeLocalCapabilityResult.getStatus()
                                                        .getStatusCode());
                                    }
                                }
                            });
        }
    }

    /**
     * This method is used to assert that we are connected to the Google Api Client for Wearable
     * APIs.
     */
    public void assertApiConnectivity() {
        if (!isConnected()) {
            Log.e(TAG, "Google API Client is not connected");
//            throw new IllegalStateException(); // maybe connected later
        }
    }

    /**
     * Adds the {@link DataConsumer} to be managed by this singleton to receive changes in
     * lifecycle or other important callbacks for various event. Calls to this method should be
     * balanced by the calls to {@link #removeWearConsumer(DataConsumer)} to avoid leaks. Clients
     * should consider building a {@link DataConsumer} by extending
     * {@link AbstractDataConsumer} instead of implementing {@link DataConsumer} directly.
     *
     * @see AbstractDataConsumer
     */
    public void addWearConsumer(DataConsumer consumer) {
        mDataConsumers.add(WearUtil.assertNotNull(consumer, "consumer"));
        // if we were connected to the Google Api Client earlier, let's call the
        // onGmsApiConnected() on new consumer manually since it won't be called again
        if (isConnected()) {
            consumer.onGmsApiConnected();
        }
    }

    /**
     * Removes the {@link DataConsumer} from this singleton. This should be when there is no need
     * for the registered {@link DataConsumer} to avoid leaks.
     *
     * @see #addWearConsumer(DataConsumer)
     */
    public void removeWearConsumer(DataConsumer consumer) {
        mDataConsumers.remove(WearUtil.assertNotNull(consumer, "consumer"));
    }

    /**
     * Clients can register to {@link DataConsumer#onGmsApiConnected()}.
     */
    private void onConnected(Bundle bundle) {
        WearUtil.logD(TAG, "Google Api Connected");
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onGmsApiConnected();
        }
        addCapabilities(mCapabilitiesToBeAdded);
        Wearable.CapabilityApi.getAllCapabilities(mGoogleApiClient,
                CapabilityApi.FILTER_REACHABLE).setResultCallback(

                new ResultCallback<CapabilityApi.GetAllCapabilitiesResult>() {
                    @Override
                    public void onResult(
                            CapabilityApi.GetAllCapabilitiesResult getAllCapabilitiesResult) {
                        if (getAllCapabilitiesResult.getStatus().isSuccess()) {
                            Map<String, CapabilityInfo> capabilities = getAllCapabilitiesResult
                                    .getAllCapabilities();
                            if (capabilities != null) {
                                for (String capability : capabilities.keySet()) {
                                    CapabilityInfo info = capabilities.get(capability);
                                    mCapabilityToNodesMapping.put(capability, info.getNodes());
                                }
                            }
                            onConnectedInitialCapabilitiesReceived();
                        } else {
                            Log.e(TAG, "getAllCapabilities(): Failed to get all the capabilities");
                        }
                    }
                });
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        if (getConnectedNodesResult.getStatus().isSuccess()) {
                            mConnectedNodes.clear();
                            mConnectedNodes.addAll(getConnectedNodesResult.getNodes());
                            onConnectedInitialNodesReceived();
                        }
                    }
                });
    }

    /**
     * Returns a view of the set of currently connected nodes. The returned set will change as
     * nodes connect and disconnect from the Wear network. The set is safe to access from multiple
     * threads. Note that this may contain cloud, as well as nodes that are not directly connected
     * to this node.
     */
    public Set<Node> getConnectedNodes() {
        return Collections.unmodifiableSet(mConnectedNodes);
    }

    /**
     * Returns {@code true} if and only of the Google Api Client for Wearable APIs is connected.
     */
    public boolean isConnected() {
        return mGoogleApiClient.isConnected();
    }

    /**
     * Returns the current set of connected nodes that provide the given {@code capability}.
     *
     * @see #getNodesForCapability(String, NodeSelectionFilter)
     */
    public Set<Node> getNodesForCapability(String capability) {
        if (TextUtils.isEmpty(capability)) {
            Log.e(TAG, "getNodesForCapability(): Capability cannot be null or empty");
        }
        return mCapabilityToNodesMapping.get(capability);
    }

    /**
     * Returns the current set of connected nodes that provide the given {@code capability} and
     * further narrowed down by the provided {@code filter}. If no such node exists, it returns an
     * empty set. Note that {@code filter} cannot be {@code null}.
     *
     * @see #getNodesForCapability(String)
     */
    public Set<Node> getNodesForCapability(String capability, NodeSelectionFilter filter) {
        if (TextUtils.isEmpty(capability)) {
            Log.e(TAG, "getNodesForCapability(): Capability cannot be null or empty");
        }
        if (filter == null) {
            Log.e(TAG, "getNodesForCapability(): filter cannot be null");
            return Collections.emptySet();
        }
        Set<Node> nodes = mCapabilityToNodesMapping.get(capability);
        if (nodes == null) {
            return Collections.emptySet();
        }
        return filter.filterNodes(nodes);
    }

    /**
     * Returns the connected node with the given {@code nodeId}, if there is one, or {@code null}
     * otherwise.
     */
    @Nullable
    public final Node getNodeById(String nodeId) {
        nodeId = WearUtil.assertNotNull(nodeId, "nodeId");
        for (Node node : mConnectedNodes) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    public void sendFile(final String requestId, Channel channel, Uri file, long startOffset,
            long length, ResultCallback<Status> callback) {

        channel.addListener(mGoogleApiClient, new FileChannelListener());
        PendingResult<Status> result
                = channel.sendFile(mGoogleApiClient, file, startOffset, length);
        if (callback == null) {
            callback = new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                    for (DataConsumer consumer : mDataConsumers) {
                        consumer.onSendFileResult(status.getStatusCode(), requestId);
                    }
                }
            };
        }

        result.setResultCallback(callback);
    }

    /**
     * Initiates opening a channel to a nearby node. When done, it will call the {@code listener}
     * and passes the status code of the request and the channel that was opened. Note that if the
     * request was not successful, the channel passed to the listener will be {@code null}. <br/>
     * <strong>Note:</strong> It is the responsibility of the caller to close the channel and the
     * stream when it is done.
     *
     * @param node     The node to which a channel should be opened. Note that {@code
     *                 node.isNearby()} should return {@code true}
     * @param path     The path used for opening a channel
     * @param listener The listener that is called when this request is completed.
     */
    public void openChannel(Node node, String path,
            final FileTransfer.OnChannelReadyListener listener) {
        if (node.isNearby()) {
            Wearable.ChannelApi.openChannel(
                    mGoogleApiClient, node.getId(), path).setResultCallback(
                    new ResultCallback<ChannelApi.OpenChannelResult>() {
                        @Override
                        public void onResult(ChannelApi.OpenChannelResult openChannelResult) {
                            int statusCode = openChannelResult.getStatus().getStatusCode();
                            Channel channel = null;
                            if (openChannelResult.getStatus().isSuccess()) {
                                channel = openChannelResult.getChannel();
                            } else {
                                Log.e(TAG, "openChannel(): Failed to get channel, status code: "
                                        + statusCode);
                            }
                            listener.onChannelReady(statusCode, channel);
                        }
                    });
        } else {
            Log.e(TAG, "openChannel(): Node should be nearby, you have: " + node);
        }
    }

    /**
     * Opens an {@link OutputStream} to a nearby node. To do this, this method first makes an
     * attempt to open a channel to the target node using the {@code path} that is provided. If
     * successful, then it opens an {@link OutputStream} using that channel. Finally, it calls the
     * {@code listener} when the {@link OutputStream} is available. On the target node, clients
     * should register a
     * {@link DataConsumer#onInputStreamForChannelOpened(int, String, Channel, InputStream)}
     * to be notified of the availability of an {@link InputStream} to handle the incoming bytes.
     * <p>
     * <p>Caller should register a {@link FileTransfer.OnChannelOutputStreamListener}
     * listener to be notified of the status of the request and to obtain a reference to the
     * {@link OutputStream} that is opened upon successful execution.
     *
     * @param node     The node to open a channel for data transfer. Note that this node should be
     *                 nearby otherwise this method will return immediately without performing any
     *                 additional tasks.
     * @param path     The path that will be used to open a channel for transfer.
     * @param listener The listener that will be notified of the status of this request. Upon a
     *                 successful execution, this listener will receive a pointer to the {@link
     *                 OutputStream} that
     *                 was opened.
     */
    public void getOutputStreamViaChannel(Node node, String path,
            final FileTransfer.OnChannelOutputStreamListener listener) {
        if (!node.isNearby()) {
            Log.e(TAG, "getOutputStreamViaChannel(): Node should be nearby, you have: " + node);
        } else {
            Wearable.ChannelApi.openChannel(
                    mGoogleApiClient, node.getId(), path).setResultCallback(
                    new ResultCallback<ChannelApi.OpenChannelResult>() {
                        @Override
                        public void onResult(ChannelApi.OpenChannelResult openChannelResult) {
                            if (openChannelResult.getStatus().isSuccess()) {
                                final Channel channel = openChannelResult.getChannel();
                                channel.addListener(mGoogleApiClient, new FileChannelListener());
                                channel.getOutputStream(mGoogleApiClient).setResultCallback(

                                        new ResultCallback<Channel.GetOutputStreamResult>() {
                                            @Override
                                            public void onResult(
                                                    Channel.GetOutputStreamResult
                                                            getOutputStreamResult) {
                                                if (getOutputStreamResult.getStatus().isSuccess()) {
                                                    OutputStream outputStream
                                                            =
                                                            getOutputStreamResult.getOutputStream();
                                                    listener.onOutputStreamForChannelReady(
                                                            getOutputStreamResult.getStatus()
                                                                    .getStatusCode(), channel,
                                                            outputStream);
                                                } else {
                                                    closeChannel(channel);
                                                    listener.onOutputStreamForChannelReady(
                                                            getOutputStreamResult.getStatus()
                                                                    .getStatusCode(), null, null);
                                                }
                                            }
                                        });
                            } else {
                                listener.onOutputStreamForChannelReady(
                                        openChannelResult.getStatus().getStatusCode(), null, null);
                            }
                        }
                    });
        }
    }

    /**
     * Closes the {@code channel} if it is not {@code null}.
     */
    public void closeChannel(Channel channel) {
        if (channel != null) {
            channel.close(mGoogleApiClient);
        }
    }

    /**
     * Extracts {@link Bitmap} data from an
     * {@link com.google.android.gms.wearable.Asset}, in a blocking way, hence should not be called
     * on the UI thread. This may return {@code null}.
     */
    public Bitmap loadBitmapFromAssetSynchronous(Asset asset) {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        if (asset == null) {
            Log.e(TAG, "Asset must be non-null");
        }

        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        return BitmapFactory.decodeStream(assetInputStream);
    }


    /**
     * Extracts byte array data from an
     * {@link com.google.android.gms.wearable.Asset}, in a blocking way, hence should not be called
     * on the UI thread. This may return {@code null}.
     */
    public byte[] loadAssetSynchronous(Asset asset) throws IOException {
        assertApiConnectivity();
        WearUtil.assertNonUiThread();
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }

        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int n = 0;
        byte[] buffer = new byte[4096];
        while (-1 != (n = assetInputStream.read(buffer))) {
            output.write(buffer, 0, n);
        }

        return output.toByteArray();
    }

    /**
     * Clients can register to {@link DataConsumer#onGmsConnectionSuspended()}.
     */
    private void onConnectionSuspended(int i) {
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onGmsConnectionSuspended();
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onGmsConnectionFailed()}.
     */
    private void onConnectionFailed(ConnectionResult connectionResult) {
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onGmsConnectionFailed();
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onMessageReceived(MessageEvent)}.
     */
    void onMessageReceived(MessageEvent messageEvent) {
        WearUtil.logD(TAG, "Received a message with path: " + messageEvent.getPath());
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onMessageReceived(messageEvent);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onPeerConnected(Node)}.
     */
    void onPeerConnected(Node peer) {
        WearUtil.logD(TAG, "onPeerConnected: " + peer);
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onPeerConnected(peer);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onPeerDisconnected(Node)}.
     */
    void onPeerDisconnected(Node peer) {
        WearUtil.logD(TAG, "onPeerDisconnected: " + peer);
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onPeerDisconnected(peer);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onConnectedNodes(List)}.
     */
    void onConnectedNodes(List<Node> connectedNodes) {
        WearUtil.logD(TAG, "onConnectedNodes: " + connectedNodes);
        mConnectedNodes.clear();
        mConnectedNodes.addAll(connectedNodes);
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onConnectedNodes(connectedNodes);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onInitialConnectedNodesReceived()}.
     */
    void onConnectedInitialNodesReceived() {
        WearUtil.logD(TAG, "onConnectedInitialNodesReceived");
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onInitialConnectedNodesReceived();
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onCapabilityChanged(String, Set)}.
     */
    void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        String capability = capabilityInfo.getName();
        Set<Node> nodes = capabilityInfo.getNodes();
        mCapabilityToNodesMapping.put(capability, nodes);
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onCapabilityChanged(capability, nodes);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onInitialConnectedCapabilitiesReceived().
     */
    void onConnectedInitialCapabilitiesReceived() {
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onInitialConnectedCapabilitiesReceived();
        }
    }

    /**
     * Clients can register to
     * {@link DataConsumer#onInputStreamForChannelOpened(int, String, Channel, InputStream)}.
     */
    void onChannelOpened(final Channel channel) {
        String path = channel.getPath();
        WearUtil.logD(TAG, "onChannelOpened(): Path =" + path);
        if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_FILE)) {
            // we are receiving a file sent by FileTransfer
            final Map<String, String> paramsMap = getFileTransferParams(path);
            final String name = paramsMap.get(FileTransfer.PARAM_NAME);
            final String requestId = paramsMap.get(FileTransfer.PARAM_REQUEST_ID);
            final long size = Long.valueOf(paramsMap.get(FileTransfer.PARAM_SIZE));
            try {
                final File outFile = prepareFile(name);
                if (outFile == null || !outFile.exists()) {
                    Log.e(TAG, "Failed to create the file: " + name);
                    return;
                }
                channel.receiveFile(mGoogleApiClient, Uri.fromFile(outFile), false)
                        .setResultCallback(
                                new ReceivedFileResultCallback(requestId, outFile, size, channel));
            } catch (IOException e) {
                Log.e(TAG, "Failed to create the file: " + name, e);
            }

        } else if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_STREAM)) {
            // we are receiving data by low level InputStream, sent by FileTransfer
            final Map<String, String> paramsMap = getStreamTransferParams(path);
            final String requestId = paramsMap.get(FileTransfer.PARAM_REQUEST_ID);
            channel.getInputStream(mGoogleApiClient).setResultCallback(
                    new com.google.android.gms.common.api.ResultCallback<Channel
                            .GetInputStreamResult>() {
                        @Override
                        public void onResult(
                                @NonNull Channel.GetInputStreamResult getInputStreamResult) {
                            int statusCode = getInputStreamResult.getStatus().getStatusCode();
                            if (!getInputStreamResult.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to open InputStream from channel, status code: "
                                        + statusCode);
                            }
                            for (DataConsumer consumer : mDataConsumers) {
                                consumer.onInputStreamForChannelOpened(statusCode,
                                        requestId, channel, getInputStreamResult.getInputStream());
                            }
                        }
                    });
        } else {
            for (DataConsumer consumer : mDataConsumers) {
                consumer.onChannelOpened(channel);
            }
        }
    }

    private Map<String, String> getFileTransferParams(String path) {
        Map<String, String> result = new HashMap<>();
        if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_FILE)) {
            String[] pieces = path.replace(Constants.PATH_FILE_TRANSFER_TYPE_FILE, "").split("\\/");
            try {
                result.put(FileTransfer.PARAM_NAME, URLDecoder.decode(pieces[0], "utf-8"));
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to decode name", e);
            }
            result.put(FileTransfer.PARAM_SIZE, pieces[1]);
            result.put(FileTransfer.PARAM_REQUEST_ID, pieces[2]);
        } else {
            Log.e(TAG, "Path doesn't start with " + Constants.PATH_FILE_TRANSFER_TYPE_FILE);
        }

        return result;
    }

    private Map<String, String> getStreamTransferParams(String path) {
        Map<String, String> result = new HashMap<>();
        if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_STREAM)) {
            String[] pieces = path.replace(Constants.PATH_FILE_TRANSFER_TYPE_FILE, "").split("\\/");
            result.put(FileTransfer.PARAM_REQUEST_ID, pieces[0]);
        } else {
            Log.e(TAG, "Path doesn't start with " + Constants.PATH_FILE_TRANSFER_TYPE_STREAM);
        }

        return result;
    }

    private File prepareFile(String name) throws IOException {
        File file = new File(mContext.getFilesDir(), name);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                return null;
            }
        }

        return file;
    }

    /**
     * Clients can register to {@link DataConsumer#onChannelClosed(Channel, int, int)}.
     */
    void onChannelClosed(Channel channel, int closeReason,
            int appSpecificErrorCode) {
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onChannelClosed(channel, closeReason, appSpecificErrorCode);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onInputClosed(Channel, int, int)}.
     */
    void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onInputClosed(channel, closeReason, appSpecificErrorCode);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onOutputClosed(Channel, int, int)}.
     */
    void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        for (DataConsumer consumer : mDataConsumers) {
            consumer.onOutputClosed(channel, closeReason, appSpecificErrorCode);
        }
    }

    /**
     * Clients can register to {@link DataConsumer#onDataChanged(DataEvent)}.
     */
    void onDataChanged(DataEventBuffer dataEvents) {
        for (DataConsumer consumer : mDataConsumers) {
            for (DataEvent event : dataEvents) {
                consumer.onDataChanged(event);
            }
        }
    }

    /**
     * A method to clean up the artifacts of the GmsWear. This should be called when we are
     * certain we would not need the GmsWear any more.
     */
    public void cleanUp() {
        WearUtil.logD(TAG, "cleanUp() ...");
        mContext.stopService(new Intent(mContext, GmsWearService.class));
        if (!mWatchedCapabilities.isEmpty()) {
            String[] capabilities = mWatchedCapabilities
                    .toArray(new String[mWatchedCapabilities.size()]);
            removeCapabilities(capabilities);
        }
        mDataConsumers.clear();
    }

    /**
     * Stops the {@link GmsWearService}.
     */
    public void stopGmsWearService() {
        WearUtil.logD(TAG, "stopGmsWearService()");
        mContext.stopService(new Intent(mContext, GmsWearService.class));
    }

    /**
     * Returns the version of this library
     */
    public String getVersion() {
        return mGmsWearVersion;
    }

    /**
     * When the client application becomes visible, we start an instance of
     * {@link com.google.android.gms.wearable.WearableListenerService} that is provided by this
     * library. Even if the service has already started, here we call this service in a way that
     * it would stay active until it is explicitly stopped. In order to avoid having a long-running
     * background service, we kill that service when the client application is no longer visible.
     */
    private void onAppEnterForeground() {
        mAppForeground = true;
        mContext.startService(new Intent(mContext, GmsWearService.class));
    }

    /**
     * Called when application goes to background. We do the needed clean up here.
     */
    private void onAppEnterBackground() {
        mAppForeground = false;
        stopGmsWearService();
    }

    private class ReceivedFileResultCallback implements ResultCallback<Status> {
        String requestId;
        long size;

        File outFile;
        Channel channel;

        ReceivedFileResultCallback(String requestId, File outFile, long size, Channel channel) {
            this.requestId = requestId;
            this.outFile = outFile;
            this.size = size;
            this.channel = channel;
        }

        @Override
        public void onResult(@NonNull Status status) {

            int statusCode = status.getStatusCode();
            if (!status.isSuccess()) {
                Log.e(TAG, "receiveFile(): Failed to receive file with "
                        + "status code = " + statusCode
                        + ", and status: " + status.getStatus());

                // Notify consumers of the failure
                for (DataConsumer consumer : mDataConsumers) {
                    consumer.onFileReceivedResult(statusCode,
                            requestId, outFile, outFile.getName());
                }
            } else {
                // Add a listener to be notified when the transfer is
                // over
                channel.addListener(mGoogleApiClient,
                        new ChannelApi.ChannelListener() {
                            @Override
                            public void onChannelOpened(
                                    Channel channel) {
                            }

                            @Override
                            public void onChannelClosed(Channel channel,
                                    int closeReason,
                                    int appSpecificErrorCode) {
                            }

                            @Override
                            public void onInputClosed(Channel channel,
                                    int closeReason,
                                    int appSpecificErrorCode) {
                                // File transfer is finished
                                int resultStatusCode;
                                if (closeReason
                                        != CLOSE_REASON_NORMAL) {
                                    Log.e(TAG,
                                            "receiveFile(): Failed to"
                                                    + " receive file "
                                                    + "with "
                                                    + "status "
                                                    + "closeReason = "
                                                    + closeReason
                                                    + ", and "
                                                    +
                                                    "appSpecificErrorCode: "
                                                    +
                                                    appSpecificErrorCode);
                                    resultStatusCode = CommonStatusCodes.ERROR;
                                } else if (size != outFile.length()) {
                                    Log.e(TAG,
                                            "receiveFile(): Size of "
                                                    + "the transferred "
                                                    + "file doesn't "
                                                    + "match the "
                                                    + "original size");
                                    resultStatusCode =
                                            CommonStatusCodes.ERROR;
                                } else {
                                    resultStatusCode =
                                            CommonStatusCodes.SUCCESS;
                                }
                                // Notify consumers
                                for (DataConsumer consumer : mDataConsumers) {
                                    consumer.onFileReceivedResult(resultStatusCode,
                                            requestId, outFile, outFile.getName());
                                }
                            }

                            @Override
                            public void onOutputClosed(Channel channel,
                                    int closeReason,
                                    int appSpecificErrorCode) {
                            }
                        });
            }
        }
    }

    private final class GmsConnectionCallbacksListener
            implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle bundle) {
            GmsWear.this.onConnected(bundle);
        }

        @Override
        public void onConnectionSuspended(int i) {
            GmsWear.this.onConnectionSuspended(i);
        }
    }

    private final class GmsConnectionFailedListener
            implements GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            GmsWear.this.onConnectionFailed(connectionResult);
        }
    }

    private final class WearAppVisibilityDetectorListener
            implements AppVisibilityDetector.Listener {

        @Override
        public void onAppEnterForeground() {
            GmsWear.this.onAppEnterForeground();
        }

        @Override
        public void onAppEnterBackground() {
            GmsWear.this.onAppEnterBackground();
        }
    }

    private class FileChannelListener implements ChannelApi.ChannelListener {
        @Override
        public void onChannelOpened(Channel channel) {
        }

        @Override
        public void onChannelClosed(Channel channel,
                int closedReason, int appSpecificErrorCode) {
            WearUtil.logD(TAG, "Channel Closed");
        }

        @Override
        public void onInputClosed(Channel channel,
                int closedReason, int appSpecificErrorCode) {
        }

        @Override
        public void onOutputClosed(Channel channel,
                int closedReason, int appSpecificErrorCode) {
            WearUtil.logD(TAG, "onOutputClosed(): Output closed so closing channel...");
            closeChannel(channel);
        }
    }
}
