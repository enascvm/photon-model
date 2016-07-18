
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TemplateUpgradeEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TemplateUpgradeEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="legacyTemplate" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TemplateUpgradeEvent", propOrder = {
    "legacyTemplate"
})
@XmlSeeAlso({
    TemplateBeingUpgradedEvent.class,
    TemplateUpgradeFailedEvent.class,
    TemplateUpgradedEvent.class
})
public class TemplateUpgradeEvent
    extends Event
{

    @XmlElement(required = true)
    protected String legacyTemplate;

    /**
     * Gets the value of the legacyTemplate property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLegacyTemplate() {
        return legacyTemplate;
    }

    /**
     * Sets the value of the legacyTemplate property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLegacyTemplate(String value) {
        this.legacyTemplate = value;
    }

}
