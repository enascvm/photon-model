
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BaseConfigInfoFileBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BaseConfigInfoFileBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}BaseConfigInfoBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="filePath" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="backingObjectId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="parent" type="{urn:vim25}BaseConfigInfoFileBackingInfo" minOccurs="0"/&gt;
 *         &lt;element name="deltaSizeInMB" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BaseConfigInfoFileBackingInfo", propOrder = {
    "filePath",
    "backingObjectId",
    "parent",
    "deltaSizeInMB"
})
@XmlSeeAlso({
    BaseConfigInfoDiskFileBackingInfo.class,
    BaseConfigInfoRawDiskMappingBackingInfo.class
})
public class BaseConfigInfoFileBackingInfo
    extends BaseConfigInfoBackingInfo
{

    @XmlElement(required = true)
    protected String filePath;
    protected String backingObjectId;
    protected BaseConfigInfoFileBackingInfo parent;
    protected Long deltaSizeInMB;

    /**
     * Gets the value of the filePath property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the value of the filePath property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFilePath(String value) {
        this.filePath = value;
    }

    /**
     * Gets the value of the backingObjectId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBackingObjectId() {
        return backingObjectId;
    }

    /**
     * Sets the value of the backingObjectId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBackingObjectId(String value) {
        this.backingObjectId = value;
    }

    /**
     * Gets the value of the parent property.
     * 
     * @return
     *     possible object is
     *     {@link BaseConfigInfoFileBackingInfo }
     *     
     */
    public BaseConfigInfoFileBackingInfo getParent() {
        return parent;
    }

    /**
     * Sets the value of the parent property.
     * 
     * @param value
     *     allowed object is
     *     {@link BaseConfigInfoFileBackingInfo }
     *     
     */
    public void setParent(BaseConfigInfoFileBackingInfo value) {
        this.parent = value;
    }

    /**
     * Gets the value of the deltaSizeInMB property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getDeltaSizeInMB() {
        return deltaSizeInMB;
    }

    /**
     * Sets the value of the deltaSizeInMB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setDeltaSizeInMB(Long value) {
        this.deltaSizeInMB = value;
    }

}
