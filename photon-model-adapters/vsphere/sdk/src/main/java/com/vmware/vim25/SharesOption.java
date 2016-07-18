
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SharesOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SharesOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="sharesOption" type="{urn:vim25}IntOption"/&gt;
 *         &lt;element name="defaultLevel" type="{urn:vim25}SharesLevel"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SharesOption", propOrder = {
    "sharesOption",
    "defaultLevel"
})
public class SharesOption
    extends DynamicData
{

    @XmlElement(required = true)
    protected IntOption sharesOption;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected SharesLevel defaultLevel;

    /**
     * Gets the value of the sharesOption property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getSharesOption() {
        return sharesOption;
    }

    /**
     * Sets the value of the sharesOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setSharesOption(IntOption value) {
        this.sharesOption = value;
    }

    /**
     * Gets the value of the defaultLevel property.
     * 
     * @return
     *     possible object is
     *     {@link SharesLevel }
     *     
     */
    public SharesLevel getDefaultLevel() {
        return defaultLevel;
    }

    /**
     * Sets the value of the defaultLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link SharesLevel }
     *     
     */
    public void setDefaultLevel(SharesLevel value) {
        this.defaultLevel = value;
    }

}
