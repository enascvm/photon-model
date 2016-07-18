
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFileAccess complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFileAccess"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="who" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="what" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFileAccess", propOrder = {
    "who",
    "what"
})
public class HostFileAccess
    extends DynamicData
{

    @XmlElement(required = true)
    protected String who;
    @XmlElement(required = true)
    protected String what;

    /**
     * Gets the value of the who property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWho() {
        return who;
    }

    /**
     * Sets the value of the who property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWho(String value) {
        this.who = value;
    }

    /**
     * Gets the value of the what property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWhat() {
        return what;
    }

    /**
     * Sets the value of the what property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWhat(String value) {
        this.what = value;
    }

}
