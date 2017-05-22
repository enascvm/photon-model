
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostGraphicsConfig complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostGraphicsConfig"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostDefaultGraphicsType" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="sharedPassthruAssignmentPolicy" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="deviceType" type="{urn:vim25}HostGraphicsConfigDeviceType" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostGraphicsConfig", propOrder = {
    "hostDefaultGraphicsType",
    "sharedPassthruAssignmentPolicy",
    "deviceType"
})
public class HostGraphicsConfig
    extends DynamicData
{

    @XmlElement(required = true)
    protected String hostDefaultGraphicsType;
    @XmlElement(required = true)
    protected String sharedPassthruAssignmentPolicy;
    protected List<HostGraphicsConfigDeviceType> deviceType;

    /**
     * Gets the value of the hostDefaultGraphicsType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostDefaultGraphicsType() {
        return hostDefaultGraphicsType;
    }

    /**
     * Sets the value of the hostDefaultGraphicsType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostDefaultGraphicsType(String value) {
        this.hostDefaultGraphicsType = value;
    }

    /**
     * Gets the value of the sharedPassthruAssignmentPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSharedPassthruAssignmentPolicy() {
        return sharedPassthruAssignmentPolicy;
    }

    /**
     * Sets the value of the sharedPassthruAssignmentPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSharedPassthruAssignmentPolicy(String value) {
        this.sharedPassthruAssignmentPolicy = value;
    }

    /**
     * Gets the value of the deviceType property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the deviceType property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDeviceType().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostGraphicsConfigDeviceType }
     * 
     * 
     */
    public List<HostGraphicsConfigDeviceType> getDeviceType() {
        if (deviceType == null) {
            deviceType = new ArrayList<HostGraphicsConfigDeviceType>();
        }
        return this.deviceType;
    }

}
