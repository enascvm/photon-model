
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmServerObjectRef complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmServerObjectRef"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmServerObjectRef" type="{urn:pbm}PbmServerObjectRef" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmServerObjectRef", propOrder = {
    "pbmServerObjectRef"
})
public class ArrayOfPbmServerObjectRef {

    @XmlElement(name = "PbmServerObjectRef")
    protected List<PbmServerObjectRef> pbmServerObjectRef;

    /**
     * Gets the value of the pbmServerObjectRef property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmServerObjectRef property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmServerObjectRef().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmServerObjectRef }
     * 
     * 
     */
    public List<PbmServerObjectRef> getPbmServerObjectRef() {
        if (pbmServerObjectRef == null) {
            pbmServerObjectRef = new ArrayList<PbmServerObjectRef>();
        }
        return this.pbmServerObjectRef;
    }

}
