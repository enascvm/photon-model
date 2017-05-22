
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RoleUpdatedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RoleUpdatedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}RoleEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="privilegeList" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="prevRoleName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="privilegesAdded" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="privilegesRemoved" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RoleUpdatedEvent", propOrder = {
    "privilegeList",
    "prevRoleName",
    "privilegesAdded",
    "privilegesRemoved"
})
public class RoleUpdatedEvent
    extends RoleEvent
{

    protected List<String> privilegeList;
    protected String prevRoleName;
    protected List<String> privilegesAdded;
    protected List<String> privilegesRemoved;

    /**
     * Gets the value of the privilegeList property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the privilegeList property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPrivilegeList().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getPrivilegeList() {
        if (privilegeList == null) {
            privilegeList = new ArrayList<String>();
        }
        return this.privilegeList;
    }

    /**
     * Gets the value of the prevRoleName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrevRoleName() {
        return prevRoleName;
    }

    /**
     * Sets the value of the prevRoleName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrevRoleName(String value) {
        this.prevRoleName = value;
    }

    /**
     * Gets the value of the privilegesAdded property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the privilegesAdded property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPrivilegesAdded().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getPrivilegesAdded() {
        if (privilegesAdded == null) {
            privilegesAdded = new ArrayList<String>();
        }
        return this.privilegesAdded;
    }

    /**
     * Gets the value of the privilegesRemoved property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the privilegesRemoved property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPrivilegesRemoved().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getPrivilegesRemoved() {
        if (privilegesRemoved == null) {
            privilegesRemoved = new ArrayList<String>();
        }
        return this.privilegesRemoved;
    }

}
