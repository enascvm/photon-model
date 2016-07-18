
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanHostMembershipInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanHostMembershipInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="nodeUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="hostname" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanHostMembershipInfo", propOrder = {
    "nodeUuid",
    "hostname"
})
public class VsanHostMembershipInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String nodeUuid;
    @XmlElement(required = true)
    protected String hostname;

    /**
     * Gets the value of the nodeUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNodeUuid() {
        return nodeUuid;
    }

    /**
     * Sets the value of the nodeUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNodeUuid(String value) {
        this.nodeUuid = value;
    }

    /**
     * Gets the value of the hostname property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Sets the value of the hostname property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostname(String value) {
        this.hostname = value;
    }

}
