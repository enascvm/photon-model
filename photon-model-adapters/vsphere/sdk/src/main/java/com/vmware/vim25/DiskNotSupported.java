
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DiskNotSupported complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DiskNotSupported"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualHardwareCompatibilityIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="disk" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DiskNotSupported", propOrder = {
    "disk"
})
@XmlSeeAlso({
    IDEDiskNotSupported.class
})
public class DiskNotSupported
    extends VirtualHardwareCompatibilityIssue
{

    protected int disk;

    /**
     * Gets the value of the disk property.
     * 
     */
    public int getDisk() {
        return disk;
    }

    /**
     * Sets the value of the disk property.
     * 
     */
    public void setDisk(int value) {
        this.disk = value;
    }

}
