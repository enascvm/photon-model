/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere.ovf;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.vim25.HttpNfcLeaseState;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.InvalidStateFaultMsg;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TimedoutFaultMsg;
import com.vmware.vim25.VimPortType;

/**
 * Keeps an HttpNfcLease alive, updating the progress every few seconds.
 */
public class LeaseProgressUpdater {
    public static final int LEASE_UPDATE_INTERVAL_MILLIS = 10000;
    public static final int LEASE_READY_RETRY_MILLIS = 1000;

    private static final Logger logger = LoggerFactory.getLogger(LeaseProgressUpdater.class.getName());

    private static final String PROP_STATE = "state";
    private static final String PROP_ERROR = "error";

    private final ManagedObjectReference nfcLease;
    private final AtomicLong reported = new AtomicLong(0);
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final long total;
    private final Connection connection;
    private final GetMoRef get;

    public LeaseProgressUpdater(Connection connection, ManagedObjectReference nfcLease,
            long total) {
        this.connection = connection;
        this.nfcLease = nfcLease;
        if (total != 0) {
            this.total = total;
        } else {
            this.total = 1;
        }
        this.get = new GetMoRef(this.connection);
    }

    public void advance(long delta) {
        this.reported.addAndGet(delta);
    }

    public void complete() {
        this.done.set(true);

        try {
            getVimPort().httpNfcLeaseProgress(this.nfcLease, 100);
            getVimPort().httpNfcLeaseComplete(this.nfcLease);
        } catch (RuntimeFaultFaultMsg | TimedoutFaultMsg | InvalidStateFaultMsg e) {
            logger.warn("Cannot complete nfcLease, continuing", e);
        }
    }

    public void start(ScheduledExecutorService executorService) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (LeaseProgressUpdater.this.done.get()) {
                    return;
                }
                try {
                    updateLease();
                } catch (RuntimeFaultFaultMsg | TimedoutFaultMsg e) {
                    logger.warn("Lease timed out", e);
                    return;
                }

                executorService.schedule(this, LEASE_UPDATE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
            }
        };

        executorService.submit(task);
    }

    private void updateLease() throws TimedoutFaultMsg, RuntimeFaultFaultMsg {
        long reported = this.reported.get();
        int pct = (int) Math.floor((double) reported / this.total * 100);

        // normalize pct
        if (pct > 100) {
            pct = 100;
        } else if (pct < 0) {
            pct = 0;
        }
        if (pct >= 100 && reported != this.total) {
            // rounding got to 100 but still not done
            pct = 99;
        }

        logger.info("Updating nfcLease {}: {} %", VimUtils.convertMoRefToString(this.nfcLease), pct);
        getVimPort().httpNfcLeaseProgress(this.nfcLease, pct);
    }

    public void abort(LocalizedMethodFault e) {
        this.done.set(true);

        try {
            getVimPort().httpNfcLeaseAbort(this.nfcLease, e);
        } catch (RuntimeFaultFaultMsg | TimedoutFaultMsg | InvalidStateFaultMsg ex) {
            logger.warn("Error aborting nfcLease {}", VimUtils.convertMoRefToString(this.nfcLease), e);
        }
    }

    private VimPortType getVimPort() {
        return this.connection.getVimPort();
    }

    /**
     * Wait up to a minute for the nfcLease to become READY.
     * @throws Exception
     */
    public void awaitReady() throws Exception {
        int i = 60;

        while (i-- > 0) {
            HttpNfcLeaseState state = getState();
            if (state.equals(HttpNfcLeaseState.ERROR)) {
                LocalizedMethodFault leaseError = this.get.entityProp(this.nfcLease, PROP_ERROR);
                logger.warn("nfcLease error: {}", leaseError.getLocalizedMessage(), leaseError);
                VimUtils.rethrow(leaseError);
            }

            if (state.equals(HttpNfcLeaseState.READY)) {
                return;
            }

            logger.debug("Waiting for nfcLease {}", VimUtils.convertMoRefToString(this.nfcLease), state);

            Thread.sleep(LEASE_READY_RETRY_MILLIS);
        }

        throw new IllegalStateException("Lease not ready within configured timeout");
    }

    /**
     * For some reason getting the state of a nfcLease returns a element instead of HttpNfcLeaseState.
     * This method parses the dom element to a state.
     *
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     */
    private HttpNfcLeaseState getState() throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        Object state = this.get.entityProp(this.nfcLease, PROP_STATE);
        if (state instanceof HttpNfcLeaseState) {
            return (HttpNfcLeaseState) state;
        }

        if (state instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) state;
            return HttpNfcLeaseState.fromValue(e.getTextContent());
        }

        throw new IllegalStateException("Cannot get state of nfcLease");
    }
}