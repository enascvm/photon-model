
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestOperationsFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestOperationsFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
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
@XmlType(name = "GuestOperationsFault")
@XmlSeeAlso({
    GuestAuthenticationChallenge.class,
    GuestComponentsOutOfDate.class,
    GuestMultipleMappings.class,
    GuestOperationsUnavailable.class,
    GuestPermissionDenied.class,
    GuestProcessNotFound.class,
    GuestRegistryFault.class,
    InvalidGuestLogin.class,
    OperationDisabledByGuest.class,
    OperationNotSupportedByGuest.class,
    TooManyGuestLogons.class
})
public class GuestOperationsFault
    extends VimFault
{


}
