
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterFailoverHostAdmissionControlPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterFailoverHostAdmissionControlPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterDasAdmissionControlPolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="failoverHosts" type="{urn:vim25}ManagedObjectReference" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="failoverLevel" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterFailoverHostAdmissionControlPolicy", propOrder = {
    "failoverHosts",
    "failoverLevel"
})
public class ClusterFailoverHostAdmissionControlPolicy
    extends ClusterDasAdmissionControlPolicy
{

    protected List<ManagedObjectReference> failoverHosts;
    protected Integer failoverLevel;

    /**
     * Gets the value of the failoverHosts property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the failoverHosts property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFailoverHosts().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ManagedObjectReference }
     * 
     * 
     */
    public List<ManagedObjectReference> getFailoverHosts() {
        if (failoverHosts == null) {
            failoverHosts = new ArrayList<ManagedObjectReference>();
        }
        return this.failoverHosts;
    }

    /**
     * Gets the value of the failoverLevel property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getFailoverLevel() {
        return failoverLevel;
    }

    /**
     * Sets the value of the failoverLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setFailoverLevel(Integer value) {
        this.failoverLevel = value;
    }

}
