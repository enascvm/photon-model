
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UpdateSystemSwapConfigurationRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UpdateSystemSwapConfigurationRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="sysSwapConfig" type="{urn:vim25}HostSystemSwapConfiguration"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UpdateSystemSwapConfigurationRequestType", propOrder = {
    "_this",
    "sysSwapConfig"
})
public class UpdateSystemSwapConfigurationRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected HostSystemSwapConfiguration sysSwapConfig;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the sysSwapConfig property.
     * 
     * @return
     *     possible object is
     *     {@link HostSystemSwapConfiguration }
     *     
     */
    public HostSystemSwapConfiguration getSysSwapConfig() {
        return sysSwapConfig;
    }

    /**
     * Sets the value of the sysSwapConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostSystemSwapConfiguration }
     *     
     */
    public void setSysSwapConfig(HostSystemSwapConfiguration value) {
        this.sysSwapConfig = value;
    }

}
