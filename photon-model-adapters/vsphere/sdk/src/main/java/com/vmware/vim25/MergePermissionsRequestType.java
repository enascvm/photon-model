
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MergePermissionsRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MergePermissionsRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="srcRoleId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="dstRoleId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MergePermissionsRequestType", propOrder = {
    "_this",
    "srcRoleId",
    "dstRoleId"
})
public class MergePermissionsRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    protected int srcRoleId;
    protected int dstRoleId;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the srcRoleId property.
     * 
     */
    public int getSrcRoleId() {
        return srcRoleId;
    }

    /**
     * Sets the value of the srcRoleId property.
     * 
     */
    public void setSrcRoleId(int value) {
        this.srcRoleId = value;
    }

    /**
     * Gets the value of the dstRoleId property.
     * 
     */
    public int getDstRoleId() {
        return dstRoleId;
    }

    /**
     * Sets the value of the dstRoleId property.
     * 
     */
    public void setDstRoleId(int value) {
        this.dstRoleId = value;
    }

}
