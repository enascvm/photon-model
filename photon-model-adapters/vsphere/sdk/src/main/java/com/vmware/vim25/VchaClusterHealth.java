
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterHealth complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VchaClusterHealth"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="runtimeInfo" type="{urn:vim25}VchaClusterRuntimeInfo"/&gt;
 *         &lt;element name="healthMessages" type="{urn:vim25}LocalizableMessage" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="additionalInformation" type="{urn:vim25}LocalizableMessage" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VchaClusterHealth", propOrder = {
    "runtimeInfo",
    "healthMessages",
    "additionalInformation"
})
public class VchaClusterHealth
    extends DynamicData
{

    @XmlElement(required = true)
    protected VchaClusterRuntimeInfo runtimeInfo;
    protected List<LocalizableMessage> healthMessages;
    protected List<LocalizableMessage> additionalInformation;

    /**
     * Gets the value of the runtimeInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VchaClusterRuntimeInfo }
     *     
     */
    public VchaClusterRuntimeInfo getRuntimeInfo() {
        return runtimeInfo;
    }

    /**
     * Sets the value of the runtimeInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VchaClusterRuntimeInfo }
     *     
     */
    public void setRuntimeInfo(VchaClusterRuntimeInfo value) {
        this.runtimeInfo = value;
    }

    /**
     * Gets the value of the healthMessages property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the healthMessages property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHealthMessages().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LocalizableMessage }
     * 
     * 
     */
    public List<LocalizableMessage> getHealthMessages() {
        if (healthMessages == null) {
            healthMessages = new ArrayList<LocalizableMessage>();
        }
        return this.healthMessages;
    }

    /**
     * Gets the value of the additionalInformation property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the additionalInformation property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAdditionalInformation().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LocalizableMessage }
     * 
     * 
     */
    public List<LocalizableMessage> getAdditionalInformation() {
        if (additionalInformation == null) {
            additionalInformation = new ArrayList<LocalizableMessage>();
        }
        return this.additionalInformation;
    }

}
