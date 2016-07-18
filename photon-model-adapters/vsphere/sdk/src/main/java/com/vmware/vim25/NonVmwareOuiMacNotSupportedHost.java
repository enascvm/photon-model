
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NonVmwareOuiMacNotSupportedHost complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NonVmwareOuiMacNotSupportedHost"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}NotSupportedHost"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NonVmwareOuiMacNotSupportedHost", propOrder = {
    "hostName"
})
public class NonVmwareOuiMacNotSupportedHost
    extends NotSupportedHost
{

    @XmlElement(required = true)
    protected String hostName;

    /**
     * Gets the value of the hostName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the value of the hostName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostName(String value) {
        this.hostName = value;
    }

}
