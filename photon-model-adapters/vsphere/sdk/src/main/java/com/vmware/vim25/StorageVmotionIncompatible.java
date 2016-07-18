
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageVmotionIncompatible complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StorageVmotionIncompatible"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualHardwareCompatibilityIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="datastore" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StorageVmotionIncompatible", propOrder = {
    "datastore"
})
public class StorageVmotionIncompatible
    extends VirtualHardwareCompatibilityIssue
{

    protected ManagedObjectReference datastore;

    /**
     * Gets the value of the datastore property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getDatastore() {
        return datastore;
    }

    /**
     * Sets the value of the datastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setDatastore(ManagedObjectReference value) {
        this.datastore = value;
    }

}
