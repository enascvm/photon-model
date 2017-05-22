
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostProfileManagerHostToConfigSpecMap complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostProfileManagerHostToConfigSpecMap"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HostProfileManagerHostToConfigSpecMap" type="{urn:vim25}HostProfileManagerHostToConfigSpecMap" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostProfileManagerHostToConfigSpecMap", propOrder = {
    "hostProfileManagerHostToConfigSpecMap"
})
public class ArrayOfHostProfileManagerHostToConfigSpecMap {

    @XmlElement(name = "HostProfileManagerHostToConfigSpecMap")
    protected List<HostProfileManagerHostToConfigSpecMap> hostProfileManagerHostToConfigSpecMap;

    /**
     * Gets the value of the hostProfileManagerHostToConfigSpecMap property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostProfileManagerHostToConfigSpecMap property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostProfileManagerHostToConfigSpecMap().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostProfileManagerHostToConfigSpecMap }
     * 
     * 
     */
    public List<HostProfileManagerHostToConfigSpecMap> getHostProfileManagerHostToConfigSpecMap() {
        if (hostProfileManagerHostToConfigSpecMap == null) {
            hostProfileManagerHostToConfigSpecMap = new ArrayList<HostProfileManagerHostToConfigSpecMap>();
        }
        return this.hostProfileManagerHostToConfigSpecMap;
    }

}
