
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ObjectSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ObjectSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="obj" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="skip" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="selectSet" type="{urn:vim25}SelectionSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ObjectSpec", propOrder = {
    "obj",
    "skip",
    "selectSet"
})
public class ObjectSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected ManagedObjectReference obj;
    protected Boolean skip;
    protected List<SelectionSpec> selectSet;

    /**
     * Gets the value of the obj property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getObj() {
        return obj;
    }

    /**
     * Sets the value of the obj property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setObj(ManagedObjectReference value) {
        this.obj = value;
    }

    /**
     * Gets the value of the skip property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isSkip() {
        return skip;
    }

    /**
     * Sets the value of the skip property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setSkip(Boolean value) {
        this.skip = value;
    }

    /**
     * Gets the value of the selectSet property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the selectSet property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSelectSet().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SelectionSpec }
     * 
     * 
     */
    public List<SelectionSpec> getSelectSet() {
        if (selectSet == null) {
            selectSet = new ArrayList<SelectionSpec>();
        }
        return this.selectSet;
    }

}
