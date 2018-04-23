/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.onboarding;

import static com.vmware.photon.controller.discovery.onboarding.organization.StatelessServiceAccessSetupService.STATELESS_SERVICES_FOR_USER_RESOURCE_GROUP;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.services.ResourceEnumerationService;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.StatelessServiceAccessSetupService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectDeletionTaskService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectUpdateTaskService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersConfigAccessService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.BroadcastQueryPageService;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;

/**
 * Helper class that starts onboarding services
 */
public class OnboardingServices {

    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) throws Throwable {
        startServices(host, addPrivilegedService,
                op -> createResourceGroupForDefaultStatelessServices(host, op));
    }

    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService,
            Consumer<Operation> statelessServiceAccessResourceGroup) throws Throwable {
        addPrivilegedService.accept(UserCreationService.class);
        host.startService(new UserCreationService());
        addPrivilegedService.accept(UserUpdateService.class);
        host.startService(new UserUpdateService());
        addPrivilegedService.accept(OrganizationCreationService.class);
        host.startService(new OrganizationCreationService());
        addPrivilegedService.accept(StatelessServiceAccessSetupService.class);
        host.startService(new StatelessServiceAccessSetupService(statelessServiceAccessResourceGroup));
        addPrivilegedService.accept(ServiceUserSetupService.class);
        host.startService(new ServiceUserSetupService());
        addPrivilegedService.accept(AutomationUserSetupService.class);
        host.startService(new AutomationUserSetupService());
        addPrivilegedService.accept(ProjectCreationTaskService.class);
        host.startFactory(ProjectCreationTaskService.class,
                () -> TaskFactoryService.create(ProjectCreationTaskService.class));
        addPrivilegedService.accept(ProjectUpdateTaskService.class);
        host.startFactory(ProjectUpdateTaskService.class,
                () -> TaskFactoryService.create(ProjectUpdateTaskService.class));
        addPrivilegedService.accept(ProjectDeletionTaskService.class);
        host.startFactory(ProjectDeletionTaskService.class,
                () -> TaskFactoryService.create(ProjectDeletionTaskService.class));
    }

    public static void createResourceGroupForDefaultStatelessServices(ServiceHost host, Operation op) {
        // specify the stateless services the user has access to explicitly
        Set<String> documentLinks = new LinkedHashSet<>();
        documentLinks.add(ConfigurationRuleQueryService.SELF_LINK);
        documentLinks.add(PhotonModelAdaptersConfigAccessService.SELF_LINK);
        documentLinks.add(ResourceOperationService.SELF_LINK);

        documentLinks.addAll(Arrays.asList(AWSAdapters.LINKS));
        documentLinks.addAll(Arrays.asList(AWSEnumerationAdapterService.LINKS));

        documentLinks.add(AWSUriPaths.AWS_COMPUTE_DESCRIPTION_CREATION_ADAPTER);
        documentLinks.add(AWSUriPaths.AWS_COMPUTE_STATE_CREATION_ADAPTER);
        documentLinks.add(AWSUriPaths.AWS_NETWORK_STATE_CREATION_ADAPTER);
        documentLinks.add(AWSUriPaths.AWS_SECURITY_GROUP_ENUMERATION_ADAPTER);
        documentLinks.add(AWSUriPaths.AWS_LOAD_BALANCER_ENUMERATION_ADAPTER);

        documentLinks.addAll(Arrays.asList(AzureAdapters.LINKS));
        documentLinks.addAll(Arrays.asList(AzureEaAdapters.LINKS));

        documentLinks.add(AzureUriPaths.AZURE_COMPUTE_ENUMERATION_ADAPTER);
        documentLinks.add(AzureUriPaths.AZURE_STORAGE_ENUMERATION_ADAPTER);
        documentLinks.add(AzureUriPaths.AZURE_NETWORK_ENUMERATION_ADAPTER);
        documentLinks.add(AzureUriPaths.AZURE_DISK_ENUMERATION_ADAPTER);
        documentLinks.add(AzureUriPaths.AZURE_RESOURCE_GROUP_ENUMERATION_ADAPTER);
        documentLinks.add(AzureUriPaths.AZURE_FIREWALL_ENUMERATION_ADAPTER);
        documentLinks.add(AzureUriPaths.AZURE_SUBSCRIPTION_ENDPOINT_CREATOR);
        documentLinks.add(AzureUriPaths.AZURE_SUBSCRIPTION_ENDPOINTS_ENUMERATOR);

        documentLinks.add(VsphereOnPremEndpointAdapterService.SELF_LINK);
        documentLinks.add(ResourcePoolConfigurationService.SELF_LINK);
        documentLinks.add(ResourceEnumerationService.SELF_LINK);
        documentLinks.add(UriPaths.CLOUD_ACCOUNT_API_SERVICE);
        documentLinks.add(UriPaths.RESOURCE_PROPERTIES_SERVICE);
        documentLinks.add(UriPaths.RESOURCE_PROPERTIES_SERVICE_V2);
        documentLinks.add(UriPaths.RESOURCE_LIST_API_SERVICE);
        documentLinks.add(UriPaths.RESOURCE_SUMMARY_API_SERVICE);
        documentLinks.add(UriPaths.RESOURCE_SUMMARY_API_SERVICE_V2);
        documentLinks.add(UriPaths.OPTIONAL_ADAPTER_SCHEDULER);
        documentLinks.add(UriPaths.USERS_API_SERVICE);
        documentLinks.add(UriPaths.NOTIFICATION_LOGGING);

        ResourceGroupState computeResourceGroupState = new ResourceGroupState();
        computeResourceGroupState.documentSelfLink = STATELESS_SERVICES_FOR_USER_RESOURCE_GROUP;
        Query.Builder queryBuilder = Query.Builder.create();
        for (String document : documentLinks) {
            queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, document, Occurance.SHOULD_OCCUR);
        }
        // add a wildcard based clause to support stateless services
        // for web sockets that have the prefix /ws-service/ and for paginated queries that have
        // prefix /core/query-page.
        queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                ServiceUriPaths.WEB_SOCKET_SERVICE_PREFIX, MatchType.PREFIX, Occurance.SHOULD_OCCUR);
        queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                ServiceUriPaths.CORE_QUERY_PAGE, MatchType.PREFIX, Occurance.SHOULD_OCCUR);
        queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriUtils.buildUriPath(ServiceUriPaths.CORE, BroadcastQueryPageService.SELF_LINK_PREFIX),
                MatchType.PREFIX, Occurance.SHOULD_OCCUR);
        queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriPaths.CLOUD_ACCOUNT_QUERY_PAGE_SERVICE, MatchType.PREFIX,
                Occurance.SHOULD_OCCUR);
        queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriPaths.RESOURCE_QUERY_PAGE_SERVICE_V3, MatchType.PREFIX,
                Occurance.SHOULD_OCCUR);
        queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriPaths.RESOURCE_QUERY_PAGE_SERVICE_V4, MatchType.PREFIX,
                Occurance.SHOULD_OCCUR);
        queryBuilder.addClause(Query.Builder.create(Occurance.SHOULD_OCCUR)
                .addClause(Query.Builder.create(Occurance.MUST_OCCUR)
                        .addKindFieldClause(ImageService.ImageState.class)
                        .addFieldClause(QuerySpecification.buildCollectionItemName(
                                ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS), "*",
                                MatchType.WILDCARD, Occurance.MUST_NOT_OCCUR).build()).build());
        computeResourceGroupState.query = queryBuilder.build();
        host.sendRequest(Operation.createPost(host, ResourceGroupService.FACTORY_LINK)
                .setBody(computeResourceGroupState)
                .setReferer(host.getUri())
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        if (postOp.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
                            op.complete();
                            return;
                        }
                        op.fail(postEx);
                        return;
                    }
                    op.complete();
                }));
    }
}
