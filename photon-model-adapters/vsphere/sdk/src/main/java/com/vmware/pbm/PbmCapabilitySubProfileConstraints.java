
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCapabilitySubProfileConstraints complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilitySubProfileConstraints"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmCapabilityConstraints"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="subProfiles" type="{urn:pbm}PbmCapabilitySubProfile" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilitySubProfileConstraints", propOrder = {
    "subProfiles"
})
public class PbmCapabilitySubProfileConstraints
    extends PbmCapabilityConstraints
{

    @XmlElement(required = true)
    protected List<PbmCapabilitySubProfile> subProfiles;

    /**
     * Gets the value of the subProfiles property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subProfiles property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubProfiles().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilitySubProfile }
     * 
     * 
     */
    public List<PbmCapabilitySubProfile> getSubProfiles() {
        if (subProfiles == null) {
            subProfiles = new ArrayList<PbmCapabilitySubProfile>();
        }
        return this.subProfiles;
    }

}
