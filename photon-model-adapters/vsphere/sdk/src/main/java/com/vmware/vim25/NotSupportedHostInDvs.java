
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NotSupportedHostInDvs complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NotSupportedHostInDvs"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}NotSupportedHost"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="switchProductSpec" type="{urn:vim25}DistributedVirtualSwitchProductSpec"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NotSupportedHostInDvs", propOrder = {
    "switchProductSpec"
})
public class NotSupportedHostInDvs
    extends NotSupportedHost
{

    @XmlElement(required = true)
    protected DistributedVirtualSwitchProductSpec switchProductSpec;

    /**
     * Gets the value of the switchProductSpec property.
     * 
     * @return
     *     possible object is
     *     {@link DistributedVirtualSwitchProductSpec }
     *     
     */
    public DistributedVirtualSwitchProductSpec getSwitchProductSpec() {
        return switchProductSpec;
    }

    /**
     * Sets the value of the switchProductSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link DistributedVirtualSwitchProductSpec }
     *     
     */
    public void setSwitchProductSpec(DistributedVirtualSwitchProductSpec value) {
        this.switchProductSpec = value;
    }

}
