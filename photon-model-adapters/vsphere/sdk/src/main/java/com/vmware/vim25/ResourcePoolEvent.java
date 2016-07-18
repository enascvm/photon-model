
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ResourcePoolEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ResourcePoolEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="resourcePool" type="{urn:vim25}ResourcePoolEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResourcePoolEvent", propOrder = {
    "resourcePool"
})
@XmlSeeAlso({
    ResourcePoolCreatedEvent.class,
    ResourcePoolDestroyedEvent.class,
    ResourcePoolMovedEvent.class,
    ResourcePoolReconfiguredEvent.class,
    ResourceViolatedEvent.class
})
public class ResourcePoolEvent
    extends Event
{

    @XmlElement(required = true)
    protected ResourcePoolEventArgument resourcePool;

    /**
     * Gets the value of the resourcePool property.
     * 
     * @return
     *     possible object is
     *     {@link ResourcePoolEventArgument }
     *     
     */
    public ResourcePoolEventArgument getResourcePool() {
        return resourcePool;
    }

    /**
     * Sets the value of the resourcePool property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResourcePoolEventArgument }
     *     
     */
    public void setResourcePool(ResourcePoolEventArgument value) {
        this.resourcePool = value;
    }

}
