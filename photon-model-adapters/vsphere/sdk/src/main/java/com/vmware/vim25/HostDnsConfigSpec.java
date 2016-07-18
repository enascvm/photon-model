
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostDnsConfigSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostDnsConfigSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostDnsConfig"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="virtualNicConnection" type="{urn:vim25}HostVirtualNicConnection" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostDnsConfigSpec", propOrder = {
    "virtualNicConnection"
})
public class HostDnsConfigSpec
    extends HostDnsConfig
{

    protected HostVirtualNicConnection virtualNicConnection;

    /**
     * Gets the value of the virtualNicConnection property.
     * 
     * @return
     *     possible object is
     *     {@link HostVirtualNicConnection }
     *     
     */
    public HostVirtualNicConnection getVirtualNicConnection() {
        return virtualNicConnection;
    }

    /**
     * Sets the value of the virtualNicConnection property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostVirtualNicConnection }
     *     
     */
    public void setVirtualNicConnection(HostVirtualNicConnection value) {
        this.virtualNicConnection = value;
    }

}
