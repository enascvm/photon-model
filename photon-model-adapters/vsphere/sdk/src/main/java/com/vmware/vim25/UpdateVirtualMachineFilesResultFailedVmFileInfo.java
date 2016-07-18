
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UpdateVirtualMachineFilesResultFailedVmFileInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UpdateVirtualMachineFilesResultFailedVmFileInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vmFile" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="fault" type="{urn:vim25}LocalizedMethodFault"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UpdateVirtualMachineFilesResultFailedVmFileInfo", propOrder = {
    "vmFile",
    "fault"
})
public class UpdateVirtualMachineFilesResultFailedVmFileInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String vmFile;
    @XmlElement(required = true)
    protected LocalizedMethodFault fault;

    /**
     * Gets the value of the vmFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVmFile() {
        return vmFile;
    }

    /**
     * Sets the value of the vmFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVmFile(String value) {
        this.vmFile = value;
    }

    /**
     * Gets the value of the fault property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public LocalizedMethodFault getFault() {
        return fault;
    }

    /**
     * Sets the value of the fault property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public void setFault(LocalizedMethodFault value) {
        this.fault = value;
    }

}
