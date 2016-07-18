
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanUpgradeSystemAutoClaimEnabledOnHostsIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanUpgradeSystemAutoClaimEnabledOnHostsIssue"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VsanUpgradeSystemPreflightCheckIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hosts" type="{urn:vim25}ManagedObjectReference" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanUpgradeSystemAutoClaimEnabledOnHostsIssue", propOrder = {
    "hosts"
})
public class VsanUpgradeSystemAutoClaimEnabledOnHostsIssue
    extends VsanUpgradeSystemPreflightCheckIssue
{

    @XmlElement(required = true)
    protected List<ManagedObjectReference> hosts;

    /**
     * Gets the value of the hosts property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hosts property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHosts().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ManagedObjectReference }
     * 
     * 
     */
    public List<ManagedObjectReference> getHosts() {
        if (hosts == null) {
            hosts = new ArrayList<ManagedObjectReference>();
        }
        return this.hosts;
    }

}
