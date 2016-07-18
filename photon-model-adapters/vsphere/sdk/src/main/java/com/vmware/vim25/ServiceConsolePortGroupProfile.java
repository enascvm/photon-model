
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServiceConsolePortGroupProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServiceConsolePortGroupProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}PortGroupProfile"&gt;
 *       &lt;sequence&gt;
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
@XmlType(name = "ServiceConsolePortGroupProfile", propOrder = {
    "ipConfig"
})
public class ServiceConsolePortGroupProfile
    extends PortGroupProfile
{

    @XmlElement(required = true)
    protected IpAddressProfile ipConfig;

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
