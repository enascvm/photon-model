
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterNetworkConfigSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterNetworkConfigSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="networkPortGroup" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="ipSettings" type="{urn:vim25}CustomizationIPSettings"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterNetworkConfigSpec", propOrder = {
    "networkPortGroup",
    "ipSettings"
})
public class ClusterNetworkConfigSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected ManagedObjectReference networkPortGroup;
    @XmlElement(required = true)
    protected CustomizationIPSettings ipSettings;

    /**
     * Gets the value of the networkPortGroup property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getNetworkPortGroup() {
        return networkPortGroup;
    }

    /**
     * Sets the value of the networkPortGroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setNetworkPortGroup(ManagedObjectReference value) {
        this.networkPortGroup = value;
    }

    /**
     * Gets the value of the ipSettings property.
     * 
     * @return
     *     possible object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public CustomizationIPSettings getIpSettings() {
        return ipSettings;
    }

    /**
     * Sets the value of the ipSettings property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public void setIpSettings(CustomizationIPSettings value) {
        this.ipSettings = value;
    }

}
