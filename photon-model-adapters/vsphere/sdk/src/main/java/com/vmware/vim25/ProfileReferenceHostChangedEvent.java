
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProfileReferenceHostChangedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ProfileReferenceHostChangedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ProfileEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="referenceHost" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="referenceHostName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="prevReferenceHostName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ProfileReferenceHostChangedEvent", propOrder = {
    "referenceHost",
    "referenceHostName",
    "prevReferenceHostName"
})
public class ProfileReferenceHostChangedEvent
    extends ProfileEvent
{

    protected ManagedObjectReference referenceHost;
    protected String referenceHostName;
    protected String prevReferenceHostName;

    /**
     * Gets the value of the referenceHost property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getReferenceHost() {
        return referenceHost;
    }

    /**
     * Sets the value of the referenceHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setReferenceHost(ManagedObjectReference value) {
        this.referenceHost = value;
    }

    /**
     * Gets the value of the referenceHostName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReferenceHostName() {
        return referenceHostName;
    }

    /**
     * Sets the value of the referenceHostName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReferenceHostName(String value) {
        this.referenceHostName = value;
    }

    /**
     * Gets the value of the prevReferenceHostName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrevReferenceHostName() {
        return prevReferenceHostName;
    }

    /**
     * Sets the value of the prevReferenceHostName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrevReferenceHostName(String value) {
        this.prevReferenceHostName = value;
    }

}
