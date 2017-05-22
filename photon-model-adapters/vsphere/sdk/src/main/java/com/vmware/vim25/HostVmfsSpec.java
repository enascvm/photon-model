
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostVmfsSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostVmfsSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="extent" type="{urn:vim25}HostScsiDiskPartition"/&gt;
 *         &lt;element name="blockSizeMb" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="majorVersion" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="volumeName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="blockSize" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="unmapGranularity" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="unmapPriority" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostVmfsSpec", propOrder = {
    "extent",
    "blockSizeMb",
    "majorVersion",
    "volumeName",
    "blockSize",
    "unmapGranularity",
    "unmapPriority"
})
public class HostVmfsSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected HostScsiDiskPartition extent;
    protected Integer blockSizeMb;
    protected int majorVersion;
    @XmlElement(required = true)
    protected String volumeName;
    protected Integer blockSize;
    protected Integer unmapGranularity;
    protected String unmapPriority;

    /**
     * Gets the value of the extent property.
     * 
     * @return
     *     possible object is
     *     {@link HostScsiDiskPartition }
     *     
     */
    public HostScsiDiskPartition getExtent() {
        return extent;
    }

    /**
     * Sets the value of the extent property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostScsiDiskPartition }
     *     
     */
    public void setExtent(HostScsiDiskPartition value) {
        this.extent = value;
    }

    /**
     * Gets the value of the blockSizeMb property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getBlockSizeMb() {
        return blockSizeMb;
    }

    /**
     * Sets the value of the blockSizeMb property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setBlockSizeMb(Integer value) {
        this.blockSizeMb = value;
    }

    /**
     * Gets the value of the majorVersion property.
     * 
     */
    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Sets the value of the majorVersion property.
     * 
     */
    public void setMajorVersion(int value) {
        this.majorVersion = value;
    }

    /**
     * Gets the value of the volumeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVolumeName() {
        return volumeName;
    }

    /**
     * Sets the value of the volumeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVolumeName(String value) {
        this.volumeName = value;
    }

    /**
     * Gets the value of the blockSize property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getBlockSize() {
        return blockSize;
    }

    /**
     * Sets the value of the blockSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setBlockSize(Integer value) {
        this.blockSize = value;
    }

    /**
     * Gets the value of the unmapGranularity property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getUnmapGranularity() {
        return unmapGranularity;
    }

    /**
     * Sets the value of the unmapGranularity property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setUnmapGranularity(Integer value) {
        this.unmapGranularity = value;
    }

    /**
     * Gets the value of the unmapPriority property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUnmapPriority() {
        return unmapPriority;
    }

    /**
     * Sets the value of the unmapPriority property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUnmapPriority(String value) {
        this.unmapPriority = value;
    }

}
