
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TooManyDisksOnLegacyHost complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TooManyDisksOnLegacyHost"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MigrationFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="diskCount" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="timeoutDanger" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TooManyDisksOnLegacyHost", propOrder = {
    "diskCount",
    "timeoutDanger"
})
public class TooManyDisksOnLegacyHost
    extends MigrationFault
{

    protected int diskCount;
    protected boolean timeoutDanger;

    /**
     * Gets the value of the diskCount property.
     * 
     */
    public int getDiskCount() {
        return diskCount;
    }

    /**
     * Sets the value of the diskCount property.
     * 
     */
    public void setDiskCount(int value) {
        this.diskCount = value;
    }

    /**
     * Gets the value of the timeoutDanger property.
     * 
     */
    public boolean isTimeoutDanger() {
        return timeoutDanger;
    }

    /**
     * Sets the value of the timeoutDanger property.
     * 
     */
    public void setTimeoutDanger(boolean value) {
        this.timeoutDanger = value;
    }

}
