
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SourceNodeSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SourceNodeSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="managementVc" type="{urn:vim25}ServiceLocator"/&gt;
 *         &lt;element name="activeVc" type="{urn:vim25}ManagedObjectReference"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SourceNodeSpec", propOrder = {
    "managementVc",
    "activeVc"
})
public class SourceNodeSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected ServiceLocator managementVc;
    @XmlElement(required = true)
    protected ManagedObjectReference activeVc;

    /**
     * Gets the value of the managementVc property.
     * 
     * @return
     *     possible object is
     *     {@link ServiceLocator }
     *     
     */
    public ServiceLocator getManagementVc() {
        return managementVc;
    }

    /**
     * Sets the value of the managementVc property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServiceLocator }
     *     
     */
    public void setManagementVc(ServiceLocator value) {
        this.managementVc = value;
    }

    /**
     * Gets the value of the activeVc property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getActiveVc() {
        return activeVc;
    }

    /**
     * Sets the value of the activeVc property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setActiveVc(ManagedObjectReference value) {
        this.activeVc = value;
    }

}
