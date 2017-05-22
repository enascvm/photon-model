
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityDescription complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityDescription"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="description" type="{urn:pbm}PbmExtendedElementDescription"/&gt;
 *         &lt;element name="value" type="{http://www.w3.org/2001/XMLSchema}anyType"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityDescription", propOrder = {
    "description",
    "value"
})
public class PbmCapabilityDescription
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmExtendedElementDescription description;
    @XmlElement(required = true)
    protected Object value;

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
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setValue(Object value) {
        this.value = value;
    }

}
