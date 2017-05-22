
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineDefinedProfileSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineDefinedProfileSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualMachineProfileSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="profileId" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="replicationSpec" type="{urn:vim25}ReplicationSpec" minOccurs="0"/&gt;
 *         &lt;element name="profileData" type="{urn:vim25}VirtualMachineProfileRawData" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineDefinedProfileSpec", propOrder = {
    "profileId",
    "replicationSpec",
    "profileData"
})
public class VirtualMachineDefinedProfileSpec
    extends VirtualMachineProfileSpec
{

    @XmlElement(required = true)
    protected String profileId;
    protected ReplicationSpec replicationSpec;
    protected VirtualMachineProfileRawData profileData;

    /**
     * Gets the value of the profileId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the value of the profileId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileId(String value) {
        this.profileId = value;
    }

    /**
     * Gets the value of the replicationSpec property.
     * 
     * @return
     *     possible object is
     *     {@link ReplicationSpec }
     *     
     */
    public ReplicationSpec getReplicationSpec() {
        return replicationSpec;
    }

    /**
     * Sets the value of the replicationSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReplicationSpec }
     *     
     */
    public void setReplicationSpec(ReplicationSpec value) {
        this.replicationSpec = value;
    }

    /**
     * Gets the value of the profileData property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachineProfileRawData }
     *     
     */
    public VirtualMachineProfileRawData getProfileData() {
        return profileData;
    }

    /**
     * Sets the value of the profileData property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachineProfileRawData }
     *     
     */
    public void setProfileData(VirtualMachineProfileRawData value) {
        this.profileData = value;
    }

}
