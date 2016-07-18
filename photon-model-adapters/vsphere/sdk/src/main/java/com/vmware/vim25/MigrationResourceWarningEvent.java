
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MigrationResourceWarningEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MigrationResourceWarningEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MigrationEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="dstPool" type="{urn:vim25}ResourcePoolEventArgument"/&gt;
 *         &lt;element name="dstHost" type="{urn:vim25}HostEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MigrationResourceWarningEvent", propOrder = {
    "dstPool",
    "dstHost"
})
public class MigrationResourceWarningEvent
    extends MigrationEvent
{

    @XmlElement(required = true)
    protected ResourcePoolEventArgument dstPool;
    @XmlElement(required = true)
    protected HostEventArgument dstHost;

    /**
     * Gets the value of the dstPool property.
     * 
     * @return
     *     possible object is
     *     {@link ResourcePoolEventArgument }
     *     
     */
    public ResourcePoolEventArgument getDstPool() {
        return dstPool;
    }

    /**
     * Sets the value of the dstPool property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResourcePoolEventArgument }
     *     
     */
    public void setDstPool(ResourcePoolEventArgument value) {
        this.dstPool = value;
    }

    /**
     * Gets the value of the dstHost property.
     * 
     * @return
     *     possible object is
     *     {@link HostEventArgument }
     *     
     */
    public HostEventArgument getDstHost() {
        return dstHost;
    }

    /**
     * Sets the value of the dstHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostEventArgument }
     *     
     */
    public void setDstHost(HostEventArgument value) {
        this.dstHost = value;
    }

}
