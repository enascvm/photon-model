
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsVNicProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsVNicProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ApplyProfile"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="key" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ipConfig" type="{urn:vim25}IpAddressProfile"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsVNicProfile", propOrder = {
    "key",
    "ipConfig"
})
@XmlSeeAlso({
    DvsServiceConsoleVNicProfile.class,
    DvsHostVNicProfile.class
})
public class DvsVNicProfile
    extends ApplyProfile
{

    @XmlElement(required = true)
    protected String key;
    @XmlElement(required = true)
    protected IpAddressProfile ipConfig;

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
     * Gets the value of the ipConfig property.
     * 
     * @return
     *     possible object is
     *     {@link IpAddressProfile }
     *     
     */
    public IpAddressProfile getIpConfig() {
        return ipConfig;
    }

    /**
     * Sets the value of the ipConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link IpAddressProfile }
     *     
     */
    public void setIpConfig(IpAddressProfile value) {
        this.ipConfig = value;
    }

}
