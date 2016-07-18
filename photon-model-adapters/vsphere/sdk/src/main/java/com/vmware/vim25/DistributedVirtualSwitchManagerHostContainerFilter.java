
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DistributedVirtualSwitchManagerHostContainerFilter complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DistributedVirtualSwitchManagerHostContainerFilter"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DistributedVirtualSwitchManagerHostDvsFilterSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostContainer" type="{urn:vim25}DistributedVirtualSwitchManagerHostContainer"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DistributedVirtualSwitchManagerHostContainerFilter", propOrder = {
    "hostContainer"
})
public class DistributedVirtualSwitchManagerHostContainerFilter
    extends DistributedVirtualSwitchManagerHostDvsFilterSpec
{

    @XmlElement(required = true)
    protected DistributedVirtualSwitchManagerHostContainer hostContainer;

    /**
     * Gets the value of the hostContainer property.
     * 
     * @return
     *     possible object is
     *     {@link DistributedVirtualSwitchManagerHostContainer }
     *     
     */
    public DistributedVirtualSwitchManagerHostContainer getHostContainer() {
        return hostContainer;
    }

    /**
     * Sets the value of the hostContainer property.
     * 
     * @param value
     *     allowed object is
     *     {@link DistributedVirtualSwitchManagerHostContainer }
     *     
     */
    public void setHostContainer(DistributedVirtualSwitchManagerHostContainer value) {
        this.hostContainer = value;
    }

}
