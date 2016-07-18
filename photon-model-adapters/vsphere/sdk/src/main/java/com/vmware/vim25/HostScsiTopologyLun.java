
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostScsiTopologyLun complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostScsiTopologyLun"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="key" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="lun" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="scsiLun" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostScsiTopologyLun", propOrder = {
    "key",
    "lun",
    "scsiLun"
})
public class HostScsiTopologyLun
    extends DynamicData
{

    @XmlElement(required = true)
    protected String key;
    protected int lun;
    @XmlElement(required = true)
    protected String scsiLun;

    /**
     * Gets the value of the key property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the value of the key property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKey(String value) {
        this.key = value;
    }

    /**
     * Gets the value of the lun property.
     * 
     */
    public int getLun() {
        return lun;
    }

    /**
     * Sets the value of the lun property.
     * 
     */
    public void setLun(int value) {
        this.lun = value;
    }

    /**
     * Gets the value of the scsiLun property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getScsiLun() {
        return scsiLun;
    }

    /**
     * Sets the value of the scsiLun property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setScsiLun(String value) {
        this.scsiLun = value;
    }

}
