
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterInfraUpdateHaConfigInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterInfraUpdateHaConfigInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="enabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="behavior" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="moderateRemediation" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="severeRemediation" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="providers" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterInfraUpdateHaConfigInfo", propOrder = {
    "enabled",
    "behavior",
    "moderateRemediation",
    "severeRemediation",
    "providers"
})
public class ClusterInfraUpdateHaConfigInfo
    extends DynamicData
{

    protected Boolean enabled;
    protected String behavior;
    protected String moderateRemediation;
    protected String severeRemediation;
    protected List<String> providers;

    /**
     * Gets the value of the enabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the value of the enabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setEnabled(Boolean value) {
        this.enabled = value;
    }

    /**
     * Gets the value of the behavior property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBehavior() {
        return behavior;
    }

    /**
     * Sets the value of the behavior property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBehavior(String value) {
        this.behavior = value;
    }

    /**
     * Gets the value of the moderateRemediation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getModerateRemediation() {
        return moderateRemediation;
    }

    /**
     * Sets the value of the moderateRemediation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setModerateRemediation(String value) {
        this.moderateRemediation = value;
    }

    /**
     * Gets the value of the severeRemediation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSevereRemediation() {
        return severeRemediation;
    }

    /**
     * Sets the value of the severeRemediation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSevereRemediation(String value) {
        this.severeRemediation = value;
    }

    /**
     * Gets the value of the providers property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the providers property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProviders().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getProviders() {
        if (providers == null) {
            providers = new ArrayList<String>();
        }
        return this.providers;
    }

}
