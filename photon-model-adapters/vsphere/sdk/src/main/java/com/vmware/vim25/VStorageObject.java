
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VStorageObject complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VStorageObject"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="config" type="{urn:vim25}VStorageObjectConfigInfo"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VStorageObject", propOrder = {
    "config"
})
public class VStorageObject
    extends DynamicData
{

    @XmlElement(required = true)
    protected VStorageObjectConfigInfo config;

    /**
     * Gets the value of the config property.
     * 
     * @return
     *     possible object is
     *     {@link VStorageObjectConfigInfo }
     *     
     */
    public VStorageObjectConfigInfo getConfig() {
        return config;
    }

    /**
     * Sets the value of the config property.
     * 
     * @param value
     *     allowed object is
     *     {@link VStorageObjectConfigInfo }
     *     
     */
    public void setConfig(VStorageObjectConfigInfo value) {
        this.config = value;
    }

}
