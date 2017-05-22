
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHealthUpdateInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHealthUpdateInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HealthUpdateInfo" type="{urn:vim25}HealthUpdateInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHealthUpdateInfo", propOrder = {
    "healthUpdateInfo"
})
public class ArrayOfHealthUpdateInfo {

    @XmlElement(name = "HealthUpdateInfo")
    protected List<HealthUpdateInfo> healthUpdateInfo;

    /**
     * Gets the value of the healthUpdateInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the healthUpdateInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHealthUpdateInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HealthUpdateInfo }
     * 
     * 
     */
    public List<HealthUpdateInfo> getHealthUpdateInfo() {
        if (healthUpdateInfo == null) {
            healthUpdateInfo = new ArrayList<HealthUpdateInfo>();
        }
        return this.healthUpdateInfo;
    }

}
