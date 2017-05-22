
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmCapabilityVendorResourceTypeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmCapabilityVendorResourceTypeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmCapabilityVendorResourceTypeInfo" type="{urn:pbm}PbmCapabilityVendorResourceTypeInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmCapabilityVendorResourceTypeInfo", propOrder = {
    "pbmCapabilityVendorResourceTypeInfo"
})
public class ArrayOfPbmCapabilityVendorResourceTypeInfo {

    @XmlElement(name = "PbmCapabilityVendorResourceTypeInfo")
    protected List<PbmCapabilityVendorResourceTypeInfo> pbmCapabilityVendorResourceTypeInfo;

    /**
     * Gets the value of the pbmCapabilityVendorResourceTypeInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmCapabilityVendorResourceTypeInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmCapabilityVendorResourceTypeInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityVendorResourceTypeInfo }
     * 
     * 
     */
    public List<PbmCapabilityVendorResourceTypeInfo> getPbmCapabilityVendorResourceTypeInfo() {
        if (pbmCapabilityVendorResourceTypeInfo == null) {
            pbmCapabilityVendorResourceTypeInfo = new ArrayList<PbmCapabilityVendorResourceTypeInfo>();
        }
        return this.pbmCapabilityVendorResourceTypeInfo;
    }

}
