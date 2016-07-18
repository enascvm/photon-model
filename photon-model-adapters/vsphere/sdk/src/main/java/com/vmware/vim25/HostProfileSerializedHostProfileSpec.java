
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostProfileSerializedHostProfileSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostProfileSerializedHostProfileSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ProfileSerializedCreateSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="validatorHost" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="validating" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostProfileSerializedHostProfileSpec", propOrder = {
    "validatorHost",
    "validating"
})
public class HostProfileSerializedHostProfileSpec
    extends ProfileSerializedCreateSpec
{

    protected ManagedObjectReference validatorHost;
    protected Boolean validating;

    /**
     * Gets the value of the validatorHost property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getValidatorHost() {
        return validatorHost;
    }

    /**
     * Sets the value of the validatorHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setValidatorHost(ManagedObjectReference value) {
        this.validatorHost = value;
    }

    /**
     * Gets the value of the validating property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isValidating() {
        return validating;
    }

    /**
     * Sets the value of the validating property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setValidating(Boolean value) {
        this.validating = value;
    }

}
