
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidController complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InvalidController"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InvalidDeviceSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="controllerKey" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidController", propOrder = {
    "controllerKey"
})
public class InvalidController
    extends InvalidDeviceSpec
{

    protected int controllerKey;

    /**
     * Gets the value of the controllerKey property.
     * 
     */
    public int getControllerKey() {
        return controllerKey;
    }

    /**
     * Sets the value of the controllerKey property.
     * 
     */
    public void setControllerKey(int value) {
        this.controllerKey = value;
    }

}
