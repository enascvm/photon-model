
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterConfigSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VchaClusterConfigSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="passiveIp" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="witnessIp" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VchaClusterConfigSpec", propOrder = {
    "passiveIp",
    "witnessIp"
})
public class VchaClusterConfigSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected String passiveIp;
    @XmlElement(required = true)
    protected String witnessIp;

    /**
     * Gets the value of the passiveIp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPassiveIp() {
        return passiveIp;
    }

    /**
     * Sets the value of the passiveIp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPassiveIp(String value) {
        this.passiveIp = value;
    }

    /**
     * Gets the value of the witnessIp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWitnessIp() {
        return witnessIp;
    }

    /**
     * Sets the value of the witnessIp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWitnessIp(String value) {
        this.witnessIp = value;
    }

}
