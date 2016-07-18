
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DVSVendorSpecificConfig complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DVSVendorSpecificConfig"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InheritablePolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="keyValue" type="{urn:vim25}DistributedVirtualSwitchKeyedOpaqueBlob" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DVSVendorSpecificConfig", propOrder = {
    "keyValue"
})
public class DVSVendorSpecificConfig
    extends InheritablePolicy
{

    protected List<DistributedVirtualSwitchKeyedOpaqueBlob> keyValue;

    /**
     * Gets the value of the keyValue property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the keyValue property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getKeyValue().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DistributedVirtualSwitchKeyedOpaqueBlob }
     * 
     * 
     */
    public List<DistributedVirtualSwitchKeyedOpaqueBlob> getKeyValue() {
        if (keyValue == null) {
            keyValue = new ArrayList<DistributedVirtualSwitchKeyedOpaqueBlob>();
        }
        return this.keyValue;
    }

}
