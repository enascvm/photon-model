
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineWipeResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineWipeResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="diskId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="shrinkableDiskSpace" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineWipeResult", propOrder = {
    "diskId",
    "shrinkableDiskSpace"
})
public class VirtualMachineWipeResult
    extends DynamicData
{

    protected int diskId;
    protected long shrinkableDiskSpace;

    /**
     * Gets the value of the diskId property.
     * 
     */
    public int getDiskId() {
        return diskId;
    }

    /**
     * Sets the value of the diskId property.
     * 
     */
    public void setDiskId(int value) {
        this.diskId = value;
    }

    /**
     * Gets the value of the shrinkableDiskSpace property.
     * 
     */
    public long getShrinkableDiskSpace() {
        return shrinkableDiskSpace;
    }

    /**
     * Sets the value of the shrinkableDiskSpace property.
     * 
     */
    public void setShrinkableDiskSpace(long value) {
        this.shrinkableDiskSpace = value;
    }

}
