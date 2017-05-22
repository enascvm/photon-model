
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmCapabilityConstraintInstance complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmCapabilityConstraintInstance"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmCapabilityConstraintInstance" type="{urn:pbm}PbmCapabilityConstraintInstance" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmCapabilityConstraintInstance", propOrder = {
    "pbmCapabilityConstraintInstance"
})
public class ArrayOfPbmCapabilityConstraintInstance {

    @XmlElement(name = "PbmCapabilityConstraintInstance")
    protected List<PbmCapabilityConstraintInstance> pbmCapabilityConstraintInstance;

    /**
     * Gets the value of the pbmCapabilityConstraintInstance property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmCapabilityConstraintInstance property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmCapabilityConstraintInstance().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityConstraintInstance }
     * 
     * 
     */
    public List<PbmCapabilityConstraintInstance> getPbmCapabilityConstraintInstance() {
        if (pbmCapabilityConstraintInstance == null) {
            pbmCapabilityConstraintInstance = new ArrayList<PbmCapabilityConstraintInstance>();
        }
        return this.pbmCapabilityConstraintInstance;
    }

}
