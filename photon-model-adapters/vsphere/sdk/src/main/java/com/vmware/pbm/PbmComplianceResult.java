
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.LocalizedMethodFault;


/**
 * <p>Java class for PbmComplianceResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmComplianceResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="checkTime" type="{http://www.w3.org/2001/XMLSchema}dateTime"/&gt;
 *         &lt;element name="entity" type="{urn:pbm}PbmServerObjectRef"/&gt;
 *         &lt;element name="profile" type="{urn:pbm}PbmProfileId" minOccurs="0"/&gt;
 *         &lt;element name="complianceTaskStatus" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="complianceStatus" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="mismatch" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="violatedPolicies" type="{urn:pbm}PbmCompliancePolicyStatus" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="errorCause" type="{urn:vim25}LocalizedMethodFault" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="operationalStatus" type="{urn:pbm}PbmComplianceOperationalStatus" minOccurs="0"/&gt;
 *         &lt;element name="info" type="{urn:pbm}PbmExtendedElementDescription" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmComplianceResult", propOrder = {
    "checkTime",
    "entity",
    "profile",
    "complianceTaskStatus",
    "complianceStatus",
    "mismatch",
    "violatedPolicies",
    "errorCause",
    "operationalStatus",
    "info"
})
public class PbmComplianceResult
    extends DynamicData
{

    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar checkTime;
    @XmlElement(required = true)
    protected PbmServerObjectRef entity;
    protected PbmProfileId profile;
    protected String complianceTaskStatus;
    @XmlElement(required = true)
    protected String complianceStatus;
    protected boolean mismatch;
    protected List<PbmCompliancePolicyStatus> violatedPolicies;
    protected List<LocalizedMethodFault> errorCause;
    protected PbmComplianceOperationalStatus operationalStatus;
    protected PbmExtendedElementDescription info;

    /**
     * Gets the value of the checkTime property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getCheckTime() {
        return checkTime;
    }

    /**
     * Sets the value of the checkTime property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setCheckTime(XMLGregorianCalendar value) {
        this.checkTime = value;
    }

    /**
     * Gets the value of the entity property.
     * 
     * @return
     *     possible object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public PbmServerObjectRef getEntity() {
        return entity;
    }

    /**
     * Sets the value of the entity property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public void setEntity(PbmServerObjectRef value) {
        this.entity = value;
    }

    /**
     * Gets the value of the profile property.
     * 
     * @return
     *     possible object is
     *     {@link PbmProfileId }
     *     
     */
    public PbmProfileId getProfile() {
        return profile;
    }

    /**
     * Sets the value of the profile property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmProfileId }
     *     
     */
    public void setProfile(PbmProfileId value) {
        this.profile = value;
    }

    /**
     * Gets the value of the complianceTaskStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getComplianceTaskStatus() {
        return complianceTaskStatus;
    }

    /**
     * Sets the value of the complianceTaskStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setComplianceTaskStatus(String value) {
        this.complianceTaskStatus = value;
    }

    /**
     * Gets the value of the complianceStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getComplianceStatus() {
        return complianceStatus;
    }

    /**
     * Sets the value of the complianceStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setComplianceStatus(String value) {
        this.complianceStatus = value;
    }

    /**
     * Gets the value of the mismatch property.
     * 
     */
    public boolean isMismatch() {
        return mismatch;
    }

    /**
     * Sets the value of the mismatch property.
     * 
     */
    public void setMismatch(boolean value) {
        this.mismatch = value;
    }

    /**
     * Gets the value of the violatedPolicies property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the violatedPolicies property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getViolatedPolicies().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCompliancePolicyStatus }
     * 
     * 
     */
    public List<PbmCompliancePolicyStatus> getViolatedPolicies() {
        if (violatedPolicies == null) {
            violatedPolicies = new ArrayList<PbmCompliancePolicyStatus>();
        }
        return this.violatedPolicies;
    }

    /**
     * Gets the value of the errorCause property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the errorCause property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getErrorCause().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LocalizedMethodFault }
     * 
     * 
     */
    public List<LocalizedMethodFault> getErrorCause() {
        if (errorCause == null) {
            errorCause = new ArrayList<LocalizedMethodFault>();
        }
        return this.errorCause;
    }

    /**
     * Gets the value of the operationalStatus property.
     * 
     * @return
     *     possible object is
     *     {@link PbmComplianceOperationalStatus }
     *     
     */
    public PbmComplianceOperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    /**
     * Sets the value of the operationalStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmComplianceOperationalStatus }
     *     
     */
    public void setOperationalStatus(PbmComplianceOperationalStatus value) {
        this.operationalStatus = value;
    }

    /**
     * Gets the value of the info property.
     * 
     * @return
     *     possible object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public PbmExtendedElementDescription getInfo() {
        return info;
    }

    /**
     * Sets the value of the info property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public void setInfo(PbmExtendedElementDescription value) {
        this.info = value;
    }

}
