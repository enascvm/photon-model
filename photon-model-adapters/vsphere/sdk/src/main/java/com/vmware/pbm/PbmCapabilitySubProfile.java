
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilitySubProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilitySubProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="capability" type="{urn:pbm}PbmCapabilityInstance" maxOccurs="unbounded"/&gt;
 *         &lt;element name="forceProvision" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilitySubProfile", propOrder = {
    "name",
    "capability",
    "forceProvision"
})
public class PbmCapabilitySubProfile
    extends DynamicData
{

    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected List<PbmCapabilityInstance> capability;
    protected Boolean forceProvision;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the capability property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the capability property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCapability().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityInstance }
     * 
     * 
     */
    public List<PbmCapabilityInstance> getCapability() {
        if (capability == null) {
            capability = new ArrayList<PbmCapabilityInstance>();
        }
        return this.capability;
    }

    /**
     * Gets the value of the forceProvision property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isForceProvision() {
        return forceProvision;
    }

    /**
     * Sets the value of the forceProvision property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setForceProvision(Boolean value) {
        this.forceProvision = value;
    }

}
