
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskPartitionedRawDiskVer2BackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskPartitionedRawDiskVer2BackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDiskRawDiskVer2BackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="partition" type="{http://www.w3.org/2001/XMLSchema}int" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskPartitionedRawDiskVer2BackingInfo", propOrder = {
    "partition"
})
public class VirtualDiskPartitionedRawDiskVer2BackingInfo
    extends VirtualDiskRawDiskVer2BackingInfo
{

    @XmlElement(type = Integer.class)
    protected List<Integer> partition;

    /**
     * Gets the value of the partition property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the partition property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPartition().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Integer }
     * 
     * 
     */
    public List<Integer> getPartition() {
        if (partition == null) {
            partition = new ArrayList<Integer>();
        }
        return this.partition;
    }

}
