
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostConnectFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostConnectFault"&gt;
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
@XmlType(name = "HostConnectFault")
@XmlSeeAlso({
    AgentInstallFailed.class,
    AlreadyBeingManaged.class,
    AlreadyConnected.class,
    CannotAddHostWithFTVmAsStandalone.class,
    CannotAddHostWithFTVmToDifferentCluster.class,
    CannotAddHostWithFTVmToNonHACluster.class,
    GatewayConnectFault.class,
    MultipleCertificatesVerifyFault.class,
    NoHost.class,
    NoPermissionOnHost.class,
    NotSupportedHost.class,
    ReadHostResourcePoolTreeFailed.class,
    SSLDisabledFault.class,
    SSLVerifyFault.class,
    TooManyHosts.class
})
public class HostConnectFault
    extends VimFault
{


}
