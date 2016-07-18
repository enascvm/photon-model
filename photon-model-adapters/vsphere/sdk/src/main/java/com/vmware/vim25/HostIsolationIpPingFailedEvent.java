
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostIsolationIpPingFailedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostIsolationIpPingFailedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostDasEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="isolationIp" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostIsolationIpPingFailedEvent", propOrder = {
    "isolationIp"
})
public class HostIsolationIpPingFailedEvent
    extends HostDasEvent
{

    @XmlElement(required = true)
    protected String isolationIp;

    /**
     * Gets the value of the isolationIp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIsolationIp() {
        return isolationIp;
    }

    /**
     * Sets the value of the isolationIp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIsolationIp(String value) {
        this.isolationIp = value;
    }

}
