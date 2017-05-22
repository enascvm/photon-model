
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfUserPrivilegeResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfUserPrivilegeResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="UserPrivilegeResult" type="{urn:vim25}UserPrivilegeResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfUserPrivilegeResult", propOrder = {
    "userPrivilegeResult"
})
public class ArrayOfUserPrivilegeResult {

    @XmlElement(name = "UserPrivilegeResult")
    protected List<UserPrivilegeResult> userPrivilegeResult;

    /**
     * Gets the value of the userPrivilegeResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the userPrivilegeResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUserPrivilegeResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link UserPrivilegeResult }
     * 
     * 
     */
    public List<UserPrivilegeResult> getUserPrivilegeResult() {
        if (userPrivilegeResult == null) {
            userPrivilegeResult = new ArrayList<UserPrivilegeResult>();
        }
        return this.userPrivilegeResult;
    }

}
