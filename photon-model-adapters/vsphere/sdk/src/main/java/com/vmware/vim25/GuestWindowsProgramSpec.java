
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestWindowsProgramSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestWindowsProgramSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}GuestProgramSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="startMinimized" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestWindowsProgramSpec", propOrder = {
    "startMinimized"
})
public class GuestWindowsProgramSpec
    extends GuestProgramSpec
{

    protected boolean startMinimized;

    /**
     * Gets the value of the startMinimized property.
     * 
     */
    public boolean isStartMinimized() {
        return startMinimized;
    }

    /**
     * Sets the value of the startMinimized property.
     * 
     */
    public void setStartMinimized(boolean value) {
        this.startMinimized = value;
    }

}
