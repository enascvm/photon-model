
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsHealthStatusChangeEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsHealthStatusChangeEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="switchUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="healthResult" type="{urn:vim25}HostMemberHealthCheckResult" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsHealthStatusChangeEvent", propOrder = {
    "switchUuid",
    "healthResult"
})
@XmlSeeAlso({
    UplinkPortVlanTrunkedEvent.class,
    UplinkPortVlanUntrunkedEvent.class,
    MtuMatchEvent.class,
    MtuMismatchEvent.class,
    UplinkPortMtuNotSupportEvent.class,
    UplinkPortMtuSupportEvent.class,
    TeamingMatchEvent.class,
    TeamingMisMatchEvent.class
})
public class DvsHealthStatusChangeEvent
    extends HostEvent
{

    @XmlElement(required = true)
    protected String switchUuid;
    protected HostMemberHealthCheckResult healthResult;

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
     * Gets the value of the healthResult property.
     * 
     * @return
     *     possible object is
     *     {@link HostMemberHealthCheckResult }
     *     
     */
    public HostMemberHealthCheckResult getHealthResult() {
        return healthResult;
    }

    /**
     * Sets the value of the healthResult property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostMemberHealthCheckResult }
     *     
     */
    public void setHealthResult(HostMemberHealthCheckResult value) {
        this.healthResult = value;
    }

}
