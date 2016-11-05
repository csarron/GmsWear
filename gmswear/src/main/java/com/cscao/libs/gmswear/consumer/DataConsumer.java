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

package com.cscao.libs.gmswear.consumer;

import android.net.Uri;

import com.cscao.libs.gmswear.GmsWear;
import com.cscao.libs.gmswear.connectivity.FileTransfer;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * An interface that is used by the library to inform application clients of changes in the state
 * or data. It is recommended that the clients extend the no-op implementation
 * {@link AbstractDataConsumer} of this interface and override only the desired subset of
 * callbacks.
 * Clients register their implementation of this interface by calling
 * {@link GmsWear#addWearConsumer(DataConsumer)} and should remember to unregister by
 * calling {@link GmsWear#removeWearConsumer(DataConsumer)} to avoid
 * any leaks.
 */
public interface DataConsumer extends FileTransfer.OnChannelOutputStreamListener {

    /**
     * Called when the result of
     * {@link GmsWear#addCapabilities(String...)} is available.
     *
     * @param statusCode The status code of the result
     */
    void onAddCapabilityResult(int statusCode);

    /**
     * Called when the result of
     * {@link GmsWear#removeCapabilities(String...)} is available.
     *
     * @param statusCode The status code of the result
     */
    void onRemoveCapabilityResult(int statusCode);

    /**
     * Called when framework reports a change in the capabilities.
     *
     * @param capability The capability that has changed.
     * @param nodes      The new set of nodes for the given capability.
     */
    void onCapabilityChanged(String capability, Set<Node> nodes);

    /**
     * Called when initial capabilities are received after google api connection.
     */
    void onInitialConnectedCapabilitiesReceived();

    /**
     * Called when the result of {@link GmsWear#sendMessage(String, String, byte[])}
     * (or other variants of that call) is available.
     *
     * @param statusCode The status code of the result
     */
    void onSendMessageResult(int statusCode);

    /**
     * Called when a message is received.
     */
    void onMessageReceived(MessageEvent messageEvent);

    /**
     * Called when the Google Api Client for Wearable APIs is connected. It is guaranteed that
     * this method is called even if the connectivity is established prior to registration of the
     * listener.
     */
    void onGmsApiConnected();

    /**
     * Called when the connection to the Google Api Client for Wearable APIs is suspended.
     */
    void onGmsConnectionSuspended();

    /**
     * Called when the connection to the Google Api Client for Wearable APIs fails.
     */
    void onGmsConnectionFailed();

    /**
     * Called when a channel is closed.
     */
    void onChannelClosed(Channel channel, int closeReason, int appSpecificErrorCode);

    /**
     * Called when InputStream associated with the channel is close.
     */
    void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode);

    /**
     * Called when the output stream associated with the channel is closed.
     */
    void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode);

    /**
     * Called when Data layer reports a change in the stored data.
     */
    void onDataChanged(DataEvent dataEvent);

    /**
     * Called when the list of connected nodes changes.
     */
    void onConnectedNodes(List<Node> connectedNodes);

    /**
     * Called when initial list of connected nodes is received - so that you can send messages to
     * it.
     */
    void onInitialConnectedNodesReceived();

    /**
     * Called when a connected peer disconnects.
     */
    void onPeerDisconnected(Node peer);

    /**
     * Called when a node changes its connectivity status to connected.
     */
    void onPeerConnected(Node peer);

    /**
     * Called when the result of
     * {@link GmsWear#putDataItem(PutDataRequest, ResultCallback)}
     * is available.
     *
     * @param statusCode The status code of the result
     */
    void onSendDataResult(int statusCode);

    /**
     * Called when the result of
     * {@link GmsWear#getDataItems(ResultCallback)}
     * is available.
     *
     * @param statusCode     The status code of the result
     * @param dataItemBuffer The data items received
     */
    void onGetDataItems(int statusCode, DataItemBuffer dataItemBuffer);

    /**
     * Called when the result of
     * {@link GmsWear#getDataItem(Uri, ResultCallback)}
     * is available.
     *
     * @param statusCode     The status code of the result
     * @param dataItemResult The data items received
     */
    void onGetDataItem(int statusCode, DataApi.DataItemResult dataItemResult);

    /**
     * Called when the result of
     * {@link GmsWear#deleteDataItems(Uri, ResultCallback)}
     * is available.
     *
     * @param statusCode The status code of the result
     */
    void onDeleteDataItemsResult(int statusCode);

    /**
     * Called when a request for opening an {@link InputStream} is available.
     * When a clients request to open an {@link java.io.OutputStream} by calling
     * {@link FileTransfer#requestOutputStream()}, this library will handle opening a channel
     * from the client node to the target node. On the target node, client should register to this
     * callback to be notified when an {@link InputStream} is available to receive the data sent
     * through the channel.
     *
     * @param statusCode  The status code corresponding to the attempt to open an
     *                    {@link InputStream}. Successful operation will be identified by
     *                    {@link com.google.android.gms.wearable.WearableStatusCodes#SUCCESS}
     * @param requestId   The unique id for the request that was made by the sender client. This
     *                    id is provided here for bookkeeping purposes and being able to correlate
     *                    different requests
     *                    from the client to the streams that open on the receiver ends.
     * @param channel     The instance of {@link Channel}
     * @param inputStream The {@link InputStream} that is opened if successful
     */
    void onInputStreamForChannelOpened(int statusCode, String requestId, Channel channel,
            InputStream inputStream);

    /**
     * Called with the result corresponding to a request to send a file using
     * {@link FileTransfer#startTransfer()}. This is called on the sender node to inform the
     * sender of the success ot failure of the operation.
     *
     * @param statusCode The status code corresponding to the attempt to transfer a file.
     *                   Successful
     *                   operation will be identified by
     *                   {@link com.google.android.gms.wearable.WearableStatusCodes#SUCCESS}
     * @param requestId  The unique id for this operation.
     */
    void onSendFileResult(int statusCode, String requestId);

    /**
     * Called when a {@link Channel} is opened.
     */
    void onChannelOpened(Channel channel);

    /**
     * Called when a node has the result of receiving a file transfer. The sender client has
     * used {@link FileTransfer#startTransfer()} to send a file and the library handles the
     * logistics of this transfer but on the receiving end, the client can register for this
     * callback to be notified when the transfer is completed, even if unsuccessful.
     *
     * @param statusCode   The status code corresponding to the attempt to transfer a file.
     *                     Successful
     *                     operation will be identified by
     *                     {@link com.google.android.gms.wearable.WearableStatusCodes#SUCCESS}
     * @param requestId    The unique id for this operation.
     * @param savedFile    The {@link File} object pointing to the file that has been transferred.
     * @param originalName The original name of the ile that was sent from the sender node.
     */
    void onFileReceivedResult(int statusCode, String requestId, File savedFile,
            String originalName);

}
