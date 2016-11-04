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

import com.cscao.libs.gmswear.util.WearUtil;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * A {@link WearableListenerService} that is started when the client application is in front.
 * GmsWear uses this as a means to get a number of wear related callbacks and as
 * such, {@link GmsWear} starts this service by calling {@code Context.startService()} which
 * then makes this service a long-lived service. {@link GmsWear} is also responsible for
 * stopping this service; clients could call {@link GmsWear#stopGmsWearService()} to do that or
 * the {@link GmsWear} will do so when the client application is no longer visible.
 */
public class GmsWearService extends WearableListenerService {

    private static final String TAG = "GmsWearService";
    private GmsWear mGmsWear;

    @Override
    public void onCreate() {
        super.onCreate();
        WearUtil.logD(TAG, "GmsWearService is being created");
        mGmsWear = GmsWear.getInstance();
    }

    @Override
    public void onDestroy() {
        WearUtil.logD(TAG, "GmsWearService is being destroyed");
        super.onDestroy();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        mGmsWear.onDataChanged(dataEvents);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        mGmsWear.onMessageReceived(messageEvent);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        mGmsWear.onPeerConnected(peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
        mGmsWear.onPeerDisconnected(peer);
    }

    @Override
    public void onConnectedNodes(List<Node> connectedNodes) {
        super.onConnectedNodes(connectedNodes);
        mGmsWear.onConnectedNodes(connectedNodes);
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
        mGmsWear.onCapabilityChanged(capabilityInfo);
    }

    @Override
    public void onChannelOpened(Channel channel) {
        super.onChannelOpened(channel);
        mGmsWear.onChannelOpened(channel);
    }

    @Override
    public void onChannelClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onChannelClosed(channel, closeReason, appSpecificErrorCode);
        mGmsWear.onChannelClosed(channel, closeReason, appSpecificErrorCode);
    }

    @Override
    public void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onInputClosed(channel, closeReason, appSpecificErrorCode);
        mGmsWear.onInputClosed(channel, closeReason, appSpecificErrorCode);
    }

    @Override
    public void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onOutputClosed(channel, closeReason, appSpecificErrorCode);
        mGmsWear.onOutputClosed(channel, closeReason, appSpecificErrorCode);
    }
}
