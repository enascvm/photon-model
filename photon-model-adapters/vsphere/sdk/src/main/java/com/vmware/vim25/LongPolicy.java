
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LongPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LongPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InheritablePolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="value" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LongPolicy", propOrder = {
    "value"
})
public class LongPolicy
    extends InheritablePolicy
{

    protected Long value;

    /**
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setValue(Long value) {
        this.value = value;
    }

}
