
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestRegistryFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestRegistryFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}GuestOperationsFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="windowsSystemErrorCode" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestRegistryFault", propOrder = {
    "windowsSystemErrorCode"
})
@XmlSeeAlso({
    GuestRegistryKeyFault.class,
    GuestRegistryValueFault.class
})
public class GuestRegistryFault
    extends GuestOperationsFault
{

    protected long windowsSystemErrorCode;

    /**
     * Gets the value of the windowsSystemErrorCode property.
     * 
     */
    public long getWindowsSystemErrorCode() {
        return windowsSystemErrorCode;
    }

    /**
     * Sets the value of the windowsSystemErrorCode property.
     * 
     */
    public void setWindowsSystemErrorCode(long value) {
        this.windowsSystemErrorCode = value;
    }

}
