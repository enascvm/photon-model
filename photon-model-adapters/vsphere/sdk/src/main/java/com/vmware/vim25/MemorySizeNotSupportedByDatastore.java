
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MemorySizeNotSupportedByDatastore complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MemorySizeNotSupportedByDatastore"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualHardwareCompatibilityIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="datastore" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="memorySizeMB" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="maxMemorySizeMB" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MemorySizeNotSupportedByDatastore", propOrder = {
    "datastore",
    "memorySizeMB",
    "maxMemorySizeMB"
})
public class MemorySizeNotSupportedByDatastore
    extends VirtualHardwareCompatibilityIssue
{

    @XmlElement(required = true)
    protected ManagedObjectReference datastore;
    protected int memorySizeMB;
    protected int maxMemorySizeMB;

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

    /**
     * Gets the value of the memorySizeMB property.
     * 
     */
    public int getMemorySizeMB() {
        return memorySizeMB;
    }

    /**
     * Sets the value of the memorySizeMB property.
     * 
     */
    public void setMemorySizeMB(int value) {
        this.memorySizeMB = value;
    }

    /**
     * Gets the value of the maxMemorySizeMB property.
     * 
     */
    public int getMaxMemorySizeMB() {
        return maxMemorySizeMB;
    }

    /**
     * Sets the value of the maxMemorySizeMB property.
     * 
     */
    public void setMaxMemorySizeMB(int value) {
        this.maxMemorySizeMB = value;
    }

}
