
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCapabilityGenericTypeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityGenericTypeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmCapabilityTypeInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="genericTypeName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityGenericTypeInfo", propOrder = {
    "genericTypeName"
})
public class PbmCapabilityGenericTypeInfo
    extends PbmCapabilityTypeInfo
{

    @XmlElement(required = true)
    protected String genericTypeName;

    /**
     * Gets the value of the genericTypeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getGenericTypeName() {
        return genericTypeName;
    }

    /**
     * Sets the value of the genericTypeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setGenericTypeName(String value) {
        this.genericTypeName = value;
    }

}
