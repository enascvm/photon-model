
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FileBackedVirtualDiskSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FileBackedVirtualDiskSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDiskSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="capacityKb" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="profile" type="{urn:vim25}VirtualMachineProfileSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
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
@XmlType(name = "FileBackedVirtualDiskSpec", propOrder = {
    "capacityKb",
    "profile",
    "crypto"
})
@XmlSeeAlso({
    SeSparseVirtualDiskSpec.class
})
public class FileBackedVirtualDiskSpec
    extends VirtualDiskSpec
{

    protected long capacityKb;
    protected List<VirtualMachineProfileSpec> profile;
    protected CryptoSpec crypto;

    /**
     * Gets the value of the capacityKb property.
     * 
     */
    public long getCapacityKb() {
        return capacityKb;
    }

    /**
     * Sets the value of the capacityKb property.
     * 
     */
    public void setCapacityKb(long value) {
        this.capacityKb = value;
    }

    /**
     * Gets the value of the profile property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the profile property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProfile().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VirtualMachineProfileSpec }
     * 
     * 
     */
    public List<VirtualMachineProfileSpec> getProfile() {
        if (profile == null) {
            profile = new ArrayList<VirtualMachineProfileSpec>();
        }
        return this.profile;
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
