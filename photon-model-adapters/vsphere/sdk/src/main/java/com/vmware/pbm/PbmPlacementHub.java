
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmPlacementHub complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPlacementHub"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hubType" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="hubId" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPlacementHub", propOrder = {
    "hubType",
    "hubId"
})
public class PbmPlacementHub
    extends DynamicData
{

    @XmlElement(required = true)
    protected String hubType;
    @XmlElement(required = true)
    protected String hubId;

    /**
     * Gets the value of the hubType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHubType() {
        return hubType;
    }

    /**
     * Sets the value of the hubType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHubType(String value) {
        this.hubType = value;
    }

    /**
     * Gets the value of the hubId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHubId() {
        return hubId;
    }

    /**
     * Sets the value of the hubId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHubId(String value) {
        this.hubId = value;
    }

}
