
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmComplianceResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmComplianceResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmComplianceResult" type="{urn:pbm}PbmComplianceResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmComplianceResult", propOrder = {
    "pbmComplianceResult"
})
public class ArrayOfPbmComplianceResult {

    @XmlElement(name = "PbmComplianceResult")
    protected List<PbmComplianceResult> pbmComplianceResult;

    /**
     * Gets the value of the pbmComplianceResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmComplianceResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmComplianceResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmComplianceResult }
     * 
     * 
     */
    public List<PbmComplianceResult> getPbmComplianceResult() {
        if (pbmComplianceResult == null) {
            pbmComplianceResult = new ArrayList<PbmComplianceResult>();
        }
        return this.pbmComplianceResult;
    }

}
