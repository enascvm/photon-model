
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmRestartedOnAlternateHostEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmRestartedOnAlternateHostEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VmPoweredOnEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="sourceHost" type="{urn:vim25}HostEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmRestartedOnAlternateHostEvent", propOrder = {
    "sourceHost"
})
public class VmRestartedOnAlternateHostEvent
    extends VmPoweredOnEvent
{

    @XmlElement(required = true)
    protected HostEventArgument sourceHost;

    /**
     * Gets the value of the sourceHost property.
     * 
     * @return
     *     possible object is
     *     {@link HostEventArgument }
     *     
     */
    public HostEventArgument getSourceHost() {
        return sourceHost;
    }

    /**
     * Sets the value of the sourceHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostEventArgument }
     *     
     */
    public void setSourceHost(HostEventArgument value) {
        this.sourceHost = value;
    }

}
