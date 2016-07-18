
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostConfigFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostConfigFault"&gt;
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
@XmlType(name = "HostConfigFault")
@XmlSeeAlso({
    AdminDisabled.class,
    AdminNotDisabled.class,
    BlockedByFirewall.class,
    ClockSkew.class,
    DisableAdminNotSupported.class,
    HostConfigFailed.class,
    HostInDomain.class,
    InvalidHostName.class,
    NoGateway.class,
    NasConfigFault.class,
    NoVirtualNic.class,
    PlatformConfigFault.class,
    VmfsMountFault.class
})
public class HostConfigFault
    extends VimFault
{


}
