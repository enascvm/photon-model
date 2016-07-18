
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ResourcePoolSummary complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ResourcePoolSummary"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="config" type="{urn:vim25}ResourceConfigSpec"/&gt;
 *         &lt;element name="runtime" type="{urn:vim25}ResourcePoolRuntimeInfo"/&gt;
 *         &lt;element name="quickStats" type="{urn:vim25}ResourcePoolQuickStats" minOccurs="0"/&gt;
 *         &lt;element name="configuredMemoryMB" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResourcePoolSummary", propOrder = {
    "name",
    "config",
    "runtime",
    "quickStats",
    "configuredMemoryMB"
})
@XmlSeeAlso({
    VirtualAppSummary.class
})
public class ResourcePoolSummary
    extends DynamicData
{

    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected ResourceConfigSpec config;
    @XmlElement(required = true)
    protected ResourcePoolRuntimeInfo runtime;
    protected ResourcePoolQuickStats quickStats;
    protected Integer configuredMemoryMB;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the config property.
     * 
     * @return
     *     possible object is
     *     {@link ResourceConfigSpec }
     *     
     */
    public ResourceConfigSpec getConfig() {
        return config;
    }

    /**
     * Sets the value of the config property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResourceConfigSpec }
     *     
     */
    public void setConfig(ResourceConfigSpec value) {
        this.config = value;
    }

    /**
     * Gets the value of the runtime property.
     * 
     * @return
     *     possible object is
     *     {@link ResourcePoolRuntimeInfo }
     *     
     */
    public ResourcePoolRuntimeInfo getRuntime() {
        return runtime;
    }

    /**
     * Sets the value of the runtime property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResourcePoolRuntimeInfo }
     *     
     */
    public void setRuntime(ResourcePoolRuntimeInfo value) {
        this.runtime = value;
    }

    /**
     * Gets the value of the quickStats property.
     * 
     * @return
     *     possible object is
     *     {@link ResourcePoolQuickStats }
     *     
     */
    public ResourcePoolQuickStats getQuickStats() {
        return quickStats;
    }

    /**
     * Sets the value of the quickStats property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResourcePoolQuickStats }
     *     
     */
    public void setQuickStats(ResourcePoolQuickStats value) {
        this.quickStats = value;
    }

    /**
     * Gets the value of the configuredMemoryMB property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getConfiguredMemoryMB() {
        return configuredMemoryMB;
    }

    /**
     * Sets the value of the configuredMemoryMB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setConfiguredMemoryMB(Integer value) {
        this.configuredMemoryMB = value;
    }

}
