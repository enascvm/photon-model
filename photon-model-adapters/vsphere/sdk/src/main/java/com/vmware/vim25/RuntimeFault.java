
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RuntimeFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RuntimeFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MethodFault"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RuntimeFault")
@XmlSeeAlso({
    CannotDisableDrsOnClusterManagedByVDC.class,
    CannotDisableDrsOnClustersWithVApps.class,
    ConflictingDatastoreFound.class,
    DatabaseError.class,
    DisallowedChangeByService.class,
    DisallowedOperationOnFailoverHost.class,
    FailToLockFaultToleranceVMs.class,
    InvalidProfileReferenceHost.class,
    LicenseAssignmentFailed.class,
    MethodAlreadyDisabledFault.class,
    MethodDisabled.class,
    OperationDisallowedOnHost.class,
    RestrictedByAdministrator.class,
    ThirdPartyLicenseAssignmentFailed.class,
    VAppOperationInProgress.class,
    HostCommunication.class,
    InvalidArgument.class,
    ManagedObjectNotFound.class,
    InvalidRequest.class,
    NotEnoughLicenses.class,
    NotImplemented.class,
    NotSupported.class,
    RequestCanceled.class,
    SecurityError.class,
    SystemError.class,
    UnexpectedFault.class
})
public class RuntimeFault
    extends MethodFault
{


}
