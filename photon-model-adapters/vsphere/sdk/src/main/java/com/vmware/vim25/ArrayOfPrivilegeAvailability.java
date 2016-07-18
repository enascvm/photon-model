
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPrivilegeAvailability complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPrivilegeAvailability"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PrivilegeAvailability" type="{urn:vim25}PrivilegeAvailability" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPrivilegeAvailability", propOrder = {
    "privilegeAvailability"
})
public class ArrayOfPrivilegeAvailability {

    @XmlElement(name = "PrivilegeAvailability")
    protected List<PrivilegeAvailability> privilegeAvailability;

    /**
     * Gets the value of the privilegeAvailability property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the privilegeAvailability property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPrivilegeAvailability().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PrivilegeAvailability }
     * 
     * 
     */
    public List<PrivilegeAvailability> getPrivilegeAvailability() {
        if (privilegeAvailability == null) {
            privilegeAvailability = new ArrayList<PrivilegeAvailability>();
        }
        return this.privilegeAvailability;
    }

}
