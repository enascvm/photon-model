
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmQueryProfileResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmQueryProfileResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmQueryProfileResult" type="{urn:pbm}PbmQueryProfileResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmQueryProfileResult", propOrder = {
    "pbmQueryProfileResult"
})
public class ArrayOfPbmQueryProfileResult {

    @XmlElement(name = "PbmQueryProfileResult")
    protected List<PbmQueryProfileResult> pbmQueryProfileResult;

    /**
     * Gets the value of the pbmQueryProfileResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmQueryProfileResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmQueryProfileResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmQueryProfileResult }
     * 
     * 
     */
    public List<PbmQueryProfileResult> getPbmQueryProfileResult() {
        if (pbmQueryProfileResult == null) {
            pbmQueryProfileResult = new ArrayList<PbmQueryProfileResult>();
        }
        return this.pbmQueryProfileResult;
    }

}
