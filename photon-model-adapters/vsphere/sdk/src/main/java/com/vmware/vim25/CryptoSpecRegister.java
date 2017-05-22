
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CryptoSpecRegister complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CryptoSpecRegister"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}CryptoSpecNoOp"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="cryptoKeyId" type="{urn:vim25}CryptoKeyId"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CryptoSpecRegister", propOrder = {
    "cryptoKeyId"
})
public class CryptoSpecRegister
    extends CryptoSpecNoOp
{

    @XmlElement(required = true)
    protected CryptoKeyId cryptoKeyId;

    /**
     * Gets the value of the cryptoKeyId property.
     * 
     * @return
     *     possible object is
     *     {@link CryptoKeyId }
     *     
     */
    public CryptoKeyId getCryptoKeyId() {
        return cryptoKeyId;
    }

    /**
     * Sets the value of the cryptoKeyId property.
     * 
     * @param value
     *     allowed object is
     *     {@link CryptoKeyId }
     *     
     */
    public void setCryptoKeyId(CryptoKeyId value) {
        this.cryptoKeyId = value;
    }

}
