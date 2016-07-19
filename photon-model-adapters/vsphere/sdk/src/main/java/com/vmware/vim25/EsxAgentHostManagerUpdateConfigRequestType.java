
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EsxAgentHostManagerUpdateConfigRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EsxAgentHostManagerUpdateConfigRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="configInfo" type="{urn:vim25}HostEsxAgentHostManagerConfigInfo"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EsxAgentHostManagerUpdateConfigRequestType", propOrder = {
    "_this",
    "configInfo"
})
public class EsxAgentHostManagerUpdateConfigRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected HostEsxAgentHostManagerConfigInfo configInfo;

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
     * Gets the value of the configInfo property.
     * 
     * @return
     *     possible object is
     *     {@link HostEsxAgentHostManagerConfigInfo }
     *     
     */
    public HostEsxAgentHostManagerConfigInfo getConfigInfo() {
        return configInfo;
    }

    /**
     * Sets the value of the configInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostEsxAgentHostManagerConfigInfo }
     *     
     */
    public void setConfigInfo(HostEsxAgentHostManagerConfigInfo value) {
        this.configInfo = value;
    }

}
