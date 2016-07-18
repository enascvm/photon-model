
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFibreChannelTargetTransport complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFibreChannelTargetTransport"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostTargetTransport"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="portWorldWideName" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="nodeWorldWideName" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFibreChannelTargetTransport", propOrder = {
    "portWorldWideName",
    "nodeWorldWideName"
})
@XmlSeeAlso({
    HostFibreChannelOverEthernetTargetTransport.class
})
public class HostFibreChannelTargetTransport
    extends HostTargetTransport
{

    protected long portWorldWideName;
    protected long nodeWorldWideName;

    /**
     * Gets the value of the portWorldWideName property.
     * 
     */
    public long getPortWorldWideName() {
        return portWorldWideName;
    }

    /**
     * Sets the value of the portWorldWideName property.
     * 
     */
    public void setPortWorldWideName(long value) {
        this.portWorldWideName = value;
    }

    /**
     * Gets the value of the nodeWorldWideName property.
     * 
     */
    public long getNodeWorldWideName() {
        return nodeWorldWideName;
    }

    /**
     * Sets the value of the nodeWorldWideName property.
     * 
     */
    public void setNodeWorldWideName(long value) {
        this.nodeWorldWideName = value;
    }

}
