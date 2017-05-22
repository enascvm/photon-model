
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.LocalizedMethodFault;


/**
 * <p>Java class for PbmQueryProfileResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmQueryProfileResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="object" type="{urn:pbm}PbmServerObjectRef"/&gt;
 *         &lt;element name="profileId" type="{urn:pbm}PbmProfileId" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="fault" type="{urn:vim25}LocalizedMethodFault" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmQueryProfileResult", propOrder = {
    "object",
    "profileId",
    "fault"
})
public class PbmQueryProfileResult
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmServerObjectRef object;
    protected List<PbmProfileId> profileId;
    protected LocalizedMethodFault fault;

    /**
     * Gets the value of the object property.
     * 
     * @return
     *     possible object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public PbmServerObjectRef getObject() {
        return object;
    }

    /**
     * Sets the value of the object property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public void setObject(PbmServerObjectRef value) {
        this.object = value;
    }

    /**
     * Gets the value of the profileId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the profileId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProfileId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmProfileId }
     * 
     * 
     */
    public List<PbmProfileId> getProfileId() {
        if (profileId == null) {
            profileId = new ArrayList<PbmProfileId>();
        }
        return this.profileId;
    }

    /**
     * Gets the value of the fault property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public LocalizedMethodFault getFault() {
        return fault;
    }

    /**
     * Sets the value of the fault property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public void setFault(LocalizedMethodFault value) {
        this.fault = value;
    }

}
