
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComplianceFailureComplianceFailureValues complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ComplianceFailureComplianceFailureValues"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="comparisonIdentifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="profileInstance" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="hostValue" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/&gt;
 *         &lt;element name="profileValue" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ComplianceFailureComplianceFailureValues", propOrder = {
    "comparisonIdentifier",
    "profileInstance",
    "hostValue",
    "profileValue"
})
public class ComplianceFailureComplianceFailureValues
    extends DynamicData
{

    @XmlElement(required = true)
    protected String comparisonIdentifier;
    protected String profileInstance;
    protected Object hostValue;
    protected Object profileValue;

    /**
     * Gets the value of the comparisonIdentifier property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getComparisonIdentifier() {
        return comparisonIdentifier;
    }

    /**
     * Sets the value of the comparisonIdentifier property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setComparisonIdentifier(String value) {
        this.comparisonIdentifier = value;
    }

    /**
     * Gets the value of the profileInstance property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileInstance() {
        return profileInstance;
    }

    /**
     * Sets the value of the profileInstance property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileInstance(String value) {
        this.profileInstance = value;
    }

    /**
     * Gets the value of the hostValue property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getHostValue() {
        return hostValue;
    }

    /**
     * Sets the value of the hostValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setHostValue(Object value) {
        this.hostValue = value;
    }

    /**
     * Gets the value of the profileValue property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getProfileValue() {
        return profileValue;
    }

    /**
     * Sets the value of the profileValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setProfileValue(Object value) {
        this.profileValue = value;
    }

}
