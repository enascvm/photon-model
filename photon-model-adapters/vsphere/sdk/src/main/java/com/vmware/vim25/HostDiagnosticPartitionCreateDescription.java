
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostDiagnosticPartitionCreateDescription complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostDiagnosticPartitionCreateDescription"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="layout" type="{urn:vim25}HostDiskPartitionLayout"/&gt;
 *         &lt;element name="diskUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="spec" type="{urn:vim25}HostDiagnosticPartitionCreateSpec"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostDiagnosticPartitionCreateDescription", propOrder = {
    "layout",
    "diskUuid",
    "spec"
})
public class HostDiagnosticPartitionCreateDescription
    extends DynamicData
{

    @XmlElement(required = true)
    protected HostDiskPartitionLayout layout;
    @XmlElement(required = true)
    protected String diskUuid;
    @XmlElement(required = true)
    protected HostDiagnosticPartitionCreateSpec spec;

    /**
     * Gets the value of the layout property.
     * 
     * @return
     *     possible object is
     *     {@link HostDiskPartitionLayout }
     *     
     */
    public HostDiskPartitionLayout getLayout() {
        return layout;
    }

    /**
     * Sets the value of the layout property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostDiskPartitionLayout }
     *     
     */
    public void setLayout(HostDiskPartitionLayout value) {
        this.layout = value;
    }

    /**
     * Gets the value of the diskUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDiskUuid() {
        return diskUuid;
    }

    /**
     * Sets the value of the diskUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDiskUuid(String value) {
        this.diskUuid = value;
    }

    /**
     * Gets the value of the spec property.
     * 
     * @return
     *     possible object is
     *     {@link HostDiagnosticPartitionCreateSpec }
     *     
     */
    public HostDiagnosticPartitionCreateSpec getSpec() {
        return spec;
    }

    /**
     * Sets the value of the spec property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostDiagnosticPartitionCreateSpec }
     *     
     */
    public void setSpec(HostDiagnosticPartitionCreateSpec value) {
        this.spec = value;
    }

}
