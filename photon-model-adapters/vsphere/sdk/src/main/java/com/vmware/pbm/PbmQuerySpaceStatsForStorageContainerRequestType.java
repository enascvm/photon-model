
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.ManagedObjectReference;


/**
 * <p>Java class for PbmQuerySpaceStatsForStorageContainerRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmQuerySpaceStatsForStorageContainerRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="datastore" type="{urn:pbm}PbmServerObjectRef"/&gt;
 *         &lt;element name="capabilityProfileId" type="{urn:pbm}PbmProfileId" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmQuerySpaceStatsForStorageContainerRequestType", propOrder = {
    "_this",
    "datastore",
    "capabilityProfileId"
})
public class PbmQuerySpaceStatsForStorageContainerRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected PbmServerObjectRef datastore;
    protected List<PbmProfileId> capabilityProfileId;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the datastore property.
     * 
     * @return
     *     possible object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public PbmServerObjectRef getDatastore() {
        return datastore;
    }

    /**
     * Sets the value of the datastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public void setDatastore(PbmServerObjectRef value) {
        this.datastore = value;
    }

    /**
     * Gets the value of the capabilityProfileId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the capabilityProfileId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCapabilityProfileId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmProfileId }
     * 
     * 
     */
    public List<PbmProfileId> getCapabilityProfileId() {
        if (capabilityProfileId == null) {
            capabilityProfileId = new ArrayList<PbmProfileId>();
        }
        return this.capabilityProfileId;
    }

}
