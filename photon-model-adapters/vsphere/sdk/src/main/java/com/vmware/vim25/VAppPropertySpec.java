
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VAppPropertySpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VAppPropertySpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ArrayUpdateSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="info" type="{urn:vim25}VAppPropertyInfo" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VAppPropertySpec", propOrder = {
    "info"
})
public class VAppPropertySpec
    extends ArrayUpdateSpec
{

    protected VAppPropertyInfo info;

    /**
     * Gets the value of the info property.
     * 
     * @return
     *     possible object is
     *     {@link VAppPropertyInfo }
     *     
     */
    public VAppPropertyInfo getInfo() {
        return info;
    }

    /**
     * Sets the value of the info property.
     * 
     * @param value
     *     allowed object is
     *     {@link VAppPropertyInfo }
     *     
     */
    public void setInfo(VAppPropertyInfo value) {
        this.info = value;
    }

}
