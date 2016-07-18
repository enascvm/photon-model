
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReadOnlyDisksWithLegacyDestination complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ReadOnlyDisksWithLegacyDestination"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MigrationFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="roDiskCount" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
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
@XmlType(name = "ReadOnlyDisksWithLegacyDestination", propOrder = {
    "roDiskCount",
    "timeoutDanger"
})
public class ReadOnlyDisksWithLegacyDestination
    extends MigrationFault
{

    protected int roDiskCount;
    protected boolean timeoutDanger;

    /**
     * Gets the value of the roDiskCount property.
     * 
     */
    public int getRoDiskCount() {
        return roDiskCount;
    }

    /**
     * Sets the value of the roDiskCount property.
     * 
     */
    public void setRoDiskCount(int value) {
        this.roDiskCount = value;
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
