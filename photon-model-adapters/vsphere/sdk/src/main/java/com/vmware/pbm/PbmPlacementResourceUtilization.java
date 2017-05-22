
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmPlacementResourceUtilization complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPlacementResourceUtilization"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="name" type="{urn:pbm}PbmExtendedElementDescription"/&gt;
 *         &lt;element name="description" type="{urn:pbm}PbmExtendedElementDescription"/&gt;
 *         &lt;element name="availableBefore" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="availableAfter" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="total" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPlacementResourceUtilization", propOrder = {
    "name",
    "description",
    "availableBefore",
    "availableAfter",
    "total"
})
public class PbmPlacementResourceUtilization
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmExtendedElementDescription name;
    @XmlElement(required = true)
    protected PbmExtendedElementDescription description;
    protected Long availableBefore;
    protected Long availableAfter;
    protected Long total;

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

    /**
     * Gets the value of the availableBefore property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getAvailableBefore() {
        return availableBefore;
    }

    /**
     * Sets the value of the availableBefore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setAvailableBefore(Long value) {
        this.availableBefore = value;
    }

    /**
     * Gets the value of the availableAfter property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getAvailableAfter() {
        return availableAfter;
    }

    /**
     * Sets the value of the availableAfter property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setAvailableAfter(Long value) {
        this.availableAfter = value;
    }

    /**
     * Gets the value of the total property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getTotal() {
        return total;
    }

    /**
     * Sets the value of the total property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setTotal(Long value) {
        this.total = value;
    }

}
