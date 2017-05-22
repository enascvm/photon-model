
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmComplianceOperationalStatus complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmComplianceOperationalStatus"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="healthy" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="operationETA" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/&gt;
 *         &lt;element name="operationProgress" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="transitional" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmComplianceOperationalStatus", propOrder = {
    "healthy",
    "operationETA",
    "operationProgress",
    "transitional"
})
public class PbmComplianceOperationalStatus
    extends DynamicData
{

    protected Boolean healthy;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar operationETA;
    protected Long operationProgress;
    protected Boolean transitional;

    /**
     * Gets the value of the healthy property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isHealthy() {
        return healthy;
    }

    /**
     * Sets the value of the healthy property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setHealthy(Boolean value) {
        this.healthy = value;
    }

    /**
     * Gets the value of the operationETA property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getOperationETA() {
        return operationETA;
    }

    /**
     * Sets the value of the operationETA property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setOperationETA(XMLGregorianCalendar value) {
        this.operationETA = value;
    }

    /**
     * Gets the value of the operationProgress property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getOperationProgress() {
        return operationProgress;
    }

    /**
     * Sets the value of the operationProgress property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setOperationProgress(Long value) {
        this.operationProgress = value;
    }

    /**
     * Gets the value of the transitional property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isTransitional() {
        return transitional;
    }

    /**
     * Sets the value of the transitional property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setTransitional(Boolean value) {
        this.transitional = value;
    }

}
