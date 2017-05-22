
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.ManagedObjectReference;


/**
 * <p>Java class for PbmCheckRequirementsRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCheckRequirementsRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="hubsToSearch" type="{urn:pbm}PbmPlacementHub" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="placementSubjectRef" type="{urn:pbm}PbmServerObjectRef" minOccurs="0"/&gt;
 *         &lt;element name="placementSubjectRequirement" type="{urn:pbm}PbmPlacementRequirement" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCheckRequirementsRequestType", propOrder = {
    "_this",
    "hubsToSearch",
    "placementSubjectRef",
    "placementSubjectRequirement"
})
public class PbmCheckRequirementsRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    protected List<PbmPlacementHub> hubsToSearch;
    protected PbmServerObjectRef placementSubjectRef;
    protected List<PbmPlacementRequirement> placementSubjectRequirement;

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
     * Gets the value of the hubsToSearch property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hubsToSearch property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHubsToSearch().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementHub }
     * 
     * 
     */
    public List<PbmPlacementHub> getHubsToSearch() {
        if (hubsToSearch == null) {
            hubsToSearch = new ArrayList<PbmPlacementHub>();
        }
        return this.hubsToSearch;
    }

    /**
     * Gets the value of the placementSubjectRef property.
     * 
     * @return
     *     possible object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public PbmServerObjectRef getPlacementSubjectRef() {
        return placementSubjectRef;
    }

    /**
     * Sets the value of the placementSubjectRef property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public void setPlacementSubjectRef(PbmServerObjectRef value) {
        this.placementSubjectRef = value;
    }

    /**
     * Gets the value of the placementSubjectRequirement property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the placementSubjectRequirement property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPlacementSubjectRequirement().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementRequirement }
     * 
     * 
     */
    public List<PbmPlacementRequirement> getPlacementSubjectRequirement() {
        if (placementSubjectRequirement == null) {
            placementSubjectRequirement = new ArrayList<PbmPlacementRequirement>();
        }
        return this.placementSubjectRequirement;
    }

}
