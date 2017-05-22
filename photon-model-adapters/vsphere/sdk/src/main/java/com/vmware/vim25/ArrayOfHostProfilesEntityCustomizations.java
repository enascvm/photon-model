
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostProfilesEntityCustomizations complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostProfilesEntityCustomizations"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HostProfilesEntityCustomizations" type="{urn:vim25}HostProfilesEntityCustomizations" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostProfilesEntityCustomizations", propOrder = {
    "hostProfilesEntityCustomizations"
})
public class ArrayOfHostProfilesEntityCustomizations {

    @XmlElement(name = "HostProfilesEntityCustomizations")
    protected List<HostProfilesEntityCustomizations> hostProfilesEntityCustomizations;

    /**
     * Gets the value of the hostProfilesEntityCustomizations property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostProfilesEntityCustomizations property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostProfilesEntityCustomizations().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostProfilesEntityCustomizations }
     * 
     * 
     */
    public List<HostProfilesEntityCustomizations> getHostProfilesEntityCustomizations() {
        if (hostProfilesEntityCustomizations == null) {
            hostProfilesEntityCustomizations = new ArrayList<HostProfilesEntityCustomizations>();
        }
        return this.hostProfilesEntityCustomizations;
    }

}
