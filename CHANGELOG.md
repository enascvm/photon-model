# CHANGELOG

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
