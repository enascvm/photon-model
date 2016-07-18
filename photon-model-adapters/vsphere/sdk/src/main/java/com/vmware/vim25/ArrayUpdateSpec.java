
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayUpdateSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayUpdateSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="operation" type="{urn:vim25}ArrayUpdateOperation"/&gt;
 *         &lt;element name="removeKey" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayUpdateSpec", propOrder = {
    "operation",
    "removeKey"
})
@XmlSeeAlso({
    ClusterDasVmConfigSpec.class,
    ClusterDrsVmConfigSpec.class,
    ClusterDpmHostConfigSpec.class,
    ClusterGroupSpec.class,
    ClusterRuleSpec.class,
    StorageDrsVmConfigSpec.class,
    StorageDrsOptionSpec.class,
    VAppProductSpec.class,
    VAppPropertySpec.class,
    VAppOvfSectionSpec.class,
    VirtualMachineCpuIdInfoSpec.class
})
public class ArrayUpdateSpec
    extends DynamicData
{

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected ArrayUpdateOperation operation;
    protected Object removeKey;

    /**
     * Gets the value of the operation property.
     * 
     * @return
     *     possible object is
     *     {@link ArrayUpdateOperation }
     *     
     */
    public ArrayUpdateOperation getOperation() {
        return operation;
    }

    /**
     * Sets the value of the operation property.
     * 
     * @param value
     *     allowed object is
     *     {@link ArrayUpdateOperation }
     *     
     */
    public void setOperation(ArrayUpdateOperation value) {
        this.operation = value;
    }

    /**
     * Gets the value of the removeKey property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getRemoveKey() {
        return removeKey;
    }

    /**
     * Sets the value of the removeKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setRemoveKey(Object value) {
        this.removeKey = value;
    }

}
