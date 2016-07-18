
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
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
@XmlType(name = "VirtualDeviceBackingInfo")
@XmlSeeAlso({
    VirtualEthernetCardDistributedVirtualPortBackingInfo.class,
    VirtualEthernetCardOpaqueNetworkBackingInfo.class,
    VirtualPCIPassthroughPluginBackingInfo.class,
    VirtualDeviceFileBackingInfo.class,
    VirtualDevicePipeBackingInfo.class,
    VirtualDeviceURIBackingInfo.class,
    VirtualSerialPortThinPrintBackingInfo.class,
    VirtualSriovEthernetCardSriovBackingInfo.class,
    VirtualDeviceDeviceBackingInfo.class,
    VirtualDeviceRemoteDeviceBackingInfo.class
})
public class VirtualDeviceBackingInfo
    extends DynamicData
{


}
