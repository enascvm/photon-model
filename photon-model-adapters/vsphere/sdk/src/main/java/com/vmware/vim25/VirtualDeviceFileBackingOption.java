
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceFileBackingOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceFileBackingOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceBackingOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="fileNameExtensions" type="{urn:vim25}ChoiceOption" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDeviceFileBackingOption", propOrder = {
    "fileNameExtensions"
})
@XmlSeeAlso({
    VirtualCdromIsoBackingOption.class,
    VirtualDiskSparseVer1BackingOption.class,
    VirtualDiskSparseVer2BackingOption.class,
    VirtualDiskFlatVer1BackingOption.class,
    VirtualDiskFlatVer2BackingOption.class,
    VirtualDiskSeSparseBackingOption.class,
    VirtualFloppyImageBackingOption.class,
    VirtualParallelPortFileBackingOption.class,
    VirtualSerialPortFileBackingOption.class
})
public class VirtualDeviceFileBackingOption
    extends VirtualDeviceBackingOption
{

    protected ChoiceOption fileNameExtensions;

    /**
     * Gets the value of the fileNameExtensions property.
     * 
     * @return
     *     possible object is
     *     {@link ChoiceOption }
     *     
     */
    public ChoiceOption getFileNameExtensions() {
        return fileNameExtensions;
    }

    /**
     * Sets the value of the fileNameExtensions property.
     * 
     * @param value
     *     allowed object is
     *     {@link ChoiceOption }
     *     
     */
    public void setFileNameExtensions(ChoiceOption value) {
        this.fileNameExtensions = value;
    }

}
