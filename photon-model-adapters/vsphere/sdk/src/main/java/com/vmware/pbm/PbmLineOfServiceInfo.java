
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmLineOfServiceInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmLineOfServiceInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="lineOfService" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="name" type="{urn:pbm}PbmExtendedElementDescription"/&gt;
 *         &lt;element name="description" type="{urn:pbm}PbmExtendedElementDescription" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmLineOfServiceInfo", propOrder = {
    "lineOfService",
    "name",
    "description"
})
@XmlSeeAlso({
    PbmPersistenceBasedDataServiceInfo.class,
    PbmVaioDataServiceInfo.class
})
public class PbmLineOfServiceInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String lineOfService;
    @XmlElement(required = true)
    protected PbmExtendedElementDescription name;
    protected PbmExtendedElementDescription description;

    /**
     * Gets the value of the lineOfService property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLineOfService() {
        return lineOfService;
    }

    /**
     * Sets the value of the lineOfService property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLineOfService(String value) {
        this.lineOfService = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public PbmExtendedElementDescription getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public void setName(PbmExtendedElementDescription value) {
        this.name = value;
    }

    /**
     * Gets the value of the description property.
     * 
     * @return
     *     possible object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public PbmExtendedElementDescription getDescription() {
        return description;
    }

    /**
     * Sets the value of the description property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public void setDescription(PbmExtendedElementDescription value) {
        this.description = value;
    }

}
