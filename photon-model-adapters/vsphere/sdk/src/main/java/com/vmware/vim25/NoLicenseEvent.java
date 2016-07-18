
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NoLicenseEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NoLicenseEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}LicenseEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="feature" type="{urn:vim25}LicenseFeatureInfo"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NoLicenseEvent", propOrder = {
    "feature"
})
public class NoLicenseEvent
    extends LicenseEvent
{

    @XmlElement(required = true)
    protected LicenseFeatureInfo feature;

    /**
     * Gets the value of the feature property.
     * 
     * @return
     *     possible object is
     *     {@link LicenseFeatureInfo }
     *     
     */
    public LicenseFeatureInfo getFeature() {
        return feature;
    }

    /**
     * Sets the value of the feature property.
     * 
     * @param value
     *     allowed object is
     *     {@link LicenseFeatureInfo }
     *     
     */
    public void setFeature(LicenseFeatureInfo value) {
        this.feature = value;
    }

}
