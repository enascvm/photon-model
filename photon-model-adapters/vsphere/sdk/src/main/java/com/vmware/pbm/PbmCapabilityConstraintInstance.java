
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityConstraintInstance complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityConstraintInstance"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="propertyInstance" type="{urn:pbm}PbmCapabilityPropertyInstance" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityConstraintInstance", propOrder = {
    "propertyInstance"
})
public class PbmCapabilityConstraintInstance
    extends DynamicData
{

    @XmlElement(required = true)
    protected List<PbmCapabilityPropertyInstance> propertyInstance;

    /**
     * Gets the value of the propertyInstance property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the propertyInstance property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPropertyInstance().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityPropertyInstance }
     * 
     * 
     */
    public List<PbmCapabilityPropertyInstance> getPropertyInstance() {
        if (propertyInstance == null) {
            propertyInstance = new ArrayList<PbmCapabilityPropertyInstance>();
        }
        return this.propertyInstance;
    }

}
