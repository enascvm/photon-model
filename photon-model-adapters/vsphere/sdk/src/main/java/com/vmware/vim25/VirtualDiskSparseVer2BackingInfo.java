
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskSparseVer2BackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskSparseVer2BackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceFileBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="diskMode" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="split" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="writeThrough" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="spaceUsedInKB" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="uuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="contentId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="changeId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="parent" type="{urn:vim25}VirtualDiskSparseVer2BackingInfo" minOccurs="0"/&gt;
 *         &lt;element name="keyId" type="{urn:vim25}CryptoKeyId" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskSparseVer2BackingInfo", propOrder = {
    "diskMode",
    "split",
    "writeThrough",
    "spaceUsedInKB",
    "uuid",
    "contentId",
    "changeId",
    "parent",
    "keyId"
})
public class VirtualDiskSparseVer2BackingInfo
    extends VirtualDeviceFileBackingInfo
{

    @XmlElement(required = true)
    protected String diskMode;
    protected Boolean split;
    protected Boolean writeThrough;
    protected Long spaceUsedInKB;
    protected String uuid;
    protected String contentId;
    protected String changeId;
    protected VirtualDiskSparseVer2BackingInfo parent;
    protected CryptoKeyId keyId;

    /**
     * Gets the value of the diskMode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDiskMode() {
        return diskMode;
    }

    /**
     * Sets the value of the diskMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDiskMode(String value) {
        this.diskMode = value;
    }

    /**
     * Gets the value of the split property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isSplit() {
        return split;
    }

    /**
     * Sets the value of the split property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setSplit(Boolean value) {
        this.split = value;
    }

    /**
     * Gets the value of the writeThrough property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isWriteThrough() {
        return writeThrough;
    }

    /**
     * Sets the value of the writeThrough property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setWriteThrough(Boolean value) {
        this.writeThrough = value;
    }

    /**
     * Gets the value of the spaceUsedInKB property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getSpaceUsedInKB() {
        return spaceUsedInKB;
    }

    /**
     * Sets the value of the spaceUsedInKB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setSpaceUsedInKB(Long value) {
        this.spaceUsedInKB = value;
    }

    /**
     * Gets the value of the uuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets the value of the uuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUuid(String value) {
        this.uuid = value;
    }

    /**
     * Gets the value of the contentId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContentId() {
        return contentId;
    }

    /**
     * Sets the value of the contentId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContentId(String value) {
        this.contentId = value;
    }

    /**
     * Gets the value of the changeId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getChangeId() {
        return changeId;
    }

    /**
     * Sets the value of the changeId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setChangeId(String value) {
        this.changeId = value;
    }

    /**
     * Gets the value of the parent property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDiskSparseVer2BackingInfo }
     *     
     */
    public VirtualDiskSparseVer2BackingInfo getParent() {
        return parent;
    }

    /**
     * Sets the value of the parent property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDiskSparseVer2BackingInfo }
     *     
     */
    public void setParent(VirtualDiskSparseVer2BackingInfo value) {
        this.parent = value;
    }

    /**
     * Gets the value of the keyId property.
     * 
     * @return
     *     possible object is
     *     {@link CryptoKeyId }
     *     
     */
    public CryptoKeyId getKeyId() {
        return keyId;
    }

    /**
     * Sets the value of the keyId property.
     * 
     * @param value
     *     allowed object is
     *     {@link CryptoKeyId }
     *     
     */
    public void setKeyId(CryptoKeyId value) {
        this.keyId = value;
    }

}
