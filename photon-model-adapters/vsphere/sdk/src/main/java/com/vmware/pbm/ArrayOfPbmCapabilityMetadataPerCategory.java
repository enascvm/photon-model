
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmCapabilityMetadataPerCategory complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmCapabilityMetadataPerCategory"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmCapabilityMetadataPerCategory" type="{urn:pbm}PbmCapabilityMetadataPerCategory" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmCapabilityMetadataPerCategory", propOrder = {
    "pbmCapabilityMetadataPerCategory"
})
public class ArrayOfPbmCapabilityMetadataPerCategory {

    @XmlElement(name = "PbmCapabilityMetadataPerCategory")
    protected List<PbmCapabilityMetadataPerCategory> pbmCapabilityMetadataPerCategory;

    /**
     * Gets the value of the pbmCapabilityMetadataPerCategory property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmCapabilityMetadataPerCategory property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmCapabilityMetadataPerCategory().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityMetadataPerCategory }
     * 
     * 
     */
    public List<PbmCapabilityMetadataPerCategory> getPbmCapabilityMetadataPerCategory() {
        if (pbmCapabilityMetadataPerCategory == null) {
            pbmCapabilityMetadataPerCategory = new ArrayList<PbmCapabilityMetadataPerCategory>();
        }
        return this.pbmCapabilityMetadataPerCategory;
    }

}
