
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareUplinkLacpPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMwareUplinkLacpPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InheritablePolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="enable" type="{urn:vim25}BoolPolicy" minOccurs="0"/&gt;
 *         &lt;element name="mode" type="{urn:vim25}StringPolicy" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VMwareUplinkLacpPolicy", propOrder = {
    "enable",
    "mode"
})
public class VMwareUplinkLacpPolicy
    extends InheritablePolicy
{

    protected BoolPolicy enable;
    protected StringPolicy mode;

    /**
     * Gets the value of the enable property.
     * 
     * @return
     *     possible object is
     *     {@link BoolPolicy }
     *     
     */
    public BoolPolicy getEnable() {
        return enable;
    }

    /**
     * Sets the value of the enable property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolPolicy }
     *     
     */
    public void setEnable(BoolPolicy value) {
        this.enable = value;
    }

    /**
     * Gets the value of the mode property.
     * 
     * @return
     *     possible object is
     *     {@link StringPolicy }
     *     
     */
    public StringPolicy getMode() {
        return mode;
    }

    /**
     * Sets the value of the mode property.
     * 
     * @param value
     *     allowed object is
     *     {@link StringPolicy }
     *     
     */
    public void setMode(StringPolicy value) {
        this.mode = value;
    }

}
