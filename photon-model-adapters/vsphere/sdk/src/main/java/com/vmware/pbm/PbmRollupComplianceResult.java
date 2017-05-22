
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
 * <p>Java class for PbmRollupComplianceResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmRollupComplianceResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="oldestCheckTime" type="{http://www.w3.org/2001/XMLSchema}dateTime"/&gt;
 *         &lt;element name="entity" type="{urn:pbm}PbmServerObjectRef"/&gt;
 *         &lt;element name="overallComplianceStatus" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="overallComplianceTaskStatus" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="result" type="{urn:pbm}PbmComplianceResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="errorCause" type="{urn:vim25}LocalizedMethodFault" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="profileMismatch" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmRollupComplianceResult", propOrder = {
    "oldestCheckTime",
    "entity",
    "overallComplianceStatus",
    "overallComplianceTaskStatus",
    "result",
    "errorCause",
    "profileMismatch"
})
public class PbmRollupComplianceResult
    extends DynamicData
{

    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar oldestCheckTime;
    @XmlElement(required = true)
    protected PbmServerObjectRef entity;
    @XmlElement(required = true)
    protected String overallComplianceStatus;
    protected String overallComplianceTaskStatus;
    protected List<PbmComplianceResult> result;
    protected List<LocalizedMethodFault> errorCause;
    protected boolean profileMismatch;

    /**
     * Gets the value of the oldestCheckTime property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getOldestCheckTime() {
        return oldestCheckTime;
    }

    /**
     * Sets the value of the oldestCheckTime property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setOldestCheckTime(XMLGregorianCalendar value) {
        this.oldestCheckTime = value;
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
     * Gets the value of the overallComplianceStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOverallComplianceStatus() {
        return overallComplianceStatus;
    }

    /**
     * Sets the value of the overallComplianceStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOverallComplianceStatus(String value) {
        this.overallComplianceStatus = value;
    }

    /**
     * Gets the value of the overallComplianceTaskStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOverallComplianceTaskStatus() {
        return overallComplianceTaskStatus;
    }

    /**
     * Sets the value of the overallComplianceTaskStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOverallComplianceTaskStatus(String value) {
        this.overallComplianceTaskStatus = value;
    }

    /**
     * Gets the value of the result property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the result property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmComplianceResult }
     * 
     * 
     */
    public List<PbmComplianceResult> getResult() {
        if (result == null) {
            result = new ArrayList<PbmComplianceResult>();
        }
        return this.result;
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
     * Gets the value of the profileMismatch property.
     * 
     */
    public boolean isProfileMismatch() {
        return profileMismatch;
    }

    /**
     * Sets the value of the profileMismatch property.
     * 
     */
    public void setProfileMismatch(boolean value) {
        this.profileMismatch = value;
    }

}
