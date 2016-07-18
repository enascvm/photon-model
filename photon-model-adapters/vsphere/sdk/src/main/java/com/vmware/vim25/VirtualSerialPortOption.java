
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualSerialPortOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualSerialPortOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="yieldOnPoll" type="{urn:vim25}BoolOption"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualSerialPortOption", propOrder = {
    "yieldOnPoll"
})
public class VirtualSerialPortOption
    extends VirtualDeviceOption
{

    @XmlElement(required = true)
    protected BoolOption yieldOnPoll;

    /**
     * Gets the value of the yieldOnPoll property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getYieldOnPoll() {
        return yieldOnPoll;
    }

    /**
     * Sets the value of the yieldOnPoll property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setYieldOnPoll(BoolOption value) {
        this.yieldOnPoll = value;
    }

}
