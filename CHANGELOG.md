# CHANGELOG

## 0.6.49_2
* Improved scheduled task handling.

## 0.6.49
* Populating azure managed disk links for Azure computes.
* Fixes related to azure bills.
* Upgrade xenon to version 1.6.2.
* Populate creation time for resource documents.
* Modified VM_HOST type to ENDPOINT_HOST for endpoint computes.
* Improvements and fixes for Azure,AWS test failures.

## 0.6.48
* Deprecate the endpointLink for the resources where
  de-duplication is already supported.
* Important bug fixes in AWS security group enumeration.
* Enable ARN-based credentials in AWS Clients.
* Refactor TagState's logic around external/internal property.

## 0.6.47
* DeDup feature: Resources enumerated only once for 
  same endpoints, IAM accounts added multiple times.

## 0.6.46
* Delete stale TagState resources.
* Important bug fixes.

## 0.6.45
* Added groomer task for deleting stale TagState resources.
* 'computeHostLink' is introduced for AWS and Azure resources.
* Support added to enumerate AWS Images.

## 0.6.44

* [Utility] Introduced utility methods to build OData expressions.
* Refactor auth credentials lookup to use inventory service if provided.
* [AWS] Added validation on min and max size of io1 type of disks.
* [vsphere] Use TOP_RESULTS when detecting existing resources.
* Introduce "computeHostLink" attribute for resources.
* [vSphere] Set the region id on discovered subnets.
* [vsphere] Pass regionId explicitly.

## 0.6.43
* Azure provisioning adapter synchronous SDK calls now run in a dedicated threadpool.
* Added inventory service to cluster utils.
* Added Azure region enumeration adapter.
* Moved large fields of DiskState into a new object - ContentState.
* vSphere enumeration adapter now enumerates all visible datacenters.

## 0.6.42
* Refactored photon-model adapters to use common QueryUtils.
* Allow detaching non-boot EBS volume from running AWS VM.
* Added region enumeration adapter service for AWS.

## 0.6.41
* Changes related to naming of internal tags for resources.
  Key for internal tag 'type' is changed to 'vmware.enumeration.type'.

## 0.6.40
* Critical fixes related to AWS and Azure costs computation.

## 0.6.39
* Added support to assign vSphere VM Network Interface to security groups.
* Upgraded xenon to v1.5.7

## 0.6.38
* Downgrade Xenon to v1.5.4-CR4.

## 0.6.37
* Added service to attach/detach disk to vSphere VMs.
* Added Azure Load Balancer provisioning.
* Added service to attach EBS volume to AWS VMs.
* Added service to manage disk attachment for Azure VMs.
* Added ability to delete an AWS disk.

## 0.6.36
* Added disk adapter to create an ebs volume on aws.
* Added Vsphere disk adapter enhancements.
* Added Azure disk adapter to create disks on Azure.
* Added support for Azure custom images while provisioning.
* Upgrade Xenon to v1.5.5.
* Wrapped Azure SDK synchronous calls into executors.
* Added logic to fail operations after throwing exceptions.

## 0.6.35
* Added enhancements for Azure provisioning.

## 0.6.34
* Upgrade Xenon to v1.5.4-CR2

## 0.6.33
* Upgraded AWS SDK version to 1.11.170.
* Added Set 'endPointLinks' in ResourceState.
* Set azure-client-* library to latest version 1.0.4.
* Added 'Resize Compute' operation for vSphere compute resources.

## 0.6.32
* Adding document option INDEX_METADATA to services.

## 0.6.31
* Upgrade to xenon v1.5.4.
* Added support to create internal tag 'type' for AWS network resources.
* Added support to create internal tag 'type' for Azure VHDs.
* Added "Outbound access" support for private subnets to create/delete a NAT gateway.

## 0.6.30

* Fix NPE in AWS Cost Stats service.
* Reverting QueryOption.INDEXED_METADATA added to queries.
* Reverting document option INDEX_METADATA added to services.

## 0.6.29

* Adding QueryOption.INDEXED_METADATA to queries.
* Adding document option INDEX_METADATA to services.

## 0.6.28

* Upgrading xenon to 1.5.3.
* Adding S3Proxy for AWS Mock.

## 0.6.27
* Added 'Revert to Snapshot' operation for vSphere compute resources.

## 0.6.26
* Added support to create internal tag 'type' for AWS storage and disk resources.
* Added support to create internal tag 'type' for Azure compute and network resources.
* Added 'Delete Snapshot' operation for vSphere compute resources.

## 0.6.25
* Added 'Create Snapshot' operation for vSphere compute resources.
* Added '/resources/routers' service under photon-model to data-collect NSX-T logical routers.
* Added support to create 'type' tag for AWS computes.

## 0.6.24
* Added services to allocate and deallocate IP addresses.
* Added support for 'Endpoint' request type for profile UI.

## 0.6.23
* Added support to set custom IP address for Azure VM.
* Added support to update LB configuration for AWS.
* Added support to retrieve hostName for VC vms.

## 0.6.22

* [AWS] Added S3 enumeration support.
* Assign 'regionId' for computes of type 'Zone'.
* Fixed ComputeDescription's disk and network links, not to be duplicated on patch.
* Removed case insensitive indexing option from regionId.
* Enabled 'SORT' on ResourceState customProperties.

## 0.6.21

* Mark regionId and creationTime fields as sortable.
* Added HTTPS listener support for AWS.
* [Azure] Added SecurityGroupInstanceAdapterService.
* Removed deprecated fields from LoadBalancerState.

## 0.6.20
* Added service to fetch list of instance types supported by AWS.
* Added support in Azure adapter to provision VM from private image.
* Removed AWS S3 enumeration.
* Added multiple listeners and healthcheck configuration in AWS.

## 0.6.19
* Enhanced ResourceOperationSpec with:
  * optional 'schema' element which describes the structure of the payload expected at runtime
  when request a resource operation
  * optional 'extensions' map, which purpose is to enable contributor to specify additional
  meta-data to the resource operation specification. Such additional meta-data could be for
  example UI related information like icon, custom UI, etc, so that interested parties can
  leverage this mata-data and process accordingly (visualize the icon, UI, etc)
* Added retention limit to stateful service documents.
* Added support to add azure storage account type in storage description and
  also to populate the encryption status of the storage account.
* Added support for ResourceOperationSpec extensions.
* Added filtering for ResourceGroups by type.
* Added enrollment number of an Azure EA account as a custom property to its compute state.
* Introduced networkName to LoadBalancerDescription.
* Added support for ICMP protocol for SecurityGroup Rules.
* Added endpoint link in storage policy enumeration.
* Added support to remove stale AWS Network and Subnet states.
* Added support to discover 'storageType' for AWS S3 resources.

## 0.6.18
* Add 'type' category field for Network resources.
* Enhanced resource operations service to accept operation as qparam.

## 0.6.17

* Upgrade to xenon 1.5.0
* Add support to add Azure EA endpoints.
* Collect hourly stats from azure tables instead of latest value.
* Linked accounts' query is scoped to Primary account's tenantLinks.
* [Azure] Adding stats service for costing data.
* Add creationTimeMicros for VM_HOST computes.
* Upgrade Azure SDK to 1.0.0.
* Restrict endpoint deletion to only via EndpointRemovalTask.
* Move 'regionId' from individual resource level to base resource.
* Move creationTime field from individual resources to base resource.
* ImageState is extended to enumerate the properties of each disk configured in the image.

## 0.6.16

* Delete Private images upon endpoint deletion.
* Removed ResourceAggregateMetricService.
* Storage description encryption support.
* Added Day 2 Resource Operation for Restart and Suspend in Azure using ResourceOperationSpecService.
* Add ability to run cost adapters remotely, will query and post resources
  to a remote photon-model specified as system property.
* Enhance DiskState with imageLink pointing to the ImageState to be used to
  to create an instance of this disk. Set either this property in case
  the ImageState is already present in the system (as a result of image enumeration)
  or set current sourceImageReference to point to the native/raw image.

## 0.6.15

* Change serviceSelfLink to serviceURI in ServiceTaskCallback
* Add resourcelink as custom property in Resource-metrics document
* Always create linked account computes
* Change EndpointAdapterUtils.registerEndpointAdapters so that it is 
  possible to register custom adapters as well, not only those declared 
  in UriPaths.AdapterTypePath.
* Introduce data schema and data field to allow schema definition
* Introducing LoadBalancerDescription

## 0.6.14

* Enable sorting for endpoint type's in EndpointState
* AutoDiscover linked accounts for the configured primary accounts
* Added support for Power On and Off operations for Azure adapter.
* Add adapters information to the ComputeStates
* VSYM-5810: Adding capability to create computes for Subscriptions under Azure EA account to store the cost stats against them.
* Bump Bouncycastle dependency to 1.56
* Unify ComputeState and ComputeDescription content during enumeration

## 0.6.13

* Upgrading to xenon-1.4.2.
* [Azure] Added Azure subnet service.
* [Azure] Image enumeration supports a predefined list of well-defined images.
* Syncing added during OVF deploy and replication.
* Additional attributes accepted for DiskState for more specificity.
* [AWS/Azure] Introduction of several long-running E2E tests for stats and enumeration.

## 0.6.12

* Add tenantLinks to the EndpointConfigRequest.
* Allow QueryTask to use SELECT_LINKS for EndpointState documents.
* Add description field to ResourceState.
* Support self-signed certificates in vSphere.
* Handle cost of reserved instance in AWS.
* Enhance StatsCollection to accept multiple responses from adapters.

## 0.6.11

* Upgrading to AWS SDK v1.11.105.
* Updates to metric creation and reading, if a metric cluster is provided.
* Add permission validation during endpoint validation.
* Separate MetricServices from PhotonModelServices.
* Implement Public-Private images enumeration.
* [vsphere] Set parentLink on resourcePools during enumeration.
* Retrieve ssl certificate for host behind a proxy.
* Cache invalid credentials for EC2 clients.
* Refactor adapters to use TaskManager to patch back Task Services.
* Introduce explicit method to get instance of ServerX509TrustManager and another one to invalidate it.

## 0.6.10

* Change default collection interval for AWS to 1 hour.
* Implement Azure image enumeration adapter.
* [vsphere] Enumerate template VMs as images.
* Do no add NOT_OCCUR if externalResource is empty.

## 0.6.9

* Bug fixes.
* [Azure] Delete tag links for vNet and Virtual Machine
* Rollback of endpoint creation if adapter configuration fails.
* Correcting check for adding current month's resource stats
* Optimize cloud watch client usage
* Upgrading to com.jcraft.jsch-0.1.54 to address CVS-2016-5725.
* Added AWSSubnetService.
* Reduce the number of metrics collected from AWS and Azure
* Fail endpoint removal task if endpoint state removal fails.
* Add e2e test case for AWSCostStatsService

## 0.6.8

* Upgrading to xenon-1.4.1.
* Paginate querying instances in Aws Cost adapter
* Update JaCoCo plugin.
* Update context so that we don't auto-discover bill bucket each month during first data-collection.
* Adding logger statements to identify who/what deleted the resources.
* Fix issue with error after restarting, vSphere endpoint computeDescription is not valid.
* Update estimated charges metric.

## 0.6.7

* Upgrading to xenon-1.4.0.
* Allow auto-merge link fields to be cleared by a PATCH request
* Implement AWS image enumeration adapter
* Add linked endpoint property to the vSphere endpoint.
* Reconfigure NICs after clone
* [Azure] Fix provisioning without specified storage account name
* [PhM] Enable tag state links deletion in the BaseEnumeration Context
* [vsphere] Set resourcePool only on initial discovery
* [AWS] Optimizations in AWS photon model cost adapter

## 0.6.6

* Add optional notification to the parent task to ProvisionSubnetTaskService.
* Set a default query task timeout of 1 minute
* Remove call to validate cloudwatch creds
* Convert synchronous Azure calls to async
* Region made optional for vSphere enumeration
* [vsphere] Build stable links based on endpoint
* Add support for certificate validation when adding an endpoint
* [AWS] Region Id should not be required during endpoint creation
* [vsphere] Enable ResourcePools as placement targets
* [Azure] Enumerate tags for VM, Network and SecurityGroup resources
* [Azure] Create Public IP conditionally based on NICDesc.assignPublicIP property
* [vsphere] Enumerate ResourcePools as Computes
* Added SubnetState.instanceAdapterReference property.
* Introduced LifecycleState enumeration and added it as SubnetState.lifecycleState property.

## 0.6.5

* Deprecating unused currency/cost fields in resource pool.
* [vsphere] Create dvPortgroups in a DVS.
* Updating documentExpiration on ResourceMetrics and ResourceAggregateMetrics to 6 hours.
* [AWS] Enumerate tags in Networks and Subnets.
* [Azure] Mark Gateway subnet with special custom property.
* Fix concurrency issue in ResourcePoolQueryHelper.
* Fix possible concurrent issue in AWS adapter.

## 0.6.4

* Add regionId to DiskState.
* Use default maven repository.
* Turn off PATCH auto merge of primitive fields in ComputeDescription.
* Compute only the estimated charges for compute host.

## 0.6.3

* Make regionId an optional parameter when defining an endpoint
  for AWS. If not specified the enumeration operation will go
  across all regions.
* Fix tenantLinks clause in QueryUtils.
* Make authCredentialsLink non mandatory field in SecurityGroupState.
* Add support to extend ResourcePools seach criteria.

## 0.6.2

* [AWS] Add, remove or update NetworkInterfaceState when updating ComputeState.
* [AWS] Fix tenantLinks usage.
* Make authCredentialsLink in NetworkState not a required field.
* Handle duplicate secondary account.
* [Azure]Fix validate virtual gateways.
* Use pagination in ResourcePoolQueryHelper.
* [AWSCostAdapter] Improve the Account Compute State query.
* Distinguish availability zone computeState from host.

## 0.6.1

* Upgrading xenon to 1.3.6. Introducing xenon-utils in root pom.
* Fix the environmentName validation on PUT in ComputeState.
* [Azure] Disable gateway enumeration.
* [AWS] Create AWS Subnets (if not exist) during VM provisioning.
* Query fixes.
* [vsphere] Option to set the guestId.
* [AWS] Use SecurityGroup IDs instead of Names during provisioning.
* Last rollup time lookup optimzation.
* [Azure] Fix tenantLinks usage.

## 0.6.0

* Adding endpointLink to network and subnet lookup query.
* [vsphere] Enumerate opaque networks.
* [AWS] Use existing SecurityGroups or create them in case they dont exist.
* [vsphere] Attach nics to opaque networks.

## 0.5.9

* Introduce a field environment name in ComputeState. This field is used to
  represent cloud providers e.g. AWS , Azure etc.
* Modifying result limit for Photon Model queries.
* Mark Subnet as supporting public IP and introduce NIC requirement for public IP.
* Add missing tenant links to query tasks.
* Fixing Endpoint removal.

## 0.5.8

* Introduced an optional SubnetState.zoneId field.
* firewallLinks in NetworkInterfaceState and NetworkInterfaceDescription are
marked as deprecated. Use securityGroupLinks instead.
* Removing resources when deleting Azure or AWS endpoints.
* [vsphere] Support absolute URIs to disks in OVF
* [AWS] Enumerate Security Groups
* [AWS] During VM provisioning lookup VPC-Subnet per NIC
* Added batching while persisting raw metrics
* Add missing field name constant in ComputeState
* Map NetworkInterfaceStates to AWS NICs by DeviceIndex
* Add validation for subnetLink for NetworkInterfaceState

## 0.5.7

* Updated xenon to 1.3.5.
* Azure Provisioning uses existing Subnets.
* Added AzureSecurityGroupEnumerationService to enumerate Azure Security Groups.
* Tag Azure NetworkStates with link to correct ResourceGroupState.
* vSphere - Fail fast if datacenter is not configured.
* FirewallService marked as deprecated. Use SecurityGroupService instead.

## 0.5.6

* Add support for subnet enumeration in Azure.
* Introduce a field compute type in ComputeState.
* Enumerate Aws availability zones as a compute. These are used for VM placements.
* Add a new service in vSphere to enumerate datacenters before endpoint creation.
* Add fixes in AWS Network enumeration to ensure that security groups and subnets
belong to the same VPC.
* Fix default provisioning in AWS when no NICs are provided.
* Delete related entities when deleting computes during vSphere enumeration.
* Updating xenon to 1.3.4.

## 0.5.5

* Various enumeration query update to cap max results at 10000.
* AWS subnets discovery.
* Azure network discovery.
* Azure storage container discovery.
* Bug fixes and improvements.

## 0.5.4

* Updated xenon to 1.3.3.

* Add resultLimit to raw metrics look up.

## 0.5.3

* Updated xenon to 1.3.2.

## 0.5.2

* Fixed the getRawMetrics query in SingleResourceStatsAggregationTaskService.

## 0.5.1

* Updated xenon to 1.3.1.

* Add storage utilization metric for Azure disks

* Setting default expiration times in SingleResourceStatsCollectionTaskService and
  SingleResourceStatsAggregationTaskService to 10 minute.

* Implementing a default data rentention model for ResourceMetrics and ResourceAggregateMetrics

* Update ScheduleTaskService to run in context of the specified user

* Introduce new field `type` on ComputeState, to explicitly define the type of the Compute.

* Introduce NetworkInterfaceDescription, to represent the desire state of a NetworkInterface.
  ComputeDescription is extended to have a list on NetworkInterfaceDescriptions, e.g. the desire
  state of NICs for the requested compute. Each NetworkInterface has a link to the
  NetworkInterfaceDescription, which it is based on.

* Introduce Subnet as first class concept in the model. The Subnet represents sub network part of a
  given network, with it's IP address range, gateway and list of DNS servers.
  NetworkInterface is extend to have a link to Subnet it is attached to.

## 0.5.0

* Changed collection interval in AWS Stats adapter to 5 minutes.

* Updated xenon to 1.3.0.

* Consolidate raw metric storage into a single document.

## 0.4.19

* Updated xenon to 1.2.0

* Fixing issues with endpoint deletion.

* Performance improvements to stats aggregation.

## 0.4.18

* Bug Fixes - Fixed a regression in AWSAdapters to bind the adapter references to LOCALHOST.

* Numeric fields in ResourcePoolState switched from long to Long with
 PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL enabled so that they can be changed through a
 PATCH request.

## 0.4.17

* Changing ResourceMetricService and ResourceAggregateMetricService to be IMMUTABLE.

* Rename DiskState.datacenterId to regionId for symmetry with ComputeDescription.

* ResourceEnumerationTaskService will pass `TaskOptions.PRESERVE_MISSING_RESOUCES` to adapters, if
 specified. If specified, the adapters `must` not delete local ComputeStates in case remote instance
 is missing, but mark ComputeState's `lifecycleState` field to `LifecycleState.RETIRED`.

* Switch ResourceEnumerationTaskService to use TaskOptions.IS_MOCK and
 TaskOptions.SELF_DELETE_ON_COMPLETION, in place of separate boolean fields `isMockRequest` and
 `deleteOnCompletion` (breaking change).

* Introduce two new TaskOptions, PRESERVE_MISSING_RESOUCES and SELF_DELETE_ON_COMPLETION.

* Introduce new field `lifecycleState` of type `LifecycleState` on ComputeState.

## 0.4.16

* Optimize SingleResourceStatsAggregationTaskService to reduce the
number of queries issued - A single query is issued per resource
as opposed to one query per metric

## 0.4.15

* Remove `ComputeState.networkId`: relationship to a network is expressed
  with a `networkInterfaceLink`

## 0.4.14

* Rename NetworkState.networkDescriptionLink to networkLink

* Remove NetworkState.networkBridgeLink (breaking change)

## 0.4.13

* Move to xenon version 1.0.0

## 0.4.12

* Bug Fixes - Fixing the burn rate calculation in the AWS Stats
  service.

## 0.4.11

* AWS Cost Stats Service implementation based on detailed bill report.

## 0.4.10

* Bug fixes

## 0.4.9

* Support for latest value aggregation and per metric aggregation type
  in SingleResourceStatsAggregationTask.

## 0.4.8

* Bug fixes - Various minor fixes including updating
  EndpointAllocationTaskService to kick off enumeration
  at the right intervals, fix computation errors in
  the vSphere adapter for empty clusters and noisy log
  messages supressed

## 0.4.7

* The stats URI for computes available at /compute/stats
no longer have the raw metrics (CPU utilization, memory) etc
available at this endpoint. The only in-memory metric available
at this URI will be the last successful collection time.

## 0.4.6

* Support a query to define resource group membership

## 0.4.5

* Support a query to resolve resource pool membership

* Field `datacenterId` is removed from ComputeDescription. Adapters using it,
  should move to use `regionId`.

* Rename ComputeSubTaskService to SubTaskService to reflect its functionality
  better

* Remove ResourceStatsAggregationTaskService and merge its functionality
  with StatsAggregationTaskService

* Remove ResourceAggregateMetricsService as it has been replaced by
ResourceAggregateMetricService

* Support a query in StatsAggregationTaskService to identify resources to aggregate

## 0.4.4

* Support multiple stats adapter per compute. Also enhance
  StatsCollectionTaskState to accept a stats adapter reference. This
  will allow to collect stats for specific stats adapter.
* Support historical aggregation of stats data

## 0.4.3

* Add support for adding and removing collections from resource services

## 0.4.2

* Add new endpoint resource and task services.

* Add support for aggregating metrics to get daily min/max/avg values over 4 weeks.

* Remove StatsCollectionTaskSchedulerService - The functionality this class provided
  can be achieved using ScheduledTaskService.

* Remove computeDescriptionLink from ComputeEnumerateResourceRequest and
  ResourceEnumerationTaskState. The description is reachable through the
  ComputeState.

* Add support for query based resource stats aggregation.

## 0.4.1

* Add support for aggregating resource metrics to get hourly min/max/avg values

* Unify the field names for calling task reference and target resource in all Request object.
  The new names are `taskReference` and `resourceReference`.
  This change is **breaking change** for existing Providers implementations.

* Unify in all Provider's Request object to use URIs as a reference to calling
  task and target resource

## 0.4.0

* Add ResourceState as a base class for all photon model resource states

* Add ResourceGroupService to represent a group of resources

* Providers should use ComputeState's id field is used to store the resource
  external identifier.
  
* New field instanceType is introduced in ComputeDescription to specify instance type,
  as understood by the providers.

* Add ScheduledTaskService to run tasks periodically

* Remove all template factory services from resource services,
  replace them with concise FactoryService.createFactory() pattern

* Resource services (the model) no longer enforce that the self link
  is derived from the id field in the initial state. The resource allocation
  task still creates links that match the id, but that is no longer
  validated or required by the resources.

* Refactored package structure in photon-azure-adapter project from
  com.vmware.photon.controller.model.adapters.azureadapter.\* to
  com.vmware.photon.controller.model.adapters.azure.\*.

## 0.3.2

* AWS stats service implementation

* Handle OData queries in ComputeService

## 0.3.1

* Make resource service factories idempotent

* Add abstractions for monitoring service

## 0.3.0

* Move build process to maven

* Use junit for testing

* Refactor network and firewal services to move state away from their
task services

* Follow coding conventions laid out by the xenon project

* Add additional tests for the AWS adapter
 
## 0.2.2

* Start of CHANGELOG. See commit history.
