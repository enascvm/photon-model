
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BaseConfigInfoRawDiskMappingBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BaseConfigInfoRawDiskMappingBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}BaseConfigInfoFileBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="lunUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="compatibilityMode" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BaseConfigInfoRawDiskMappingBackingInfo", propOrder = {
    "lunUuid",
    "compatibilityMode"
})
public class BaseConfigInfoRawDiskMappingBackingInfo
    extends BaseConfigInfoFileBackingInfo
{

    @XmlElement(required = true)
    protected String lunUuid;
    @XmlElement(required = true)
    protected String compatibilityMode;

    /**
     * Gets the value of the lunUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLunUuid() {
        return lunUuid;
    }

    /**
     * Sets the value of the lunUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLunUuid(String value) {
        this.lunUuid = value;
    }

    /**
     * Gets the value of the compatibilityMode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCompatibilityMode() {
        return compatibilityMode;
    }

    /**
     * Sets the value of the compatibilityMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCompatibilityMode(String value) {
        this.compatibilityMode = value;
    }

}
