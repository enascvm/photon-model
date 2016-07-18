
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterDasAamHostInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterDasAamHostInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterDasHostInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostDasState" type="{urn:vim25}ClusterDasAamNodeState" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="primaryHosts" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterDasAamHostInfo", propOrder = {
    "hostDasState",
    "primaryHosts"
})
public class ClusterDasAamHostInfo
    extends ClusterDasHostInfo
{

    protected List<ClusterDasAamNodeState> hostDasState;
    protected List<String> primaryHosts;

    /**
     * Gets the value of the hostDasState property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostDasState property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostDasState().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDasAamNodeState }
     * 
     * 
     */
    public List<ClusterDasAamNodeState> getHostDasState() {
        if (hostDasState == null) {
            hostDasState = new ArrayList<ClusterDasAamNodeState>();
        }
        return this.hostDasState;
    }

    /**
     * Gets the value of the primaryHosts property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the primaryHosts property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPrimaryHosts().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getPrimaryHosts() {
        if (primaryHosts == null) {
            primaryHosts = new ArrayList<String>();
        }
        return this.primaryHosts;
    }

}
