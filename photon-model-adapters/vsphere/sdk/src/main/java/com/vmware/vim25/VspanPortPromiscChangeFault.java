
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VspanPortPromiscChangeFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VspanPortPromiscChangeFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="portKey" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VspanPortPromiscChangeFault", propOrder = {
    "portKey"
})
public class VspanPortPromiscChangeFault
    extends DvsFault
{

    @XmlElement(required = true)
    protected String portKey;

    /**
     * Gets the value of the portKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPortKey() {
        return portKey;
    }

    /**
     * Sets the value of the portKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPortKey(String value) {
        this.portKey = value;
    }

}
