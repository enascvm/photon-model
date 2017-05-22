
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmDatastoreSpaceStatistics complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmDatastoreSpaceStatistics"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="profileId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="physicalTotalInMB" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="physicalFreeInMB" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="physicalUsedInMB" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="logicalLimitInMB" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="logicalFreeInMB" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="logicalUsedInMB" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmDatastoreSpaceStatistics", propOrder = {
    "profileId",
    "physicalTotalInMB",
    "physicalFreeInMB",
    "physicalUsedInMB",
    "logicalLimitInMB",
    "logicalFreeInMB",
    "logicalUsedInMB"
})
public class PbmDatastoreSpaceStatistics
    extends DynamicData
{

    protected String profileId;
    protected long physicalTotalInMB;
    protected long physicalFreeInMB;
    protected long physicalUsedInMB;
    protected Long logicalLimitInMB;
    protected long logicalFreeInMB;
    protected long logicalUsedInMB;

    /**
     * Gets the value of the profileId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the value of the profileId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileId(String value) {
        this.profileId = value;
    }

    /**
     * Gets the value of the physicalTotalInMB property.
     * 
     */
    public long getPhysicalTotalInMB() {
        return physicalTotalInMB;
    }

    /**
     * Sets the value of the physicalTotalInMB property.
     * 
     */
    public void setPhysicalTotalInMB(long value) {
        this.physicalTotalInMB = value;
    }

    /**
     * Gets the value of the physicalFreeInMB property.
     * 
     */
    public long getPhysicalFreeInMB() {
        return physicalFreeInMB;
    }

    /**
     * Sets the value of the physicalFreeInMB property.
     * 
     */
    public void setPhysicalFreeInMB(long value) {
        this.physicalFreeInMB = value;
    }

    /**
     * Gets the value of the physicalUsedInMB property.
     * 
     */
    public long getPhysicalUsedInMB() {
        return physicalUsedInMB;
    }

    /**
     * Sets the value of the physicalUsedInMB property.
     * 
     */
    public void setPhysicalUsedInMB(long value) {
        this.physicalUsedInMB = value;
    }

    /**
     * Gets the value of the logicalLimitInMB property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getLogicalLimitInMB() {
        return logicalLimitInMB;
    }

    /**
     * Sets the value of the logicalLimitInMB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setLogicalLimitInMB(Long value) {
        this.logicalLimitInMB = value;
    }

    /**
     * Gets the value of the logicalFreeInMB property.
     * 
     */
    public long getLogicalFreeInMB() {
        return logicalFreeInMB;
    }

    /**
     * Sets the value of the logicalFreeInMB property.
     * 
     */
    public void setLogicalFreeInMB(long value) {
        this.logicalFreeInMB = value;
    }

    /**
     * Gets the value of the logicalUsedInMB property.
     * 
     */
    public long getLogicalUsedInMB() {
        return logicalUsedInMB;
    }

    /**
     * Sets the value of the logicalUsedInMB property.
     * 
     */
    public void setLogicalUsedInMB(long value) {
        this.logicalUsedInMB = value;
    }

}
