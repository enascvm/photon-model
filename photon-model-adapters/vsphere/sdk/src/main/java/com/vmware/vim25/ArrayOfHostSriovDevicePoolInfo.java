
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostSriovDevicePoolInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostSriovDevicePoolInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HostSriovDevicePoolInfo" type="{urn:vim25}HostSriovDevicePoolInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostSriovDevicePoolInfo", propOrder = {
    "hostSriovDevicePoolInfo"
})
public class ArrayOfHostSriovDevicePoolInfo {

    @XmlElement(name = "HostSriovDevicePoolInfo")
    protected List<HostSriovDevicePoolInfo> hostSriovDevicePoolInfo;

    /**
     * Gets the value of the hostSriovDevicePoolInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostSriovDevicePoolInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostSriovDevicePoolInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostSriovDevicePoolInfo }
     * 
     * 
     */
    public List<HostSriovDevicePoolInfo> getHostSriovDevicePoolInfo() {
        if (hostSriovDevicePoolInfo == null) {
            hostSriovDevicePoolInfo = new ArrayList<HostSriovDevicePoolInfo>();
        }
        return this.hostSriovDevicePoolInfo;
    }

}
