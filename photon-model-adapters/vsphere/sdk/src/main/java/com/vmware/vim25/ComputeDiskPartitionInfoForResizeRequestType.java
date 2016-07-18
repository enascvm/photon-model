
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComputeDiskPartitionInfoForResizeRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ComputeDiskPartitionInfoForResizeRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="partition" type="{urn:vim25}HostScsiDiskPartition"/&gt;
 *         &lt;element name="blockRange" type="{urn:vim25}HostDiskPartitionBlockRange"/&gt;
 *         &lt;element name="partitionFormat" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ComputeDiskPartitionInfoForResizeRequestType", propOrder = {
    "_this",
    "partition",
    "blockRange",
    "partitionFormat"
})
public class ComputeDiskPartitionInfoForResizeRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected HostScsiDiskPartition partition;
    @XmlElement(required = true)
    protected HostDiskPartitionBlockRange blockRange;
    protected String partitionFormat;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the partition property.
     * 
     * @return
     *     possible object is
     *     {@link HostScsiDiskPartition }
     *     
     */
    public HostScsiDiskPartition getPartition() {
        return partition;
    }

    /**
     * Sets the value of the partition property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostScsiDiskPartition }
     *     
     */
    public void setPartition(HostScsiDiskPartition value) {
        this.partition = value;
    }

    /**
     * Gets the value of the blockRange property.
     * 
     * @return
     *     possible object is
     *     {@link HostDiskPartitionBlockRange }
     *     
     */
    public HostDiskPartitionBlockRange getBlockRange() {
        return blockRange;
    }

    /**
     * Sets the value of the blockRange property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostDiskPartitionBlockRange }
     *     
     */
    public void setBlockRange(HostDiskPartitionBlockRange value) {
        this.blockRange = value;
    }

    /**
     * Gets the value of the partitionFormat property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPartitionFormat() {
        return partitionFormat;
    }

    /**
     * Sets the value of the partitionFormat property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPartitionFormat(String value) {
        this.partitionFormat = value;
    }

}
