
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComputeResourceHostSPBMLicenseInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ComputeResourceHostSPBMLicenseInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="host" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="licenseState" type="{urn:vim25}ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ComputeResourceHostSPBMLicenseInfo", propOrder = {
    "host",
    "licenseState"
})
public class ComputeResourceHostSPBMLicenseInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected ManagedObjectReference host;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState licenseState;

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
     * Gets the value of the licenseState property.
     * 
     * @return
     *     possible object is
     *     {@link ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState }
     *     
     */
    public ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState getLicenseState() {
        return licenseState;
    }

    /**
     * Sets the value of the licenseState property.
     * 
     * @param value
     *     allowed object is
     *     {@link ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState }
     *     
     */
    public void setLicenseState(ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState value) {
        this.licenseState = value;
    }

}
