# CHANGELOG

## 0.4.10-SNAPSHOT

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
