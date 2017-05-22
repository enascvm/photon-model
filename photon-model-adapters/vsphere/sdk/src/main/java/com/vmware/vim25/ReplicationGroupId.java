
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReplicationGroupId complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ReplicationGroupId"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="faultDomainId" type="{urn:vim25}FaultDomainId"/&gt;
 *         &lt;element name="deviceGroupId" type="{urn:vim25}DeviceGroupId"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReplicationGroupId", propOrder = {
    "faultDomainId",
    "deviceGroupId"
})
public class ReplicationGroupId
    extends DynamicData
{

    @XmlElement(required = true)
    protected FaultDomainId faultDomainId;
    @XmlElement(required = true)
    protected DeviceGroupId deviceGroupId;

    /**
     * Gets the value of the faultDomainId property.
     * 
     * @return
     *     possible object is
     *     {@link FaultDomainId }
     *     
     */
    public FaultDomainId getFaultDomainId() {
        return faultDomainId;
    }

    /**
     * Sets the value of the faultDomainId property.
     * 
     * @param value
     *     allowed object is
     *     {@link FaultDomainId }
     *     
     */
    public void setFaultDomainId(FaultDomainId value) {
        this.faultDomainId = value;
    }

    /**
     * Gets the value of the deviceGroupId property.
     * 
     * @return
     *     possible object is
     *     {@link DeviceGroupId }
     *     
     */
    public DeviceGroupId getDeviceGroupId() {
        return deviceGroupId;
    }

    /**
     * Sets the value of the deviceGroupId property.
     * 
     * @param value
     *     allowed object is
     *     {@link DeviceGroupId }
     *     
     */
    public void setDeviceGroupId(DeviceGroupId value) {
        this.deviceGroupId = value;
    }

}
