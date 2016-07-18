
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsOperationBulkFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsOperationBulkFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostFault" type="{urn:vim25}DvsOperationBulkFaultFaultOnHost" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsOperationBulkFault", propOrder = {
    "hostFault"
})
public class DvsOperationBulkFault
    extends DvsFault
{

    @XmlElement(required = true)
    protected List<DvsOperationBulkFaultFaultOnHost> hostFault;

    /**
     * Gets the value of the hostFault property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostFault property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostFault().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DvsOperationBulkFaultFaultOnHost }
     * 
     * 
     */
    public List<DvsOperationBulkFaultFaultOnHost> getHostFault() {
        if (hostFault == null) {
            hostFault = new ArrayList<DvsOperationBulkFaultFaultOnHost>();
        }
        return this.hostFault;
    }

}
