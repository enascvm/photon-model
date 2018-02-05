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

package com.vmware.photon.controller.model.adapters.azure.model.cost;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;

public class BillParsingStatus {
    private boolean parsingComplete;
    private long noLinesRead;

    public BillParsingStatus() {
        this.parsingComplete = false;
        this.noLinesRead = AzureCostConstants.DEFAULT_LINES_TO_SKIP;
    }

    public boolean isParsingComplete() {
        return this.parsingComplete;
    }

    public void setParsingComplete(boolean parsingComplete) {
        this.parsingComplete = parsingComplete;
    }

    public long getNoLinesRead() {
        return this.noLinesRead;
    }

    public void setNoLinesRead(long noLinesRead) {
        this.noLinesRead = noLinesRead;
    }

    @Override
    public String toString() {
        return "BillParsingStatus{" +
                "parsingComplete=" + this.parsingComplete +
                ", noLinesRead=" + this.noLinesRead +
                '}';
    }
}
