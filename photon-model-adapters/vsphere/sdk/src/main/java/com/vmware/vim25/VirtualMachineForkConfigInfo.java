
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineForkConfigInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineForkConfigInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="parentEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="childForkGroupId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="childType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineForkConfigInfo", propOrder = {
    "parentEnabled",
    "childForkGroupId",
    "childType"
})
public class VirtualMachineForkConfigInfo
    extends DynamicData
{

    protected Boolean parentEnabled;
    protected String childForkGroupId;
    protected String childType;

    /**
     * Gets the value of the parentEnabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isParentEnabled() {
        return parentEnabled;
    }

    /**
     * Sets the value of the parentEnabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setParentEnabled(Boolean value) {
        this.parentEnabled = value;
    }

    /**
     * Gets the value of the childForkGroupId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getChildForkGroupId() {
        return childForkGroupId;
    }

    /**
     * Sets the value of the childForkGroupId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setChildForkGroupId(String value) {
        this.childForkGroupId = value;
    }

    /**
     * Gets the value of the childType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getChildType() {
        return childType;
    }

    /**
     * Sets the value of the childType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setChildType(String value) {
        this.childType = value;
    }

}
