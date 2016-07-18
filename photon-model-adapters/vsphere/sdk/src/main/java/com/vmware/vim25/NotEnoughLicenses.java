
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NotEnoughLicenses complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NotEnoughLicenses"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}RuntimeFault"&gt;
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
@XmlType(name = "NotEnoughLicenses")
@XmlSeeAlso({
    ExpiredFeatureLicense.class,
    FailToEnableSPBM.class,
    HostInventoryFull.class,
    InUseFeatureManipulationDisallowed.class,
    IncorrectHostInformation.class,
    InvalidEditionLicense.class,
    InventoryHasStandardAloneHosts.class,
    LicenseDowngradeDisallowed.class,
    LicenseExpired.class,
    LicenseKeyEntityMismatch.class,
    LicenseRestricted.class,
    LicenseSourceUnavailable.class,
    NoLicenseServerConfigured.class,
    VmLimitLicense.class,
    VramLimitLicense.class
})
public class NotEnoughLicenses
    extends RuntimeFault
{


}
