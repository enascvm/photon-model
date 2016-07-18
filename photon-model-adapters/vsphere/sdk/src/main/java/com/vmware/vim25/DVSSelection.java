
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DVSSelection complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DVSSelection"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}SelectionSet"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="dvsUuid" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DVSSelection", propOrder = {
    "dvsUuid"
})
public class DVSSelection
    extends SelectionSet
{

    @XmlElement(required = true)
    protected String dvsUuid;

    /**
     * Gets the value of the dvsUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDvsUuid() {
        return dvsUuid;
    }

    /**
     * Sets the value of the dvsUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDvsUuid(String value) {
        this.dvsUuid = value;
    }

}
