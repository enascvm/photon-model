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

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decorates an InputStream by remembering every byte read.
 */
public class StoringInputStream extends FilterInputStream {
    private final ByteArrayOutputStream buffer;

    protected StoringInputStream(InputStream in) {
        super(in);
        this.buffer = new ByteArrayOutputStream(8 * 1024);
    }

    @Override
    public int read() throws IOException {
        int r = super.read();
        this.buffer.write(r);

        return r;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) {
            this.buffer.write(b, off, n);
        }
        return n;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = in.read(b);
        if (n > 0) {
            this.buffer.write(b, 0, n);
        }

        return n;
    }

    public byte[] getStoredBytes() {
        return this.buffer.toByteArray();
    }
}
