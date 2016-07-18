
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OptionDef complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OptionDef"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ElementDescription"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="optionType" type="{urn:vim25}OptionType"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OptionDef", propOrder = {
    "optionType"
})
public class OptionDef
    extends ElementDescription
{

    @XmlElement(required = true)
    protected OptionType optionType;

    /**
     * Gets the value of the optionType property.
     * 
     * @return
     *     possible object is
     *     {@link OptionType }
     *     
     */
    public OptionType getOptionType() {
        return optionType;
    }

    /**
     * Sets the value of the optionType property.
     * 
     * @param value
     *     allowed object is
     *     {@link OptionType }
     *     
     */
    public void setOptionType(OptionType value) {
        this.optionType = value;
    }

}
