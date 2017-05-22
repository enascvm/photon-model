
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfCryptoKeyId complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfCryptoKeyId"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="CryptoKeyId" type="{urn:vim25}CryptoKeyId" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfCryptoKeyId", propOrder = {
    "cryptoKeyId"
})
public class ArrayOfCryptoKeyId {

    @XmlElement(name = "CryptoKeyId")
    protected List<CryptoKeyId> cryptoKeyId;

    /**
     * Gets the value of the cryptoKeyId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the cryptoKeyId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCryptoKeyId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CryptoKeyId }
     * 
     * 
     */
    public List<CryptoKeyId> getCryptoKeyId() {
        if (cryptoKeyId == null) {
            cryptoKeyId = new ArrayList<CryptoKeyId>();
        }
        return this.cryptoKeyId;
    }

}
