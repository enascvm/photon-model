
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FailoverNodeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FailoverNodeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="clusterIpSettings" type="{urn:vim25}CustomizationIPSettings"/&gt;
 *         &lt;element name="failoverIp" type="{urn:vim25}CustomizationIPSettings" minOccurs="0"/&gt;
 *         &lt;element name="biosUuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FailoverNodeInfo", propOrder = {
    "clusterIpSettings",
    "failoverIp",
    "biosUuid"
})
public class FailoverNodeInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected CustomizationIPSettings clusterIpSettings;
    protected CustomizationIPSettings failoverIp;
    protected String biosUuid;

    /**
     * Gets the value of the clusterIpSettings property.
     * 
     * @return
     *     possible object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public CustomizationIPSettings getClusterIpSettings() {
        return clusterIpSettings;
    }

    /**
     * Sets the value of the clusterIpSettings property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public void setClusterIpSettings(CustomizationIPSettings value) {
        this.clusterIpSettings = value;
    }

    /**
     * Gets the value of the failoverIp property.
     * 
     * @return
     *     possible object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public CustomizationIPSettings getFailoverIp() {
        return failoverIp;
    }

    /**
     * Sets the value of the failoverIp property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public void setFailoverIp(CustomizationIPSettings value) {
        this.failoverIp = value;
    }

    /**
     * Gets the value of the biosUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBiosUuid() {
        return biosUuid;
    }

    /**
     * Sets the value of the biosUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBiosUuid(String value) {
        this.biosUuid = value;
    }

}
