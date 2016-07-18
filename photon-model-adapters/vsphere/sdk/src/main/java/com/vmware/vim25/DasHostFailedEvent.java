
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DasHostFailedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DasHostFailedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="failedHost" type="{urn:vim25}HostEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DasHostFailedEvent", propOrder = {
    "failedHost"
})
public class DasHostFailedEvent
    extends ClusterEvent
{

    @XmlElement(required = true)
    protected HostEventArgument failedHost;

    /**
     * Gets the value of the failedHost property.
     * 
     * @return
     *     possible object is
     *     {@link HostEventArgument }
     *     
     */
    public HostEventArgument getFailedHost() {
        return failedHost;
    }

    /**
     * Sets the value of the failedHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostEventArgument }
     *     
     */
    public void setFailedHost(HostEventArgument value) {
        this.failedHost = value;
    }

}
