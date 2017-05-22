
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VStorageObjectConfigInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VStorageObjectConfigInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}BaseConfigInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="capacityInMB" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="consumptionType" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="consumerId" type="{urn:vim25}ID" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VStorageObjectConfigInfo", propOrder = {
    "capacityInMB",
    "consumptionType",
    "consumerId"
})
public class VStorageObjectConfigInfo
    extends BaseConfigInfo
{

    protected long capacityInMB;
    protected List<String> consumptionType;
    protected List<ID> consumerId;

    /**
     * Gets the value of the capacityInMB property.
     * 
     */
    public long getCapacityInMB() {
        return capacityInMB;
    }

    /**
     * Sets the value of the capacityInMB property.
     * 
     */
    public void setCapacityInMB(long value) {
        this.capacityInMB = value;
    }

    /**
     * Gets the value of the consumptionType property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the consumptionType property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConsumptionType().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getConsumptionType() {
        if (consumptionType == null) {
            consumptionType = new ArrayList<String>();
        }
        return this.consumptionType;
    }

    /**
     * Gets the value of the consumerId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the consumerId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConsumerId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ID }
     * 
     * 
     */
    public List<ID> getConsumerId() {
        if (consumerId == null) {
            consumerId = new ArrayList<ID>();
        }
        return this.consumerId;
    }

}
