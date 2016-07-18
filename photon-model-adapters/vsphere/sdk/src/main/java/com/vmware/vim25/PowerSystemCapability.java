
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PowerSystemCapability complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PowerSystemCapability"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="availablePolicy" type="{urn:vim25}HostPowerPolicy" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PowerSystemCapability", propOrder = {
    "availablePolicy"
})
public class PowerSystemCapability
    extends DynamicData
{

    @XmlElement(required = true)
    protected List<HostPowerPolicy> availablePolicy;

    /**
     * Gets the value of the availablePolicy property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the availablePolicy property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAvailablePolicy().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostPowerPolicy }
     * 
     * 
     */
    public List<HostPowerPolicy> getAvailablePolicy() {
        if (availablePolicy == null) {
            availablePolicy = new ArrayList<HostPowerPolicy>();
        }
        return this.availablePolicy;
    }

}
