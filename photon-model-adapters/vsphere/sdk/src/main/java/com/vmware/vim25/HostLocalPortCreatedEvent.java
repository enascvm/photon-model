
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostLocalPortCreatedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostLocalPortCreatedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostLocalPort" type="{urn:vim25}DVSHostLocalPortInfo"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostLocalPortCreatedEvent", propOrder = {
    "hostLocalPort"
})
public class HostLocalPortCreatedEvent
    extends DvsEvent
{

    @XmlElement(required = true)
    protected DVSHostLocalPortInfo hostLocalPort;

    /**
     * Gets the value of the hostLocalPort property.
     * 
     * @return
     *     possible object is
     *     {@link DVSHostLocalPortInfo }
     *     
     */
    public DVSHostLocalPortInfo getHostLocalPort() {
        return hostLocalPort;
    }

    /**
     * Sets the value of the hostLocalPort property.
     * 
     * @param value
     *     allowed object is
     *     {@link DVSHostLocalPortInfo }
     *     
     */
    public void setHostLocalPort(DVSHostLocalPortInfo value) {
        this.hostLocalPort = value;
    }

}
