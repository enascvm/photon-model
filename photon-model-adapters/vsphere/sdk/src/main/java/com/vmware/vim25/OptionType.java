
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OptionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OptionType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="valueIsReadonly" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OptionType", propOrder = {
    "valueIsReadonly"
})
@XmlSeeAlso({
    BoolOption.class,
    ChoiceOption.class,
    FloatOption.class,
    IntOption.class,
    LongOption.class,
    StringOption.class
})
public class OptionType
    extends DynamicData
{

    protected Boolean valueIsReadonly;

    /**
     * Gets the value of the valueIsReadonly property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isValueIsReadonly() {
        return valueIsReadonly;
    }

    /**
     * Sets the value of the valueIsReadonly property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setValueIsReadonly(Boolean value) {
        this.valueIsReadonly = value;
    }

}
