/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationService;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

public abstract class BaseAdaptersRegistryServiceTest extends BaseModelTest {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    private Set<String> documentsToDelete = new HashSet<>();

    @Before
    public void setUp() throws Throwable {
        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));

        PhotonModelServices.startServices(this.host);
        PhotonModelMetricServices.startServices(this.host);

        this.host.addPrivilegedService(ResourceOperationService.class);
        PhotonModelAdaptersRegistryAdapters.startServices(this.host);

        this.host.setTimeoutSeconds(300);

        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
    }

    @After
    public void tearDown() {
        if (!this.documentsToDelete.isEmpty()) {
            List<DeferredResult<Operation>> ops = new ArrayList<>();
            this.documentsToDelete.forEach(documentSelfLink -> {
                this.logger.info("Going to delete: " + documentSelfLink);
                Operation dOp = Operation.createDelete(this.host, documentSelfLink);

                DeferredResult<Operation> dr = this.host.sendWithDeferredResult(dOp)
                        .whenComplete((o, e) -> {
                            final String msg = "Delete state: " + documentSelfLink;
                            if (e != null) {
                                this.logger.warning(msg + " : ERROR. Cause: " + Utils.toString(e));
                            } else {
                                this.logger.info(msg + " : SUCCESS");
                            }
                        });
                ops.add(dr);
            });
            join(DeferredResult.allOf(ops));
        }
    }

    protected void markForDelete(String documentSelfLink) {
        if (documentSelfLink == null) {
            this.logger.severe("cannot register null documentSelfLink.");
        } else {
            this.logger.info("Mark for delete: " + documentSelfLink);
            this.documentsToDelete.add(documentSelfLink);
        }
    }

    public static <T> T join(DeferredResult<T> dr) {
        return ((CompletableFuture<T>) dr.toCompletionStage()).join();
    }

}