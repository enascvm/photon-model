
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityVendorNamespaceInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityVendorNamespaceInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vendorInfo" type="{urn:pbm}PbmCapabilitySchemaVendorInfo"/&gt;
 *         &lt;element name="namespaceInfo" type="{urn:pbm}PbmCapabilityNamespaceInfo"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityVendorNamespaceInfo", propOrder = {
    "vendorInfo",
    "namespaceInfo"
})
public class PbmCapabilityVendorNamespaceInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmCapabilitySchemaVendorInfo vendorInfo;
    @XmlElement(required = true)
    protected PbmCapabilityNamespaceInfo namespaceInfo;

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

}
