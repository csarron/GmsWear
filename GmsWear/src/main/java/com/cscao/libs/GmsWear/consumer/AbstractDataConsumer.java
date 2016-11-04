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

import com.cscao.libs.gmswear.GmsWear;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/**
 * A no-op implementation of {@link DataConsumer}. Clients can extend this class and override only
 * the subset of callbacks that they are interested in. Note that after extending this class, you
 * need to register your extension by calling
 * {@link GmsWear#addWearConsumer(DataConsumer)} and unregister it, when done, by calling
 * {@link GmsWear#removeWearConsumer(DataConsumer)}
 */
public abstract class AbstractDataConsumer implements DataConsumer {

    @Override
    public void onInitialConnectedCapabilitiesReceived() {
        //no-op
    }

    @Override
    public void onInitialConnectedNodesReceived() {
        //no-op
    }

    @Override
    public void onAddCapabilityResult(int statusCode) {
        //no-op
    }

    @Override
    public void onRemoveCapabilityResult(int statusCode) {
        //no-op
    }

    @Override
    public void onCapabilityChanged(String capability, Set<Node> nodes) {
        //no-op
    }

    @Override
    public void onSendMessageResult(int statusCode) {
        //no-op
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        //no-op
    }

    @Override
    public void onGmsApiConnected() {
        //no-op
    }

    @Override
    public void onGmsConnectionSuspended() {
        //no-op
    }

    @Override
    public void onGmsConnectionFailed() {
        //no-op
    }

    @Override
    public void onChannelOpened(Channel channel) {
        //no-op
    }

    @Override
    public void onFileReceivedResult(int statusCode, String requestId, File savedFile,
            String originalName) {
        //no-op
    }

    @Override
    public void onChannelClosed(Channel channel, int closeReason,
            int appSpecificErrorCode) {
        //no-op
    }

    @Override
    public void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        //no-op
    }

    @Override
    public void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        //no-op
    }

    @Override
    public void onDataChanged(DataEvent dataEvent) {
        //no-op
    }

    @Override
    public void onConnectedNodes(List<Node> connectedNodes) {
        //no-op
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        //no-op
    }

    @Override
    public void onPeerConnected(Node peer) {
        //no-op
    }

    @Override
    public void onSendDataResult(int statusCode) {
        //no-op
    }

    @Override
    public void onGetDataItems(int status, DataItemBuffer dataItemBuffer) {
        //no-op
    }

    @Override
    public void onGetDataItem(int statusCode, DataApi.DataItemResult dataItemResult) {
        //no-op
    }

    @Override
    public void onDeleteDataItemsResult(int statusCode) {
        //no-op
    }

    @Override
    public void onInputStreamForChannelOpened(int statusCode, String requestId,
            Channel channel, InputStream inputStream) {
        //no-op
    }

    @Override
    public void onSendFileResult(int statusCode, String requestId) {
        //no-op
    }

    @Override
    public void onOutputStreamForChannelReady(int statusCode, Channel channel,
            OutputStream outputStream) {
        //no-op
    }
}
