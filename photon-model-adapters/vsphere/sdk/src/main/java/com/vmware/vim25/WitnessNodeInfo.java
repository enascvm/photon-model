
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for WitnessNodeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="WitnessNodeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ipSettings" type="{urn:vim25}CustomizationIPSettings"/&gt;
 *         &lt;element name="biosUuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WitnessNodeInfo", propOrder = {
    "ipSettings",
    "biosUuid"
})
public class WitnessNodeInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected CustomizationIPSettings ipSettings;
    protected String biosUuid;

    /**
     * Gets the value of the ipSettings property.
     * 
     * @return
     *     possible object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public CustomizationIPSettings getIpSettings() {
        return ipSettings;
    }

    /**
     * Sets the value of the ipSettings property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public void setIpSettings(CustomizationIPSettings value) {
        this.ipSettings = value;
    }

    /**
     * Gets the value of the biosUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBiosUuid() {
        return biosUuid;
    }

    /**
     * Sets the value of the biosUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBiosUuid(String value) {
        this.biosUuid = value;
    }

}
