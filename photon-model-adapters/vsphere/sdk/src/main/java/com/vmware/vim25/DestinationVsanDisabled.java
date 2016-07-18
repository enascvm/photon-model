
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DestinationVsanDisabled complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DestinationVsanDisabled"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}CannotMoveVsanEnabledHost"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="destinationCluster" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DestinationVsanDisabled", propOrder = {
    "destinationCluster"
})
public class DestinationVsanDisabled
    extends CannotMoveVsanEnabledHost
{

    @XmlElement(required = true)
    protected String destinationCluster;

    /**
     * Gets the value of the destinationCluster property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDestinationCluster() {
        return destinationCluster;
    }

    /**
     * Sets the value of the destinationCluster property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDestinationCluster(String value) {
        this.destinationCluster = value;
    }

}
