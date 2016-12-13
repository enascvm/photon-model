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

package com.vmware.photon.controller.model.adapters.vsphere.util.finders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SelectionSpec;
import com.vmware.vim25.TraversalSpec;

/**
 * This class computes a flat list of datacenters reachable from a connection. The returned elements contain
 * the full path to the Datacenter. It abstracts the boilerplate for converting property collector object graph to
 * a list of Elements.
 */
public class DatacenterLister extends BaseHelper {
    public DatacenterLister(Connection connection) {
        super(connection);
    }

    public List<Element> listAllDatacenters() throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        PropertyFilterSpec spec = new PropertyFilterSpec();

        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(connection.getServiceContent().getRootFolder());
        ospec.setSkip(false);
        spec.getObjectSet().add(ospec);

        TraversalSpec traverseFolders = new TraversalSpec();
        traverseFolders.setPath("childEntity");
        traverseFolders.setSkip(false);
        String traversalName = "folder2child";
        traverseFolders.setName(traversalName);
        traverseFolders.setType(VimNames.TYPE_FOLDER);

        SelectionSpec selSpec = new SelectionSpec();
        selSpec.setName(traversalName);
        traverseFolders.getSelectSet().add(selSpec);

        ospec.getSelectSet().add(traverseFolders);

        for (String t : new String[] { VimNames.TYPE_FOLDER, VimNames.TYPE_DATACENTER }) {
            PropertySpec pspec = new PropertySpec();
            pspec.setType(t);
            pspec.getPathSet().add(VimNames.PROPERTY_NAME);
            pspec.getPathSet().add(VimNames.PROPERTY_PARENT);

            spec.getPropSet().add(pspec);
        }

        List<ObjectContent> ocs = retrieveProperties(spec);
        List<Element> res = new ArrayList<>();

        for (ObjectContent oc : ocs) {
            if (oc.getObj().getType().equals(VimNames.TYPE_FOLDER)) {
                continue;
            }

            String parentPath = buildPathToParent(prop(oc, VimNames.PROPERTY_PARENT), ocs);
            res.add(Element.make(oc.getObj(), parentPath + "/" + prop(oc, VimNames.PROPERTY_NAME)));
        }

        return res;
    }

    private String buildPathToParent(ManagedObjectReference ref, List<ObjectContent> ocs) {
        ObjectContent parent = findByParent(ref, ocs);
        ObjectContent self = findByRef(ref, ocs);
        if (parent == null) {
            return "/" + prop(self, VimNames.PROPERTY_NAME);
        } else {
            return buildPathToParent(parent.getObj(), ocs) + "/" + prop(self, VimNames.PROPERTY_NAME);
        }
    }

    private ObjectContent findByRef(ManagedObjectReference ref, List<ObjectContent> ocs) {
        if (ref == null) {
            return null;
        }

        for (ObjectContent oc : ocs) {
            ManagedObjectReference obj = oc.getObj();
            if (Objects.equals(ref.getValue(), obj.getValue())) {
                return oc;
            }
        }

        return null;
    }

    private ObjectContent findByParent(ManagedObjectReference ref, List<ObjectContent> ocs) {
        if (ref == null) {
            return null;
        }

        for (ObjectContent oc : ocs) {
            ManagedObjectReference obj = oc.getObj();
            if (Objects.equals(ref.getValue(), obj.getValue())) {
                return findByRef(prop(oc, VimNames.PROPERTY_PARENT), ocs);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T prop(ObjectContent oc, String prop) {
        for (DynamicProperty dp : oc.getPropSet()) {
            if (prop.equals(dp.getName())) {
                return (T) dp.getVal();
            }
        }

        return null;
    }

    private List<ObjectContent> retrieveProperties(PropertyFilterSpec spec)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference pc = this.connection.getServiceContent().getPropertyCollector();

        return this.connection.getVimPort().retrieveProperties(pc, Arrays.asList(spec));
    }
}
