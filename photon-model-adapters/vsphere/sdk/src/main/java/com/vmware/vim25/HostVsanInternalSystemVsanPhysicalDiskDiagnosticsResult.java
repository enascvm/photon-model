
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="diskUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="success" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="failureReason" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult", propOrder = {
    "diskUuid",
    "success",
    "failureReason"
})
public class HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult
    extends DynamicData
{

    @XmlElement(required = true)
    protected String diskUuid;
    protected boolean success;
    protected String failureReason;

    /**
     * Gets the value of the diskUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDiskUuid() {
        return diskUuid;
    }

    /**
     * Sets the value of the diskUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDiskUuid(String value) {
        this.diskUuid = value;
    }

    /**
     * Gets the value of the success property.
     * 
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the value of the success property.
     * 
     */
    public void setSuccess(boolean value) {
        this.success = value;
    }

    /**
     * Gets the value of the failureReason property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Sets the value of the failureReason property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFailureReason(String value) {
        this.failureReason = value;
    }

}
