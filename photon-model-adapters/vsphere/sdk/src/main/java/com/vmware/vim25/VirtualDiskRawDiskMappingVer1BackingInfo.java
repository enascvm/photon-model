
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskRawDiskMappingVer1BackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskRawDiskMappingVer1BackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceFileBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="lunUuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="deviceName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="compatibilityMode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="diskMode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="uuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="contentId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="changeId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="parent" type="{urn:vim25}VirtualDiskRawDiskMappingVer1BackingInfo" minOccurs="0"/&gt;
 *         &lt;element name="sharing" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskRawDiskMappingVer1BackingInfo", propOrder = {
    "lunUuid",
    "deviceName",
    "compatibilityMode",
    "diskMode",
    "uuid",
    "contentId",
    "changeId",
    "parent",
    "sharing"
})
public class VirtualDiskRawDiskMappingVer1BackingInfo
    extends VirtualDeviceFileBackingInfo
{

    protected String lunUuid;
    protected String deviceName;
    protected String compatibilityMode;
    protected String diskMode;
    protected String uuid;
    protected String contentId;
    protected String changeId;
    protected VirtualDiskRawDiskMappingVer1BackingInfo parent;
    protected String sharing;

    /**
     * Gets the value of the lunUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLunUuid() {
        return lunUuid;
    }

    /**
     * Sets the value of the lunUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLunUuid(String value) {
        this.lunUuid = value;
    }

    /**
     * Gets the value of the deviceName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Sets the value of the deviceName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDeviceName(String value) {
        this.deviceName = value;
    }

    /**
     * Gets the value of the compatibilityMode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCompatibilityMode() {
        return compatibilityMode;
    }

    /**
     * Sets the value of the compatibilityMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCompatibilityMode(String value) {
        this.compatibilityMode = value;
    }

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
     *     {@link VirtualDiskRawDiskMappingVer1BackingInfo }
     *     
     */
    public VirtualDiskRawDiskMappingVer1BackingInfo getParent() {
        return parent;
    }

    /**
     * Sets the value of the parent property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDiskRawDiskMappingVer1BackingInfo }
     *     
     */
    public void setParent(VirtualDiskRawDiskMappingVer1BackingInfo value) {
        this.parent = value;
    }

    /**
     * Gets the value of the sharing property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSharing() {
        return sharing;
    }

    /**
     * Sets the value of the sharing property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSharing(String value) {
        this.sharing = value;
    }

}
