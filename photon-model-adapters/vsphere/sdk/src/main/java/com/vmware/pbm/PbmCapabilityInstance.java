
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityInstance complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityInstance"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="id" type="{urn:pbm}PbmCapabilityMetadataUniqueId"/&gt;
 *         &lt;element name="constraint" type="{urn:pbm}PbmCapabilityConstraintInstance" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityInstance", propOrder = {
    "id",
    "constraint"
})
public class PbmCapabilityInstance
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmCapabilityMetadataUniqueId id;
    @XmlElement(required = true)
    protected List<PbmCapabilityConstraintInstance> constraint;

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityMetadataUniqueId }
     *     
     */
    public PbmCapabilityMetadataUniqueId getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityMetadataUniqueId }
     *     
     */
    public void setId(PbmCapabilityMetadataUniqueId value) {
        this.id = value;
    }

    /**
     * Gets the value of the constraint property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the constraint property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConstraint().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityConstraintInstance }
     * 
     * 
     */
    public List<PbmCapabilityConstraintInstance> getConstraint() {
        if (constraint == null) {
            constraint = new ArrayList<PbmCapabilityConstraintInstance>();
        }
        return this.constraint;
    }

}
