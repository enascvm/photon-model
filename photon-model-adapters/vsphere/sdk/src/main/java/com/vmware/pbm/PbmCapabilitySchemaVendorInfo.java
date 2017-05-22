
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilitySchemaVendorInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilitySchemaVendorInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vendorUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="info" type="{urn:pbm}PbmExtendedElementDescription"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilitySchemaVendorInfo", propOrder = {
    "vendorUuid",
    "info"
})
public class PbmCapabilitySchemaVendorInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String vendorUuid;
    @XmlElement(required = true)
    protected PbmExtendedElementDescription info;

    /**
     * Gets the value of the vendorUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVendorUuid() {
        return vendorUuid;
    }

    /**
     * Sets the value of the vendorUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVendorUuid(String value) {
        this.vendorUuid = value;
    }

    /**
     * Gets the value of the info property.
     * 
     * @return
     *     possible object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public PbmExtendedElementDescription getInfo() {
        return info;
    }

    /**
     * Sets the value of the info property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public void setInfo(PbmExtendedElementDescription value) {
        this.info = value;
    }

}
