
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMotionLinkDown complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMotionLinkDown"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VMotionInterfaceIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="network" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VMotionLinkDown", propOrder = {
    "network"
})
public class VMotionLinkDown
    extends VMotionInterfaceIssue
{

    @XmlElement(required = true)
    protected String network;

    /**
     * Gets the value of the network property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNetwork() {
        return network;
    }

    /**
     * Sets the value of the network property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNetwork(String value) {
        this.network = value;
    }

}
