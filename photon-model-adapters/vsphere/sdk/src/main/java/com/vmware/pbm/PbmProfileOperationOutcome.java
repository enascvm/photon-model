
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.LocalizedMethodFault;


/**
 * <p>Java class for PbmProfileOperationOutcome complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmProfileOperationOutcome"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="profileId" type="{urn:pbm}PbmProfileId"/&gt;
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
@XmlType(name = "PbmProfileOperationOutcome", propOrder = {
    "profileId",
    "fault"
})
public class PbmProfileOperationOutcome
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmProfileId profileId;
    protected LocalizedMethodFault fault;

    /**
     * Gets the value of the profileId property.
     * 
     * @return
     *     possible object is
     *     {@link PbmProfileId }
     *     
     */
    public PbmProfileId getProfileId() {
        return profileId;
    }

    /**
     * Sets the value of the profileId property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmProfileId }
     *     
     */
    public void setProfileId(PbmProfileId value) {
        this.profileId = value;
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
