
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostSriovNetworkDevicePoolInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostSriovNetworkDevicePoolInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostSriovDevicePoolInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="switchKey" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="switchUuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="pnic" type="{urn:vim25}PhysicalNic" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostSriovNetworkDevicePoolInfo", propOrder = {
    "switchKey",
    "switchUuid",
    "pnic"
})
public class HostSriovNetworkDevicePoolInfo
    extends HostSriovDevicePoolInfo
{

    protected String switchKey;
    protected String switchUuid;
    protected List<PhysicalNic> pnic;

    /**
     * Gets the value of the switchKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSwitchKey() {
        return switchKey;
    }

    /**
     * Sets the value of the switchKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSwitchKey(String value) {
        this.switchKey = value;
    }

    /**
     * Gets the value of the switchUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSwitchUuid() {
        return switchUuid;
    }

    /**
     * Sets the value of the switchUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSwitchUuid(String value) {
        this.switchUuid = value;
    }

    /**
     * Gets the value of the pnic property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pnic property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPnic().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PhysicalNic }
     * 
     * 
     */
    public List<PhysicalNic> getPnic() {
        if (pnic == null) {
            pnic = new ArrayList<PhysicalNic>();
        }
        return this.pnic;
    }

}
