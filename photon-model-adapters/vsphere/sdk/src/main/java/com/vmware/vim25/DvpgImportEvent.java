
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvpgImportEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvpgImportEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DVPortgroupEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="importType" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvpgImportEvent", propOrder = {
    "importType"
})
public class DvpgImportEvent
    extends DVPortgroupEvent
{

    @XmlElement(required = true)
    protected String importType;

    /**
     * Gets the value of the importType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getImportType() {
        return importType;
    }

    /**
     * Sets the value of the importType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setImportType(String value) {
        this.importType = value;
    }

}
