
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualCdromPassthroughBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualCdromPassthroughBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceDeviceBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="exclusive" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualCdromPassthroughBackingInfo", propOrder = {
    "exclusive"
})
public class VirtualCdromPassthroughBackingInfo
    extends VirtualDeviceDeviceBackingInfo
{

    protected boolean exclusive;

    /**
     * Gets the value of the exclusive property.
     * 
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Sets the value of the exclusive property.
     * 
     */
    public void setExclusive(boolean value) {
        this.exclusive = value;
    }

}
