
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmProfileOperationOutcome complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmProfileOperationOutcome"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmProfileOperationOutcome" type="{urn:pbm}PbmProfileOperationOutcome" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmProfileOperationOutcome", propOrder = {
    "pbmProfileOperationOutcome"
})
public class ArrayOfPbmProfileOperationOutcome {

    @XmlElement(name = "PbmProfileOperationOutcome")
    protected List<PbmProfileOperationOutcome> pbmProfileOperationOutcome;

    /**
     * Gets the value of the pbmProfileOperationOutcome property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmProfileOperationOutcome property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmProfileOperationOutcome().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmProfileOperationOutcome }
     * 
     * 
     */
    public List<PbmProfileOperationOutcome> getPbmProfileOperationOutcome() {
        if (pbmProfileOperationOutcome == null) {
            pbmProfileOperationOutcome = new ArrayList<PbmProfileOperationOutcome>();
        }
        return this.pbmProfileOperationOutcome;
    }

}
