
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilitySchema complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilitySchema"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vendorInfo" type="{urn:pbm}PbmCapabilitySchemaVendorInfo"/&gt;
 *         &lt;element name="namespaceInfo" type="{urn:pbm}PbmCapabilityNamespaceInfo"/&gt;
 *         &lt;element name="lineOfService" type="{urn:pbm}PbmLineOfServiceInfo" minOccurs="0"/&gt;
 *         &lt;element name="capabilityMetadataPerCategory" type="{urn:pbm}PbmCapabilityMetadataPerCategory" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilitySchema", propOrder = {
    "vendorInfo",
    "namespaceInfo",
    "lineOfService",
    "capabilityMetadataPerCategory"
})
public class PbmCapabilitySchema
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmCapabilitySchemaVendorInfo vendorInfo;
    @XmlElement(required = true)
    protected PbmCapabilityNamespaceInfo namespaceInfo;
    protected PbmLineOfServiceInfo lineOfService;
    @XmlElement(required = true)
    protected List<PbmCapabilityMetadataPerCategory> capabilityMetadataPerCategory;

    /**
     * Gets the value of the vendorInfo property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilitySchemaVendorInfo }
     *     
     */
    public PbmCapabilitySchemaVendorInfo getVendorInfo() {
        return vendorInfo;
    }

    /**
     * Sets the value of the vendorInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilitySchemaVendorInfo }
     *     
     */
    public void setVendorInfo(PbmCapabilitySchemaVendorInfo value) {
        this.vendorInfo = value;
    }

    /**
     * Gets the value of the namespaceInfo property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityNamespaceInfo }
     *     
     */
    public PbmCapabilityNamespaceInfo getNamespaceInfo() {
        return namespaceInfo;
    }

    /**
     * Sets the value of the namespaceInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityNamespaceInfo }
     *     
     */
    public void setNamespaceInfo(PbmCapabilityNamespaceInfo value) {
        this.namespaceInfo = value;
    }

    /**
     * Gets the value of the lineOfService property.
     * 
     * @return
     *     possible object is
     *     {@link PbmLineOfServiceInfo }
     *     
     */
    public PbmLineOfServiceInfo getLineOfService() {
        return lineOfService;
    }

    /**
     * Sets the value of the lineOfService property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmLineOfServiceInfo }
     *     
     */
    public void setLineOfService(PbmLineOfServiceInfo value) {
        this.lineOfService = value;
    }

    /**
     * Gets the value of the capabilityMetadataPerCategory property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the capabilityMetadataPerCategory property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCapabilityMetadataPerCategory().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityMetadataPerCategory }
     * 
     * 
     */
    public List<PbmCapabilityMetadataPerCategory> getCapabilityMetadataPerCategory() {
        if (capabilityMetadataPerCategory == null) {
            capabilityMetadataPerCategory = new ArrayList<PbmCapabilityMetadataPerCategory>();
        }
        return this.capabilityMetadataPerCategory;
    }

}
