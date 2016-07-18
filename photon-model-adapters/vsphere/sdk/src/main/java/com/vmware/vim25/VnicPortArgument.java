
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VnicPortArgument complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VnicPortArgument"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vnic" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="port" type="{urn:vim25}DistributedVirtualSwitchPortConnection"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VnicPortArgument", propOrder = {
    "vnic",
    "port"
})
public class VnicPortArgument
    extends DynamicData
{

    @XmlElement(required = true)
    protected String vnic;
    @XmlElement(required = true)
    protected DistributedVirtualSwitchPortConnection port;

    /**
     * Gets the value of the vnic property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVnic() {
        return vnic;
    }

    /**
     * Sets the value of the vnic property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVnic(String value) {
        this.vnic = value;
    }

    /**
     * Gets the value of the port property.
     * 
     * @return
     *     possible object is
     *     {@link DistributedVirtualSwitchPortConnection }
     *     
     */
    public DistributedVirtualSwitchPortConnection getPort() {
        return port;
    }

    /**
     * Sets the value of the port property.
     * 
     * @param value
     *     allowed object is
     *     {@link DistributedVirtualSwitchPortConnection }
     *     
     */
    public void setPort(DistributedVirtualSwitchPortConnection value) {
        this.port = value;
    }

}
