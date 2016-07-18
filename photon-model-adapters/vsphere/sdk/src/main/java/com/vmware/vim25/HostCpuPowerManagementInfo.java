
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostCpuPowerManagementInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostCpuPowerManagementInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="currentPolicy" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="hardwareSupport" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostCpuPowerManagementInfo", propOrder = {
    "currentPolicy",
    "hardwareSupport"
})
public class HostCpuPowerManagementInfo
    extends DynamicData
{

    protected String currentPolicy;
    protected String hardwareSupport;

    /**
     * Gets the value of the currentPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCurrentPolicy() {
        return currentPolicy;
    }

    /**
     * Sets the value of the currentPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCurrentPolicy(String value) {
        this.currentPolicy = value;
    }

    /**
     * Gets the value of the hardwareSupport property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHardwareSupport() {
        return hardwareSupport;
    }

    /**
     * Sets the value of the hardwareSupport property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHardwareSupport(String value) {
        this.hardwareSupport = value;
    }

}
