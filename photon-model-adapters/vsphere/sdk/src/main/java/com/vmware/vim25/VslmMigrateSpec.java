
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VslmMigrateSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VslmMigrateSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="backingSpec" type="{urn:vim25}VslmCreateSpecBackingSpec"/&gt;
 *         &lt;element name="consolidate" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VslmMigrateSpec", propOrder = {
    "backingSpec",
    "consolidate"
})
@XmlSeeAlso({
    VslmCloneSpec.class,
    VslmRelocateSpec.class
})
public class VslmMigrateSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected VslmCreateSpecBackingSpec backingSpec;
    protected Boolean consolidate;

    /**
     * Gets the value of the backingSpec property.
     * 
     * @return
     *     possible object is
     *     {@link VslmCreateSpecBackingSpec }
     *     
     */
    public VslmCreateSpecBackingSpec getBackingSpec() {
        return backingSpec;
    }

    /**
     * Sets the value of the backingSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link VslmCreateSpecBackingSpec }
     *     
     */
    public void setBackingSpec(VslmCreateSpecBackingSpec value) {
        this.backingSpec = value;
    }

    /**
     * Gets the value of the consolidate property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isConsolidate() {
        return consolidate;
    }

    /**
     * Sets the value of the consolidate property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setConsolidate(Boolean value) {
        this.consolidate = value;
    }

}
