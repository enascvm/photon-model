
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDVSPvlanConfigSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMwareDVSPvlanConfigSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="pvlanEntry" type="{urn:vim25}VMwareDVSPvlanMapEntry"/&gt;
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
@XmlType(name = "VMwareDVSPvlanConfigSpec", propOrder = {
    "pvlanEntry",
    "operation"
})
public class VMwareDVSPvlanConfigSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected VMwareDVSPvlanMapEntry pvlanEntry;
    @XmlElement(required = true)
    protected String operation;

    /**
     * Gets the value of the pvlanEntry property.
     * 
     * @return
     *     possible object is
     *     {@link VMwareDVSPvlanMapEntry }
     *     
     */
    public VMwareDVSPvlanMapEntry getPvlanEntry() {
        return pvlanEntry;
    }

    /**
     * Sets the value of the pvlanEntry property.
     * 
     * @param value
     *     allowed object is
     *     {@link VMwareDVSPvlanMapEntry }
     *     
     */
    public void setPvlanEntry(VMwareDVSPvlanMapEntry value) {
        this.pvlanEntry = value;
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
