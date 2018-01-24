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

package com.vmware.photon.controller.model.resources;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.resources.ResourceState.TagInfo;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class ResourceUtils {

    /**
     * Optional link fields in resources cannot be cleared with a regular PATCH request because the
     * automatic merge just ignores {@code null} fields from the PATCH body, for optimization
     * purposes.
     *
     * This constant can be used instead of a {@code null} value. It is applicable only for
     * {@link PropertyUsageOption#LINK} fields that are marked with
     * {@link PropertyUsageOption#AUTO_MERGE_IF_NOT_NULL} flag in the resource state document.
     */
    public static final String NULL_LINK_VALUE = "__noLink";

    /**
     * This method handles merging of state for patch requests. It first checks to see if the
     * patch body is for updating collections. If not it invokes the mergeWithState() method.
     * Finally, users can specify a custom callback method to perform service specific merge
     * operations.
     *
     * <p>If no changes are made to the current state, a response code {@code NOT_MODIFIED} is
     * returned with no body. If changes are made, the response body contains the full updated
     * state.
     *
     * @param op Input PATCH operation
     * @param currentState The current state of the service
     * @param description The service description
     * @param stateClass Service state class
     * @param customPatchHandler custom callback handler
     */
    public static <T extends ResourceState> void handlePatch(Service service, Operation op, T
            currentState, ServiceDocumentDescription description, Class<T> stateClass,
            Function<Operation, Boolean> customPatchHandler) {
        try {
            boolean hasStateChanged;
            final Set<String> originalTagLinks = currentState.tagLinks != null
                    ? new HashSet<>(currentState.tagLinks)
                    : null;

            // apply standard patch merging
            EnumSet<Utils.MergeResult> mergeResult =
                    Utils.mergeWithStateAdvanced(description, currentState, stateClass, op);
            hasStateChanged = mergeResult.contains(Utils.MergeResult.STATE_CHANGED);

            if (!mergeResult.contains(Utils.MergeResult.SPECIAL_MERGE)) {
                T patchBody = op.getBody(stateClass);

                // apply ResourceState-specific merging
                hasStateChanged |= ResourceUtils.mergeResourceStateWithPatch(currentState,
                        patchBody);

                // handle NULL_LINK_VALUE links
                hasStateChanged |= nullifyLinkFields(description, currentState, patchBody);
            }

            // apply custom patch handler, if any
            if (customPatchHandler != null) {
                hasStateChanged |= customPatchHandler.apply(op);
            }

            if (!hasStateChanged) {
                op.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED);
            }

            // populate tags if tag links have changed
            if (!Objects.equals(originalTagLinks, currentState.tagLinks)) {
                populateTags(service, currentState).thenAccept(__ -> {
                    op.setBody(currentState);
                }).whenCompleteNotify(op);
            } else {
                op.setBody(currentState);
                op.complete();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            op.fail(e);
        }
    }

    /**
     * @deprecated Use the handlePatch overload with a service arg
     */
    @Deprecated
    public static <T extends ResourceState> void handlePatch(Operation op, T
            currentState, ServiceDocumentDescription description, Class<T> stateClass,
            Function<Operation, Boolean> customPatchHandler) {
        handlePatch(null, op, currentState, description, stateClass, customPatchHandler);
    }

    /**
     * Updates the state of the service based on the input patch.
     *
     * @param source currentState of the service
     * @param patch patch state
     * @return whether the state has changed or not
     */
    private static boolean mergeResourceStateWithPatch(ResourceState source,
            ResourceState patch) {
        boolean isChanged = false;

        // tenantLinks requires special handling so that although it is a list, duplicate items
        // are not allowed (i.e. it should behave as a set)
        if (patch.tenantLinks != null && !patch.tenantLinks.isEmpty()) {
            if (source.tenantLinks == null || source.tenantLinks.isEmpty()) {
                source.tenantLinks = patch.tenantLinks;
                isChanged = true;
            } else {
                for (String e : patch.tenantLinks) {
                    if (!source.tenantLinks.contains(e)) {
                        source.tenantLinks.add(e);
                        isChanged = true;
                    }
                }
            }
        }

        return isChanged;
    }

    /**
     * Nullifies link fields if the patch body contains NULL_LINK_VALUE links.
     */
    private static <T extends ResourceState> boolean nullifyLinkFields(
            ServiceDocumentDescription desc, T currentState, T patchBody) {
        boolean modified = false;
        for (PropertyDescription prop : desc.propertyDescriptions.values()) {
            if (prop.usageOptions != null &&
                    prop.usageOptions.contains(PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) &&
                    prop.usageOptions.contains(PropertyUsageOption.LINK)) {
                Object patchValue = ReflectionUtils.getPropertyValue(prop, patchBody);
                if (NULL_LINK_VALUE.equals(patchValue)) {
                    Object currentValue = ReflectionUtils.getPropertyValue(prop, currentState);
                    modified |= currentValue != null;
                    ReflectionUtils.setPropertyValue(prop, currentState, null);
                }
            }
        }
        return modified;
    }

    public static void handleDelete(Operation op, StatefulService service) {
        service.logInfo("Deleting document %s, Operation ID: %d, Referrer: %s",
                op.getUri().getPath(), op.getId(), op.getRefererAsString());

        ServiceDocument currentState = service.getState(op);

        // If delete request specifies the document expiration time then set that, otherwise
        // by default set the expiration to one month later.
        if (op.hasBody()) {
            ServiceDocument opState = op.getBody(ServiceDocument.class);

            if (opState.documentExpirationTimeMicros > 0) {
                currentState.documentExpirationTimeMicros = opState.documentExpirationTimeMicros;
            }
        } else {
            currentState.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + TimeUnit.DAYS.toMicros(31);
        }

        op.complete();
    }

    /**
     * Populates the resource tags based on the tag links.
     */
    public static <T extends ResourceState> DeferredResult<Void> populateTags(Service service,
            T currentState) {
        if (currentState.tagLinks == null) {
            currentState.expandedTags = null;
            return DeferredResult.completed(null);
        }

        if (service == null) {
            return DeferredResult.completed(null);
        }

        List<DeferredResult<TagState>> tagGetDrs = currentState.tagLinks.stream()
                .map(tagLink -> {
                    Operation tagGetOp = Operation.createGet(service.getHost(), tagLink);
                    return service.sendWithDeferredResult(tagGetOp, TagState.class)
                        .exceptionally(e -> {
                            // just log and ignore errors
                            service.getHost().log(Level.WARNING, "Error expanding tag %s in "
                                    + "resource %s: %s", tagLink, currentState.documentSelfLink,
                                    e.getMessage());
                            return null;
                        });
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(tagGetDrs)
                .handle((tags, e) -> {
                    if (e != null) {
                        service.getHost().log(Level.WARNING, "Error populating tags in "
                                + "resource %s: %s", currentState.documentSelfLink, e.getMessage());
                        currentState.expandedTags = null;
                    } else {
                        currentState.expandedTags = tags.stream()
                                .filter(Objects::nonNull)
                                .map(ResourceUtils::tagStateToTagInfo)
                                .collect(Collectors.toList());
                    }
                    return (Void)null;
                });
    }

    private static TagInfo tagStateToTagInfo(TagState tagState) {
        TagInfo tagInfo = new TagInfo();
        tagInfo.tag = encodeTag(tagState.key, tagState.value);
        return tagInfo;
    }

    private static String encodeTag(String key, String value) {
        return (key != null ? key : "")
                + TagInfo.KEY_VALUE_SEPARATOR
                + (value != null ? value : "");
    }
}
