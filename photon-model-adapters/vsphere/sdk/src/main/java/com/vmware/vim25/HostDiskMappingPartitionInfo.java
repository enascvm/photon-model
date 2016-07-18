
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostDiskMappingPartitionInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostDiskMappingPartitionInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="fileSystem" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="capacityInKb" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostDiskMappingPartitionInfo", propOrder = {
    "name",
    "fileSystem",
    "capacityInKb"
})
public class HostDiskMappingPartitionInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected String fileSystem;
    protected long capacityInKb;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the fileSystem property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFileSystem() {
        return fileSystem;
    }

    /**
     * Sets the value of the fileSystem property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFileSystem(String value) {
        this.fileSystem = value;
    }

    /**
     * Gets the value of the capacityInKb property.
     * 
     */
    public long getCapacityInKb() {
        return capacityInKb;
    }

    /**
     * Sets the value of the capacityInKb property.
     * 
     */
    public void setCapacityInKb(long value) {
        this.capacityInKb = value;
    }

}
