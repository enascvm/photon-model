
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfCustomizationSpecInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfCustomizationSpecInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="CustomizationSpecInfo" type="{urn:vim25}CustomizationSpecInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfCustomizationSpecInfo", propOrder = {
    "customizationSpecInfo"
})
public class ArrayOfCustomizationSpecInfo {

    @XmlElement(name = "CustomizationSpecInfo")
    protected List<CustomizationSpecInfo> customizationSpecInfo;

    /**
     * Gets the value of the customizationSpecInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the customizationSpecInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCustomizationSpecInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CustomizationSpecInfo }
     * 
     * 
     */
    public List<CustomizationSpecInfo> getCustomizationSpecInfo() {
        if (customizationSpecInfo == null) {
            customizationSpecInfo = new ArrayList<CustomizationSpecInfo>();
        }
        return this.customizationSpecInfo;
    }

}
