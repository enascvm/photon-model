
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceConfigSpecBackingSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceConfigSpecBackingSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="parent" type="{urn:vim25}VirtualDeviceConfigSpecBackingSpec" minOccurs="0"/&gt;
 *         &lt;element name="crypto" type="{urn:vim25}CryptoSpec" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDeviceConfigSpecBackingSpec", propOrder = {
    "parent",
    "crypto"
})
public class VirtualDeviceConfigSpecBackingSpec
    extends DynamicData
{

    protected VirtualDeviceConfigSpecBackingSpec parent;
    protected CryptoSpec crypto;

    /**
     * Gets the value of the parent property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDeviceConfigSpecBackingSpec }
     *     
     */
    public VirtualDeviceConfigSpecBackingSpec getParent() {
        return parent;
    }

    /**
     * Sets the value of the parent property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDeviceConfigSpecBackingSpec }
     *     
     */
    public void setParent(VirtualDeviceConfigSpecBackingSpec value) {
        this.parent = value;
    }

    /**
     * Gets the value of the crypto property.
     * 
     * @return
     *     possible object is
     *     {@link CryptoSpec }
     *     
     */
    public CryptoSpec getCrypto() {
        return crypto;
    }

    /**
     * Sets the value of the crypto property.
     * 
     * @param value
     *     allowed object is
     *     {@link CryptoSpec }
     *     
     */
    public void setCrypto(CryptoSpec value) {
        this.crypto = value;
    }

}
