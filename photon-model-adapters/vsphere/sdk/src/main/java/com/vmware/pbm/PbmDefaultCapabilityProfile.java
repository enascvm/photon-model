
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmDefaultCapabilityProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmDefaultCapabilityProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmCapabilityProfile"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vvolType" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded"/&gt;
 *         &lt;element name="containerId" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmDefaultCapabilityProfile", propOrder = {
    "vvolType",
    "containerId"
})
public class PbmDefaultCapabilityProfile
    extends PbmCapabilityProfile
{

    @XmlElement(required = true)
    protected List<String> vvolType;
    @XmlElement(required = true)
    protected String containerId;

    /**
     * Gets the value of the vvolType property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vvolType property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVvolType().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getVvolType() {
        if (vvolType == null) {
            vvolType = new ArrayList<String>();
        }
        return this.vvolType;
    }

    /**
     * Gets the value of the containerId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * Sets the value of the containerId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContainerId(String value) {
        this.containerId = value;
    }

}
