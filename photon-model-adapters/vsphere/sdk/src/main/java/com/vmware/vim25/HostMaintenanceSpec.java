
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostMaintenanceSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostMaintenanceSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vsanMode" type="{urn:vim25}VsanHostDecommissionMode" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostMaintenanceSpec", propOrder = {
    "vsanMode"
})
public class HostMaintenanceSpec
    extends DynamicData
{

    protected VsanHostDecommissionMode vsanMode;

    /**
     * Gets the value of the vsanMode property.
     * 
     * @return
     *     possible object is
     *     {@link VsanHostDecommissionMode }
     *     
     */
    public VsanHostDecommissionMode getVsanMode() {
        return vsanMode;
    }

    /**
     * Sets the value of the vsanMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link VsanHostDecommissionMode }
     *     
     */
    public void setVsanMode(VsanHostDecommissionMode value) {
        this.vsanMode = value;
    }

}
