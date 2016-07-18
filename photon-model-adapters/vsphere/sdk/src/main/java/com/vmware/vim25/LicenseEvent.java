
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LicenseEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LicenseEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
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
@XmlType(name = "LicenseEvent")
@XmlSeeAlso({
    ServerLicenseExpiredEvent.class,
    HostLicenseExpiredEvent.class,
    VMotionLicenseExpiredEvent.class,
    NoLicenseEvent.class,
    LicenseServerUnavailableEvent.class,
    LicenseServerAvailableEvent.class,
    InvalidEditionEvent.class,
    HostInventoryFullEvent.class,
    LicenseRestrictedEvent.class,
    IncorrectHostInformationEvent.class,
    UnlicensedVirtualMachinesEvent.class,
    UnlicensedVirtualMachinesFoundEvent.class,
    AllVirtualMachinesLicensedEvent.class,
    LicenseNonComplianceEvent.class
})
public class LicenseEvent
    extends Event
{


}
