
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.ManagedObjectReference;


/**
 * <p>Java class for PbmServiceInstanceContent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmServiceInstanceContent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="aboutInfo" type="{urn:pbm}PbmAboutInfo"/&gt;
 *         &lt;element name="sessionManager" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="capabilityMetadataManager" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="profileManager" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="complianceManager" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="placementSolver" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="replicationManager" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmServiceInstanceContent", propOrder = {
    "aboutInfo",
    "sessionManager",
    "capabilityMetadataManager",
    "profileManager",
    "complianceManager",
    "placementSolver",
    "replicationManager"
})
public class PbmServiceInstanceContent
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmAboutInfo aboutInfo;
    @XmlElement(required = true)
    protected ManagedObjectReference sessionManager;
    @XmlElement(required = true)
    protected ManagedObjectReference capabilityMetadataManager;
    @XmlElement(required = true)
    protected ManagedObjectReference profileManager;
    @XmlElement(required = true)
    protected ManagedObjectReference complianceManager;
    @XmlElement(required = true)
    protected ManagedObjectReference placementSolver;
    protected ManagedObjectReference replicationManager;

    /**
     * Gets the value of the aboutInfo property.
     * 
     * @return
     *     possible object is
     *     {@link PbmAboutInfo }
     *     
     */
    public PbmAboutInfo getAboutInfo() {
        return aboutInfo;
    }

    /**
     * Sets the value of the aboutInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmAboutInfo }
     *     
     */
    public void setAboutInfo(PbmAboutInfo value) {
        this.aboutInfo = value;
    }

    /**
     * Gets the value of the sessionManager property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getSessionManager() {
        return sessionManager;
    }

    /**
     * Sets the value of the sessionManager property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setSessionManager(ManagedObjectReference value) {
        this.sessionManager = value;
    }

    /**
     * Gets the value of the capabilityMetadataManager property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getCapabilityMetadataManager() {
        return capabilityMetadataManager;
    }

    /**
     * Sets the value of the capabilityMetadataManager property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setCapabilityMetadataManager(ManagedObjectReference value) {
        this.capabilityMetadataManager = value;
    }

    /**
     * Gets the value of the profileManager property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getProfileManager() {
        return profileManager;
    }

    /**
     * Sets the value of the profileManager property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setProfileManager(ManagedObjectReference value) {
        this.profileManager = value;
    }

    /**
     * Gets the value of the complianceManager property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getComplianceManager() {
        return complianceManager;
    }

    /**
     * Sets the value of the complianceManager property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setComplianceManager(ManagedObjectReference value) {
        this.complianceManager = value;
    }

    /**
     * Gets the value of the placementSolver property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getPlacementSolver() {
        return placementSolver;
    }

    /**
     * Sets the value of the placementSolver property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setPlacementSolver(ManagedObjectReference value) {
        this.placementSolver = value;
    }

    /**
     * Gets the value of the replicationManager property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getReplicationManager() {
        return replicationManager;
    }

    /**
     * Sets the value of the replicationManager property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setReplicationManager(ManagedObjectReference value) {
        this.replicationManager = value;
    }

}
