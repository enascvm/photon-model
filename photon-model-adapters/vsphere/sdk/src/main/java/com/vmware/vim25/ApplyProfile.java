
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ApplyProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ApplyProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="enabled" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="policy" type="{urn:vim25}ProfilePolicy" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="profileTypeName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="profileVersion" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="property" type="{urn:vim25}ProfileApplyProfileProperty" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="favorite" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="toBeMerged" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="toReplaceWith" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="toBeDeleted" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="copyEnableStatus" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ApplyProfile", propOrder = {
    "enabled",
    "policy",
    "profileTypeName",
    "profileVersion",
    "property",
    "favorite",
    "toBeMerged",
    "toReplaceWith",
    "toBeDeleted",
    "copyEnableStatus"
})
@XmlSeeAlso({
    ProfileApplyProfileElement.class,
    HostApplyProfile.class,
    PhysicalNicProfile.class,
    HostMemoryProfile.class,
    UserProfile.class,
    UserGroupProfile.class,
    SecurityProfile.class,
    OptionProfile.class,
    DateTimeProfile.class,
    ServiceProfile.class,
    FirewallProfileRulesetProfile.class,
    FirewallProfile.class,
    NasStorageProfile.class,
    StorageProfile.class,
    NetworkProfileDnsConfigProfile.class,
    NetworkProfile.class,
    DvsVNicProfile.class,
    DvsProfile.class,
    PnicUplinkProfile.class,
    IpRouteProfile.class,
    StaticRouteProfile.class,
    LinkProfile.class,
    NumPortsProfile.class,
    VirtualSwitchProfile.class,
    NetStackInstanceProfile.class,
    VlanProfile.class,
    VirtualSwitchSelectionProfile.class,
    PortGroupProfile.class,
    NetworkPolicyProfile.class,
    IpAddressProfile.class,
    AuthenticationProfile.class,
    ActiveDirectoryProfile.class,
    PermissionProfile.class
})
public class ApplyProfile
    extends DynamicData
{

    protected boolean enabled;
    protected List<ProfilePolicy> policy;
    protected String profileTypeName;
    protected String profileVersion;
    protected List<ProfileApplyProfileProperty> property;
    protected Boolean favorite;
    protected Boolean toBeMerged;
    protected Boolean toReplaceWith;
    protected Boolean toBeDeleted;
    protected Boolean copyEnableStatus;

    /**
     * Gets the value of the enabled property.
     * 
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the value of the enabled property.
     * 
     */
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * Gets the value of the policy property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the policy property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPolicy().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProfilePolicy }
     * 
     * 
     */
    public List<ProfilePolicy> getPolicy() {
        if (policy == null) {
            policy = new ArrayList<ProfilePolicy>();
        }
        return this.policy;
    }

    /**
     * Gets the value of the profileTypeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileTypeName() {
        return profileTypeName;
    }

    /**
     * Sets the value of the profileTypeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileTypeName(String value) {
        this.profileTypeName = value;
    }

    /**
     * Gets the value of the profileVersion property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileVersion() {
        return profileVersion;
    }

    /**
     * Sets the value of the profileVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileVersion(String value) {
        this.profileVersion = value;
    }

    /**
     * Gets the value of the property property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the property property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProperty().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProfileApplyProfileProperty }
     * 
     * 
     */
    public List<ProfileApplyProfileProperty> getProperty() {
        if (property == null) {
            property = new ArrayList<ProfileApplyProfileProperty>();
        }
        return this.property;
    }

    /**
     * Gets the value of the favorite property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isFavorite() {
        return favorite;
    }

    /**
     * Sets the value of the favorite property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setFavorite(Boolean value) {
        this.favorite = value;
    }

    /**
     * Gets the value of the toBeMerged property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isToBeMerged() {
        return toBeMerged;
    }

    /**
     * Sets the value of the toBeMerged property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setToBeMerged(Boolean value) {
        this.toBeMerged = value;
    }

    /**
     * Gets the value of the toReplaceWith property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isToReplaceWith() {
        return toReplaceWith;
    }

    /**
     * Sets the value of the toReplaceWith property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setToReplaceWith(Boolean value) {
        this.toReplaceWith = value;
    }

    /**
     * Gets the value of the toBeDeleted property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isToBeDeleted() {
        return toBeDeleted;
    }

    /**
     * Sets the value of the toBeDeleted property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setToBeDeleted(Boolean value) {
        this.toBeDeleted = value;
    }

    /**
     * Gets the value of the copyEnableStatus property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isCopyEnableStatus() {
        return copyEnableStatus;
    }

    /**
     * Sets the value of the copyEnableStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setCopyEnableStatus(Boolean value) {
        this.copyEnableStatus = value;
    }

}
