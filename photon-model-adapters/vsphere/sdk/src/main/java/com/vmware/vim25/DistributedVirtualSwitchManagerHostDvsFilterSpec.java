
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DistributedVirtualSwitchManagerHostDvsFilterSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DistributedVirtualSwitchManagerHostDvsFilterSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="inclusive" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DistributedVirtualSwitchManagerHostDvsFilterSpec", propOrder = {
    "inclusive"
})
@XmlSeeAlso({
    DistributedVirtualSwitchManagerHostArrayFilter.class,
    DistributedVirtualSwitchManagerHostContainerFilter.class,
    DistributedVirtualSwitchManagerHostDvsMembershipFilter.class
})
public class DistributedVirtualSwitchManagerHostDvsFilterSpec
    extends DynamicData
{

    protected boolean inclusive;

    /**
     * Gets the value of the inclusive property.
     * 
     */
    public boolean isInclusive() {
        return inclusive;
    }

    /**
     * Sets the value of the inclusive property.
     * 
     */
    public void setInclusive(boolean value) {
        this.inclusive = value;
    }

}
