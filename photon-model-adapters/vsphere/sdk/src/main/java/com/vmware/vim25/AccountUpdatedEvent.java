
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AccountUpdatedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AccountUpdatedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="spec" type="{urn:vim25}HostAccountSpec"/&gt;
 *         &lt;element name="group" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="prevDescription" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AccountUpdatedEvent", propOrder = {
    "spec",
    "group",
    "prevDescription"
})
public class AccountUpdatedEvent
    extends HostEvent
{

    @XmlElement(required = true)
    protected HostAccountSpec spec;
    protected boolean group;
    protected String prevDescription;

    /**
     * Gets the value of the spec property.
     * 
     * @return
     *     possible object is
     *     {@link HostAccountSpec }
     *     
     */
    public HostAccountSpec getSpec() {
        return spec;
    }

    /**
     * Sets the value of the spec property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostAccountSpec }
     *     
     */
    public void setSpec(HostAccountSpec value) {
        this.spec = value;
    }

    /**
     * Gets the value of the group property.
     * 
     */
    public boolean isGroup() {
        return group;
    }

    /**
     * Sets the value of the group property.
     * 
     */
    public void setGroup(boolean value) {
        this.group = value;
    }

    /**
     * Gets the value of the prevDescription property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrevDescription() {
        return prevDescription;
    }

    /**
     * Sets the value of the prevDescription property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrevDescription(String value) {
        this.prevDescription = value;
    }

}
