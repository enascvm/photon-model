
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfCryptoManagerKmipClusterStatus complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfCryptoManagerKmipClusterStatus"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="CryptoManagerKmipClusterStatus" type="{urn:vim25}CryptoManagerKmipClusterStatus" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfCryptoManagerKmipClusterStatus", propOrder = {
    "cryptoManagerKmipClusterStatus"
})
public class ArrayOfCryptoManagerKmipClusterStatus {

    @XmlElement(name = "CryptoManagerKmipClusterStatus")
    protected List<CryptoManagerKmipClusterStatus> cryptoManagerKmipClusterStatus;

    /**
     * Gets the value of the cryptoManagerKmipClusterStatus property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the cryptoManagerKmipClusterStatus property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCryptoManagerKmipClusterStatus().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CryptoManagerKmipClusterStatus }
     * 
     * 
     */
    public List<CryptoManagerKmipClusterStatus> getCryptoManagerKmipClusterStatus() {
        if (cryptoManagerKmipClusterStatus == null) {
            cryptoManagerKmipClusterStatus = new ArrayList<CryptoManagerKmipClusterStatus>();
        }
        return this.cryptoManagerKmipClusterStatus;
    }

}
