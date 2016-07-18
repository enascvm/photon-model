
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MismatchedBundle complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MismatchedBundle"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="bundleUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="hostUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="bundleBuildNumber" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="hostBuildNumber" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MismatchedBundle", propOrder = {
    "bundleUuid",
    "hostUuid",
    "bundleBuildNumber",
    "hostBuildNumber"
})
public class MismatchedBundle
    extends VimFault
{

    @XmlElement(required = true)
    protected String bundleUuid;
    @XmlElement(required = true)
    protected String hostUuid;
    protected int bundleBuildNumber;
    protected int hostBuildNumber;

    /**
     * Gets the value of the bundleUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBundleUuid() {
        return bundleUuid;
    }

    /**
     * Sets the value of the bundleUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBundleUuid(String value) {
        this.bundleUuid = value;
    }

    /**
     * Gets the value of the hostUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostUuid() {
        return hostUuid;
    }

    /**
     * Sets the value of the hostUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostUuid(String value) {
        this.hostUuid = value;
    }

    /**
     * Gets the value of the bundleBuildNumber property.
     * 
     */
    public int getBundleBuildNumber() {
        return bundleBuildNumber;
    }

    /**
     * Sets the value of the bundleBuildNumber property.
     * 
     */
    public void setBundleBuildNumber(int value) {
        this.bundleBuildNumber = value;
    }

    /**
     * Gets the value of the hostBuildNumber property.
     * 
     */
    public int getHostBuildNumber() {
        return hostBuildNumber;
    }

    /**
     * Sets the value of the hostBuildNumber property.
     * 
     */
    public void setHostBuildNumber(int value) {
        this.hostBuildNumber = value;
    }

}
