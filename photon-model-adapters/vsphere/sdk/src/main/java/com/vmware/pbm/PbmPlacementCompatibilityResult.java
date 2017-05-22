
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.LocalizedMethodFault;


/**
 * <p>Java class for PbmPlacementCompatibilityResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPlacementCompatibilityResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hub" type="{urn:pbm}PbmPlacementHub"/&gt;
 *         &lt;element name="matchingResources" type="{urn:pbm}PbmPlacementMatchingResources" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="howMany" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="utilization" type="{urn:pbm}PbmPlacementResourceUtilization" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="warning" type="{urn:vim25}LocalizedMethodFault" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="error" type="{urn:vim25}LocalizedMethodFault" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPlacementCompatibilityResult", propOrder = {
    "hub",
    "matchingResources",
    "howMany",
    "utilization",
    "warning",
    "error"
})
public class PbmPlacementCompatibilityResult
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmPlacementHub hub;
    protected List<PbmPlacementMatchingResources> matchingResources;
    protected Long howMany;
    protected List<PbmPlacementResourceUtilization> utilization;
    protected List<LocalizedMethodFault> warning;
    protected List<LocalizedMethodFault> error;

    /**
     * Gets the value of the hub property.
     * 
     * @return
     *     possible object is
     *     {@link PbmPlacementHub }
     *     
     */
    public PbmPlacementHub getHub() {
        return hub;
    }

    /**
     * Sets the value of the hub property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmPlacementHub }
     *     
     */
    public void setHub(PbmPlacementHub value) {
        this.hub = value;
    }

    /**
     * Gets the value of the matchingResources property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the matchingResources property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMatchingResources().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementMatchingResources }
     * 
     * 
     */
    public List<PbmPlacementMatchingResources> getMatchingResources() {
        if (matchingResources == null) {
            matchingResources = new ArrayList<PbmPlacementMatchingResources>();
        }
        return this.matchingResources;
    }

    /**
     * Gets the value of the howMany property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getHowMany() {
        return howMany;
    }

    /**
     * Sets the value of the howMany property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setHowMany(Long value) {
        this.howMany = value;
    }

    /**
     * Gets the value of the utilization property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the utilization property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUtilization().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementResourceUtilization }
     * 
     * 
     */
    public List<PbmPlacementResourceUtilization> getUtilization() {
        if (utilization == null) {
            utilization = new ArrayList<PbmPlacementResourceUtilization>();
        }
        return this.utilization;
    }

    /**
     * Gets the value of the warning property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the warning property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getWarning().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LocalizedMethodFault }
     * 
     * 
     */
    public List<LocalizedMethodFault> getWarning() {
        if (warning == null) {
            warning = new ArrayList<LocalizedMethodFault>();
        }
        return this.warning;
    }

    /**
     * Gets the value of the error property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the error property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getError().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LocalizedMethodFault }
     * 
     * 
     */
    public List<LocalizedMethodFault> getError() {
        if (error == null) {
            error = new ArrayList<LocalizedMethodFault>();
        }
        return this.error;
    }

}
