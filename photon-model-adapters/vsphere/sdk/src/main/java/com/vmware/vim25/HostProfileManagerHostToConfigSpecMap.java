
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostProfileManagerHostToConfigSpecMap complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostProfileManagerHostToConfigSpecMap"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="host" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="configSpec" type="{urn:vim25}AnswerFileCreateSpec"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostProfileManagerHostToConfigSpecMap", propOrder = {
    "host",
    "configSpec"
})
public class HostProfileManagerHostToConfigSpecMap
    extends DynamicData
{

    @XmlElement(required = true)
    protected ManagedObjectReference host;
    @XmlElement(required = true)
    protected AnswerFileCreateSpec configSpec;

    /**
     * Gets the value of the host property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getHost() {
        return host;
    }

    /**
     * Sets the value of the host property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setHost(ManagedObjectReference value) {
        this.host = value;
    }

    /**
     * Gets the value of the configSpec property.
     * 
     * @return
     *     possible object is
     *     {@link AnswerFileCreateSpec }
     *     
     */
    public AnswerFileCreateSpec getConfigSpec() {
        return configSpec;
    }

    /**
     * Sets the value of the configSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link AnswerFileCreateSpec }
     *     
     */
    public void setConfigSpec(AnswerFileCreateSpec value) {
        this.configSpec = value;
    }

}
