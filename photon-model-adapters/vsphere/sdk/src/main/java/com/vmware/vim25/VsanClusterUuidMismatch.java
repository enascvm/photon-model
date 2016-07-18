
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanClusterUuidMismatch complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanClusterUuidMismatch"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}CannotMoveVsanEnabledHost"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostClusterUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="destinationClusterUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanClusterUuidMismatch", propOrder = {
    "hostClusterUuid",
    "destinationClusterUuid"
})
public class VsanClusterUuidMismatch
    extends CannotMoveVsanEnabledHost
{

    @XmlElement(required = true)
    protected String hostClusterUuid;
    @XmlElement(required = true)
    protected String destinationClusterUuid;

    /**
     * Gets the value of the hostClusterUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostClusterUuid() {
        return hostClusterUuid;
    }

    /**
     * Sets the value of the hostClusterUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostClusterUuid(String value) {
        this.hostClusterUuid = value;
    }

    /**
     * Gets the value of the destinationClusterUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDestinationClusterUuid() {
        return destinationClusterUuid;
    }

    /**
     * Sets the value of the destinationClusterUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDestinationClusterUuid(String value) {
        this.destinationClusterUuid = value;
    }

}
