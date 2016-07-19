
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FloatOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FloatOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OptionType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="min" type="{http://www.w3.org/2001/XMLSchema}float"/&gt;
 *         &lt;element name="max" type="{http://www.w3.org/2001/XMLSchema}float"/&gt;
 *         &lt;element name="defaultValue" type="{http://www.w3.org/2001/XMLSchema}float"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FloatOption", propOrder = {
    "min",
    "max",
    "defaultValue"
})
public class FloatOption
    extends OptionType
{

    protected float min;
    protected float max;
    protected float defaultValue;

    /**
     * Gets the value of the min property.
     * 
     */
    public float getMin() {
        return min;
    }

    /**
     * Sets the value of the min property.
     * 
     */
    public void setMin(float value) {
        this.min = value;
    }

    /**
     * Gets the value of the max property.
     * 
     */
    public float getMax() {
        return max;
    }

    /**
     * Sets the value of the max property.
     * 
     */
    public void setMax(float value) {
        this.max = value;
    }

    /**
     * Gets the value of the defaultValue property.
     * 
     */
    public float getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the value of the defaultValue property.
     * 
     */
    public void setDefaultValue(float value) {
        this.defaultValue = value;
    }

}
