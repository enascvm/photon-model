
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDVSVspanConfigSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMwareDVSVspanConfigSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vspanSession" type="{urn:vim25}VMwareVspanSession"/&gt;
 *         &lt;element name="operation" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VMwareDVSVspanConfigSpec", propOrder = {
    "vspanSession",
    "operation"
})
public class VMwareDVSVspanConfigSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected VMwareVspanSession vspanSession;
    @XmlElement(required = true)
    protected String operation;

    /**
     * Gets the value of the vspanSession property.
     * 
     * @return
     *     possible object is
     *     {@link VMwareVspanSession }
     *     
     */
    public VMwareVspanSession getVspanSession() {
        return vspanSession;
    }

    /**
     * Sets the value of the vspanSession property.
     * 
     * @param value
     *     allowed object is
     *     {@link VMwareVspanSession }
     *     
     */
    public void setVspanSession(VMwareVspanSession value) {
        this.vspanSession = value;
    }

    /**
     * Gets the value of the operation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Sets the value of the operation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOperation(String value) {
        this.operation = value;
    }

}
