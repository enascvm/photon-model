
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfVslmTagEntry complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfVslmTagEntry"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="VslmTagEntry" type="{urn:vim25}VslmTagEntry" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfVslmTagEntry", propOrder = {
    "vslmTagEntry"
})
public class ArrayOfVslmTagEntry {

    @XmlElement(name = "VslmTagEntry")
    protected List<VslmTagEntry> vslmTagEntry;

    /**
     * Gets the value of the vslmTagEntry property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vslmTagEntry property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVslmTagEntry().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VslmTagEntry }
     * 
     * 
     */
    public List<VslmTagEntry> getVslmTagEntry() {
        if (vslmTagEntry == null) {
            vslmTagEntry = new ArrayList<VslmTagEntry>();
        }
        return this.vslmTagEntry;
    }

}
