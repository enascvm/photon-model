
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfApplyHostProfileConfigurationSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfApplyHostProfileConfigurationSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ApplyHostProfileConfigurationSpec" type="{urn:vim25}ApplyHostProfileConfigurationSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfApplyHostProfileConfigurationSpec", propOrder = {
    "applyHostProfileConfigurationSpec"
})
public class ArrayOfApplyHostProfileConfigurationSpec {

    @XmlElement(name = "ApplyHostProfileConfigurationSpec")
    protected List<ApplyHostProfileConfigurationSpec> applyHostProfileConfigurationSpec;

    /**
     * Gets the value of the applyHostProfileConfigurationSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the applyHostProfileConfigurationSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getApplyHostProfileConfigurationSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ApplyHostProfileConfigurationSpec }
     * 
     * 
     */
    public List<ApplyHostProfileConfigurationSpec> getApplyHostProfileConfigurationSpec() {
        if (applyHostProfileConfigurationSpec == null) {
            applyHostProfileConfigurationSpec = new ArrayList<ApplyHostProfileConfigurationSpec>();
        }
        return this.applyHostProfileConfigurationSpec;
    }

}
