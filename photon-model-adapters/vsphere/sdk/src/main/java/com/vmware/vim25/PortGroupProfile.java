
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PortGroupProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PortGroupProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ApplyProfile"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="key" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="vlan" type="{urn:vim25}VlanProfile"/&gt;
 *         &lt;element name="vswitch" type="{urn:vim25}VirtualSwitchSelectionProfile"/&gt;
 *         &lt;element name="networkPolicy" type="{urn:vim25}NetworkPolicyProfile"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PortGroupProfile", propOrder = {
    "key",
    "name",
    "vlan",
    "vswitch",
    "networkPolicy"
})
@XmlSeeAlso({
    VmPortGroupProfile.class,
    HostPortGroupProfile.class,
    ServiceConsolePortGroupProfile.class
})
public class PortGroupProfile
    extends ApplyProfile
{

    @XmlElement(required = true)
    protected String key;
    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected VlanProfile vlan;
    @XmlElement(required = true)
    protected VirtualSwitchSelectionProfile vswitch;
    @XmlElement(required = true)
    protected NetworkPolicyProfile networkPolicy;

    /**
     * Gets the value of the key property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the value of the key property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKey(String value) {
        this.key = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the vlan property.
     * 
     * @return
     *     possible object is
     *     {@link VlanProfile }
     *     
     */
    public VlanProfile getVlan() {
        return vlan;
    }

    /**
     * Sets the value of the vlan property.
     * 
     * @param value
     *     allowed object is
     *     {@link VlanProfile }
     *     
     */
    public void setVlan(VlanProfile value) {
        this.vlan = value;
    }

    /**
     * Gets the value of the vswitch property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualSwitchSelectionProfile }
     *     
     */
    public VirtualSwitchSelectionProfile getVswitch() {
        return vswitch;
    }

    /**
     * Sets the value of the vswitch property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualSwitchSelectionProfile }
     *     
     */
    public void setVswitch(VirtualSwitchSelectionProfile value) {
        this.vswitch = value;
    }

    /**
     * Gets the value of the networkPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link NetworkPolicyProfile }
     *     
     */
    public NetworkPolicyProfile getNetworkPolicy() {
        return networkPolicy;
    }

    /**
     * Sets the value of the networkPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link NetworkPolicyProfile }
     *     
     */
    public void setNetworkPolicy(NetworkPolicyProfile value) {
        this.networkPolicy = value;
    }

}
