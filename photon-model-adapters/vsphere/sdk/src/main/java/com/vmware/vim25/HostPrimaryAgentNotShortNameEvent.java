
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostPrimaryAgentNotShortNameEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostPrimaryAgentNotShortNameEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostDasEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="primaryAgent" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostPrimaryAgentNotShortNameEvent", propOrder = {
    "primaryAgent"
})
public class HostPrimaryAgentNotShortNameEvent
    extends HostDasEvent
{

    @XmlElement(required = true)
    protected String primaryAgent;

    /**
     * Gets the value of the primaryAgent property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrimaryAgent() {
        return primaryAgent;
    }

    /**
     * Sets the value of the primaryAgent property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrimaryAgent(String value) {
        this.primaryAgent = value;
    }

}
