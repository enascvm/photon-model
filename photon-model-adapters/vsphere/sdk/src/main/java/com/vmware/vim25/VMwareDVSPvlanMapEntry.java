
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDVSPvlanMapEntry complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMwareDVSPvlanMapEntry"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="primaryVlanId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="secondaryVlanId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="pvlanType" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VMwareDVSPvlanMapEntry", propOrder = {
    "primaryVlanId",
    "secondaryVlanId",
    "pvlanType"
})
public class VMwareDVSPvlanMapEntry
    extends DynamicData
{

    protected int primaryVlanId;
    protected int secondaryVlanId;
    @XmlElement(required = true)
    protected String pvlanType;

    /**
     * Gets the value of the primaryVlanId property.
     * 
     */
    public int getPrimaryVlanId() {
        return primaryVlanId;
    }

    /**
     * Sets the value of the primaryVlanId property.
     * 
     */
    public void setPrimaryVlanId(int value) {
        this.primaryVlanId = value;
    }

    /**
     * Gets the value of the secondaryVlanId property.
     * 
     */
    public int getSecondaryVlanId() {
        return secondaryVlanId;
    }

    /**
     * Sets the value of the secondaryVlanId property.
     * 
     */
    public void setSecondaryVlanId(int value) {
        this.secondaryVlanId = value;
    }

    /**
     * Gets the value of the pvlanType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPvlanType() {
        return pvlanType;
    }

    /**
     * Sets the value of the pvlanType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPvlanType(String value) {
        this.pvlanType = value;
    }

}
