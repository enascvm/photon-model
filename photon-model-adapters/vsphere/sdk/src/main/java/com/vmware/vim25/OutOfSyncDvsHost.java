
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OutOfSyncDvsHost complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OutOfSyncDvsHost"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostOutOfSync" type="{urn:vim25}DvsOutOfSyncHostArgument" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OutOfSyncDvsHost", propOrder = {
    "hostOutOfSync"
})
public class OutOfSyncDvsHost
    extends DvsEvent
{

    @XmlElement(required = true)
    protected List<DvsOutOfSyncHostArgument> hostOutOfSync;

    /**
     * Gets the value of the hostOutOfSync property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostOutOfSync property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostOutOfSync().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DvsOutOfSyncHostArgument }
     * 
     * 
     */
    public List<DvsOutOfSyncHostArgument> getHostOutOfSync() {
        if (hostOutOfSync == null) {
            hostOutOfSync = new ArrayList<DvsOutOfSyncHostArgument>();
        }
        return this.hostOutOfSync;
    }

}
