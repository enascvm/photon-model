
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMotionInterfaceIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMotionInterfaceIssue"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MigrationFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="atSourceHost" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="failedHost" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="failedHostEntity" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VMotionInterfaceIssue", propOrder = {
    "atSourceHost",
    "failedHost",
    "failedHostEntity"
})
@XmlSeeAlso({
    VMotionLinkCapacityLow.class,
    VMotionLinkDown.class,
    VMotionNotConfigured.class,
    VMotionNotLicensed.class,
    VMotionNotSupported.class
})
public class VMotionInterfaceIssue
    extends MigrationFault
{

    protected boolean atSourceHost;
    @XmlElement(required = true)
    protected String failedHost;
    protected ManagedObjectReference failedHostEntity;

    /**
     * Gets the value of the atSourceHost property.
     * 
     */
    public boolean isAtSourceHost() {
        return atSourceHost;
    }

    /**
     * Sets the value of the atSourceHost property.
     * 
     */
    public void setAtSourceHost(boolean value) {
        this.atSourceHost = value;
    }

    /**
     * Gets the value of the failedHost property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFailedHost() {
        return failedHost;
    }

    /**
     * Sets the value of the failedHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFailedHost(String value) {
        this.failedHost = value;
    }

    /**
     * Gets the value of the failedHostEntity property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getFailedHostEntity() {
        return failedHostEntity;
    }

    /**
     * Sets the value of the failedHostEntity property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setFailedHostEntity(ManagedObjectReference value) {
        this.failedHostEntity = value;
    }

}
