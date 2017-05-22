
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityVendorResourceTypeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityVendorResourceTypeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="resourceType" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="vendorNamespaceInfo" type="{urn:pbm}PbmCapabilityVendorNamespaceInfo" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityVendorResourceTypeInfo", propOrder = {
    "resourceType",
    "vendorNamespaceInfo"
})
public class PbmCapabilityVendorResourceTypeInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String resourceType;
    @XmlElement(required = true)
    protected List<PbmCapabilityVendorNamespaceInfo> vendorNamespaceInfo;

    /**
     * Gets the value of the resourceType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Sets the value of the resourceType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResourceType(String value) {
        this.resourceType = value;
    }

    /**
     * Gets the value of the vendorNamespaceInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vendorNamespaceInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVendorNamespaceInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityVendorNamespaceInfo }
     * 
     * 
     */
    public List<PbmCapabilityVendorNamespaceInfo> getVendorNamespaceInfo() {
        if (vendorNamespaceInfo == null) {
            vendorNamespaceInfo = new ArrayList<PbmCapabilityVendorNamespaceInfo>();
        }
        return this.vendorNamespaceInfo;
    }

}
