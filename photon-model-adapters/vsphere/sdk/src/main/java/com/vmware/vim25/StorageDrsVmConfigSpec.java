
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageDrsVmConfigSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StorageDrsVmConfigSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ArrayUpdateSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="info" type="{urn:vim25}StorageDrsVmConfigInfo" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StorageDrsVmConfigSpec", propOrder = {
    "info"
})
public class StorageDrsVmConfigSpec
    extends ArrayUpdateSpec
{

    protected StorageDrsVmConfigInfo info;

    /**
     * Gets the value of the info property.
     * 
     * @return
     *     possible object is
     *     {@link StorageDrsVmConfigInfo }
     *     
     */
    public StorageDrsVmConfigInfo getInfo() {
        return info;
    }

    /**
     * Sets the value of the info property.
     * 
     * @param value
     *     allowed object is
     *     {@link StorageDrsVmConfigInfo }
     *     
     */
    public void setInfo(StorageDrsVmConfigInfo value) {
        this.info = value;
    }

}
