
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ScheduledHardwareUpgradeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ScheduledHardwareUpgradeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="upgradePolicy" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="versionKey" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="scheduledHardwareUpgradeStatus" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="fault" type="{urn:vim25}LocalizedMethodFault" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ScheduledHardwareUpgradeInfo", propOrder = {
    "upgradePolicy",
    "versionKey",
    "scheduledHardwareUpgradeStatus",
    "fault"
})
public class ScheduledHardwareUpgradeInfo
    extends DynamicData
{

    protected String upgradePolicy;
    protected String versionKey;
    protected String scheduledHardwareUpgradeStatus;
    protected LocalizedMethodFault fault;

    /**
     * Gets the value of the upgradePolicy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUpgradePolicy() {
        return upgradePolicy;
    }

    /**
     * Sets the value of the upgradePolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUpgradePolicy(String value) {
        this.upgradePolicy = value;
    }

    /**
     * Gets the value of the versionKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVersionKey() {
        return versionKey;
    }

    /**
     * Sets the value of the versionKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVersionKey(String value) {
        this.versionKey = value;
    }

    /**
     * Gets the value of the scheduledHardwareUpgradeStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getScheduledHardwareUpgradeStatus() {
        return scheduledHardwareUpgradeStatus;
    }

    /**
     * Sets the value of the scheduledHardwareUpgradeStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setScheduledHardwareUpgradeStatus(String value) {
        this.scheduledHardwareUpgradeStatus = value;
    }

    /**
     * Gets the value of the fault property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public LocalizedMethodFault getFault() {
        return fault;
    }

    /**
     * Sets the value of the fault property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public void setFault(LocalizedMethodFault value) {
        this.fault = value;
    }

}
